package loginsignup.portconfig;

import mainpack.MyClass;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PortConfig extends JFrame {
    private JButton backButton;
    private JButton submitButton;
    private JTextField hostField;
    private JTextField portField;
    private JPanel panel;
    private JButton scanButton;

    public PortConfig(JFrame parentWindow, boolean setup) {
        setContentPane(panel);
        if (setup) backButton.setEnabled(false);
        backButton.addActionListener(new ActionListener() {
                                         @Override
                                         public void actionPerformed(ActionEvent e) {
                                             dispose();
                                             if (parentWindow == null)
                                                 JOptionPane.showMessageDialog(PortConfig.this, "parent window is null", "error", JOptionPane.ERROR_MESSAGE);
                                             else parentWindow.setVisible(true);
                                             ;
                                         }
                                     }
        );
        submitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String port = portField.getText().trim();
                String host = hostField.getText().trim();
                if (port.isEmpty() || host.isEmpty()) {
                    JOptionPane.showMessageDialog(PortConfig.this, "please enter valid data", "error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if(!setPortInJson(port) || !setHostInJson(host))return;
                if(setup) {
                    dispose();
                }
                MyClass.loadConfiguration(setup);

            }
        });
        pack();
        scanButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                scanForMySqlServers();
            }
        });
    }

    private void scanForMySqlServers() {
        int port;
        String enteredPort = portField.getText().trim();
        try {
            port = enteredPort.isEmpty() ? 3306 : Integer.parseInt(enteredPort);
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            JOptionPane.showMessageDialog(this, "Port must be a number between 1 and 65535.",
                    "Invalid port", JOptionPane.ERROR_MESSAGE);
            return;
        }

        final int scanPort = port;
        scanButton.setEnabled(false);
        scanButton.setText("Scanning...");
        new SwingWorker<List<MySqlServer>, Void>() {
            @Override
            protected List<MySqlServer> doInBackground() throws Exception {
                return findMySqlServers(scanPort);
            }

            @Override
            protected void done() {
                scanButton.setEnabled(true);
                scanButton.setText("scan");
                try {
                    List<MySqlServer> servers = get();
                    if (servers.isEmpty()) {
                        JOptionPane.showMessageDialog(PortConfig.this,
                                "No MySQL server responded on port " + scanPort + ".",
                                "Scan complete", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        showServerSelection(servers);
                    }
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    JOptionPane.showMessageDialog(PortConfig.this,
                            "Could not scan the network: " + cause.getMessage(),
                            "Scan failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private List<MySqlServer> findMySqlServers(int port) throws Exception {
        Set<String> candidates = discoverCandidateAddresses();
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(32, Math.max(1, candidates.size())));
        try {
            List<Future<MySqlServer>> checks = new ArrayList<>();
            for (String address : candidates) {
                checks.add(executor.submit(() -> isMySqlServer(address, port)
                        ? new MySqlServer(address, port)
                        : null));
            }

            List<MySqlServer> servers = new ArrayList<>();
            for (Future<MySqlServer> check : checks) {
                MySqlServer server = check.get();
                if (server != null) servers.add(server);
            }
            servers.sort((first, second) -> compareAddresses(first.host, second.host));
            return servers;
        } finally {
            executor.shutdownNow();
        }
    }

    private Set<String> discoverCandidateAddresses() throws Exception {
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add("127.0.0.1");
        String currentHost = hostField.getText().trim();
        if (!currentHost.isEmpty()) candidates.add(currentHost);

        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        if (interfaces != null) {
            for (NetworkInterface network : Collections.list(interfaces)) {
                if (!network.isUp() || network.isLoopback()) continue;
                for (InterfaceAddress interfaceAddress : network.getInterfaceAddresses()) {
                    InetAddress address = interfaceAddress.getAddress();
                    if (!(address instanceof Inet4Address) || address.isLinkLocalAddress()) continue;
                    addSubnetCandidates(candidates, address.getAddress(), interfaceAddress.getNetworkPrefixLength());
                }
            }
        }

        addTailscalePeers(candidates);
        return candidates;
    }

    private void addSubnetCandidates(Set<String> candidates, byte[] address, short prefixLength) {
        long ip = ((address[0] & 255L) << 24)
                | ((address[1] & 255L) << 16)
                | ((address[2] & 255L) << 8)
                | (address[3] & 255L);
        int scanPrefix = Math.max(24, Math.min(32, prefixLength));
        long mask = scanPrefix == 0 ? 0 : (0xFFFFFFFFL << (32 - scanPrefix)) & 0xFFFFFFFFL;
        long network = ip & mask;
        long broadcast = network | (~mask & 0xFFFFFFFFL);

        if (scanPrefix == 32) {
            candidates.add(toAddress(ip));
            return;
        }
        for (long candidate = network + 1; candidate < broadcast; candidate++) {
            candidates.add(toAddress(candidate));
        }
    }

    private void addTailscalePeers(Set<String> candidates) {
        Process process = null;
        try {
            process = new ProcessBuilder("tailscale", "status")
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return;
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            Pattern addressPattern = Pattern.compile("(?m)^\\s*(\\d{1,3}(?:\\.\\d{1,3}){3})\\s+");
            Matcher matcher = addressPattern.matcher(output);
            while (matcher.find()) {
                String address = matcher.group(1);
                if (InetAddress.getByName(address) instanceof Inet4Address) candidates.add(address);
            }
        } catch (Exception ignored) {
            // Tailscale is optional; active local interfaces are still scanned.
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }

    private boolean isMySqlServer(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 300);
            socket.setSoTimeout(500);
            byte[] greeting = socket.getInputStream().readNBytes(5);
            return greeting.length == 5
                    && greeting[3] == 0
                    && (greeting[4] & 255) == 10;
        } catch (IOException ignored) {
            return false;
        }
    }

    private void showServerSelection(List<MySqlServer> servers) {
        DefaultListModel<MySqlServer> model = new DefaultListModel<>();
        for (MySqlServer server : servers) model.addElement(server);
        JList<MySqlServer> serverList = new JList<>(model);
        serverList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        serverList.setVisibleRowCount(Math.min(10, model.size()));

        JDialog dialog = new JDialog(this, "MySQL servers", true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.add(new JLabel("Double-click a server to use it."), BorderLayout.NORTH);
        dialog.add(new JScrollPane(serverList), BorderLayout.CENTER);

        serverList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)) {
                    MySqlServer selected = serverList.getSelectedValue();
                    if (selected != null) {
                        hostField.setText(selected.host);
                        portField.setText(String.valueOf(selected.port));
                        dialog.dispose();
                    }
                }
            }
        });
        serverList.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "selectServer");
        serverList.getActionMap().put("selectServer", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                MySqlServer selected = serverList.getSelectedValue();
                if (selected != null) {
                    hostField.setText(selected.host);
                    portField.setText(String.valueOf(selected.port));
                    dialog.dispose();
                }
            }
        });

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private static int compareAddresses(String first, String second) {
        String[] firstParts = first.split("\\.");
        String[] secondParts = second.split("\\.");
        if (firstParts.length == 4 && secondParts.length == 4) {
            for (int index = 0; index < 4; index++) {
                int comparison = Integer.compare(Integer.parseInt(firstParts[index]),
                        Integer.parseInt(secondParts[index]));
                if (comparison != 0) return comparison;
            }
        }
        return first.compareToIgnoreCase(second);
    }

    private static String toAddress(long address) {
        return ((address >>> 24) & 255) + "."
                + ((address >>> 16) & 255) + "."
                + ((address >>> 8) & 255) + "."
                + (address & 255);
    }

    private static final class MySqlServer {
        private final String host;
        private final int port;

        private MySqlServer(String host, int port) {
            this.host = host;
            this.port = port;
        }

        @Override
        public String toString() {
            return host + ":" + port;
        }
    }

    private boolean setHostInJson(String host) {
        host = host == null ? "" : host.trim();
        if (host.isEmpty() || !host.matches("[A-Za-z0-9._:\\-\\[\\]]+")) {
            JOptionPane.showMessageDialog(null, "Please enter a valid host.",
                    "Invalid host", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        java.nio.file.Path path = java.nio.file.Path.of("src", "resources", "PORT.json");
        try {
            java.nio.file.Files.createDirectories(path.getParent());
            if (java.nio.file.Files.notExists(path)) {
                java.nio.file.Files.writeString(path,
                        "{\n  \"HOST\": \"\",\n  \"PORT\": \"\"\n}\n",
                        java.nio.charset.StandardCharsets.UTF_8);
            }
            String json = java.nio.file.Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "\\\"HOST\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
            java.util.regex.Matcher matcher = pattern.matcher(json);
            if (!matcher.find()) {
                throw new java.io.IOException("HOST entry was not found in PORT.json.");
            }
            String updated = json.substring(0, matcher.start(1)) + host + json.substring(matcher.end(1));
            java.nio.file.Files.writeString(path, updated, java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException exception) {
            JOptionPane.showMessageDialog(null, "Could not update HOST: " + exception.getMessage(),
                    "PORT.json error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }

    private boolean setPortInJson(String port) {
        port = port == null ? "" : port.trim();
        int portNumber;
        try {
            portNumber = Integer.parseInt(port);
            if (portNumber < 1 || portNumber > 65535) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            JOptionPane.showMessageDialog(null, "Port must be a number between 1 and 65535.",
                    "Invalid port", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        java.nio.file.Path path = java.nio.file.Path.of("src", "resources", "PORT.json");
        try {
            java.nio.file.Files.createDirectories(path.getParent());
            if (java.nio.file.Files.notExists(path)) {
                java.nio.file.Files.writeString(path,
                        "{\n  \"HOST\": \"\",\n  \"PORT\": \"\"\n}\n",
                        java.nio.charset.StandardCharsets.UTF_8);
            }
            String json = java.nio.file.Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "\\\"PORT\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
            java.util.regex.Matcher matcher = pattern.matcher(json);
            if (!matcher.find()) {
                throw new java.io.IOException("PORT entry was not found in PORT.json.");
            }
            String updated = json.substring(0, matcher.start(1)) + portNumber + json.substring(matcher.end(1));
            java.nio.file.Files.writeString(path, updated, java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException exception) {
            JOptionPane.showMessageDialog(null, "Could not update PORT: " + exception.getMessage(),
                    "PORT.json error", JOptionPane.ERROR_MESSAGE);
            return false;
        }return true;
    }
}
