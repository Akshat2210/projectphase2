package loginsignup.login.loggedin.inventorymanagement;

import loginsignup.login.loggedin.inventorymanagement.addinventory.AddInventory;
import mainpack.MyClass;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public class InventoryScreen extends JFrame {
    private JPanel panel;
    private JButton backButton;
    private JTable inventoryTable;
    private JButton addInventoryButton;

    public InventoryScreen() {

    }

    public void init() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        setContentPane(panel);


        backButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                MyClass.mainScreen.setInventoryManagementButtonEnabled(true);
                MyClass.mainScreen.setVisible(true);
                dispose();

            }
        });
        addInventoryButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setVisible(false);
                MyClass.addInventory = new AddInventory();
                MyClass.addInventory.init();
                MyClass.addInventory.setVisible(true);
            }
        });
        DefaultTableModel m = new DefaultTableModel(
                new Object[][]{},
                new String[]{
                        "Design ID",
                        "Total Quantity",
                        "Item Name",
                        "Price",
                        "Opening Stock",
                        "Sell Price",
                        "Supplier Name"
                }
        ) {
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        inventoryTable.setModel(m);
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(m);
        sorter.setSortKeys(List.of(new RowSorter.SortKey(0, SortOrder.ASCENDING)));
        inventoryTable.setRowSorter(sorter);

        try (Connection con = MyClass.createConnection();
             Statement stmt = con.createStatement()) {

            String query = "SELECT DESIGNID, TotalQuantity, itemname, price, openingstock, sellPrice, SupplierName FROM inventory";

            try (ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {

                    String designId = rs.getString("DESIGNID");
                    int totalQuantity = rs.getInt("TotalQuantity");
                    String itemName = rs.getString("itemname");
                    BigDecimal price = rs.getBigDecimal("price");
                    int openingStock = rs.getInt("openingstock");
                    BigDecimal sellPrice = rs.getBigDecimal("sellPrice");
                    String supplierName = rs.getString("SupplierName");

                    m.addRow(new Object[]{
                            designId,
                            totalQuantity,
                            itemName,
                            price,
                            openingStock,
                            sellPrice,
                            supplierName
                    });
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        sorter.sort();
        pack();
    }
}
