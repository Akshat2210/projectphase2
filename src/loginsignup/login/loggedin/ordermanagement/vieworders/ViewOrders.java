package loginsignup.login.loggedin.ordermanagement.vieworders;

import mainpack.MyClass;
import loginsignup.login.loggedin.ordermanagement.generateorder.OrderSlipPdf;
import loginsignup.login.loggedin.processingmanagement.send.BrowserPdfPrint;
import org.jdesktop.swingx.prompt.PromptSupport;
import utils.CONSTANTS;
import utils.UtilityMethods;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.Objects;

import static utils.UtilityMethods.*;

public class ViewOrders extends JFrame {
    private static final int DESIGN_ID_INDEX = 0;
    private static final int QUANTITY_INDEX = 2;
    private static final boolean PREVIOUSPRESS = true;
    private static final boolean NEXTPRESS = false;


    public ViewOrders() {
    }

    // direction: 0 = first match, 1 = next, -1 = previous.
    private void loadFilteredSlip(int direction) {
        boolean customerSelected = customerComboBox.getSelectedIndex() > 0;
        boolean typeSelected = panaTypeComboBox.getSelectedIndex() > 0;
        String query = "SELECT slip_id FROM order_slips WHERE slip_id IS NOT NULL";
        if (direction != 0) query += direction > 0 ? " AND slip_id > ?" : " AND slip_id < ?";
        if (customerSelected) query += " AND customer_name = ?";
        if (typeSelected) query += " AND slip_type = ?";
        query += direction < 0 ? " ORDER BY slip_id DESC LIMIT 1" : " ORDER BY slip_id ASC LIMIT 1";
        Integer selectedId = null;
        try (Connection con = MyClass.createConnection(); PreparedStatement stmt = con.prepareStatement(query)) {
            int parameter = 1;
            if (direction != 0) stmt.setInt(parameter++, getCurrentBillID());
            if (customerSelected) stmt.setString(parameter++, utils.TextNormalization.trimToEmpty(customerComboBox.getSelectedItem()));
            if (typeSelected) stmt.setString(parameter, utils.TextNormalization.trimToEmpty(panaTypeComboBox.getSelectedItem()));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) selectedId = rs.getInt(1);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Could not load order slip: " + ex.getMessage());
            return;
        }
        if (selectedId != null) setCurrentBill(selectedId);
        else if (direction == 0) setCurrentBill(-1);
    }

    int id;

    public int getCurrentBillID() {
        return id;
    }

    private void setCurrentBill(int id) {
        if (id == -1) {
            DefaultTableModel model = (DefaultTableModel) orderSlipTable.getModel();
            dateLabel.setText("");
            billIDLabel.setText("");
            nameLabel.setText("");

            this.id = id;
            model.setRowCount(0);
            return;
        }

        ActionListener[] panas = panaTypeComboBox.getActionListeners();
        ActionListener[] customers = customerComboBox.getActionListeners();

        try (Connection con = MyClass.createConnection(); Statement stmt1 = con.createStatement(); Statement stmt2 = con.createStatement()) {
            for (ActionListener pana : panas) {
                panaTypeComboBox.removeActionListener(pana);
            }
            for (ActionListener customer : customers) {
                customerComboBox.removeActionListener(customer);
            }
            // Query to fetch data based on slip_id
            String query = "SELECT design_id, item_name, quantity, plating_grams, raw_material_price, other_details, customer_name, slip_id,slip_type,billed_quantity FROM order_slips WHERE slip_id = " + id;
            DefaultTableModel model = new DefaultTableModel() {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            model.setColumnIdentifiers(new String[]{"Design ID", "Item Name", "Quantity", "Plating", "Raw Material Cost", "Other Details"});

            int slipId = 1;
            BigDecimal totalPlating = BigDecimal.ZERO;
            int count = 0;
            try (ResultSet rs = stmt1.executeQuery(query)) {
                while (rs.next()) {
                    count++;
                    customerName = utils.TextNormalization.trimNullable(rs.getString("customer_name"));
                    slipId = rs.getInt("slip_id");
                    panaType = utils.TextNormalization.trimNullable(rs.getString("slip_type"));
                    totalPlating = totalPlating.add(BigDecimal.valueOf(Double.parseDouble(rs.getString("plating_grams"))));
                    totalPlatingField.setText("total plating: " + totalPlating.setScale(3, RoundingMode.HALF_UP));
                    model.addRow(new Object[]{utils.TextNormalization.trimNullable(rs.getString("design_id")), utils.TextNormalization.trimNullable(rs.getString("item_name")), rs.getInt("billed_quantity") + "/" + rs.getInt("quantity"), rs.getBigDecimal("plating_grams"), rs.getBigDecimal("raw_material_price"), utils.TextNormalization.stripTrailingNullable(rs.getString("other_details"))});
                }
            }
            if (count == 0) {
                JOptionPane.showMessageDialog(ViewOrders.this, "slip not found error", "error", JOptionPane.ERROR_MESSAGE);
                setCurrentBill(-1);
                throw new RuntimeException();
            }
            orderSlipTable.setModel(model);
            nameLabel.setText(customerName + "-> " + panaType);

            billIDLabel.setText(String.valueOf(slipId));
            this.id = slipId;
            query = "SELECT DATE_FORMAT(created_at, '%d-%m-%y') AS formatted_date FROM order_slips WHERE slip_id = " + id + ";";
            try (ResultSet rs = stmt2.executeQuery(query);) {
                if (rs.next()) dateLabel.setText("date: " + rs.getString(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error fetching bill details: " + e.getMessage());
        }
        for (ActionListener pana : panas) {
            panaTypeComboBox.addActionListener(pana);
        }
        for (ActionListener customer : customers) {
            customerComboBox.addActionListener(customer);
        }

    }

    String customerName = "", panaType = "";

    public void init() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        setContentPane(panel);
        pack();
        javax.swing.Timer filterSelectionDelay = new javax.swing.Timer(200, e -> {
            loadFilteredSlip(0);
        });
        filterSelectionDelay.setRepeats(false);
        customerComboBox.addActionListener(e -> filterSelectionDelay.restart());
        panaTypeComboBox.addActionListener(e -> filterSelectionDelay.restart());
        backButton.addActionListener(e -> {
            panaTypeComboBox.getActionListeners();
            setVisible(false);
            MyClass.orderScreen.setVisible(true);
        });

        nextButton.addActionListener(e -> loadFilteredSlip(1));
        prevButton.addActionListener(e -> loadFilteredSlip(-1));
        searchField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                int id;
                try {
                    id = searchField.getText().contentEquals("") ? -1 : Integer.parseInt(searchField.getText().trim());

                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(ViewOrders.this, "invalid number entered", "error", JOptionPane.ERROR_MESSAGE);
                    searchField.setText("");
                    return;
                }
                if (!IDExists(id)) {
                    JOptionPane.showMessageDialog(ViewOrders.this, "id not found from field", "error", JOptionPane.ERROR_MESSAGE);
                    searchField.setText("");

                    return;
                }
                setCurrentBill(id);
            }

            private boolean IDExists(int id) {
                try (Connection con = MyClass.createConnection(); Statement stmt = con.createStatement()) {
                    String query = "select slip_id from order_slips where slip_id=" + id + ";";
                    try (ResultSet rs = stmt.executeQuery(query);) {
                        if (rs.next())
                            return true;
                        else return false;
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        printButton.addActionListener(event -> {
            if (getCurrentBillID() <= 0 || orderSlipTable.getRowCount() == 0) {
                JOptionPane.showMessageDialog(this, "Select an order slip with items before printing.");
                return;
            }
            java.nio.file.Path pdf;
            printButton.setEnabled(false);
            try {
                // Keep this flow sequential: save the PDF, close the read connection, then open the browser.
                try (Connection connection = MyClass.createConnection()) {
                    pdf = OrderSlipPdf.saveView(connection, getCurrentBillID(), MyClass.login.getDatabase(),
                            java.nio.file.Paths.get("data"), orderSlipTable.getModel());
                }
            } catch (Exception exportError) {
                printButton.setEnabled(true);
                JOptionPane.showMessageDialog(this, "Could not save the order-slip PDF:\n" + exportError.getMessage(),
                        "PDF not saved", JOptionPane.ERROR_MESSAGE);
                return;
            }
            try {
                BrowserPdfPrint.openPrintDialog(pdf);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                JOptionPane.showMessageDialog(this, "PDF saved. Open it and press Ctrl+P.\n" + pdf,
                        "Printing interrupted", JOptionPane.WARNING_MESSAGE);
            } catch (java.io.IOException printError) {
                JOptionPane.showMessageDialog(this, "PDF saved.\n" + printError.getMessage() + "\nPDF: " + pdf,
                        "Open PDF for printing", JOptionPane.WARNING_MESSAGE);
            } finally {
                printButton.setEnabled(true);
            }
        });
        deleteButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int deleteAction = JOptionPane.showConfirmDialog(ViewOrders.this, "Are you sure you want to delete order slip " + id, "confirm delete action", JOptionPane.OK_CANCEL_OPTION);
                if (deleteAction == JOptionPane.CANCEL_OPTION) {
                    return;
                }
                if (deleteAction == JOptionPane.OK_OPTION) {
                    Connection con = null;
                    Statement prevNextStatement = null;
                    Statement orderSlipMainDeleteStatement = null;
                    PreparedStatement inventoryUpdateStatement = null;

                    boolean nextOrPrev;
                    try {
                        con = MyClass.createConnection();
                        con.setAutoCommit(false);
                        String prevNextQuery;
                        prevNextQuery = "select min(slip_id) from order_slips ";
                        if (!(customerComboBox.getSelectedIndex() == 0)) {
                            prevNextQuery += "where customer_name=\"" + customerName + "\" ";
                            if (!(panaTypeComboBox.getSelectedIndex() == 0)) {
                                prevNextQuery += " and slip_type= \"" + panaType + "\";";
                            } else {
                                prevNextQuery += ";";
                            }

                        } else if (!(panaTypeComboBox.getSelectedIndex() == 0)) {
                            prevNextQuery += "where slip_type=\"" + panaType + "\";";
                        } else prevNextQuery += ";";
                        prevNextStatement = con.createStatement();
                        ResultSet rs = prevNextStatement.executeQuery(prevNextQuery);
                        int idd = ViewOrders.this.id;
//                        if (rs.next()) {
//                            if (rs.getString(1).contentEquals("" + idd)) {
//                                System.out.println(rs.getString(1) + "       " + idd);
//                                nextButton.doClick();
//                                nextOrPrev = PREVIOUSPRESS;// if error occurs, previous will be pressed to get back to current billid
//                            } else {
//                                prevButton.doClick();
//                                nextOrPrev = NEXTPRESS;// if error occurs, next will be pressed to get back to current billid
//                                System.out.println(rs.getString(1) + "       " + idd);
//                            }
//                        }
                        String orderSlipMainDeleteString = "delete from order_slips_main where slip_id=" + idd;
                        orderSlipMainDeleteStatement = con.createStatement();
                        orderSlipMainDeleteStatement.executeUpdate(orderSlipMainDeleteString);
                        DefaultTableModel model = (DefaultTableModel) orderSlipTable.getModel();
                        String inventoryUpdateQuery = "update inventory set totalquantity=totalquantity+? where designid = ? ";
                        inventoryUpdateStatement = con.prepareStatement(inventoryUpdateQuery);
                        for (int i = 0; i < orderSlipTable.getRowCount(); i++) {
                            Object designID = model.getValueAt(i, DESIGN_ID_INDEX);
                            if (designID != null && !designID.toString().isEmpty()) {

                                String designIDValue = designID.toString();
                                Object quantityObject = model.getValueAt(i, QUANTITY_INDEX);
                                String quantityString = quantityObject != null ? quantityObject.toString() : "0/0";
                                int separator = quantityString.indexOf('/');
                                int quantity = Integer.parseInt(quantityString.substring(separator + 1))
                                        - Integer.parseInt(quantityString.substring(0, separator));
                                inventoryUpdateStatement.setInt(1, quantity);
                                inventoryUpdateStatement.setString(2, utils.TextNormalization.trimNullable(designIDValue));
                                inventoryUpdateStatement.addBatch();
                                System.out.println(designIDValue + " quantity is updated by increasing by " + quantity);
                            }
                        }
                        inventoryUpdateStatement.executeBatch();
                        con.commit();
                        con.close();
                        System.out.println("id ->" + idd + "deleted");
                    } catch (SQLException ex) {
                        try {
                            if (con != null) con.rollback();
                            System.out.println("rollbacked");
                            ex.printStackTrace();
                            return;
                        } catch (SQLException exc) {
                            JOptionPane.showMessageDialog(ViewOrders.this, "rollback error occured");
                            exc.printStackTrace();
                            return;
                        }
                    } finally {

                        try {
                            if (prevNextStatement != null) prevNextStatement.close();
                            if (orderSlipMainDeleteStatement != null) orderSlipMainDeleteStatement.close();
                            if (inventoryUpdateStatement != null) inventoryUpdateStatement.close();
                            if (con != null) con.close();

                        } catch (SQLException ex) {
                            JOptionPane.showMessageDialog(ViewOrders.this, "logs are saved in logs file contact the technical support");
                            ex.printStackTrace();
                            UtilityMethods.storeLogs(ex);
                        }
                    }

                }
            }


        });

        String[] columnNames = {"design id", "Item Name", "Quantity", "Plating", "Raw Material Cost", "Other Details"};//jtable content
        ActionListener[] panas = panaTypeComboBox.getActionListeners();
        ActionListener[] customers = customerComboBox.getActionListeners();
        for (ActionListener pana : panas) panaTypeComboBox.removeActionListener(pana);
        for (ActionListener customer : customers) customerComboBox.removeActionListener(customer);
        panaTypeComboBox.removeAllItems();
        customerComboBox.removeAllItems();
        PromptSupport.setPrompt("Go to id", searchField);
        customerComboBox.addItem("Select Customer");
        panaTypeComboBox.addItem("All Slips");
        System.out.println();
        try (Connection con = MyClass.createConnection();
             Statement stmt1 = con.createStatement();
             Statement stmt2 = con.createStatement()) {

            try (ResultSet rs1 = stmt1.executeQuery("select type_name from ordertype;");) {
                while (rs1.next()) {
                    panaTypeComboBox.addItem(utils.TextNormalization.trimNullable(rs1.getString("type_name")));
                }
            }
            try (ResultSet rs2 = stmt2.executeQuery("select customer_name from customers;");) {
                while (rs2.next()) {
                    customerComboBox.addItem(utils.TextNormalization.trimNullable(rs2.getString("customer_name")));
                }
            }


        } catch (SQLException e) {
            e.printStackTrace();
        }
        for (ActionListener pana : panas) panaTypeComboBox.addActionListener(pana);
        for (ActionListener customer : customers) customerComboBox.addActionListener(customer);


        DefaultTableModel model = new DefaultTableModel(columnNames, 1);
        orderSlipTable.setModel(model);
        billIDLabel.setText("Bill ID: ");
        String query = "SELECT MIN(slip_id) FROM order_slips ";
        if (!(customerComboBox.getSelectedIndex() == 0)) {
            query += "WHERE customer_name = \"" + customerComboBox.getSelectedItem().toString() + "\" ";
            if (!(panaTypeComboBox.getSelectedIndex() == 0)) {
                query += "AND slip_type = \"" + Objects.requireNonNull(panaTypeComboBox.getSelectedItem()) + "\";";
            } else {
                query += ";";
            }
        } else if (!(panaTypeComboBox.getSelectedIndex() == 0)) {
            query += "WHERE slip_type =\"" + Objects.requireNonNull(panaTypeComboBox.getSelectedItem()) + "\";";
        } else query += ";";
        try (Connection con = MyClass.createConnection(); Statement stmt = con.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(query);) {
                if (rs.next()) {
                    if (rs.getObject(1) != null)
                        id = rs.getInt(1);
                    else setCurrentBill(-1);
                } else {
                    throw new RuntimeException();
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        setCurrentBill(id);
        totalPlatingField.setEditable(false);
        pack();
    }

    private JButton backButton;
    private JTable orderSlipTable;
    private JButton nextButton;
    private JButton prevButton;
    private JComboBox customerComboBox;
    private JComboBox panaTypeComboBox;
    private JPanel panel;
    private JLabel billIDLabel;
    private JLabel nameLabel;
    private JTextField searchField;
    private JTextField totalPlatingField;
    private JButton printButton;
    private JButton deleteButton;
    private JLabel dateLabel;
}
