package loginsignup.login.loggedin.billing.newBill;

import mainpack.MyClass;
import utils.DBStructure;
import utils.UtilityMethods;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.event.*;
import java.sql.*;
import java.util.HashMap;
import java.util.Vector;

public class SearchResultWindow extends JFrame {
    private static final int DESIGN_ID_INDEX = 0;
    private JPanel panel1;
    private String mode="inventory";

    public void setSearchFlag(boolean searchFlag) {
        this.searchFlag = searchFlag;
    }

    public boolean isSearchFlag() {
        return searchFlag;
    }

    private boolean searchFlag = false;

    private JButton backButton;
    private JButton button2;
    private JTable dataTable;
    //    private JLabel slipID;
    private JLabel cutomerName;
    private JLabel panaTypeLabel;
    private JComboBox<String> selectionComboBox;
    int orderSlipID;

    public DefaultTableModel getTableModel() {
        return (DefaultTableModel) dataTable.getModel();
    }

    public String getCutomerName() {
        String name;
        try (Connection con = MyClass.createConnection(); Statement stmt = con.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("select " + DBStructure.ORDER_SLIPS_CUSTOMER_NAME + " from " + DBStructure.ORDER_SLIPS_TABLE + " where " + DBStructure.ORDER_SLIPS_SLIP_ID + "=" + orderSlipID + ";");) {
                if (rs.next()) {
                    name = rs.getString(1);
                    return name;
                } else {
                    JOptionPane.showMessageDialog(null, "There is no such order slip id in table");
                    return null;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }


    }

    public SearchResultWindow() {

    }

    public void init() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setTitle("CREATE BILL");
        setContentPane(panel1);
        selectionComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selection = UtilityMethods.parseString((selectionComboBox.getSelectedItem()));
                if (selection.equals("Inventory")) {
                    mode="inventory";
                    fetchInventory((DefaultTableModel) dataTable.getModel());
                } else {
                    mode="orderslip";
                    SearchResultWindow.this.orderSlipID = UtilityMethods.parseInt(selectionComboBox.getSelectedItem());
                    fetchOrderSlip((DefaultTableModel) dataTable.getModel());
                }
            }
        });
        backButton.addActionListener(e -> {
            MyClass.newBill.setExtendedState(JFrame.MAXIMIZED_BOTH);
            MyClass.newBill.getBackButton().setEnabled(true);
            MyClass.newBill.getSubmitButton().setEnabled(true);

            dispose();
        });
        dataTable.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    activateSelection();
                }
            }
        });

    }

    private void fetchInventory(DefaultTableModel model) {
        model.setRowCount(0);

        String query = "SELECT "
                + DBStructure.INVENTORY_DESIGN_ID + ", "
                + DBStructure.INVENTORY_ITEM_NAME + ", "
                + DBStructure.INVENTORY_TOTAL_QUANTITY + ", "
                + DBStructure.INVENTORY_SELL_PRICE + ", "
                + DBStructure.INVENTORY_BUY_PRICE
                + " FROM " + DBStructure.INVENTORY_TABLE
                + " ORDER BY " + DBStructure.INVENTORY_DESIGN_ID;

        // Design ID -> quantity already selected directly from inventory in this bill.
        HashMap<String, Integer> alreadyAddedToBill = new HashMap<>();

        JTable billTable = MyClass.newBill.getBillTable();
        DefaultTableModel billModel = (DefaultTableModel) billTable.getModel();

        for (int row = 0; row < billModel.getRowCount(); row++) {
            Object orderSlipValue = billModel.getValueAt(
                    row,
                    NewBill.ORDER_SLIP_QUANTITY_INDEX
            );

            // Do not subtract order-slip items from inventory availability.
            if (orderSlipValue != null && !orderSlipValue.toString().trim().isEmpty()) {
                continue;
            }

            Object designIdValue = billModel.getValueAt(row, NewBill.DESIGN_ID_INDEX);
            Object quantityValue = billModel.getValueAt(row, NewBill.QUANTITY_INDEX);

            if (designIdValue == null || designIdValue.toString().trim().isEmpty()
                    || quantityValue == null || quantityValue.toString().trim().isEmpty()) {
                continue;
            }

            try {
                String designId = designIdValue.toString().trim();
                int quantity = Integer.parseInt(quantityValue.toString().trim());

                alreadyAddedToBill.merge(designId, quantity, Integer::sum);
            } catch (NumberFormatException ignored) {
                // Skip incomplete or invalid rows.
            }
        }

        try (
                Connection con = MyClass.createConnection();
                PreparedStatement statement = con.prepareStatement(query);
                ResultSet resultSet = statement.executeQuery()
        ) {
            while (resultSet.next()) {
                String designId = resultSet.getString(DBStructure.INVENTORY_DESIGN_ID);

                int databaseQuantity = resultSet.getInt(
                        DBStructure.INVENTORY_TOTAL_QUANTITY
                );

                int alreadyUsedInBill = alreadyAddedToBill.getOrDefault(designId, 0);

                int remainingQuantity = databaseQuantity - alreadyUsedInBill;
                int row = model.getRowCount();
                model.setRowCount(row + 1);

                model.setValueAt("", row, 0);
                model.setValueAt(designId, row, 1);
                model.setValueAt(
                        resultSet.getString(DBStructure.INVENTORY_ITEM_NAME),
                        row,
                        2
                );
                model.setValueAt(remainingQuantity, row, 3);
                model.setValueAt("", row, 4); // Plating is not applicable to inventory.
                model.setValueAt(
                        resultSet.getBigDecimal(DBStructure.INVENTORY_BUY_PRICE),
                        row,
                        5
                );
                model.setValueAt("", row, 6);
            }
        } catch (SQLException exception) {
            JOptionPane.showMessageDialog(
                    SearchResultWindow.this,
                    "Could not load inventory: " + exception.getMessage(),
                    "Inventory Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }
    private Vector<Integer> sqlOrderSlipUpdate(int BID) //this will also writen values to append to the bill
    {
        Vector<Integer> detailsToPush = new Vector<>();

        DefaultTableModel model = (DefaultTableModel) dataTable.getModel();
        int selectedRow = dataTable.getSelectedRow();
        String itemName = model.getValueAt(selectedRow, model.findColumn("Item Name")).toString();
        int maxQuantity = Integer.parseInt(model.getValueAt(selectedRow, model.findColumn("Quantity")).toString());

        int selectedQuantity = showOrderSlipPrompt(maxQuantity, itemName);

        if (selectedQuantity < 0) return null;
        String query = "UPDATE " + DBStructure.ORDER_SLIPS_TABLE + " SET " + DBStructure.ORDER_SLIPS_BILLED_QUANTITY + " = " + DBStructure.ORDER_SLIPS_BILLED_QUANTITY + " + ? WHERE " + DBStructure.ORDER_SLIPS_SLIP_ID + " = ? AND " + DBStructure.ORDER_SLIPS_ITEM_ID + " = ?";
        try {
            Connection con = MyClass.newBill.getTransacTemp();
            PreparedStatement statement = con.prepareStatement(query);
            int itemid = snoToItemIdMap.get(Integer.parseInt(dataTable.getValueAt(dataTable.getSelectedRow(), ((DefaultTableModel) dataTable.getModel()).findColumn("sno")).toString()));
            statement.setInt(1, selectedQuantity);
            statement.setInt(2, BID);
            statement.setInt(3, itemid);
            statement.executeUpdate();
            detailsToPush.add(itemid);
            detailsToPush.add(selectedQuantity);
            fetchOrderSlip((DefaultTableModel) dataTable.getModel());

        } catch (SQLException | NullPointerException e) {
            throw new RuntimeException();
        }
        return detailsToPush;
    }

    int showOrderSlipPrompt(int maxQuantity, String itemName) {
        String quantity = JOptionPane.showInputDialog(null, "How many " + itemName + "s would you like to include?", "Quantity Selection", JOptionPane.QUESTION_MESSAGE);

        try {
            int selectedQuantity = Integer.parseInt(quantity);
            if (selectedQuantity > 0 && selectedQuantity <= maxQuantity) {
                JOptionPane.showMessageDialog(null, selectedQuantity + " " + itemName + "(s) added to the bill.");
                return selectedQuantity;
            } else {
                JOptionPane.showMessageDialog(null, "Invalid quantity! Please enter a value between 1 and " + maxQuantity, "Error", JOptionPane.ERROR_MESSAGE);
                return -2;
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(null, "Please enter a valid number.", "Error", JOptionPane.ERROR_MESSAGE);
            return -1;
        }
    }
    int showInventoryPrompt(int availableQuantity, String itemName) {
        String quantityText = JOptionPane.showInputDialog(
                this,
                "How many " + itemName + "(s) would you like to include?\n"
                        + "Available quantity: " + availableQuantity,
                "Quantity Selection",
                JOptionPane.QUESTION_MESSAGE
        );

        if (quantityText == null) {
            return -1; // User cancelled.
        }

        try {
            int selectedQuantity = Integer.parseInt(quantityText.trim());

            if (selectedQuantity <= 0) {
                JOptionPane.showMessageDialog(
                        this,
                        "Please enter a quantity greater than 0.",
                        "Invalid quantity",
                        JOptionPane.ERROR_MESSAGE
                );
                return -1;
            }

            if (selectedQuantity > availableQuantity) {
                int remainingStock = availableQuantity - selectedQuantity;

                JOptionPane.showMessageDialog(
                        this,
                        "Warning: This sale will make stock negative.\n"
                                + "Stock after adding this item: " + remainingStock,
                        "Negative Stock Warning",
                        JOptionPane.WARNING_MESSAGE
                );
            }

            return selectedQuantity;

        } catch (NumberFormatException exception) {
            JOptionPane.showMessageDialog(
                    this,
                    "Please enter a valid whole number.",
                    "Invalid quantity",
                    JOptionPane.ERROR_MESSAGE
            );
            return -1;
        }
    }
    public String getPanaType() {
        try (Connection con = MyClass.createConnection(); Statement statement = con.createStatement()) {
            try (ResultSet rs = statement.executeQuery("select " + DBStructure.ORDER_SLIPS_SLIP_TYPE + " from " + DBStructure.ORDER_SLIPS_TABLE + " where " + DBStructure.ORDER_SLIPS_SLIP_ID + "=" + orderSlipID);) {
                if (rs.next()) {
                    return rs.getString(1);
                } else {
                    JOptionPane.showMessageDialog(this, "error occured panatype not found");
                    return null;
                }
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "SQL EXCEPTION OCCURED");
            throw new RuntimeException();

        }

    }

    public SearchResultWindow(int modeID) {// -INVENTORY_MODE FOR  getting started with inventory
        init();
        String customerName = "";
        selectionComboBox.setModel(generateSlipListModel(customerName));
        if(modeID!=NewBill.INVENTORY_MODE){
            this.orderSlipID = modeID;
//        slipIDComboBox.setText("order slip id: " + ID);
            cutomerName.setText(getCutomerName());
            panaTypeLabel.setText(getPanaType());
        }

        String[] columnNames = {"sno", "design id", "Item Name", "Quantity", "Plating", "Raw Material Cost", "Other Details"};//jtable content
        DefaultTableModel model = new DefaultTableModel(columnNames, 1) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        dataTable.setModel(model);
        dataTable.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && dataTable.getSelectedRow() != -1) {
                    activateSelection();
                }
            }
        });
        fetchInventory(model);

        pack();

    }

    private void activateSelection() {

       if(mode.contentEquals("orderslip")) {
           MyClass.newBill.notThroughOrderSlip = false;
           try {
               orderSlipItem(sqlOrderSlipUpdate(orderSlipID));
           } finally {
               MyClass.newBill.notThroughOrderSlip = true;
           }
       }else if(mode.contentEquals("inventory")) {
           pushInventoryData();
       }
    }

    private void pushInventoryData() {
        int selectedRow = dataTable.getSelectedRow();

        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(
                    this,
                    "Please select an inventory item first.",
                    "No item selected",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        DefaultTableModel inventoryModel = (DefaultTableModel) dataTable.getModel();
        String designId = String.valueOf(inventoryModel.getValueAt(selectedRow, 1));
        String itemName = String.valueOf(inventoryModel.getValueAt(selectedRow, 2));

        int availableQuantity = Integer.parseInt(
                String.valueOf(inventoryModel.getValueAt(selectedRow, 3))
        );

        Object rawCost = inventoryModel.getValueAt(selectedRow, 5);
        int selectedQuantity = showInventoryPrompt(availableQuantity, itemName);
        if (selectedQuantity <= 0) {
            return; // User cancelled or entered an invalid quantity.
        }

        JTable billTable = MyClass.newBill.getBillTable();
        DefaultTableModel billModel = (DefaultTableModel) billTable.getModel();

        // NewBill keeps its final row blank for entering the next item.
        int billRow = billModel.getRowCount() - 1;

        setSearchFlag(true);
        try {
            billModel.setValueAt(designId, billRow, NewBill.DESIGN_ID_INDEX);
            billModel.setValueAt(itemName, billRow, NewBill.ITEM_NAME_INDEX);
            billModel.setValueAt(rawCost, billRow, NewBill.RAW_INDEX);
            billModel.setValueAt(selectedQuantity, billRow, NewBill.QUANTITY_INDEX);

            billModel.fireTableDataChanged();
        } finally {
            setSearchFlag(false);
            fetchInventory(inventoryModel);
        }
    }

    public DefaultComboBoxModel<String> generateSlipListModel(String customerName) {
        DefaultComboBoxModel<String> comboBoxModel = new DefaultComboBoxModel<>();
        String query = "select " + DBStructure.ORDER_SLIPS_MAIN_SLIP_ID + " from " + DBStructure.ORDER_SLIPS_MAIN_TABLE + " where " + DBStructure.ORDER_SLIPS_MAIN_CUSTOMER_NAME + "=?";
        comboBoxModel.addElement("Inventory");
        try (Connection con = MyClass.createConnection(); PreparedStatement pstmt = con.prepareStatement(query)) {
            pstmt.setString(1, customerName);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    System.out.println("data added " + rs.getString(DBStructure.ORDER_SLIPS_MAIN_SLIP_ID));
                    comboBoxModel.addElement(rs.getString(DBStructure.ORDER_SLIPS_MAIN_SLIP_ID));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return comboBoxModel;
    }

    private void orderSlipItem(Vector<Integer> detailsToPush) {//detailstopush-> item id at index 0 and quantity at index 1
        if (detailsToPush == null) return;
        if (detailsToPush.size() < 2) {
            JOptionPane.showMessageDialog(null, "Invalid details to push!");
            return;
        }

        Connection con = MyClass.newBill.getTransacTemp();
        int itemId = detailsToPush.get(0);
        int quantity = detailsToPush.get(1);

        JTable billTable = MyClass.newBill.getBillTable();
        DefaultTableModel model = (DefaultTableModel) billTable.getModel();

        String query = "SELECT "
                + DBStructure.ORDER_SLIPS_QUANTITY + ", "
                + DBStructure.ORDER_SLIPS_BILLED_QUANTITY + ", "
                + DBStructure.ORDER_SLIPS_SLIP_ID + ", "
                + DBStructure.ORDER_SLIPS_ITEM_NAME + ", "
                + DBStructure.ORDER_SLIPS_DESIGN_ID + ", "
                + DBStructure.ORDER_SLIPS_RAW_MATERIAL_PRICE
                + " FROM " + DBStructure.ORDER_SLIPS_TABLE
                + " WHERE " + DBStructure.ORDER_SLIPS_ITEM_ID + " = ?";
        try (PreparedStatement pstmt = con.prepareStatement(query)) {
            pstmt.setInt(1, itemId);
            ResultSet rs = pstmt.executeQuery();
            int lastrow = billTable.getRowCount() - 1;


            if (rs.next()) {
                TableColumnModel columnModel = billTable.getColumnModel();
                setUpdateThroughSlip(true);
                MyClass.newBill.setItemID(itemId);
                setSearchFlag(true);
                model.setValueAt(rs.getString("slip_id") + "." + itemId + "/" + (rs.getInt("quantity") - rs.getInt("billed_quantity")), lastrow, columnModel.getColumnIndex("OrderSlip/quantity"));
                model.setValueAt(String.valueOf(quantity), lastrow, columnModel.getColumnIndex("Quantity"));
                model.setValueAt(rs.getString("item_name"), lastrow, columnModel.getColumnIndex("ItemName"));
                model.setValueAt(rs.getString("raw_material_price"), lastrow, columnModel.getColumnIndex("Raw"));
                model.setValueAt(rs.getString("design_id"), lastrow, columnModel.getColumnIndex("DesignID"));
                model.fireTableDataChanged();
                setSearchFlag(false);
            } else {
                JOptionPane.showMessageDialog(null, "Item not found in order_slips!");
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Database error occurred!");
            throw new RuntimeException();

        }

    }

    private void setUpdateThroughSlip(boolean b) {
        MyClass.newBill.updateThroughSlip = b;
    }


    HashMap<Integer, Integer> snoToItemIdMap;

    public void fetchOrderSlip(DefaultTableModel model) {
        model.setRowCount(0);
        String query = "SELECT "
                + DBStructure.ORDER_SLIPS_CUSTOMER_NAME + ", "
                + DBStructure.ORDER_SLIPS_SLIP_ID + ", "
                + DBStructure.ORDER_SLIPS_ITEM_ID + ", "
                + DBStructure.ORDER_SLIPS_SNO + ", "
                + DBStructure.ORDER_SLIPS_DESIGN_ID + ", "
                + DBStructure.ORDER_SLIPS_ITEM_NAME + ", "
                + DBStructure.ORDER_SLIPS_QUANTITY + ", "
                + DBStructure.ORDER_SLIPS_PLATING_GRAMS + ", "
                + DBStructure.ORDER_SLIPS_RAW_MATERIAL_PRICE + ", "
                + DBStructure.ORDER_SLIPS_OTHER_DETAILS + ", "
                + DBStructure.ORDER_SLIPS_BILLED_QUANTITY
                + " FROM " + DBStructure.ORDER_SLIPS_TABLE
                + " WHERE " + DBStructure.ORDER_SLIPS_SLIP_ID + " = ? "
                + " ORDER BY " + DBStructure.ORDER_SLIPS_ITEM_ID;
        try {
            PreparedStatement pstmt = MyClass.newBill.getTransacTemp().prepareStatement(query);
            pstmt.setInt(1, orderSlipID);
            ResultSet rs = pstmt.executeQuery();
            snoToItemIdMap = new HashMap<>();

            while (rs.next()) {

                int sno = rs.getInt("sno");
                int itemId = rs.getInt("item_id");

                snoToItemIdMap.put(sno, itemId); //
                model.addRow(new Object[]{
                        rs.getString("sno"), rs.getString("design_id"), rs.getString("item_name"), (rs.getInt("quantity") - rs.getInt("billed_quantity")), rs.getBigDecimal("plating_grams"), rs.getBigDecimal("raw_material_price"), rs.getString("other_details")});// now this
            }
        } catch (SQLException e) {
            throw new RuntimeException();

        }

    }

    public void setComboBoxModel(DefaultComboBoxModel<String> comboBoxModel) {
        selectionComboBox.setModel(comboBoxModel);
    }

    public void setInventorySelected() {
        selectionComboBox.setSelectedIndex(0);
    }
}
