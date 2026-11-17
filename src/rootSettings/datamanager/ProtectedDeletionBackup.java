package rootSettings.datamanager;

import mainpack.MyClass;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/** Encrypted SQL snapshots required before DataManager history compaction. */
public final class ProtectedDeletionBackup {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final byte[] MAGIC = "ASPBK001".getBytes(StandardCharsets.US_ASCII);
    private static final int ITERATIONS = 600_000;
    private static final int HEADER_SIZE = 8 + 4 + 16 + 12;
    private static final Path HOLDER = Path.of("passwordHolder.txt").toAbsolutePath().normalize();

    private ProtectedDeletionBackup() { }

    public static char[] recoveryPassword() throws IOException {
        if (!Files.exists(HOLDER)) {
            char[] password = generatePassword();
            Path temporary = privateTemp(HOLDER.getParent(), ".recovery-password-", ".tmp");
            try {
                Files.writeString(temporary, new String(password), StandardCharsets.US_ASCII);
                // CREATE_NEW avoids overwriting a password created by another application instance.
                try (OutputStream output = Files.newOutputStream(HOLDER, StandardOpenOption.CREATE_NEW)) {
                    // Restrict the newly created empty file before writing any password bytes.
                    if (Files.getFileAttributeView(HOLDER, AclFileAttributeView.class) != null) {
                        Files.getFileAttributeView(HOLDER, AclFileAttributeView.class).setAcl(
                                Files.getFileAttributeView(temporary, AclFileAttributeView.class).getAcl());
                    } else {
                        Files.setPosixFilePermissions(HOLDER, PosixFilePermissions.fromString("rw-------"));
                    }
                    Files.copy(temporary, output);
                } catch (FileAlreadyExistsException ignored) { }
            } finally { Arrays.fill(password, '\0'); Files.deleteIfExists(temporary); }
        }
        String value = Files.readString(HOLDER, StandardCharsets.US_ASCII).strip();
        if (!value.matches("[0-9]{16}")) throw new IOException("passwordHolder.txt must contain exactly 16 digits. It was not replaced.");
        return value.toCharArray();
    }

    static char[] generatePassword() {
        char[] password = new char[16];
        for (int i = 0; i < password.length; i++) password[i] = (char) ('0' + RANDOM.nextInt(10));
        return password;
    }

    /** Called after deletion locks are acquired and before any accounting data changes. */
    public static Path create(String database, char[] copiedPassword, Path directory) throws Exception {
        if (database == null || database.isBlank() || database.startsWith("-") || database.indexOf('\0') >= 0)
            throw new IOException("Invalid backup database.");
        if (directory == null || !Files.isDirectory(directory) || !Files.isWritable(directory)) {
            throw new IOException("Select an existing writable backup folder.");
        }
        directory = directory.toAbsolutePath().normalize();
        Path lockPath = HOLDER.resolveSibling("passwordHolder.lock");
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.tryLock()) {
            if (lock == null) throw new IOException("Another protected backup is in progress.");
            char[] current = recoveryPassword();
            try {
                if (!Arrays.equals(current, copiedPassword))
                    throw new IOException("The recovery password changed. Cancel and copy the current password again.");
                String prefix = database.replaceAll("[^A-Za-z0-9_-]", "_");
                Path destination = directory.resolve(prefix + "_before_delete_"
                        + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                        + "_" + UUID.randomUUID() + ".sql.enc");
                Path temporary = privateTemp(directory, ".encrypted-backup-", ".part");
                try {
                    byte[] expectedDigest = dumpEncrypted(database, copiedPassword, temporary);
                    // Authenticate the entire ciphertext and compare decrypted bytes before authorizing deletion.
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    try (OutputStream sink = new DigestOutputStream(OutputStream.nullOutputStream(), digest)) {
                        decrypt(temporary, copiedPassword, sink);
                    }
                    if (!MessageDigest.isEqual(expectedDigest, digest.digest())) throw new IOException("Backup verification failed.");
                    try (FileChannel file = FileChannel.open(temporary, StandardOpenOption.WRITE)) { file.force(true); }
                    Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
                    // A valid backup remains available even if rotation fails. Deletion must still stop.
                    try { rotatePassword(copiedPassword); }
                    catch (Exception error) { throw new IOException("Backup saved at " + destination + ", but password rotation failed. Deletion was blocked.", error); }
                    return destination;
                } finally { Files.deleteIfExists(temporary); }
            } finally { Arrays.fill(current, '\0'); }
        }
    }

    private static byte[] dumpEncrypted(String database, char[] recoveryPassword, Path output) throws Exception {
        if (MyClass.login == null) throw new IOException("Log in before creating a backup.");
        String user = MyClass.login.getLoginID();
        String password = MyClass.login.getPassword();
        String host = MyClass.login.getHost();
        String port = MyClass.login.getPort();
        if (user == null || password == null || host == null || host.isBlank()) throw new IOException("Missing MySQL login details.");
        int portNumber = Integer.parseInt(port);
        if (portNumber < 1 || portNumber > 65535) throw new IOException("Invalid MySQL port.");
        Path credentials = privateTemp(null, "mysql-deletion-backup-", ".cnf");
        Process process = null;
        ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "DeletionBackupTimeout"); thread.setDaemon(true); return thread;
        });
        try {
            Files.writeString(credentials, "[client]\nuser=\"" + option(user) + "\"\npassword=\"" + option(password) + "\"\n", StandardCharsets.UTF_8);
            ProcessBuilder builder = new ProcessBuilder("mysqldump.exe", "--defaults-extra-file=" + credentials,
                    "--host=" + host, "--port=" + portNumber, "--default-character-set=utf8mb4", "--set-charset",
                    "--single-transaction", "--skip-lock-tables", "--routines", "--events", "--triggers", "--hex-blob", database);
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            process = builder.start(); process.getOutputStream().close();
            final Process child = process;
            watchdog.schedule(child::destroyForcibly, 5, TimeUnit.MINUTES);
            byte[] digest;
            try (InputStream sql = process.getInputStream()) { digest = encrypt(sql, recoveryPassword, output); }
            int exit = process.waitFor();
            if (exit != 0) throw new IOException("mysqldump failed or timed out (exit " + exit + "). Nothing was deleted.");
            return digest;
        } finally {
            watchdog.shutdownNow();
            try {
                if (process != null && process.isAlive()) {
                    process.destroyForcibly(); process.waitFor();
                }
            } finally { Files.deleteIfExists(credentials); }
        }
    }

    private static String option(String value) throws IOException {
        if (value.indexOf('\0') >= 0) throw new IOException("Invalid MySQL credential.");
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    static byte[] encrypt(InputStream sql, char[] password, Path output) throws Exception {
        byte[] salt = new byte[16], nonce = new byte[12]; RANDOM.nextBytes(salt); RANDOM.nextBytes(nonce);
        byte[] header = ByteBuffer.allocate(HEADER_SIZE).put(MAGIC).putInt(ITERATIONS).put(salt).put(nonce).array();
        Cipher cipher = cipher(Cipher.ENCRYPT_MODE, password, salt, nonce, ITERATIONS); cipher.updateAAD(header);
        MessageDigest digest = MessageDigest.getInstance("SHA-256"); long total = 0;
        try (OutputStream encrypted = Files.newOutputStream(output)) {
            encrypted.write(header); byte[] buffer = new byte[65536]; int count;
            while ((count = sql.read(buffer)) != -1) {
                digest.update(buffer, 0, count); total += count;
                byte[] bytes = cipher.update(buffer, 0, count); if (bytes != null) encrypted.write(bytes);
            }
            if (total == 0) throw new IOException("mysqldump returned an empty backup.");
            encrypted.write(cipher.doFinal()); Arrays.fill(buffer, (byte) 0);
        }
        return digest.digest();
    }

    public static void decrypt(Path source, char[] password, OutputStream output) throws Exception {
        try (InputStream encrypted = Files.newInputStream(source)) {
            byte[] header = encrypted.readNBytes(HEADER_SIZE);
            if (header.length != HEADER_SIZE) throw new IOException("Incomplete encrypted backup.");
            ByteBuffer fields = ByteBuffer.wrap(header); byte[] magic = new byte[8]; fields.get(magic);
            if (!Arrays.equals(magic, MAGIC)) throw new IOException("Unsupported encrypted backup format.");
            int iterations = fields.getInt();
            if (iterations != ITERATIONS) throw new IOException("Unsupported password derivation settings.");
            byte[] salt = new byte[16], nonce = new byte[12]; fields.get(salt); fields.get(nonce);
            Cipher cipher = cipher(Cipher.DECRYPT_MODE, password, salt, nonce, iterations); cipher.updateAAD(header);
            byte[] buffer = new byte[65536]; int count;
            while ((count = encrypted.read(buffer)) != -1) {
                byte[] bytes = cipher.update(buffer, 0, count); if (bytes != null) output.write(bytes);
            }
            // doFinal verifies the GCM authentication tag; wrong passwords/corruption must fail.
            output.write(cipher.doFinal()); Arrays.fill(buffer, (byte) 0);
        }
    }

    private static Cipher cipher(int mode, char[] password, byte[] salt, byte[] nonce, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        byte[] key = null;
        try {
            key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce)); return cipher;
        } finally { spec.clearPassword(); if (key != null) Arrays.fill(key, (byte) 0); }
    }

    private static void rotatePassword(char[] previous) throws IOException {
        char[] next;
        do { next = generatePassword(); } while (Arrays.equals(previous, next));
        Path temporary = privateTemp(HOLDER.getParent(), ".recovery-password-", ".tmp");
        try {
            Files.writeString(temporary, new String(next), StandardCharsets.US_ASCII);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            Files.move(temporary, HOLDER, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Arrays.fill(next, '\0'); Files.deleteIfExists(temporary); }
    }

    private static Path privateTemp(Path directory, String prefix, String suffix) throws IOException {
        Path base = directory == null ? Path.of(System.getProperty("java.io.tmpdir")) : directory;
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("acl")) {
            UserPrincipal owner = FileSystems.getDefault().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));
            AclEntry entry = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build();
            FileAttribute<List<AclEntry>> permissions = new FileAttribute<>() {
                public String name() { return "acl:acl"; }
                public List<AclEntry> value() { return List.of(entry); }
            };
            return Files.createTempFile(base, prefix, suffix, permissions);
        }
        return Files.createTempFile(base, prefix, suffix, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
    }

    /** Recovery decrypts to a SQL file; it never imports or overwrites an existing file. */
    public static void main(String[] args) throws Exception {
        if (args.length != 2 || System.console() == null) {
            System.err.println("Run in a terminal: java ... rootSettings.datamanager.ProtectedDeletionBackup backup.sql.enc recovered.sql"); return;
        }
        Path source = Path.of(args[0]), destination = Path.of(args[1]).toAbsolutePath().normalize();
        if (Files.exists(destination)) throw new IOException("Recovery destination already exists.");
        char[] password = System.console().readPassword("16-digit recovery password: ");
        if (password == null) return;
        Path temporary = null;
        try {
            Files.createDirectories(destination.getParent());
            temporary = privateTemp(destination.getParent(), ".sql-recovery-", ".tmp");
            try (OutputStream output = Files.newOutputStream(temporary)) { decrypt(source, password, output); }
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            System.out.println("SQL recovered to " + destination + ". Import it separately into a recovery database.");
        } finally {
            Arrays.fill(password, '\0');
            if (temporary != null) Files.deleteIfExists(temporary);
        }
    }
}
