package testpackage;

import mainpack.MyClass;
import utils.DBStructure;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

public class MainTestClass {
    public static void main(String[] args) {
        String query1 = "SELECT customer_name, slip_id, item_id, sno, design_id, item_name, quantity, plating_grams, raw_material_price, other_details, billed_quantity " + "FROM order_slips WHERE slip_id = ? Order by item_id";
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
                + " WHERE " + DBStructure.ORDER_SLIPS_SLIP_ID + " = ?"
                + " ORDER BY " + DBStructure.ORDER_SLIPS_ITEM_ID;
        billGenerate(1);
    }

    private static void randomTest() {
          billGenerate(20);
    }


    static void orderSlip(int noOfSlips) {
        MyClass.main(null);
        MyClass.login_signup.clickLoginButton();
        MyClass.login.setUserText("shiv");
        MyClass.login.setPasswordText("shiv");
        MyClass.login.clickLoginButton();
        MyClass.mainScreen.clickOrderManagementButton();
        MyClass.orderScreen.clickGenerateANewOrderButton();
       for(int i=0;i<noOfSlips;i++) {
            generateARandomSlip("random");
            MyClass.orderGenerateForm.clickSubmitButton();
        }
    }
    static void billGenerate(int noOfBills){
        MyClass.main(null);
        MyClass.login_signup.clickLoginButton();
        MyClass.login.setUserText("shiv");
        MyClass.login.setPasswordText("shiv");
        MyClass.login.clickLoginButton();
        MyClass.mainScreen.clickBillingButton();
        MyClass.billingScreen.clickNewBillButton();
        generateARandomBill();

    }

    private static void generateARandomBill() {

    }

    static void generateARandomSlip(String date) {
        String[] jewelleryItems = {"Gold Ring", "Diamond Ring", "Silver Ring", "Gold Chain", "Lightweight Chain", "Stone Bracelet", "Gold Bracelet", "Bangle Set", "Mangalsutra", "Pearl Necklace", "Rani Haar", "Mini Haar", "Pendant Set", "Peacock Pendant", "Temple Necklace", "Stud Earrings", "Jhumka Small", "Meena Earrings", "Hoop Earrings", "Nose Pin", "Anklet", "Toe Ring", "Kada", "Brooch", "Choker Necklace"};
        String[] jewelleryTasks = {

                "extra buffing", "no copper plating", "copper plating with surface activation", "light copper", "heavy gold plating", "no dull chilai", "double rhodium finish", "meena color touchup", "stone tightening required", "extra shine polish", "remove old plating", "antique matte finish", "light gold touch", "heavy copper base", "micro gold coating", "surface cleaning required", "premium polish finish", "nickel free plating", "extra vibrator process", "careful stone setting", "manual brush finishing", "high shine rhodium", "no final polish", "double gold coating", "repair hook locking", "solder joint correction", "light antique finish", "extra surface activation", "deep cleaning process", "custom color meena", "remove surface scratches", "heavy rhodium coating", "partial gold plating", "strong copper layer", "extra challa process", "soft matte texture", "diamond area protection", "careful polishing required", "light brush finish", "premium final finishing"

        };
        Random random = new Random();

        if (date.toUpperCase().contentEquals("RANDOM")) {
            date = LocalDate.now().minusDays(random.nextInt(0, 366)).format(DateTimeFormatter.ofPattern("dd-MM-yy"));
        }
        MyClass.orderGenerateForm.setDate(date);
        int noOfCustomer = MyClass.orderGenerateForm.getCustomerNameComboBoxSize();
        MyClass.orderGenerateForm.setCustomer(random.nextInt(1, noOfCustomer));
        int noOfOrderTypes = MyClass.orderGenerateForm.getNumberOfOrderTypes();

        MyClass.orderGenerateForm.setOrderType(random.nextInt(noOfOrderTypes));
        int numberOfItem = random.nextInt(1, 16);

        for (int i = 0; i < numberOfItem; i++) {
            int choice = random.nextInt(3);
            int quantity;
            int randomNumber;
            String name;
            double plating;
            randomNumber = random.nextInt(jewelleryItems.length);
            name = jewelleryItems[randomNumber];
            randomNumber = random.nextInt(1, 16);
            quantity = randomNumber;
            plating = new BigDecimal((double) random.nextInt(5) + random.nextDouble()).setScale(3, RoundingMode.HALF_UP).doubleValue();
            randomNumber = random.nextInt(jewelleryTasks.length);
            String otherDetails = jewelleryTasks[randomNumber];
            List<String> designIDs = DBStructure.getInventoryDesignIDs();
            String designID = designIDs.get(random.nextInt(designIDs.size()));


            switch (choice) {
                case 0:
                    MyClass.orderGenerateForm.insertData(null, name, quantity, plating, otherDetails,null);
                    break;
                case 1:
                    MyClass.orderGenerateForm.insertData(designID, null, quantity, plating, otherDetails,null);
                    break;
                case 2:
                    MyClass.inventorySelect.pushAnRandomItem();
                    MyClass.orderGenerateForm.insertData(null, null, null, plating, otherDetails,MyClass.orderGenerateForm.getNumberOfTableRows()-2);
                    break;
            }

        }

    }
}