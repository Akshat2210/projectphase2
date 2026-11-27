package loginsignup.login.loggedin.billing.viewbills.editBill;

import javax.swing.*;
import java.awt.event.*;
import java.sql.*;
import mainpack.MyClass;
import javax.swing.table.DefaultTableModel;

public class EditDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JComboBox customerNameComboBox;
    private JComboBox typeComboBox;
    private JComboBox dateComboBox;
    private JPanel panel;
    private JTable table1;
    private JTextField goldRateField;
    private java.math.BigDecimal goldrate = java.math.BigDecimal.ZERO.setScale(2);
    private boolean goldRateEdited;
    private boolean recalculating;
    private final int billID;
    // Original database values are kept outside the editable table, keyed by bill item ID.
    private final java.util.Map<Integer, java.util.Map<String, Object>> originalBillDetails = new java.util.LinkedHashMap<>();
    private final java.util.List<Integer> billItemIdsByModelRow = new java.util.ArrayList<>();

    public EditDialog(int billId) {
        this.billID = billId;
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });

        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        fetchCustomerListWithSelectedCustomer();
        fetchDateListWithSelectedDate();
        fetchGoldRate();
        fetchTypeListWithSelectedType();
        fetchSelectedBillDetailsIntoTable();
        customerNameComboBox.setEnabled(false);
        dateComboBox.setEnabled(false);
        typeComboBox.setEnabled(false);
        ((javax.swing.text.AbstractDocument) goldRateField.getDocument())
                .setDocumentFilter(utils.UtilityMethods.getDocFilter());
        goldRateField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { textChanged(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { textChanged(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { }

            private void textChanged() {
                String text = goldRateField.getText().trim();
                try {
                    goldrate = (text.isEmpty() ? java.math.BigDecimal.ZERO : new java.math.BigDecimal(text))
                            .setScale(2, java.math.RoundingMode.HALF_UP);
                } catch (NumberFormatException invalid) {
                    return; // A partial decimal being typed must not throw on the UI thread.
                }
                goldRateEdited = true;
                recalculateBillRows();
            }
        });
        table1.getModel().addTableModelListener(e -> {
            if (recalculating || e.getType() != javax.swing.event.TableModelEvent.UPDATE) return;
            int column = e.getColumn();
            if (column == 3 || (column >= 5 && column <= 11) || column == 14) {
                recalculateBillRows();
            }
        });
    }

    private void fetchGoldRate() {
        try (Connection con = MyClass.createConnection();
             PreparedStatement statement = con.prepareStatement(
                     "SELECT GoldRate FROM billdetails WHERE BillID = ? ORDER BY SNo, itemid_bill LIMIT 1")) {
            statement.setInt(1, billID);
            try (ResultSet rs = statement.executeQuery()) {
                java.math.BigDecimal saved = rs.next() ? rs.getBigDecimal("GoldRate") : null;
                goldrate = (saved == null ? java.math.BigDecimal.ZERO : saved)
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                goldRateField.setText(goldrate.toPlainString());
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Could not load bill gold rate.\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private java.math.BigDecimal decimalValue(Object value) {
        if (value == null || value.toString().trim().isEmpty()) return java.math.BigDecimal.ZERO;
        try {
            return new java.math.BigDecimal(value.toString().trim());
        } catch (NumberFormatException invalid) {
            return java.math.BigDecimal.ZERO; // Same numeric fallback as NewBill.
        }
    }

    private void recalculateBillRows() {
        if (recalculating) return;
        DefaultTableModel model = (DefaultTableModel) table1.getModel();
        recalculating = true;
        try {
            for (int row = 0; row < model.getRowCount(); row++) {
                int quantity;
                Object quantityValue = model.getValueAt(row, 3);
                try {
                    quantity = quantityValue == null || quantityValue.toString().trim().isEmpty()
                            ? 0 : Integer.parseInt(quantityValue.toString().trim());
                } catch (NumberFormatException invalid) {
                    continue; // Leave invalid quantity visible for correction instead of crashing.
                }
                java.math.BigDecimal perPiece = java.math.BigDecimal.ZERO;
                for (int column = 5; column <= 11; column++) {
                    java.math.BigDecimal charge = decimalValue(model.getValueAt(row, column))
                            .setScale(2, java.math.RoundingMode.HALF_UP);
                    model.setValueAt(charge.signum() == 0 ? "" : charge, row, column);
                    perPiece = perPiece.add(charge);
                }
                java.math.BigDecimal weight = decimalValue(model.getValueAt(row, 14))
                        .setScale(3, java.math.RoundingMode.HALF_UP);
                java.math.BigDecimal rowRate = goldrate;
                // Retain individual saved rates until the user explicitly changes the shared rate.
                if (!goldRateEdited && row < billItemIdsByModelRow.size()) {
                    java.util.Map<String, Object> original = originalBillDetails.get(billItemIdsByModelRow.get(row));
                    if (original != null) rowRate = decimalValue(original.get("GoldRate"));
                }
                java.math.BigDecimal base = perPiece.multiply(java.math.BigDecimal.valueOf(quantity))
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                java.math.BigDecimal gold = weight.multiply(rowRate).setScale(2, java.math.RoundingMode.HALF_UP);
                java.math.BigDecimal total = base.add(gold).setScale(2, java.math.RoundingMode.HALF_UP);
                model.setValueAt(weight.signum() == 0 ? "" : weight, row, 14);
                model.setValueAt(base.signum() == 0 ? "" : base, row, 13);
                model.setValueAt(gold.signum() == 0 ? "" : gold, row, 15);
                model.setValueAt(total.signum() == 0 ? "" : total, row, 16);
            }
        } finally {
            recalculating = false;
        }
    }
    private void fetchCustomerListWithSelectedCustomer() {
        try (Connection con = MyClass.createConnection();
             PreparedStatement bill = con.prepareStatement("SELECT customer_name FROM bills WHERE BillID = ?");
             PreparedStatement customers = con.prepareStatement("SELECT customer_name FROM customers ORDER BY customer_name")) {
            bill.setInt(1, billID);
            String selected = null;
            try (ResultSet rs = bill.executeQuery()) {
                if (rs.next()) selected = rs.getString("customer_name");
            }
            DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
            try (ResultSet rs = customers.executeQuery()) {
                while (rs.next()) model.addElement(rs.getString("customer_name"));
            }
            // Preserve the saved name even if it is absent from the current party list.
            if (selected != null && model.getIndexOf(selected) < 0) model.addElement(selected);
            model.setSelectedItem(selected);
            customerNameComboBox.setModel(model);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Could not load bill customer.\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void fetchDateListWithSelectedDate() {
        try (Connection con = MyClass.createConnection();
             PreparedStatement statement = con.prepareStatement("SELECT date FROM bills WHERE BillID = ?")) {
            statement.setInt(1, billID);
            java.time.LocalDate selected = null;
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    Timestamp saved = rs.getTimestamp("date");
                    if (saved != null) selected = saved.toLocalDateTime().toLocalDate();
                }
            }
            java.time.format.DateTimeFormatter format = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yy");
            DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
            java.time.LocalDate today = java.time.LocalDate.now();
            for (java.time.LocalDate day = today; !day.isBefore(today.minusYears(1)); day = day.minusDays(1)) {
                model.addElement(day.format(format));
            }
            String selectedText = selected == null ? null : selected.format(format);
            // Older or future-dated bills must retain their original selected date.
            if (selectedText != null && model.getIndexOf(selectedText) < 0) model.addElement(selectedText);
            model.setSelectedItem(selectedText);
            dateComboBox.setModel(model);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Could not load bill date.\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void fetchTypeListWithSelectedType() {
        try (Connection con = MyClass.createConnection();
             PreparedStatement types = con.prepareStatement("SELECT type_name FROM ordertype ORDER BY type_name");
             PreparedStatement details = con.prepareStatement("SELECT ordertype FROM billdetails WHERE BillID = ? ORDER BY SNo, itemid_bill LIMIT 1")) {
            DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
            try (ResultSet rs = types.executeQuery()) {
                while (rs.next()) model.addElement(rs.getString("type_name"));
            }
            details.setInt(1, billID);
            String selected = null;
            try (ResultSet rs = details.executeQuery()) {
                if (rs.next()) selected = rs.getString("ordertype");
            }
            // Order type is stored per item; the combo initially shows the first item's type.
            if (selected != null && model.getIndexOf(selected) < 0) model.addElement(selected);
            model.setSelectedItem(selected);
            typeComboBox.setModel(model);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Could not load order types.\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void fetchSelectedBillDetailsIntoTable() {
        // Same visible columns and order as NewBill; database identifiers remain separate.
        String[] columns = {"SNo", "OrderSlip/quantity", "ItemName", "Quantity", "DesignID", "L", "Raw",
                "dc", "M/CM", "Rh", "Nag", "Other", "OtherDetails", "+G", "Gold(ing g)", "gold cost", "total"};
        String query = "SELECT d.*, s.slip_id AS source_slip_id, "
                + "s.quantity - s.billed_quantity AS source_remaining_quantity "
                + "FROM billdetails d LEFT JOIN order_slips s ON s.item_id = d.OrderSlipNumber "
                + "WHERE d.BillID = ? ORDER BY d.SNo, d.itemid_bill";
        try (Connection con = MyClass.createConnection();
             PreparedStatement statement = con.prepareStatement(query)) {
            statement.setInt(1, billID);
            DefaultTableModel model = new DefaultTableModel(columns, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    // Match NewBill's read-only serial, source and calculated columns.
                    if (column == 0 || column == 1 || column == 13 || column == 15 || column == 16) return false;
                    Object source = getValueAt(row, 1);
                    boolean linked = source != null && !source.toString().isEmpty();
                    return (column != 3 && column != 4) || !linked;
                }
            };
            java.util.Map<Integer, java.util.Map<String, Object>> originals = new java.util.LinkedHashMap<>();
            java.util.List<Integer> itemIds = new java.util.ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    int itemId = rs.getInt("itemid_bill");
                    java.util.Map<String, Object> saved = new java.util.LinkedHashMap<>();
                    ResultSetMetaData metadata = rs.getMetaData();
                    for (int column = 1; column <= metadata.getColumnCount(); column++) {
                        saved.put(metadata.getColumnLabel(column), rs.getObject(column));
                    }
                    originals.put(itemId, saved);
                    itemIds.add(itemId);
                    Object orderSlipItem = rs.getObject("OrderSlipNumber");
                    String source = "";
                    if (orderSlipItem != null && ((Number) orderSlipItem).longValue() != 0) {
                        Object slip = rs.getObject("source_slip_id");
                        Object remaining = rs.getObject("source_remaining_quantity");
                        source = (slip == null ? "?" : slip.toString()) + "." + orderSlipItem
                                + "/" + (remaining == null ? "?" : remaining.toString());
                    }
                    model.addRow(new Object[]{
                            rs.getObject("SNo"), source, rs.getString("ItemName"), rs.getObject("quantity"),
                            rs.getString("DesignID"), rs.getBigDecimal("LabourCost"), rs.getBigDecimal("RawCost"),
                            rs.getBigDecimal("DullChillaiCost"), rs.getBigDecimal("MeenaColorMeenaCost"),
                            rs.getBigDecimal("RhodiumCost"), rs.getBigDecimal("NagSettingCost"),
                            rs.getBigDecimal("OtherBaseCosts"), rs.getString("OtherBaseCostNotes"),
                            rs.getBigDecimal("TotalBaseCosting"), rs.getBigDecimal("GoldPlatingWeight"),
                            rs.getBigDecimal("TotalGoldCost"), rs.getBigDecimal("TotalFinalCost")
                    });
                }
            }
            table1.setModel(model);
            originalBillDetails.clear();
            originalBillDetails.putAll(originals);
            billItemIdsByModelRow.clear();
            billItemIdsByModelRow.addAll(itemIds);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Could not load bill items.\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    private void onOK() {
        if (table1.isEditing() && !table1.getCellEditor().stopCellEditing()) return;
        try {
            saveSaleBillChanges();
            JOptionPane.showMessageDialog(this, "Bill changes saved.");
            dispose();
        } catch (IllegalArgumentException validation) {
            JOptionPane.showMessageDialog(this, validation.getMessage(), "Cannot save bill", JOptionPane.ERROR_MESSAGE);
        } catch (SQLException failure) {
            JOptionPane.showMessageDialog(this, "Could not save bill changes.\n" + failure.getMessage(),
                    "Save failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Applies an edit as one sale-bill transaction.  The old effects are removed
     * first, then the edited effects are applied, so stock, order quantities,
     * balances, journals and bill details cannot be committed independently.
     */
    private void saveSaleBillChanges() throws SQLException {
        java.util.List<EditedLine> editedLines = readEditedLines();
        String orderType = selectedText(typeComboBox, "Select an order type.");

        try (Connection connection = MyClass.createConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                BillHeader oldHeader = lockSaleBill(connection);
                java.util.List<StoredLine> oldLines = lockStoredLines(connection);
                verifySameBillLines(oldLines, editedLines);

                java.math.BigDecimal oldTotal = oldLines.stream()
                        .map(StoredLine::total).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                java.math.BigDecimal editedTotal = editedLines.stream()
                        .map(EditedLine::total).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

                undoOldSaleEffects(connection, oldLines);
                applyEditedSaleEffects(connection, editedLines, oldHeader.customerName());
                updateCustomerBalance(connection, oldHeader.customerName(), editedTotal.subtract(oldTotal));
                updateSaleJournal(connection, editedTotal);
                updateBillDetails(connection, editedLines, orderType);
                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }

    private java.util.List<EditedLine> readEditedLines() {
        if (table1.getRowCount() == 0) throw new IllegalArgumentException("A bill must contain at least one item.");
        if (billItemIdsByModelRow.size() != table1.getRowCount()) {
            throw new IllegalArgumentException("Bill rows changed while editing. Close and reopen the bill.");
        }
        java.math.BigDecimal sharedGoldRate = nonNegativeDecimal(goldRateField.getText(), "Gold rate", 2);
        java.util.List<EditedLine> lines = new java.util.ArrayList<>();
        for (int row = 0; row < table1.getRowCount(); row++) {
            int quantity = nonNegativeInteger(table1.getValueAt(row, 3), "Quantity at row " + (row + 1));
            String itemName = text(table1.getValueAt(row, 2));
            String designId = text(table1.getValueAt(row, 4));
            if (itemName.length() > 100 || designId.length() > 50) {
                throw new IllegalArgumentException("Item name or design ID is too long at row " + (row + 1) + ".");
            }
            java.math.BigDecimal labour = nonNegativeDecimal(table1.getValueAt(row, 5), "Labour", 2);
            java.math.BigDecimal raw = nonNegativeDecimal(table1.getValueAt(row, 6), "Raw cost", 2);
            java.math.BigDecimal dull = nonNegativeDecimal(table1.getValueAt(row, 7), "Dull/chilai", 2);
            java.math.BigDecimal meena = nonNegativeDecimal(table1.getValueAt(row, 8), "Meena", 2);
            java.math.BigDecimal rhodium = nonNegativeDecimal(table1.getValueAt(row, 9), "Rhodium", 2);
            java.math.BigDecimal nag = nonNegativeDecimal(table1.getValueAt(row, 10), "Nag", 2);
            java.math.BigDecimal other = nonNegativeDecimal(table1.getValueAt(row, 11), "Other cost", 2);
            String notes = text(table1.getValueAt(row, 12));
            java.math.BigDecimal weight = nonNegativeDecimal(table1.getValueAt(row, 14), "Gold weight", 3);
            java.util.Map<String, Object> original = originalBillDetails.get(billItemIdsByModelRow.get(row));
            java.math.BigDecimal rate = goldRateEdited ? sharedGoldRate
                    : decimalValue(original == null ? null : original.get("GoldRate")).setScale(2, java.math.RoundingMode.HALF_UP);
            java.math.BigDecimal base = raw.add(labour).add(dull).add(meena).add(rhodium).add(nag).add(other)
                    .multiply(java.math.BigDecimal.valueOf(quantity)).setScale(2, java.math.RoundingMode.HALF_UP);
            java.math.BigDecimal gold = weight.multiply(rate).setScale(2, java.math.RoundingMode.HALF_UP);
            Long orderSlipItemId = longValue(original == null ? null : original.get("OrderSlipNumber"));
            lines.add(new EditedLine(billItemIdsByModelRow.get(row), row + 1, itemName, designId, quantity,
                    labour, raw, dull, meena, rhodium, nag, other, notes, base, rate, weight, gold,
                    base.add(gold).setScale(2, java.math.RoundingMode.HALF_UP), orderSlipItemId));
        }
        return lines;
    }

    private BillHeader lockSaleBill(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT customer_name, billType FROM bills WHERE BillID = ? FOR UPDATE")) {
            statement.setInt(1, billID);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new SQLException("The bill no longer exists.");
                if (!"sale".equals(rows.getString("billType"))) {
                    throw new SQLException("This editor can save sale bills only.");
                }
                return new BillHeader(rows.getString("customer_name"));
            }
        }
    }

    private java.util.List<StoredLine> lockStoredLines(Connection connection) throws SQLException {
        java.util.List<StoredLine> lines = new java.util.ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT itemid_bill, DesignID, quantity, OrderSlipNumber, TotalFinalCost "
                        + "FROM billdetails WHERE BillID = ? ORDER BY SNo, itemid_bill FOR UPDATE")) {
            statement.setInt(1, billID);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    lines.add(new StoredLine(rows.getInt("itemid_bill"), text(rows.getString("DesignID")),
                            rows.getInt("quantity"), longValue(rows.getObject("OrderSlipNumber")),
                            decimalValue(rows.getBigDecimal("TotalFinalCost")).setScale(2, java.math.RoundingMode.HALF_UP)));
                }
            }
        }
        if (lines.isEmpty()) throw new SQLException("The bill has no detail rows.");
        return lines;
    }

    private void verifySameBillLines(java.util.List<StoredLine> stored, java.util.List<EditedLine> edited) throws SQLException {
        java.util.Set<Integer> storedIds = new java.util.HashSet<>();
        for (StoredLine line : stored) storedIds.add(line.itemId());
        for (EditedLine line : edited) {
            if (!storedIds.remove(line.itemId())) throw new SQLException("The bill was changed by another user. Reopen it before saving.");
        }
        if (!storedIds.isEmpty()) throw new SQLException("The bill was changed by another user. Reopen it before saving.");
    }

    private void undoOldSaleEffects(Connection connection, java.util.List<StoredLine> oldLines) throws SQLException {
        for (StoredLine line : oldLines) {
            if (hasOrderSlip(line.orderSlipItemId())) {
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE order_slips SET billed_quantity = billed_quantity - ? "
                                + "WHERE item_id = ? AND billed_quantity >= ?")) {
                    update.setInt(1, line.quantity());
                    update.setLong(2, line.orderSlipItemId());
                    update.setInt(3, line.quantity());
                    requireOne(update.executeUpdate(), "The linked order slip cannot be restored.");
                }
            } else if (!line.designId().isEmpty() && !line.designId().equals("NoID")) {
                changeInventory(connection, line.designId(), line.quantity());
            }
        }
    }

    private void applyEditedSaleEffects(Connection connection, java.util.List<EditedLine> editedLines,
                                        String customer) throws SQLException {
        for (EditedLine line : editedLines) {
            if (hasOrderSlip(line.orderSlipItemId())) {
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE order_slips SET billed_quantity = billed_quantity + ? "
                                + "WHERE item_id = ? AND customer_name = ? AND billed_quantity + ? <= quantity")) {
                    update.setInt(1, line.quantity());
                    update.setLong(2, line.orderSlipItemId());
                    update.setString(3, customer);
                    update.setInt(4, line.quantity());
                    requireOne(update.executeUpdate(), "The linked order slip is no longer available for this customer or quantity.");
                }
            } else if (!line.designId().isEmpty() && !line.designId().equals("NoID")) {
                changeInventory(connection, line.designId(), -line.quantity());
            }
        }
    }

    private void changeInventory(Connection connection, String designId, int change) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE inventory SET TotalQuantity = TotalQuantity + ? WHERE TRIM(DESIGNID) = ?")) {
            update.setInt(1, change);
            update.setString(2, designId);
            requireOne(update.executeUpdate(), "Design '" + designId + "' no longer exists in inventory.");
        }
    }

    private void updateCustomerBalance(Connection connection, String customer, java.math.BigDecimal change) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE customers SET balance = COALESCE(balance, 0) + ? WHERE customer_name = ?")) {
            update.setBigDecimal(1, change);
            update.setString(2, customer);
            requireOne(update.executeUpdate(), "Customer '" + customer + "' no longer exists.");
        }
    }

    private void updateSaleJournal(Connection connection, java.math.BigDecimal total) throws SQLException {
        long customerRow = 0, salesRow = 0;
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id, accountname FROM journalentry WHERE billid = ? AND transaction_type = 'sale' FOR UPDATE")) {
            select.setInt(1, billID);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    if ("Sales".equalsIgnoreCase(rows.getString("accountname"))) salesRow = rows.getLong("id");
                    else if (customerRow == 0) customerRow = rows.getLong("id");
                    else throw new SQLException("The sale journal has more than one customer row.");
                }
            }
        }
        if (customerRow == 0 || salesRow == 0) throw new SQLException("The sale journal pair is missing or invalid.");
        updateJournalRow(connection, customerRow, java.math.BigDecimal.ZERO, total);
        updateJournalRow(connection, salesRow, total, java.math.BigDecimal.ZERO);
    }

    private void updateJournalRow(Connection connection, long rowId,
                                  java.math.BigDecimal credit, java.math.BigDecimal debit) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE journalentry SET credit = ?, debit = ? "
                        + "WHERE id = ? AND billid = ? AND transaction_type = 'sale'")) {
            update.setBigDecimal(1, credit);
            update.setBigDecimal(2, debit);
            update.setLong(3, rowId);
            update.setInt(4, billID);
            requireOne(update.executeUpdate(), "The sale journal changed while saving.");
        }
    }
    private void updateBillDetails(Connection connection, java.util.List<EditedLine> lines, String orderType) throws SQLException {
        String sql = "UPDATE billdetails SET SNo = ?, ItemName = ?, DesignID = ?, ordertype = ?, "
                + "LabourCost = ?, DullChillaiCost = ?, MeenaColorMeenaCost = ?, RhodiumCost = ?, "
                + "NagSettingCost = ?, OtherBaseCosts = ?, OtherBaseCostNotes = ?, TotalBaseCosting = ?, "
                + "GoldRate = ?, GoldPlatingWeight = ?, TotalGoldCost = ?, TotalFinalCost = ?, RawCost = ?, quantity = ? "
                + "WHERE itemid_bill = ? AND BillID = ?";
        try (PreparedStatement update = connection.prepareStatement(sql)) {
            for (EditedLine line : lines) {
                update.setInt(1, line.sno());
                update.setString(2, line.itemName());
                update.setString(3, line.designId().isEmpty() ? null : line.designId());
                update.setString(4, orderType);
                update.setBigDecimal(5, line.labour());
                update.setBigDecimal(6, line.dull());
                update.setBigDecimal(7, line.meena());
                update.setBigDecimal(8, line.rhodium());
                update.setBigDecimal(9, line.nag());
                update.setBigDecimal(10, line.other());
                update.setString(11, line.notes().isEmpty() ? null : line.notes());
                update.setBigDecimal(12, line.base());
                update.setBigDecimal(13, line.goldRate());
                update.setBigDecimal(14, line.goldWeight());
                update.setBigDecimal(15, line.goldCost());
                update.setBigDecimal(16, line.total());
                update.setBigDecimal(17, line.raw());
                update.setInt(18, line.quantity());
                update.setInt(19, line.itemId());
                update.setInt(20, billID);
                requireOne(update.executeUpdate(), "A bill detail changed while saving.");
            }
        }
    }

    private static String selectedText(JComboBox box, String error) {
        Object selected = box.getSelectedItem();
        String value = text(selected);
        if (value.isEmpty()) throw new IllegalArgumentException(error);
        return value;
    }

    private static int nonNegativeInteger(Object value, String label) {
        try {
            int result = value == null || value.toString().trim().isEmpty() ? 0 : Integer.parseInt(value.toString().trim());
            if (result < 0) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(label + " must be a non-negative whole number.");
        }
    }

    private static java.math.BigDecimal nonNegativeDecimal(Object value, String label, int scale) {
        try {
            java.math.BigDecimal result = value == null || value.toString().trim().isEmpty()
                    ? java.math.BigDecimal.ZERO : new java.math.BigDecimal(value.toString().trim());
            if (result.signum() < 0) throw new NumberFormatException();
            return result.setScale(scale, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(label + " must be a non-negative number.");
        }
    }

    private static String text(Object value) { return value == null ? "" : value.toString().trim(); }
    private static Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : value == null ? null : Long.valueOf(value.toString());
    }
    private static boolean hasOrderSlip(Long orderSlipItemId) { return orderSlipItemId != null && orderSlipItemId != 0; }
    private static void requireOne(int affected, String message) throws SQLException {
        if (affected != 1) throw new SQLException(message);
    }

    private record BillHeader(String customerName) { }
    private record StoredLine(int itemId, String designId, int quantity, Long orderSlipItemId,
                              java.math.BigDecimal total) { }
    private record EditedLine(int itemId, int sno, String itemName, String designId, int quantity,
                              java.math.BigDecimal labour, java.math.BigDecimal raw, java.math.BigDecimal dull,
                              java.math.BigDecimal meena, java.math.BigDecimal rhodium, java.math.BigDecimal nag,
                              java.math.BigDecimal other, String notes, java.math.BigDecimal base,
                              java.math.BigDecimal goldRate, java.math.BigDecimal goldWeight,
                              java.math.BigDecimal goldCost, java.math.BigDecimal total, Long orderSlipItemId) { }

    private void onCancel() {
        // add your code here if necessary
        dispose();
    }

    public static void main(String[] args) {
        EditDialog dialog = new EditDialog(0);
        dialog.pack();
        dialog.setVisible(true);
        System.exit(0);
    }
}
