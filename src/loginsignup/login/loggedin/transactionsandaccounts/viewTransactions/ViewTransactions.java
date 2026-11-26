package loginsignup.login.loggedin.transactionsandaccounts.viewTransactions;

import loginsignup.login.loggedin.transactionsandaccounts.TransactionsScreen;
import mainpack.MyClass;
import utils.UtilityMethods;
import loginsignup.login.loggedin.processingmanagement.send.BrowserPdfPrint;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ViewTransactions extends JFrame {
    private JButton backButton;
    private JComboBox<String> dateComboBox;
    private JComboBox<String> partyNameComboBox;
    private JPanel panel;
    private JTable transactionsTable;
    private JButton printPanaButton;
    DefaultTableModel tableModel;
    private final TransactionsScreen parentWindow;

    public ViewTransactions(TransactionsScreen parentWindow) {

        this.parentWindow = parentWindow;
        printPanaButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                generateAndPrintPanaPDF(askDate());

            }
        });
    }

    private TransactionPDF.DateRange askDate() {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT);
        LocalDate initial = LocalDate.now();
        if (dateComboBox.getSelectedIndex() > 0) {
            try {
                initial = LocalDate.parse(String.valueOf(dateComboBox.getSelectedItem()), DateTimeFormatter.ofPattern("dd-MM-yy"));
            } catch (java.time.format.DateTimeParseException ignored) { }
        }
        JTextField from = new JTextField(format.format(initial), 12);
        JTextField to = new JTextField(format.format(initial), 12);
        JPanel dates = new JPanel(new java.awt.GridLayout(0, 2, 8, 8));
        dates.add(new JLabel("पहली तारीख (DD-MM-YYYY)"));
        dates.add(from);
        dates.add(new JLabel("अंतिम तारीख (DD-MM-YYYY)"));
        dates.add(to);
        while (JOptionPane.showConfirmDialog(this, dates, "पाना की तारीखें - सभी खाते",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                return new TransactionPDF.DateRange(LocalDate.parse(from.getText().trim(), format),
                        LocalDate.parse(to.getText().trim(), format));
            } catch (IllegalArgumentException | java.time.format.DateTimeParseException invalidDate) {
                JOptionPane.showMessageDialog(this, "कृपया सही तारीखें दर्ज करें। अंतिम तारीख पहली तारीख से पहले न हो।");
            }
        }
        return null;
    }

    private void generateAndPrintPanaPDF(TransactionPDF.DateRange range) {
        if (range == null) return;
        Path pdf = null;
        printPanaButton.setEnabled(false);
        try {
            List<TransactionPDF.Entry> entries = fetchPanaEntries(range);
            pdf = TransactionPDF.save(range, entries, MyClass.login.getDatabase(), Path.of("data"));
            BrowserPdfPrint.openPrintDialog(pdf);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            JOptionPane.showMessageDialog(this, "PDF सहेज दी गई है। इसे खोलकर Ctrl+P दबाएँ।\n" + pdf);
        } catch (Exception failure) {
            utils.LogUtils.logError(failure);
            JOptionPane.showMessageDialog(this, pdf == null
                    ? "पाना PDF नहीं बन सकी।\n" + failure.getMessage()
                    : "PDF सहेज दी गई है। इसे खोलकर प्रिंट करें।\n" + pdf + "\n" + failure.getMessage());
        } finally {
            printPanaButton.setEnabled(true);
        }
    }

    /** Additional read-only export queries; the screen query and all accounting writes are unchanged. */
    private List<TransactionPDF.Entry> fetchPanaEntries(TransactionPDF.DateRange range) throws SQLException {
        List<TransactionPDF.Entry> entries = new ArrayList<>();
        String payments = "SELECT customer_name, amount, date, remark FROM transactions "
                + "WHERE date >= ? AND date <= ? ORDER BY date, transaction_id";
        String journals = "SELECT id, date, accountname, remark, debit, credit, journal_group_id FROM journalentry "
                + "WHERE transaction_type = 'direct' AND date >= ? AND date <= ? ORDER BY date, id";
        try (Connection connection = MyClass.createConnection();
             PreparedStatement paymentStatement = connection.prepareStatement(payments);
             PreparedStatement journalStatement = connection.prepareStatement(journals)) {
            for (PreparedStatement statement : new PreparedStatement[]{paymentStatement, journalStatement}) {
                statement.setDate(1, java.sql.Date.valueOf(range.from()));
                statement.setDate(2, java.sql.Date.valueOf(range.to()));
            }
            try (ResultSet rows = paymentStatement.executeQuery()) {
                while (rows.next()) {
                    entries.add(new TransactionPDF.Entry(rows.getDate("date").toLocalDate(), rows.getBigDecimal("amount"),
                            rows.getString("customer_name"), rows.getString("remark"), ""));
                }
            }
            Map<String, List<PanaJournalLine>> groups = new LinkedHashMap<>();
            try (ResultSet rows = journalStatement.executeQuery()) {
                while (rows.next()) {
                    String group = rows.getString("journal_group_id");
                    if (group == null || group.isBlank()) {
                        throw new SQLException("Journal " + rows.getInt("id") + " has no pair/group reference. Pair it before printing Pana.");
                    }
                    groups.computeIfAbsent(group, key -> new ArrayList<>()).add(new PanaJournalLine(
                            rows.getDate("date").toLocalDate(), rows.getString("accountname"), rows.getString("remark"),
                            rows.getBigDecimal("debit"), rows.getBigDecimal("credit")));
                }
            }
            for (Map.Entry<String, List<PanaJournalLine>> group : groups.entrySet()) {
                addJournalPanaEntries(entries, group.getKey(), group.getValue());
            }
        }
        entries.sort(java.util.Comparator.comparing(TransactionPDF.Entry::date));
        return entries;
    }

    private static void addJournalPanaEntries(List<TransactionPDF.Entry> entries, String group,
                                              List<PanaJournalLine> pair) throws SQLException {
        if (pair.size() != 2) throw new SQLException("Journal group " + group + " must contain one debit/credit pair.");
        PanaJournalLine first = pair.get(0), second = pair.get(1);
        for (PanaJournalLine line : pair) {
            if (line.debit == null || line.credit == null || line.debit.signum() < 0 || line.credit.signum() < 0
                    || (line.debit.signum() > 0 && line.credit.signum() > 0)) {
                throw new SQLException("Invalid debit/credit amounts in journal group " + group);
            }
        }
        if (!first.date.equals(second.date)
                || first.debit.add(second.debit).compareTo(first.credit.add(second.credit)) != 0) {
            throw new SQLException("Journal group " + group + " is not a balanced pair on one date.");
        }
        // Non-Cash credit => virtual Cash debit (aavak); non-Cash debit => Cash credit (jaavak).
        // If the pair already contains Cash, its non-Cash line produces the single real Cash movement.
        for (int i = 0; i < pair.size(); i++) {
            PanaJournalLine line = pair.get(i);
            if (line.account != null && line.account.trim().equalsIgnoreCase("Cash")) continue;
            entries.add(new TransactionPDF.Entry(line.date, line.credit.subtract(line.debit), line.account,
                    line.remark, pair.get(1 - i).account));
        }
    }

    private record PanaJournalLine(LocalDate date, String account, String remark, BigDecimal debit, BigDecimal credit) { }

    public void init() {
        setContentPane(panel);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        UtilityMethods.generateAndAddDates(dateComboBox, true);
        UtilityMethods.generateAndAddNames(partyNameComboBox);
        javax.swing.Timer filterSelectionDelay = new javax.swing.Timer(200, e -> getData());
        filterSelectionDelay.setRepeats(false);
        dateComboBox.addActionListener(e -> filterSelectionDelay.restart());
        partyNameComboBox.addActionListener(e -> filterSelectionDelay.restart());
        backButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                parentWindow.setVisible(true);
                setVisible(false);
                for (ActionListener listener : partyNameComboBox.getActionListeners())
                    partyNameComboBox.removeActionListener(listener);
                for (ActionListener listener : dateComboBox.getActionListeners())
                    dateComboBox.removeActionListener(listener);
            }
        });

        String[] columnNames = new String[]{"party name", "date", "amount", "remark", "billID", "transaction_id"};
        tableModel = new DefaultTableModel(columnNames, 0){
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        transactionsTable.setModel(tableModel);
        getData();

        setExtendedState(JFrame.MAXIMIZED_BOTH);
    }

    private void getData() {

        tableModel.setRowCount(0);
        String query = "Select transaction_id,customer_name,amount,date,remark,billid from transactions ";
        boolean dateNotZero = dateComboBox.getSelectedIndex() != 0;
        boolean nameNotZero = partyNameComboBox.getSelectedIndex() != 0;
        String date = "";
        String customerName = "";
        if (dateNotZero || nameNotZero) {
            query += "WHERE ";

            if (dateNotZero) {
                date = dateComboBox.getSelectedItem() == null ? "" : dateComboBox.getSelectedItem().toString();

                query += "date = ? ";
                if (nameNotZero) {
                    customerName = partyNameComboBox.getSelectedItem() == null ? "" : partyNameComboBox.getSelectedItem().toString();
                    query += "AND customer_name = ?";
                }
            } else {
                customerName = partyNameComboBox.getSelectedItem().toString();
                query += "customer_name = ?";
            }
        }


        try (Connection con=MyClass.createConnection();PreparedStatement stmt=con.prepareStatement(query,
                ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)){

            if (dateNotZero || nameNotZero) {
                if (dateNotZero) {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yy");
                    LocalDate localDate = LocalDate.parse(date, formatter);
                    java.sql.Date sqlDate = java.sql.Date.valueOf(localDate);
                    stmt.setDate(1, sqlDate);
                    if (nameNotZero) stmt.setString(2, utils.TextNormalization.trimNullable(customerName));
                } else {
                    stmt.setString(1, utils.TextNormalization.trimNullable(customerName));
                }
            }
            ResultSet rs = stmt.executeQuery();
            rs.afterLast();
            while (rs.previous()) {
                TableColumnModel columnModel = transactionsTable.getColumnModel();

                String[] values = new String[6];

                values[columnModel.getColumnIndex("amount")] = rs.getString("amount");
                values[columnModel.getColumnIndex("transaction_id")] = rs.getString("transaction_id");
                values[columnModel.getColumnIndex("date")] = rs.getString("date");
                values[columnModel.getColumnIndex("remark")] = utils.TextNormalization.stripTrailingNullable(rs.getString("remark"));
                values[columnModel.getColumnIndex("party name")] = utils.TextNormalization.trimNullable(rs.getString("customer_name"));
                values[columnModel.getColumnIndex("billID")] = rs.getString("billid");

                tableModel.addRow(values);

            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
