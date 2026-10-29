package loginsignup.signup;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Sequential JDBC equivalent of the former signup batch script. */
final class SignupDatabase {
    private SignupDatabase() { }

    static void validateUsername(String username) {
        if (username == null || !username.matches("[A-Za-z0-9_]{1,32}")) {
            throw new IllegalArgumentException("Username must contain 1-32 letters, numbers or underscores.");
        }
    }

    static String loadSchema() throws IOException {
        Path path = Path.of("src", "resources", "structure.sql");
        if (Files.isRegularFile(path)) return Files.readString(path, StandardCharsets.UTF_8);
        InputStream resource = SignupDatabase.class.getResourceAsStream("/resources/structure.sql");
        if (resource == null) resource = SignupDatabase.class.getResourceAsStream("/structure.sql");
        if (resource == null) throw new IOException("Could not find src/resources/structure.sql.");
        try (InputStream input = resource) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static void create(Connection connection, String username, String password, List<String> schema) throws SQLException {
        validateUsername(username);
        if (password == null || password.isEmpty()) throw new IllegalArgumentException("Password is empty.");
        if (schema.isEmpty()) throw new IllegalArgumentException("The database structure file is empty.");
        String step = "Checking existing user";
        boolean creationStarted = false;
        try {
            try (PreparedStatement query = connection.prepareStatement("SELECT User FROM mysql.user WHERE User = ?")) {
                query.setString(1, username);
                try (ResultSet result = query.executeQuery()) {
                    if (result.next()) throw new SQLException("USER ALREADY EXISTS", "45000", 1644);
                }
            }
            // Identifiers cannot be bound as values. The allowlist above makes this identifier safe.
            String database = "`" + username + "`";
            String account = "'" + username + "'@'%'";
            try (Statement statement = connection.createStatement()) {
                step = "Creating database";
                statement.executeUpdate("CREATE DATABASE " + database);
                creationStarted = true;
                step = "Creating user";
                // Connector/J client-side preparation safely quotes the password as a data value.
                try (PreparedStatement createUser = connection.prepareStatement("CREATE USER " + account + " IDENTIFIED BY ?")) {
                    createUser.setString(1, password);
                    createUser.executeUpdate();
                }
                step = "Granting privileges";
                statement.executeUpdate("GRANT ALL PRIVILEGES ON " + database + ".* TO " + account);
            }
            step = "Selecting new database";
            connection.setCatalog(username);
            // Connector/J Statements capture their database when they are created.
            // Do not reuse the administration Statement created under mysql above.
            try (Statement importStatement = connection.createStatement()) {
                for (int index = 0; index < schema.size(); index++) {
                    step = "Importing structure (statement " + (index + 1) + ")";
                    importStatement.execute(schema.get(index));
                }
            }
            step = "Verifying imported tables";
            verifyTables(connection, username, schema);
        } catch (SQLException error) {
            if (!creationStarted && error.getErrorCode() == 1644 && "45000".equals(error.getSQLState())) throw error;
            // Do not expose SQL text: CREATE USER errors can contain the supplied password.
            throw new SQLException(step + " failed (MySQL error " + error.getErrorCode() + ")."
                    + (creationStarted ? " Earlier setup steps may already exist; no existing data was removed." : ""),
                    error.getSQLState(), error.getErrorCode());
        }
    }

    private static void verifyTables(Connection connection, String database, List<String> schema) throws SQLException {
        java.util.Set<String> expected = new java.util.LinkedHashSet<>();
        java.util.regex.Pattern table = java.util.regex.Pattern.compile(
                "(?is)^CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(?:`([^`]+)`|([A-Za-z0-9_]+))");
        for (String sql : schema) {
            java.util.regex.Matcher match = table.matcher(sql);
            if (match.find()) expected.add(match.group(1) == null ? match.group(2) : match.group(1));
        }
        if (expected.isEmpty()) throw new SQLException("The structure file has no table definitions.", "45000");
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE'")) {
            query.setString(1, database);
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) expected.remove(result.getString(1));
            }
        }
        if (!expected.isEmpty()) {
            throw new SQLException("Tables missing from " + database + ": " + String.join(", ", expected), "45000");
        }
    }

    /** Split MySQL dump statements, retaining executable version comments and quoted semicolons. */
    static List<String> splitSchema(String script) {
        if (script.startsWith("\uFEFF")) script = script.substring(1);
        if (java.util.regex.Pattern.compile("(?im)^\\s*DELIMITER\\s+").matcher(script).find()) {
            throw new IllegalArgumentException("This structure file uses DELIMITER commands, which this importer does not support.");
        }
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean lineComment = false, blockComment = false, executableComment = false;
        for (int index = 0; index < script.length(); index++) {
            char ch = script.charAt(index);
            char next = index + 1 < script.length() ? script.charAt(index + 1) : 0;
            if (lineComment) {
                if (ch == '\n' || ch == '\r') { lineComment = false; current.append(' '); }
                continue;
            }
            if (blockComment) {
                if (ch == '*' && next == '/') {
                    if (executableComment) current.append("*/");
                    blockComment = false; executableComment = false; index++;
                    current.append(' ');
                } else if (executableComment) current.append(ch);
                continue;
            }
            if (quote != 0) {
                current.append(ch);
                if (ch == '\\' && next != 0) { current.append(next); index++; }
                else if (ch == quote) {
                    if (next == quote) { current.append(next); index++; }
                    else quote = 0;
                }
                continue;
            }
            if (ch == '\'' || ch == '"' || ch == '`') { quote = ch; current.append(ch); }
            else if (ch == '#' || (ch == '-' && next == '-' && (index + 2 == script.length() || Character.isWhitespace(script.charAt(index + 2))))) {
                lineComment = true; current.append(' ');
            } else if (ch == '/' && next == '*') {
                blockComment = true;
                executableComment = index + 2 < script.length() && script.charAt(index + 2) == '!';
                if (executableComment) current.append("/*");
                else current.append(' ');
                index++;
            } else if (ch == ';') {
                if (!current.toString().trim().isEmpty()) statements.add(current.toString().trim());
                current.setLength(0);
            } else current.append(ch);
        }
        if (quote != 0 || blockComment) throw new IllegalArgumentException("The structure file contains an unfinished quote or comment.");
        if (!current.toString().trim().isEmpty()) statements.add(current.toString().trim());
        return statements;
    }
}
