package loginsignup.login.loggedin.ordermanagement.generateorder;

import mainpack.MyClass;
import utils.UtilityMethods;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.event.*;
import java.sql.*;
import java.util.*;

import static utils.CONSTANTS.*;
import static utils.DBStructure.*;

public class InventorySelect extends JFrame {
    private static final int COL_DESIGN_ID_INDEX = 0;
    private static final int COL_ITEM_NAME_INDEX = 1;
    private static final int COL_TOTAL_QTY_INDEX = 2;
    private JPanel panel;
    private JButton backButton;
    private JTable inventoryTable;

    static void main() {
        InventorySelect inventorySelect = new InventorySelect();
        inventorySelect.init(null);
        inventorySelect.setVisible(true);
    }

    public InventorySelect() {

    }

    OrderGenerateForm orderGenerateForm = null;

    public void init(OrderGenerateForm orderGenerateForm) {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        rowDesignIdMap=new HashMap<>();
        this.orderGenerateForm = orderGenerateForm;
        setContentPane(panel);
        inventoryTable.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                super.keyPressed(e);
                if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
                    processSelection();
                }
            }
        });
        backButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        Vector<String> data = new Vector<>(List.of(new String[]{"design id", "item name", "total quantity available"}));

        DefaultTableModel model = new DefaultTableModel(data, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        inventoryTable.setModel(model);
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        sorter.setSortKeys(List.of(new RowSorter.SortKey(COL_DESIGN_ID_INDEX, SortOrder.ASCENDING)));
        inventoryTable.setRowSorter(sorter);
        fetchData(model);
        inventoryTable.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    processSelection();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {

            }

            @Override
            public void mouseReleased(MouseEvent e) {

            }

            @Override
            public void mouseEntered(MouseEvent e) {

            }

            @Override
            public void mouseExited(MouseEvent e) {

            }
        });
        pack();
    }

    private void processSelection() {
        int quantity;
        try {
            quantity = Integer.parseInt(JOptionPane.showInputDialog("enter quantity"));
            if (quantity <= 0) {
                JOptionPane.showMessageDialog(InventorySelect.this, "please enter valid quantity");
                return;
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(InventorySelect.this, "please enter valid quantity");
            return;
        }
        int selectedRow = inventoryTable.convertRowIndexToModel(inventoryTable.getSelectedRow());
        String designID = Objects.toString(inventoryTable.getModel().getValueAt(selectedRow, COL_DESIGN_ID_INDEX), "");
        pushDataToGenerator(designID, quantity, orderGenerateForm,true);

    }
    private void processSelection(int quantity){
        int randomRow= UtilityMethods.RANDOM.nextInt(inventoryTable.getRowCount());
        String designID =Objects.toString( inventoryTable.getModel().getValueAt(randomRow, COL_DESIGN_ID_INDEX),"");
        pushDataToGenerator(designID, quantity, orderGenerateForm,false);
    }
    public int getSqlDataToMemory() {

        return WRITE_CODE_HERE;
    }

    public int fetchMemoryToTable() {

        return WRITE_CODE_HERE;
    }

    public int updateTable(int inputQuantity, String designID) {
        System.out.println("input q is  " + inputQuantity);
        DefaultTableModel model = (DefaultTableModel) inventoryTable.getModel();
        Integer rowIndex=rowDesignIdMap.get(designID.toUpperCase());
        if(rowIndex==null)return NOT_FOUND;//TABLE SUCCESFULLY UPDATED(AS NO UPDATION WAS NEEDED... THIS IS A SUCCESS
        String quantityString= Objects.toString(model.getValueAt(rowIndex, COL_TOTAL_QTY_INDEX),"0");
        int quantity = Integer.parseInt(quantityString.trim().isEmpty()?"0":quantityString);
        int updatedQuantity = quantity - inputQuantity;

        model.setValueAt(updatedQuantity, rowIndex, COL_TOTAL_QTY_INDEX);
        return SUCCESS_CODE;
    }

//    public int updateSql(int quantity, String designID) {
//        String inventoryUpdateQuery = "update " + INVENTORY_TABLE + " set " + INVENTORY_TOTAL_QUANTITY + " = " + INVENTORY_TOTAL_QUANTITY + "-? where " + INVENTORY_DESIGN_ID + " = ?";
//        try (PreparedStatement preparedStatement = orderGenerateForm.getOrderSlipConnectionObject().prepareStatement(inventoryUpdateQuery)) {
//            preparedStatement.setInt(1, quantity);
//            preparedStatement.setString(2, designID);
//            return SUCCESS_CODE;
//
//        } catch (SQLException e) {
//            e.printStackTrace();
//            return SQL_ERROR;
//        } catch (Exception e) {
//            return FAIL_CODE;
//        }
//
//
//    }

    private int pushDataToGenerator(String designID, int quantity, OrderGenerateForm orderGenerateForm,boolean showConfirmDialog) {
        if(showConfirmDialog && isOutOfStock(quantity,designID)){
            int confirm = JOptionPane.showConfirmDialog(InventorySelect.this, "out of stock press yes to continue anyways", "out of stock:", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) {
                return OUT_OF_STOCK_ERROR;
            }
        }
        int lastRow = orderGenerateForm.model.getRowCount() - 1;
        orderGenerateForm.model.setValueAt(quantity, lastRow, OrderGenerateForm.QUANTITY_INDEX);
        orderGenerateForm.model.setValueAt(designID, lastRow, OrderGenerateForm.DESIGN_ID_INDEX);
        orderGenerateForm.fireTableRowChange();
        return SUCCESS_CODE;
    }

    private boolean isOutOfStock(int quantity, String designID) {
        int row=rowDesignIdMap.get(designID.toUpperCase());
        String oldQuantityObject = Objects.toString(inventoryTable.getModel().getValueAt(row, COL_TOTAL_QTY_INDEX), "");
        int oldQuantity= Integer.parseInt(oldQuantityObject.trim().equals("")? "0":oldQuantityObject);
        if(oldQuantity-quantity<0){
            return true;
        }
        return false;
    }


    Map<String, Integer> rowDesignIdMap;
    public void fetchData(DefaultTableModel model) {
        String query = "Select " + INVENTORY_DESIGN_ID + ", " + INVENTORY_ITEM_NAME + ", " + INVENTORY_TOTAL_QUANTITY + " from " + INVENTORY_TABLE;
        try (Connection con = MyClass.createConnection(); PreparedStatement preparedStatement = con.prepareStatement(query)) {
            try (ResultSet rs = preparedStatement.executeQuery()) {
                rowDesignIdMap.clear();
               int row=0;
                while (rs.next()) {
                    model.addRow(new String[]{rs.getString(INVENTORY_DESIGN_ID), rs.getString(INVENTORY_ITEM_NAME), rs.getString(INVENTORY_TOTAL_QUANTITY)});
                    rowDesignIdMap.put(rs.getString(INVENTORY_DESIGN_ID).toUpperCase(), row++);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
//            throw new RuntimeException(e);
        }
    }

    public void pushAnRandomItem() {
        processSelection(new Random().nextInt(1,16));

    }
}
