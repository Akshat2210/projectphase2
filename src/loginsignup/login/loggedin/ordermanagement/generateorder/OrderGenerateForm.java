package loginsignup.login.loggedin.ordermanagement.generateorder;

import mainpack.MyClass;
import utils.DBStructure;
import utils.CONSTANTS;
import utils.LogUtils;
import loginsignup.login.loggedin.processingmanagement.send.BrowserPdfPrint;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;
import java.awt.event.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static utils.DBStructure.*;
import static utils.UtilityMethods.*;

public class OrderGenerateForm extends JFrame {

    HashMap<Integer, String> snoToDetailsMap;
    private JPanel panel;
    private JButton backButton;
    TableModelListener modelListener;
    private JComboBox<String> customerNameComboBox;
    private JComboBox<String> orderSlipTypeComboBox;
    private JTable orderSlipTable;
    private JButton submitButton;
    private JButton resetFormButton;
    private JComboBox<String> dateComboBox;
    private JLabel slipIDLabel;
    private JButton inventorySelectButton;
    //THE BELOW CODE IS FOR COLUMN NAMES CONSTANTS
    public static final int DESIGN_ID_INDEX = 0;
    public static final int ITEM_NAME_INDEX = 1;
    public static final int QUANTITY_INDEX = 2;
    public static final int PLATING_INDEX = 3;
    public static final int RAW_MATERIAL_COST_INDEX = 4;
    public static final int OTHER_DETAILS_INDEX = 5;

    Connection orderSlipConnectionObject;

    Vector<Integer[]> listOfDisabledCells;
    private Vector<Integer> listOfDisabledColumn;

    public void fireTableRowChange() {
        DefaultTableModel model1 = (DefaultTableModel) orderSlipTable.getModel();
        model1.fireTableRowsInserted(0, orderSlipTable.getRowCount() - 1);
    }

    public OrderGenerateForm() {

    }

    public int getCustomerNameComboBoxSize() {
        return customerNameComboBox.getItemCount();
    }

    int oldQuantity;
    String oldDesignID = "";

    public void init() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        orderSlipTable.addPropertyChangeListener(evt -> {
            int row = orderSlipTable.getSelectedRow();
            DefaultTableModel defaultTableModel = (DefaultTableModel) orderSlipTable.getModel();
            if (row == -1) return;
            try {
                oldQuantity = parseInt(defaultTableModel.getValueAt(row, QUANTITY_INDEX));
            } catch (NumberFormatException e) {
                oldQuantity = 0;
            }
            oldDesignID = parseString(model.getValueAt(row, DESIGN_ID_INDEX));
        });
        setContentPane(panel);
        backButton.addActionListener(e -> {
            try {
                if (orderSlipConnectionObject != null
                        && !orderSlipConnectionObject.isClosed()) {
                    orderSlipConnectionObject.rollback();
                    orderSlipConnectionObject.close();
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(OrderGenerateForm.this, ex.getMessage(),"error",JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
            dispose();
            MyClass.inventorySelect.dispose();
            MyClass.orderScreen.setVisible(true);
        });
        orderSlipTable.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                super.keyTyped(e);
            }
        });
        resetFormButton.addActionListener(e -> {
            int answer = JOptionPane.showConfirmDialog(OrderGenerateForm.this, "are you sure you want to reset the form, data will be lost?");
            if (answer != JOptionPane.YES_OPTION) return;
            dispose();
            Vector<String> vector1 = new Vector(List.of(new String[]{String.valueOf(OrderGenerateForm.this.getX()), String.valueOf(OrderGenerateForm.this.getY()), String.valueOf(OrderGenerateForm.this.getWidth()), String.valueOf(OrderGenerateForm.this.getHeight())}));
            Vector<String> vector2 = new Vector(List.of(String.valueOf(MyClass.inventorySelect.getX()), String.valueOf(MyClass.inventorySelect.getY()), String.valueOf(MyClass.inventorySelect.getWidth()), String.valueOf(MyClass.inventorySelect.getHeight())));
            Vector<Vector> vector = new Vector<>();
            vector.add(vector1);
            vector.add(vector2);
            LogUtils.generateVectorLog(vector, "coordinates");
            MyClass.inventorySelect.dispose();
            MyClass.orderGenerateForm = new OrderGenerateForm();
            MyClass.orderGenerateForm.init();
            MyClass.orderGenerateForm.setVisible(true);
        });
        submitButton.addActionListener(e -> {
            if (orderSlipTable.isEditing() && !orderSlipTable.getCellEditor().stopCellEditing()) {
                JOptionPane.showMessageDialog(this, "Finish editing the current item first.");
                return;
            }
            java.nio.file.Path savedPdf = null;
            String currentDatabase = MyClass.login.getDatabase();
            if (customerNameComboBox.getSelectedIndex() == 0) {
                JOptionPane.showMessageDialog(MyClass.orderGenerateForm, "please select a customer name", "incomplete information", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String uniqueID = "";

            uniqueID = LogUtils.generateInventorySnapShot("BEFORE", "");
            LogUtils.generateVectorLog(model.getDataVector(), "ORDERSLIP");

            if (pushSlipData() == CONSTANTS.SUCCESS_CODE && updateInventory() == CONSTANTS.SUCCESS_CODE) {
                try {
                    orderSlipConnectionObject.commit();
                    // Sequential: export only after commit, before the old form is cleared.
                    try {
                        savedPdf = OrderSlipPdf.save(orderSlipConnectionObject, getSlipID(),
                                currentDatabase, java.nio.file.Paths.get("data"));
                    } catch (Exception pdfError) {
                        JOptionPane.showMessageDialog(this,
                                "Order slip " + getSlipID() + " is saved, but its PDF could not be created.\n"
                                        + pdfError.getMessage() + "\nDo not submit the order again.",
                                "Order saved - PDF not saved", JOptionPane.WARNING_MESSAGE);
                    }
                    JOptionPane.showMessageDialog(this, "Order has been updated successfully", "success", JOptionPane.INFORMATION_MESSAGE);
                    try {
                        orderSlipConnectionObject.close();
                    } catch (SQLException ex) {
                        JOptionPane.showMessageDialog(MyClass.orderGenerateForm, "Could not close connection", "error", JOptionPane.ERROR_MESSAGE);
                        throw new RuntimeException(ex);
                    }
                    dispose();
                    MyClass.inventorySelect.dispose();
                    MyClass.orderGenerateForm = new OrderGenerateForm();
                    MyClass.orderGenerateForm.init();
                    MyClass.orderGenerateForm.setVisible(true);
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(MyClass.orderGenerateForm, "transaction commit faliur", "error", JOptionPane.ERROR_MESSAGE);
                    throw new RuntimeException(ex);
                }
            } else {
                try {
                    orderSlipConnectionObject.rollback();
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(MyClass.orderGenerateForm, "roll back failed", "error", JOptionPane.ERROR_MESSAGE);
                    throw new RuntimeException(ex);
                }
                try {
                    orderSlipConnectionObject.close();
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(MyClass.orderGenerateForm, "closing connection failed", "error", JOptionPane.ERROR_MESSAGE);
                    throw new RuntimeException(ex);
                }

            }
            LogUtils.generateInventorySnapShot("AFTER", uniqueID);
            // Open only after the replacement form is visible, so it cannot steal browser focus.
            if (savedPdf != null) {
                try {
                    BrowserPdfPrint.openPrintDialog(savedPdf);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    JOptionPane.showMessageDialog(MyClass.orderGenerateForm,
                            "The order and PDF are saved. Open the PDF and press Ctrl+P.\n" + savedPdf,
                            "Printing interrupted", JOptionPane.WARNING_MESSAGE);
                } catch (java.io.IOException printError) {
                    JOptionPane.showMessageDialog(MyClass.orderGenerateForm,
                            "The order and PDF are saved.\n" + printError.getMessage() + "\nPDF: " + savedPdf,
                            "Open PDF for printing", JOptionPane.WARNING_MESSAGE);
                }
            }
        });
        inventorySelectButton.addActionListener(e -> {
            if (MyClass.inventorySelect.isVisible()) return;
            MyClass.inventorySelect = new InventorySelect();
            MyClass.inventorySelect.init(OrderGenerateForm.this);
            MyClass.inventorySelect.setVisible(true);
            splitFrame(OrderGenerateForm.this, MyClass.inventorySelect, VERTI_SPLIT);
        });
        MyClass.inventorySelect = new InventorySelect();
        MyClass.inventorySelect.init(this);
        splitFrame(this, MyClass.inventorySelect, VERTI_SPLIT);
        MyClass.inventorySelect.setVisible(true);
        columnNames = new Vector<>(Arrays.asList(new String[]{"design id", "Item Name", "Quantity", "Plating", "Raw Material Cost", "Other Details"}));
        listOfDisabledColumn = new Vector<>();
        listOfDisabledCells = new Vector<>();
        generateAndAddDates(dateComboBox, false);
        generateAndAddNames(customerNameComboBox);
        orderSlipTable.getTableHeader().setReorderingAllowed(false);

        model = new DefaultTableModel(columnNames, 1) {
            @Override
            public boolean isCellEditable(int row, int column) {
                for (Integer[] cell : listOfDisabledCells) {
                    if (cell[0] == row && cell[1] == column) {
                        return false;
                    }
                }
                for (Integer col : listOfDisabledColumn) {
                    if (col == column) return false;

                }
                return true;
            }
        };
        modelListener = e -> {

            if (e.getType() == TableModelEvent.UPDATE) {

                TableModelListener[] listeners = removeModelListener(model);
                int row = e.getFirstRow();
                int column = e.getColumn();
                Integer quantity;
                try {
                    quantity = parseInt(model.getValueAt(row, QUANTITY_INDEX));
                } catch (NumberFormatException ex) {
                    quantity = 0;
                }
                String cellContent;
                try {
                    cellContent = parseString(model.getValueAt(row, column));
                } catch (ArrayIndexOutOfBoundsException ex) {
                    ex.printStackTrace();
                    addModelListeners(listeners, model);
                    return;
                }

                if (column == DESIGN_ID_INDEX) {
                    String designID = cellContent;
                    String itemName = DBStructure.getInventoryItemName(designID);
                    if (!itemName.contentEquals(String.valueOf(DBStructure.NOT_FOUND))) {
                        model.setValueAt(itemName, row, ITEM_NAME_INDEX);
                        model.setValueAt(DBStructure.getSellPrice(designID), row, RAW_MATERIAL_COST_INDEX);
                        listOfDisabledCells.add(new Integer[]{row, ITEM_NAME_INDEX});
                    } else {
                        model.setValueAt("", row, DESIGN_ID_INDEX);
                        model.setValueAt("", row, ITEM_NAME_INDEX);
                        model.setValueAt("", row, RAW_MATERIAL_COST_INDEX);
                        model.setValueAt("", row, QUANTITY_INDEX);
                        quantity = 0;
                        listOfDisabledCells.removeIf(cell -> (cell[0] == row && cell[1] == ITEM_NAME_INDEX));
                    }
                }
                if (column == QUANTITY_INDEX) {
                    if (quantity == null) quantity = 0;
                    if (quantity == 0) {
                        model.setValueAt("", row, QUANTITY_INDEX);
                    }
                }

                if (column == RAW_MATERIAL_COST_INDEX) {
                    double rawPrice;
                    try {
                        rawPrice = parseDouble((model.getValueAt(row, RAW_MATERIAL_COST_INDEX)));
                    } catch (NumberFormatException ex) {
                        rawPrice = 0;
                    }
                    model.setValueAt(rawPrice == 0 ? "" : rawPrice, row, RAW_MATERIAL_COST_INDEX);
                }
                if (column == PLATING_INDEX) {
                    double plating;
                    try {
                        plating = parseDouble((model.getValueAt(row, PLATING_INDEX)));
                    } catch (NumberFormatException ex) {
                        plating = 0;
                    }
                    model.setValueAt(plating == 0 ? "" : plating, row, PLATING_INDEX);
                }
                if (row == model.getRowCount() - 1 && !cellContent.isEmpty()) {
                    model.setRowCount(model.getRowCount() + 1);
                }
                if ((row != model.getRowCount() - 1) && isRowEmpty(row)) {
                    model.removeRow(row);
                    refreshListOfDisabledCells();
                }
                orderSlipTable.repaint();
                String designID = parseString(model.getValueAt(row, DESIGN_ID_INDEX));
                System.out.println();
                if (oldDesignID.toUpperCase().contentEquals(designID.toUpperCase())) {
                    System.out.println("design id is not changed");
                    quantity = (quantity - oldQuantity);
                    MyClass.inventorySelect.updateTable(quantity, designID);
                } else {
                    System.out.println("design id is changed");
                    MyClass.inventorySelect.updateTable((-oldQuantity), oldDesignID);
                    MyClass.inventorySelect.updateTable((quantity), designID);
                }
                addModelListeners(listeners, model);

            }
        };
        model.addTableModelListener(modelListener);
        Vector<String> orderSlipType = new Vector<>();
        try (Connection con = MyClass.createConnection(); Statement statement = con.createStatement()) {
            try (ResultSet rs = statement.executeQuery("select " + ORDERTYPE_TYPE_NAME + " from " + ORDERTYPE_TABLE + ";")) {
                while (rs.next()) orderSlipType.add(rs.getString("type_name"));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }


        //---------------------------------------------order_slip_mmain table data below--------------------------------------------------
        String insertMainQuery = "INSERT INTO " + ORDER_SLIPS_MAIN_TABLE + " (" + ORDER_SLIPS_MAIN_STATUS +") VALUES (?)";
        orderSlipConnectionObject = null;
        try {
            orderSlipConnectionObject = MyClass.createConnection();
            orderSlipConnectionObject.setAutoCommit(false);
            try (PreparedStatement stmt = orderSlipConnectionObject.prepareStatement(insertMainQuery, Statement.RETURN_GENERATED_KEYS);) {
                stmt.setInt(1, CONSTANTS.DRAFT_STATUS_CODE);
                stmt.executeUpdate();
                // Get the generated slip_id
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        setSlipID(rs.getInt(1));
                    } else setSlipID(-1);
                }
            }
        } catch (SQLException e) {
            dispose();
            MyClass.orderScreen.setVisible(true);
            try {
                orderSlipConnectionObject.close();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                throw new RuntimeException(ex);
            }
            throw new RuntimeException(e);
        }
        DefaultComboBoxModel<String> panaTypeModel = new DefaultComboBoxModel<>(orderSlipType);
        orderSlipTypeComboBox.setModel(panaTypeModel);
        orderSlipTable.setModel(model);
    }

    private int updateInventory() {
        String query = "update " + INVENTORY_TABLE + " SET " + INVENTORY_TOTAL_QUANTITY + " =" + INVENTORY_TOTAL_QUANTITY + "- ? WHERE " + INVENTORY_DESIGN_ID + " = ?";

        try (PreparedStatement updateInventoryStatement = orderSlipConnectionObject.prepareStatement(query)) {
            for (int i = 0; i < orderSlipTable.getRowCount() - 1; i++) {
                updateInventoryStatement.setInt(1, parseInt(orderSlipTable.getValueAt(i, QUANTITY_INDEX)));
                updateInventoryStatement.setString(2, parseString(orderSlipTable.getValueAt(i, DESIGN_ID_INDEX)));
                updateInventoryStatement.addBatch();
            }
            updateInventoryStatement.executeBatch();
            return CONSTANTS.SUCCESS_CODE;
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return CONSTANTS.SQL_ERROR;
        }

    }

    public int pushSlipData() {
        DefaultTableModel model = (DefaultTableModel) orderSlipTable.getModel();
        int rowCount = model.getRowCount();
        String updateOrderSlipsMainTableQuery = "update " + ORDER_SLIPS_MAIN_TABLE + " set " + ORDER_SLIPS_CREATED_AT + "=?, " + ORDER_SLIPS_MAIN_SLIP_TYPE + "=?,"+ORDER_SLIPS_MAIN_CUSTOMER_NAME+"=? where " + ORDER_SLIPS_MAIN_SLIP_ID + "=?";
        String slipDataInsertQuery = "INSERT INTO " + ORDER_SLIPS_TABLE + " (" + ORDER_SLIPS_SLIP_TYPE + ", " + ORDER_SLIPS_CUSTOMER_NAME + ", " + ORDER_SLIPS_SLIP_ID + ", " + ORDER_SLIPS_DESIGN_ID + ", " + ORDER_SLIPS_ITEM_NAME + ", " + ORDER_SLIPS_QUANTITY + ", " + ORDER_SLIPS_PLATING_GRAMS + ", "
                + ORDER_SLIPS_RAW_MATERIAL_PRICE + ", " + ORDER_SLIPS_OTHER_DETAILS + ", " + ORDER_SLIPS_SNO + "," + ORDER_SLIPS_CREATED_AT + ") " + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String updateSlipStatusQuery = "update " + ORDER_SLIPS_MAIN_TABLE + " SET " + ORDER_SLIPS_MAIN_STATUS + " = ? WHERE " + ORDER_SLIPS_MAIN_SLIP_ID + " = ?";
        try (PreparedStatement updateOrderSlipsMainTableStatement = orderSlipConnectionObject.prepareStatement(updateOrderSlipsMainTableQuery);
             PreparedStatement slipDataStatement = orderSlipConnectionObject.prepareStatement(slipDataInsertQuery);
             PreparedStatement updateSlipStatusStatement = orderSlipConnectionObject.prepareStatement(updateSlipStatusQuery)
        ) {
            if (!isSlipValid()) {
                JOptionPane.showMessageDialog(this, "Invalid Slip Data!", "Error", JOptionPane.ERROR_MESSAGE);
                return CONSTANTS.FAIL_CODE;
            }
            java.sql.Timestamp timestamp = java.sql.Timestamp.valueOf(LocalDateTime.parse(parseString(dateComboBox.getSelectedItem()) + " 00:00:00", DateTimeFormatter.ofPattern("dd-MM-yy HH:mm:ss")));
            updateOrderSlipsMainTableStatement.setTimestamp(1, timestamp);
            updateOrderSlipsMainTableStatement.setString(2, parseString(orderSlipTypeComboBox.getSelectedItem()));
            updateOrderSlipsMainTableStatement.setString(3, parseString(customerNameComboBox.getSelectedItem()));
            updateOrderSlipsMainTableStatement.setInt(4, getSlipID());
            updateOrderSlipsMainTableStatement.executeUpdate();

            for (int i = 0; i < rowCount - 1; i++) {
                if (parseString(model.getValueAt(i, ITEM_NAME_INDEX)).trim().contentEquals(""))
                    break;
                String designId = parseString(model.getValueAt(i, DESIGN_ID_INDEX));
                String itemName = (String) model.getValueAt(i, ITEM_NAME_INDEX);
                int quantity = parseInt((model.getValueAt(i, QUANTITY_INDEX)));
                double platingGrams = parseDouble(model.getValueAt(i, PLATING_INDEX));
                double rawMaterialCost = parseDouble((model.getValueAt(i, RAW_MATERIAL_COST_INDEX)));
                String otherDetails;
                try {
                    otherDetails = parseString(model.getValueAt(i, OTHER_DETAILS_INDEX));
                } catch (NullPointerException ex) {
                    otherDetails = "";
                }
                String customerName = parseString(customerNameComboBox.getSelectedItem());
                String orderSlipType = parseString(orderSlipTypeComboBox.getSelectedItem());
                int sno = i + 1;
                // Insert Query
                slipDataStatement.setString(1, orderSlipType);
                slipDataStatement.setString(2, customerName);
                slipDataStatement.setInt(3, getSlipID());
                slipDataStatement.setString(4, designId);
                slipDataStatement.setString(5, itemName);
                slipDataStatement.setInt(6, quantity);
                slipDataStatement.setDouble(7, platingGrams);
                slipDataStatement.setDouble(8, rawMaterialCost);
                slipDataStatement.setString(9, otherDetails);
                slipDataStatement.setInt(10, sno);
                slipDataStatement.setTimestamp(11, timestamp);
                slipDataStatement.addBatch();
            }
            slipDataStatement.executeBatch();
            updateSlipStatusStatement.setInt(1, CONSTANTS.COMMITED_STATUS_CODE);
            updateSlipStatusStatement.setInt(2, getSlipID());
            updateSlipStatusStatement.executeUpdate();

            return CONSTANTS.SUCCESS_CODE;
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(MyClass.orderGenerateForm, " error " + ex.getMessage());
            ex.printStackTrace();
            return CONSTANTS.SQL_ERROR;
        }

    }

    private boolean isSlipValid() {
        int numberOfRows = orderSlipTable.getRowCount();
        for (int i = 0; i < numberOfRows - 1; i++) {
            if (parseString(model.getValueAt(i, ITEM_NAME_INDEX)).trim().contentEquals("") || parseString(model.getValueAt(i, QUANTITY_INDEX)).trim().contentEquals(""))
                return false;
        }
        return true;
    }

    int prevRow = 0;
    DefaultTableModel model;
    Vector<String> columnNames;

    private void refreshListOfDisabledCells() {
        listOfDisabledCells.clear();
        for (int i = 0; i < model.getRowCount(); i++) {
            String value = parseString(model.getValueAt(i, DESIGN_ID_INDEX));
            if (!value.isEmpty()) {
                listOfDisabledCells.add(new Integer[]{i, ITEM_NAME_INDEX});
            }
        }
        for (Integer[] value : listOfDisabledCells) {
            System.out.println(value[0] + " " + value[1]);
        }
    }

    private void setSlipID(int slipID) {
        this.slipID = slipID;
        slipIDLabel.setText(slipID + "");
    }

    private int slipID;

    public int getSlipID() {
        return slipID;
    }


    private boolean isRowEmpty(int row) {
        for (int i = 0; i < model.getColumnCount(); i++) {
            String value = parseString(model.getValueAt(row, i));
            if (!value.isEmpty()) return false;
        }
        return true;
    }

    public void setDate(String date) {
        dateComboBox.setSelectedItem(date);
    }

    public void setCustomer(int i) {
        customerNameComboBox.setSelectedIndex(i);
    }

    public int getNumberOfOrderTypes() {
        return orderSlipTypeComboBox.getItemCount();
    }

    public void setOrderType(int i) {
        orderSlipTypeComboBox.setSelectedIndex(i);
    }

    public void insertData(String designID, String name, Integer quantity, double plating, String jewelleryTasks,Integer row) {
        if(row==null) row = orderSlipTable.getRowCount() - 1;
        DefaultTableModel model = (DefaultTableModel) orderSlipTable.getModel();
        if (designID != null) model.setValueAt(designID, row, DESIGN_ID_INDEX);
        if (name != null) model.setValueAt(name, row, ITEM_NAME_INDEX);
        if(quantity!=null)model.setValueAt(quantity, row, QUANTITY_INDEX);
        model.setValueAt(plating, row, PLATING_INDEX);
        if(jewelleryTasks!=null) model.setValueAt(jewelleryTasks, row, OTHER_DETAILS_INDEX);
    }

    public Integer getNumberOfTableRows() {
        return orderSlipTable.getRowCount();
    }

    public void clickSubmitButton() {
        submitButton.doClick();
    }
}
