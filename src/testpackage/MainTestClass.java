package testpackage;

import mainpack.MyClass;
import utils.DBStructure;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MainTestClass {
    private static final String[] CUSTOMER_NAME_DOMAIN = {
            "Aarav Sharma", "Vivaan Verma", "Aditya Gupta", "Arjun Mehta", "Rohan Kapoor",
            "Kunal Jain", "Rahul Singh", "Aman Patel", "Siddharth Shah", "Nikhil Agarwal",
            "Ananya Sharma", "Aditi Verma", "Priya Gupta", "Kavya Mehta", "Riya Kapoor",
            "Sneha Jain", "Pooja Singh", "Neha Patel", "Isha Shah", "Meera Agarwal",
            "Akash Bansal", "Varun Malhotra", "Manish Soni", "Deepak Yadav", "Rakesh Tiwari",
            "Sanjay Mishra", "Vikas Chauhan", "Harsh Vardhan", "Mohit Arora", "Gaurav Saxena",
            "Nisha Bansal", "Swati Malhotra", "Ritu Soni", "Komal Yadav", "Pallavi Tiwari",
            "Sakshi Mishra", "Preeti Chauhan", "Shreya Arora", "Tanvi Saxena", "Bhavna Joshi",
            "Devendra Kumar", "Prakash Chandra", "Mukesh Bhatt", "Dinesh Rathore", "Rajesh Solanki",
            "Suresh Goyal", "Ashok Bhatia", "Vinod Khanna", "Naresh Sethi", "Pankaj Desai",
            "Lakshmi Kumar", "Suman Chandra", "Rekha Bhatt", "Kiran Rathore", "Sunita Solanki",
            "Geeta Goyal", "Asha Bhatia", "Usha Khanna", "Maya Sethi", "Renu Desai",
            "Krishna Nair", "Ravi Iyer", "Sandeep Menon", "Vijay Pillai", "Arvind Reddy",
            "Srinivas Rao", "Karthik Iyer", "Pranav Nair", "Ramesh Menon", "Bharat Pillai",
            "Divya Nair", "Anjali Iyer", "Nandini Menon", "Keerthi Pillai", "Asha Reddy",
            "Sowmya Rao", "Vaishnavi Iyer", "Pavithra Nair", "Harini Menon", "Bhavana Pillai",
            "Imran Khan", "Farhan Ali", "Salman Sheikh", "Yusuf Ansari", "Arif Qureshi",
            "Sameer Hussain", "Zaid Siddiqui", "Faisal Khan", "Rehan Ali", "Asif Sheikh",
            "Sana Khan", "Ayesha Ali", "Fatima Sheikh", "Nazia Ansari", "Hina Qureshi",
            "Saira Hussain", "Alina Siddiqui", "Mariyam Khan", "Rukhsar Ali", "Noor Sheikh"
    };

    public static void main(String[] args) {
        createCustomers("raj", "", "akshat");
    }

    private static void randomTest() {
          billGenerate(20);
    }

    static void createCustomers(String user, String database, String password) {
        MyClass.main(null);
        MyClass.login_signup.clickLoginButton();
        MyClass.login.setUserText(user);
        MyClass.login.setPasswordText(password);
        MyClass.login.setDatabaseText(database);
        MyClass.login.clickLoginButton();

        MyClass.mainScreen.clickAddPartyButton();
        List<String> randomNames = new ArrayList<>(Arrays.asList(CUSTOMER_NAME_DOMAIN));
        Collections.shuffle(randomNames);
        for (int index = 1; index <= 10; index++) {
            MyClass.addParty.setCustomerName(randomNames.get(index - 1));
            MyClass.addParty.setOpeningBalance("0");
            MyClass.addParty.clickAddButton();
        }
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
