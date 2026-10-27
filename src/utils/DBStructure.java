package utils;

import mainpack.MyClass;

import javax.swing.*;
import javax.swing.plaf.nimbus.State;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DBStructure {

    //starting inventory table defination


    public static final int NOT_FOUND = -18 * 1;
    private static final int SQLEXCEPTIONOCCURED = -18 * 2;
    private static final int CONNECTION_NOT_CLOSE_ERROR = -18 * 3;


    // ====================== billdetails ======================
    public static final String BILLDETAILS_TABLE = "billdetails";
    public static final String BILLDETAILS_BILL_ID = "BillID";
    public static final String BILLDETAILS_SNO = "SNo";
    public static final String BILLDETAILS_ITEM_NAME = "ItemName";
    public static final String BILLDETAILS_DESIGN_ID = "DesignID";
    public static final String BILLDETAILS_ORDER_TYPE = "ordertype";
    public static final String BILLDETAILS_LABOUR_COST = "LabourCost";
    public static final String BILLDETAILS_DULL_CHILLAI_COST = "DullChillaiCost";
    public static final String BILLDETAILS_MEENA_COLOR_MEENA_COST = "MeenaColorMeenaCost";
    public static final String BILLDETAILS_RHODIUM_COST = "RhodiumCost";
    public static final String BILLDETAILS_NAG_SETTING_COST = "NagSettingCost";
    public static final String BILLDETAILS_OTHER_BASE_COSTS = "OtherBaseCosts";
    public static final String BILLDETAILS_OTHER_BASE_COST_NOTES = "OtherBaseCostNotes";
    public static final String BILLDETAILS_TOTAL_BASE_COSTING = "TotalBaseCosting";
    public static final String BILLDETAILS_GOLD_RATE = "GoldRate";
    public static final String BILLDETAILS_GOLD_PLATING_WEIGHT = "GoldPlatingWeight";
    public static final String BILLDETAILS_TOTAL_GOLD_COST = "TotalGoldCost";
    public static final String BILLDETAILS_TOTAL_FINAL_COST = "TotalFinalCost";
    public static final String BILLDETAILS_ORDER_SLIP_NUMBER = "OrderSlipNumber";
    public static final String BILLDETAILS_ITEMID_BILL = "itemid_bill";
    public static final String BILLDETAILS_RAW_COST = "RawCost";
    public static final String BILLDETAILS_QUANTITY = "quantity";

    // ====================== bills ======================
    public static final String BILLS_BILL_TYPE = "billType";
    public static final String BILLS_TABLE = "bills";
    public static final String BILLS_BILL_ID = "BillID";
    public static final String BILLS_DATE = "date";
    public static final String BILLS_CUSTOMER_NAME = "customer_name";
    public static final String BILLS_DRAFT = "drafts";
    public static final String BILLS_BILL_NUMBER = "BillNumber";
    // ====================== customers ======================
    public static final String CUSTOMERS_TABLE = "customers";
    public static final String CUSTOMERS_CUSTOMER_ID = "customer_id";
    public static final String CUSTOMERS_CUSTOMER_NAME = "customer_name";
    public static final String CUSTOMERS_BALANCE = "balance";
    public static final String CUSTOMERS_OPENING_ACCOUNT = "openingaccount";

    // ====================== inventory ======================
    public static final String INVENTORY_TABLE = "inventory";
    public static final String INVENTORY_DESIGN_ID = "DESIGNID";
    public static final String INVENTORY_TOTAL_QUANTITY = "TotalQuantity";
    public static final String INVENTORY_ITEM_NAME = "itemname";
    public static final String INVENTORY_BUY_PRICE = "price";
    public static final String INVENTORY_OPENING_STOCK = "openingstock";
    public static final String INVENTORY_SELL_PRICE = "sellPrice";
    public static final String INVENTORY_SUPPLIER_NAME = "SupplierName";

    // ====================== order_slips ======================
    public static final String ORDER_SLIPS_TABLE = "order_slips";
    public static final String ORDER_SLIPS_SLIP_TYPE = "slip_type";
    public static final String ORDER_SLIPS_CUSTOMER_NAME = "customer_name";
    public static final String ORDER_SLIPS_RAW_MATERIAL_PRICE = "raw_material_price";
    public static final String ORDER_SLIPS_ITEM_NAME = "item_name";
    public static final String ORDER_SLIPS_QUANTITY = "quantity";
    public static final String ORDER_SLIPS_PLATING_GRAMS = "plating_grams";
    public static final String ORDER_SLIPS_CREATED_AT = "created_at";
    public static final String ORDER_SLIPS_OTHER_DETAILS = "other_details";
    public static final String ORDER_SLIPS_DESIGN_ID = "design_id";
    public static final String ORDER_SLIPS_SLIP_ID = "slip_id";
    public static final String ORDER_SLIPS_ITEM_ID = "item_id";
    public static final String ORDER_SLIPS_SNO = "sno";
    public static final String ORDER_SLIPS_BILLED_QUANTITY = "billed_quantity";

    // ====================== order_slips_main ======================
    public static final String ORDER_SLIPS_MAIN_TABLE = "order_slips_main";
    public static final String ORDER_SLIPS_MAIN_SLIP_ID = "slip_id";
    public static final String ORDER_SLIPS_MAIN_SLIP_TYPE = "slip_type";
    public static final String ORDER_SLIPS_MAIN_CREATED_AT = "created_at";
    public static final String ORDER_SLIPS_MAIN_CUSTOMER_NAME = "customer_name";
    public static final String ORDER_SLIPS_MAIN_STATUS = "STATUS";


    // ====================== ordertype ======================
    public static final String ORDERTYPE_TABLE = "ordertype";
    public static final String ORDERTYPE_TYPE_ID = "type_id";
    public static final String ORDERTYPE_TYPE_NAME = "type_name";

    // ====================== transactions ======================
    public static final String TRANSACTIONS_TABLE = "transactions";
    public static final String TRANSACTIONS_TRANSACTION_ID = "transaction_id";
    public static final String TRANSACTIONS_BILLID = "billid";
    public static final String TRANSACTIONS_AMOUNT = "amount";
    public static final String TRANSACTIONS_DATE = "date";
    public static final String TRANSACTIONS_CUSTOMER_NAME = "customer_name";
    public static final String TRANSACTIONS_REMARK = "remark";

    // ====================== journalentry ======================
    public static final String JOURNAL_ENTRY_TABLE = "journalentry";
    public static final String JOURNAL_ENTRY_ID = "id";
    public static final String JOURNAL_ENTRY_TRANSACTION_TYPE = "transaction_type";
    public static final String JOURNAL_ENTRY_DATE = "date";
    public static final String JOURNAL_ENTRY_ACCOUNT_NAME = "accountname";
    public static final String JOURNAL_ENTRY_REMARK = "remark";
    public static final String JOURNAL_ENTRY_BILL_ID = "billid";
    public static final String JOURNAL_ENTRY_TRANSACTION_ID = "transaction_id";
    public static final String JOURNAL_ENTRY_CREDIT = "credit";
    public static final String JOURNAL_ENTRY_DEBIT = "debit";

    //users table for signup and login structure:
    public static final String USER_ACCOUNT_TABLE = "users";
    public static final String user_name = "user";
    public static final String password = "password";
    public static final String authority = "authority";
    public static final String USER_DEFAULT_GODOWN_ID = "default_godown_id";

    // ====================== godowns ======================
    // A sub-godown is represented by a godowns row with a parent_godown_id.
    public static final String GODOWNS_TABLE = "godowns";
    public static final String GODOWNS_GODOWN_ID = "godown_id";
    public static final String GODOWNS_PARENT_GODOWN_ID = "parent_godown_id";
    public static final String GODOWNS_GODOWN_NAME = "godown_name";
    public static final String GODOWNS_IS_ACTIVE = "is_active";

    // ====================== manufacturing send lots ======================
    public static final String SEND_LOTS_TABLE = "send_lots";
    public static final String SEND_LOTS_ID = "send_lot_id";
    public static final String SEND_LOTS_SEND_DATE = "send_date";
    public static final String SEND_LOTS_DEFAULT_SOURCE_GODOWN_ID = "default_source_godown_id";
    public static final String SEND_LOTS_DEFAULT_DESTINATION_GODOWN_ID = "default_destination_godown_id";
    public static final String SEND_LOTS_REMARK = "lot_remark";
    public static final String SEND_LOTS_CREATED_BY_USER = "created_by_user";
    public static final String SEND_LOTS_CREATED_AT = "created_at";

    // ====================== manufacturing send packets ======================
    public static final String SEND_PACKETS_TABLE = "send_packets";
    public static final String SEND_PACKETS_ID = "send_packet_id";
    public static final String SEND_PACKETS_SEND_LOT_ID = "send_lot_id";
    public static final String SEND_PACKETS_PACKET_NUMBER = "packet_number";
    public static final String SEND_PACKETS_CUSTOMER_NAME = "customer_name";
    public static final String SEND_PACKETS_REMARK = "packet_remark";

    // ====================== manufacturing send items ======================
    public static final String SEND_ITEMS_TABLE = "send_items";
    public static final String SEND_ITEMS_ID = "send_item_id";
    public static final String SEND_ITEMS_SEND_PACKET_ID = "send_packet_id";
    public static final String SEND_ITEMS_ITEM_NUMBER = "item_number";
    public static final String SEND_ITEMS_SOURCE_GODOWN_ID = "source_godown_id";
    public static final String SEND_ITEMS_DESTINATION_GODOWN_ID = "destination_godown_id";
    public static final String SEND_ITEMS_INTENDED_RECIPIENT_USER = "intended_recipient_user";
    public static final String SEND_ITEMS_ORDER_SLIP_ID = "order_slip_id";
    public static final String SEND_ITEMS_ORDER_SLIP_ITEM_ID = "order_slip_item_id";
    public static final String SEND_ITEMS_DESIGN_ID = "design_id";
    public static final String SEND_ITEMS_ITEM_NAME = "item_name";
    public static final String SEND_ITEMS_SENT_QUANTITY = "sent_quantity";
    public static final String SEND_ITEMS_REMARK = "item_remark";

    // ====================== manufacturing receive actions ======================
    public static final String RECEIVE_ACTIONS_TABLE = "receive_actions";
    public static final String RECEIVE_ACTIONS_ID = "receive_action_id";
    public static final String RECEIVE_ACTIONS_SEND_LOT_ID = "send_lot_id";
    public static final String RECEIVE_ACTIONS_RECEIVE_DATE = "receive_date";
    public static final String RECEIVE_ACTIONS_GODOWN_ID = "receiving_godown_id";
    public static final String RECEIVE_ACTIONS_RECEIVED_BY_USER = "received_by_user";
    public static final String RECEIVE_ACTIONS_LOT_REMARK = "receive_lot_remark";
    public static final String RECEIVE_ACTIONS_CREATED_AT = "created_at";

    // ====================== manufacturing receive items ======================
    public static final String RECEIVE_ITEMS_TABLE = "receive_items";
    public static final String RECEIVE_ITEMS_ID = "receive_item_id";
    public static final String RECEIVE_ITEMS_RECEIVE_ACTION_ID = "receive_action_id";
    public static final String RECEIVE_ITEMS_SEND_ITEM_ID = "send_item_id";
    public static final String RECEIVE_ITEMS_RECEIVED_QUANTITY = "received_quantity";
    public static final String RECEIVE_ITEMS_REMARK = "receive_item_remark";


    static int getStock(String designID) {
        String query = "select " + INVENTORY_TOTAL_QUANTITY + "+" + INVENTORY_OPENING_STOCK + " FROM " + INVENTORY_TABLE + " WHERE " + INVENTORY_DESIGN_ID + " = ?";

        try (Connection con = MyClass.createConnection(); PreparedStatement preparedStatement = con.prepareStatement(query)) {
            preparedStatement.setString(1, designID);
            try (ResultSet rs = preparedStatement.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
                else return NOT_FOUND;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return SQLEXCEPTIONOCCURED;
        }
    }

    public static String getInventoryItemName(String designID) {
        String query = "select " + INVENTORY_ITEM_NAME + " from " + INVENTORY_TABLE + " where " + INVENTORY_DESIGN_ID + " = ?";
        try (Connection con = MyClass.createConnection(); PreparedStatement pstmt = con.prepareStatement(query)) {
            pstmt.setString(1, designID);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getString(1);
                else return String.valueOf(NOT_FOUND);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e.getMessage() + ": error occured");
            return String.valueOf(SQLEXCEPTIONOCCURED);
        }
    }

    public static double getSellPrice(String designID) {
        String query = "select " + INVENTORY_SELL_PRICE + " from " + INVENTORY_TABLE + " WHERE " + INVENTORY_DESIGN_ID + " = ?";
        try (Connection con = MyClass.createConnection(); PreparedStatement pstmt = con.prepareStatement(query)) {
            pstmt.setString(1, designID);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
                else return NOT_FOUND;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e.getMessage() + ": error occured");
            return SQLEXCEPTIONOCCURED;
        }


    }

    public static double getBuyPrice(String designID) {
        String query = "select " + INVENTORY_BUY_PRICE + " from " + INVENTORY_TABLE + " WHERE " + INVENTORY_DESIGN_ID + " = ?";
        try (Connection con = MyClass.createConnection(); PreparedStatement pstmt = con.prepareStatement(query)) {
            pstmt.setString(1, designID);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
                else return NOT_FOUND;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e.getMessage() + ": error occured");
            return SQLEXCEPTIONOCCURED;
        }


    }

    public static void setBuyPrice(String designID, double price) {
        String query = "update " + INVENTORY_TABLE + " set " + INVENTORY_BUY_PRICE + " = ? where " + INVENTORY_DESIGN_ID + " = ?";
        try (Connection con = MyClass.createConnection(); PreparedStatement stmt = con.prepareStatement(query);) {
            stmt.setDouble(1, price);
            stmt.setString(2, designID);
            stmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

    }

    public static List<String> getInventoryDesignIDs() {
        String query = "select " + INVENTORY_DESIGN_ID + " from " + INVENTORY_TABLE;
        List<String> designIDs = new ArrayList<>();
        try (Connection con = MyClass.createConnection(); Statement stmt = con.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    designIDs.add(rs.getString(INVENTORY_DESIGN_ID));
                }
            }
            return designIDs;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
