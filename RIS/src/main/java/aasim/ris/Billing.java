package aasim.ris;

/**
 *
 * @author 14048
 */
import static aasim.ris.App.ds;
import datastorage.User;
import datastorage.Appointment;
import datastorage.InputValidation;
import datastorage.Order;
import datastorage.Patient;
import datastorage.Payment;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Pair;

public class Billing extends Stage {
    
    //<editor-fold>
    //Stage Structure
    HBox navbar = new HBox();
    Button logOut = new Button("Log Out");
    Label username = new Label("Logged In as Biller: " + App.user.getFullName());
    ImageView pfp = new ImageView(App.user.getPfp());

    BorderPane main = new BorderPane();
    Scene scene = new Scene(main);
    //Table Structure
    TableView table = new TableView();
    Label billing = new Label("Billing");
    Label metrics = new Label("Metrics");

    //Search Bar for Billing page view.
    FilteredList<Appointment> flAppointment;
    ChoiceBox<String> billingChoiceBox = new ChoiceBox();
    TextField search = new TextField("Search Appointments");
    
    // Metric selection for Metrics page view.
    ChoiceBox<String> metricsChoiceBox = new ChoiceBox();
    
    // Metrics date range selector.
    // TODO: Turn this into an actual date range selector.
    Label metricsDateBeginLabel = new Label("From:");
    Label metricsDateEndLabel = new Label("To:");
    DatePicker metricsDateBegin = new DatePicker();
    DatePicker metricsDateEnd = new DatePicker();

    //Buttons
    Button billingRefreshTable = new Button("Refresh Appointments");
    
    //Containers
    HBox billingSearchContainer = new HBox(billingChoiceBox, search);
    HBox billingButtonContainer = new HBox(billingRefreshTable, billingSearchContainer);
    VBox billingContainer = new VBox(table, billingButtonContainer);
    HBox metricsDateBeginContainer = new HBox(metricsDateBeginLabel, metricsDateBegin);
    HBox metricsDateEndContainer = new HBox(metricsDateEndLabel, metricsDateEnd);
    VBox metricsDateRangeContainer = new VBox(metricsDateBeginContainer, metricsDateEndContainer);
    HBox metricsButtonContainer = new HBox(metricsChoiceBox, metricsDateRangeContainer);
    VBox metricsContainer = new VBox(); // Contents added on the fly.

//</editor-fold>
    ArrayList<Order> varName = new ArrayList<>();
    //Populate the stage

    Billing() {
        this.setTitle("RIS- Radiology Information System (Billing)");
        //Navbar
        pfp.setPreserveRatio(true);
        pfp.setFitHeight(38);
        navbar.setAlignment(Pos.TOP_RIGHT);
        logOut.setPrefHeight(30);
        username.setId("navbar");
        username.setOnMouseClicked(eh -> userInfo());
        HBox navButtons = new HBox(billing, metrics);
        navButtons.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(navButtons, Priority.ALWAYS);
        navbar.getChildren().addAll(navButtons, username, pfp, logOut);
        navbar.setStyle("-fx-background-color: #2f4f4f; -fx-spacing: 15;");
        main.setTop(navbar);
        
        billing.setId("navbar");
        metrics.setId("navbar");
        
        logOut.setOnAction((ActionEvent e) -> {
            logOut();
        });
        //End navbar

        //Center
        Label tutorial = new Label("Select one of the buttons above to get started!");
        main.setCenter(tutorial);
        billing.setOnMouseClicked(eh -> billingPageView());
        metrics.setOnMouseClicked(eh -> metricsPageView());
        //End Center

        //Scene Structure
        scene.getStylesheets().add("file:stylesheet.css");
        this.setScene(scene);
        //End scene
    }

    private void populateTable() {
        table.getItems().clear();
        //Connect to database

        String sql = "SELECT appt_id, patient_id, patients.full_name, time, statusCode.status"
                + " FROM appointments"
                + " INNER JOIN statusCode ON appointments.statusCode = statusCode.statusID "
                + " INNER JOIN patients ON patients.patientID = appointments.patient_id"
                + " WHERE statusCode > 3 AND viewable != 0 "
                + " ORDER BY time ASC;";

        try {
            Connection conn = ds.getConnection();

            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            //
            List<Appointment> list = new ArrayList<Appointment>();

            while (rs.next()) {
                //What I receieve:  apptId, patientID, fullname, time, address, insurance, referral, status, order
                Appointment appt = new Appointment(rs.getString("appt_id"), rs.getString("patient_id"), rs.getString("time"), rs.getString("status"), getPatOrders(rs.getString("patient_id"), rs.getString("appt_id")));
                appt.setFullName(rs.getString("full_name"));
                appt.setTotal(calculateTotalCost(appt));
                appt.button.setText("Make Payment");
                appt.button.setOnAction(eh -> makePayment(appt));
                appt.placeholder.setText("View Bill");
                appt.placeholder.setOnAction(eh -> viewBill(appt));
                appt.placeholder1.setText("Remove Appointment");
                appt.placeholder1.setId("cancel");
                appt.placeholder1.setOnAction(eh -> removeAppointment(appt));
                list.add(appt);
            }

            for (Appointment x : list) {
            }
            flAppointment = new FilteredList(FXCollections.observableList(list), p -> true);
            table.getItems().addAll(flAppointment);
            //
            rs.close();
            stmt.close();
            conn.close();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    private String getPatOrders(String patientID, String aInt) {

        String sql = "SELECT orderCodes.orders "
                + " FROM appointmentsOrdersConnector "
                + " INNER JOIN orderCodes ON appointmentsOrdersConnector.orderCodeID = orderCodes.orderID "
                + " WHERE apptID = '" + aInt + "';";

        String value = "";
        try {
            Connection conn = ds.getConnection();

            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            //

            while (rs.next()) {

                value += rs.getString("orders") + ", ";
            }
            //
            rs.close();
            stmt.close();
            conn.close();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return value;
    }

    //On button press, log out
    private void logOut() {
        App.user = new User();
        Stage x = new Login();
        x.show();
        x.setMaximized(true);
        this.hide();
    }
    //On button press, open up a new stage (calls private nested class)
//    private void addAppointment() {
//        Stage x = new AddAppointment();
//        x.setTitle("Add Appointment");
//        x.initOwner(this);
//        x.initModality(Modality.WINDOW_MODAL);
//        x.showAndWait();
//        populateTable();
//    }
    //On button press, open up a new stage (calls private nested class)

    private float calculateTotalCost(Appointment appt) {

        String sql = "SELECT orderCodes.cost "
                + " FROM appointmentsOrdersConnector "
                + " INNER JOIN orderCodes ON appointmentsOrdersConnector.orderCodeID = orderCodes.orderID "
                + " WHERE apptID = '" + appt.getApptID() + "';";

        float value = 0;
        try {
            Connection conn = ds.getConnection();

            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            //

            while (rs.next()) {

                value += rs.getFloat("cost");
            }
            //
            rs.close();
            stmt.close();
            conn.close();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return value;
    }

//        float value = -1;
//        return value;
    private void viewBill(Appointment appt) {
        Stage x = new Stage();
        x.setTitle("View Bill");
        x.setMaximized(true);
//        x.initOwner(this);
//        x.initModality(Modality.WINDOW_MODAL);
//        x.show();
        BorderPane bp = new BorderPane();
        Scene sc = new Scene(bp);
        x.setScene(sc);
        sc.getStylesheets().add("file:stylesheet.css");
// code goes here
//header
        HBox header = new HBox();
        Label patientName = new Label("Patient Name:\n" + appt.getFullName());
        Label patientEmail = new Label("Email:\n" + getEmail(appt.getPatientID()));
        Label patientAddress = new Label("Address:\n" + getAddress(appt.getPatientID()));
        Label patientInsurance = new Label("Insurance:\n" + getInsurance(appt.getPatientID()));
        header.getChildren().addAll(patientName, patientEmail, patientAddress, patientInsurance);
        bp.setTop(header);
//end header
//center
        float paybox = 0;
        GridPane grid = new GridPane();
        grid.setGridLinesVisible(true);

        VBox center = new VBox(grid);
        ScrollPane sp = new ScrollPane(center);
        String order[] = appt.getOrder().split(",");
        int counter = 0;
        for (int i = 0; i < order.length - 1; i++) {
            Label tempOrder = new Label(order[i].trim());
            Label tempCost = new Label("Hello");
            for (Order a : varName) {
                if (a.getOrder().equals(order[i].trim())) {
                    tempCost.setText(a.getCost() + "");
                    paybox += a.getCost();
                }
            }
            Label apptDate = new Label(appt.getTime().split(" ")[0]);
            grid.add(apptDate, 1, i);
            grid.add(tempOrder, 0, i);
            grid.add(tempCost, 2, i);
            counter = i;
//            center.getChildren().add(he);
        }
        counter++;
        ArrayList<Payment> payment = populatePayment(appt.getApptID());
        for (Payment p : payment) {
            Label byWhom = new Label("Patient Paid");
            if (p.getByPatient() == 0) {
                byWhom.setText("Insurance Paid");
            }
            Label tempPaymentDate = new Label(p.getTime());
            float num = -1 * p.getPayment();
            String positive = "";
            if (num > 0) {
                positive = "+";
            }
            Label tempPayment = new Label(positive + num);
            if (num > 0) {
                byWhom.setId("shadeRed");
                tempPaymentDate.setId("shadeRed");
                tempPayment.setId("shadeRed");
            } else {
                byWhom.setId("shadeGreen");
                tempPaymentDate.setId("shadeGreen");
                tempPayment.setId("shadeGreen");

            }

            grid.add(byWhom, 0, counter);
            grid.add(tempPaymentDate, 1, counter);
            grid.add(tempPayment, 2, counter);
            paybox -= p.getPayment();
            counter++;
        }
        bp.setCenter(sp);
//end center
//footer
        Button btn = new Button("Go Back");
        btn.setId("cancel");
        btn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent eh) {
                x.close();
            }
        });
        Button btn1 = new Button("Make Payment");
        btn1.setId("complete");
        btn1.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent eh) {
                makePayment(appt);
                x.close();
                viewBill(appt);
            }
        });
        HBox footer = new HBox();
        Label blank = new Label("Total Bill Remaining: ");
        Label tc = new Label("" + paybox);

        footer.getChildren().addAll(btn, blank, tc, btn1);
        bp.setBottom(footer);
//end footer
        x.show();
    }

    private String getAddress(String patientID) {

        String sql = "SELECT address FROM patients WHERE patientID = '" + patientID + "';";

        String value = "";
        try {
            Connection conn = ds.getConnection();

            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            //

            while (rs.next()) {

                value += rs.getString("address");
            }
            //
            rs.close();
            stmt.close();
            conn.close();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return value;
    }

    private String getEmail(String patientID) {

        String sql = "SELECT email FROM patients WHERE patientID = '" + patientID + "';";

        String value = "";
        try {
            Connection conn = ds.getConnection();

            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            //

            while (rs.next()) {

                value += rs.getString("email");
            }
            //
            rs.close();
            stmt.close();
            conn.close();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return value;
    }

    private String getInsurance(String patientID) {

        String sql = "SELECT insurance FROM patients WHERE patientID = '" + patientID + "';";

        String value = "";
        try {
            Connection conn = ds.getConnection();

            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            //

            while (rs.next()) {

                value += rs.getString("insurance");
            }
            //
            rs.close();
            stmt.close();
            conn.close();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return value;
    }

    private ArrayList<Order> populateOrders() {

        String sql = "SELECT * FROM orderCodes;";

        ArrayList<Order> value = new ArrayList<>();
        try {
            Connection conn = ds.getConnection();

            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            //

            while (rs.next()) {
                Order order = new Order(rs.getString("orderID"), rs.getString("orders"));
                order.setCost(rs.getFloat("cost"));
                // value += rs.getS("insurance");
                value.add(order);
            }
            //
            rs.close();
            stmt.close();
            conn.close();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return value;

    }

    private ArrayList<Payment> populatePayment(String apptID) {

        String sql = "SELECT * FROM patientPayments WHERE apptID ='" + apptID + "';";

        ArrayList<Payment> value = new ArrayList<>();
        try {
            Connection conn = ds.getConnection();

            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            //

            while (rs.next()) {
                Payment payment = new Payment(rs.getString("apptID"), rs.getString("time"), rs.getFloat("patientPayment"));
                payment.setByPatient(rs.getInt("byPatient"));
                // value += rs.getS("insurance"); 
                value.add(payment);
            }
            //
            rs.close();
            stmt.close();
            conn.close();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return value;

    }

    private void makePayment(Appointment appt) {
        Stage x = new Stage();
        VBox container = new VBox();
        Scene scene = new Scene(container);
        scene.getStylesheets().add("file:stylesheet.css");
        x.setScene(scene);

        HBox hello = new HBox();
        Label enterpay = new Label("Enter Payment Here");
        TextField ep = new TextField();
        ComboBox dropdown = new ComboBox();
        dropdown.getItems().addAll("Patient", "Insurance");
        dropdown.setValue("Patient");
        Button b = new Button("Submit");
        hello.getChildren().addAll(enterpay, ep, dropdown, b);
        container.getChildren().addAll(hello);
        b.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent eh) {
                if (!InputValidation.validatePayment(ep.getText())) {

                    return;

                }
                String sql = "";
                if (dropdown.getValue().toString().equals("Patient")) {
                    sql = "INSERT INTO patientPayments(apptID, time, patientPayment, byPatient) VALUES ('" + appt.getApptID() + "', '" + LocalDate.now() + "' , '" + ep.getText() + "', '1' )";
                } else {
                    sql = "INSERT INTO patientPayments(apptID, time, patientPayment, byPatient) VALUES ('" + appt.getApptID() + "', '" + LocalDate.now() + "' , '" + ep.getText() + "', '0' )";
                }
                App.executeSQLStatement(sql);
                x.close();
                //sql = "INSERT INTO patientPayments(apptID, time, patientPayment, byPatient) VALUES ('"+appt.getApptID()+"', '"+LocalDate.now() +"' , '"+ep.getText()+"', '1' )";
            }
        });
        x.showAndWait();
    }

    private void removeAppointment(Appointment appt) {
        String sql = "UPDATE appointments SET viewable = 0 WHERE appt_id = '" + appt.getApptID() + "';";
        App.executeSQLStatement(sql);
        populateTable();
    }

    private void userInfo() {
        Stage x = new UserInfo();
        x.show();
        x.setMaximized(true);
        this.close();
    }
    
    private void populateMethodologyCostBreakdown()
    {
        // Obtain a list of all methodologies.
        class Methodology {
            public Long order_id;
            public String name;
            public Float cost;
        }
        
        List<Methodology> methodologies = new ArrayList<>();
        String sql = "SELECT * FROM ordercodes;";
        
        try {
            Connection conn = ds.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            //
            while (rs.next()) {
                Methodology newMethodology = new Methodology();
                newMethodology.order_id = rs.getLong("orderid");
                newMethodology.name = rs.getString("orders");
                newMethodology.cost = rs.getFloat("cost");
                methodologies.add(newMethodology);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        
        // TODO: Add a parameter for specifying date ranges.
        // Obtain a list of all appointments.
        List<String> appt_ids = new ArrayList<>();
        
        sql = "SELECT appt_id"
            + " FROM appointments"
            + " WHERE time"
            + " BETWEEN '"
            + metricsDateBegin.getValue()
            + " 00:00'"
            + " AND '"
            + metricsDateEnd.getValue()
            + " 23:59';";
        
        try {
            Connection conn = ds.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            //
            while (rs.next()) {
                appt_ids.add(rs.getString("appt_id"));
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        
        // Obtain a list of orders associated with all appointments.
        List<Long> order_ids = new ArrayList<>();
        sql = "SELECT ordercodeid"
            + " FROM appointmentsordersconnector"
            + " WHERE apptid"
            + " IN ("
            + String.join(",", appt_ids)
            + ");";
        
        try {
            Connection conn = ds.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            //
            while (rs.next()) {
                order_ids.add(rs.getLong("ordercodeid"));
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        
        // Compute the total cost attributed to each methodology.
        List<Pair<Methodology, Float>> costs = new ArrayList<>();
        for (Methodology methodology : methodologies) {
            // Compute the total cost of the `i`th methodology.
            int orders = 0;
            for (Long order_id : order_ids) {
                if (order_id.equals(methodology.order_id)) {
                    orders++;
                }
            }
            
            // Store this cost in the list of total costs.
            Pair<Methodology, Float> cost = new Pair<>(
                    methodology,
                    methodology.cost * orders
            );
            costs.add(cost);
        }
        
        // Create a pie chart.
        ObservableList<PieChart.Data> data = FXCollections.observableArrayList();
        for (Pair<Methodology, Float> cost : costs) {
            data.add(new PieChart.Data(cost.getKey().name, cost.getValue()));
        }
        PieChart costsChart = new PieChart(data);
        costsChart.setTitle("Total Costs by Methodology");
        costsChart.setLegendVisible(false);
        // Show dollar amounts for each methodology in labels.
        final Label caption = new Label("");
        for (final PieChart.Data datum : costsChart.getData()) {
            datum.nameProperty().setValue(
                datum.getName()
                + ": $"
                + datum.getPieValue()
            );
        }
        
        metricsContainer.getChildren().addAll(costsChart, caption);
    }
    
    private void populateAppointmentStatusBreakdown()
    {
        // Obtain all appointments with statuses.
        Map<Integer, Integer> statuses = new HashMap<>();
        // TODO: Add a parameter for specifying date ranges.
        String sql = "SELECT statuscode"
                   + " FROM appointments"
                   + " WHERE time"
                   + " BETWEEN '"
                   + metricsDateBegin.getValue()
                   + " 00:00'"
                   + " AND '"
                   + metricsDateEnd.getValue()
                   + " 23:59';";
        
        try {
            Connection conn = ds.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            //
            while (rs.next()) {
                Integer status = rs.getInt("statuscode");
                if (statuses.containsKey(status)) {
                    // Increment status counter.
                    statuses.replace(status, statuses.get(status) + 1);
                } else {
                    statuses.put(status, 1);
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        
        // Obtain all status codes.
        Map<Integer, String> statusCodes = new HashMap<>();
        sql = "SELECT * FROM statuscode;";
        
        try {
            Connection conn = ds.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            //
            while (rs.next()) {
                statusCodes.put(rs.getInt("statusid"), rs.getString("status"));
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        
        // Create a pie chart.
        ObservableList<PieChart.Data> data = FXCollections.observableArrayList();
        for (Integer status : statuses.keySet()) {
            data.add(new PieChart.Data(statusCodes.get(status), statuses.get(status)));
        }
        PieChart statusesChart = new PieChart(data);
        statusesChart.setTitle("Appointment Statuses");
        statusesChart.setLegendVisible(false);
        // Show quantity for each status in labels.
        final Label caption = new Label("");
        for (final PieChart.Data datum : statusesChart.getData()) {
            datum.nameProperty().setValue(
                datum.getName()
                + ": "
                + (int)datum.getPieValue()
            );
        }
        
        metricsContainer.getChildren().addAll(statusesChart, caption);
    }
    
    private void populateCostTimeline()
    {
        // Obtain status code associated with completed appointment.
        String sql = "SELECT statusid FROM statuscode WHERE status = '"
                   + "Referral Doctor Signature Completed."
                   + "';";
        Integer completedApptStatusCode = null;
        
        try {
            Connection conn = ds.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            //
            rs.next();
            completedApptStatusCode = rs.getInt("statusid");
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        
        // Obtain all appointment IDs by age.
        Map<LocalDate, Long> appts = new HashMap<>();
        sql = "SELECT appt_id, time"
            + " FROM appointments"
            + " WHERE (statuscode = "
            + completedApptStatusCode
            + ") AND (time BETWEEN '"
            + metricsDateBegin.getValue()
            + " 00:00'"
            + " AND '"
            + metricsDateEnd.getValue()
            + " 23:59')"
            + " ORDER BY time ASC;";
        
        LocalDate earliestDate = null;
        LocalDate latestDate = null;
        try {
            Connection conn = ds.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            //
            while (rs.next()) {
                latestDate = LocalDate.parse(rs.getString("time").split(" ")[0]);
                if (earliestDate == null) {
                    earliestDate = latestDate;
                }
                appts.put(
                    latestDate,
                    rs.getLong("appt_id")
                );
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        
        // Generate date ranges.
        if (earliestDate == null) { // In case of no results.
            earliestDate = metricsDateBegin.getValue();
            latestDate = metricsDateEnd.getValue();
        }
        long earliest = earliestDate.toEpochDay();
        long latest = latestDate.toEpochDay();
        LocalDate[] dateRangeEnds = {
            LocalDate.ofEpochDay((long)(0.2 * earliest + 0.8 * latest)),
            LocalDate.ofEpochDay((long)(0.4 * earliest + 0.6 * latest)),
            LocalDate.ofEpochDay((long)(0.6 * earliest + 0.4 * latest)),
            LocalDate.ofEpochDay((long)(0.8 * earliest + 0.2 * latest)),
            latestDate
        };
        
        // Obtain a list of all methodologies.
        class Methodology {
            public Long order_id;
            public String name;
            public Float cost;
        }
        
        List<Methodology> methodologies = new ArrayList<>();
        sql = "SELECT * FROM ordercodes;";
        
        try {
            Connection conn = ds.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            //
            while (rs.next()) {
                Methodology newMethodology = new Methodology();
                newMethodology.order_id = rs.getLong("orderid");
                newMethodology.name = rs.getString("orders");
                newMethodology.cost = rs.getFloat("cost");
                methodologies.add(newMethodology);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        
        // Obtain appointments corresponding to each date range.
        List<String>[] apptsByDateRange = new ArrayList[dateRangeEnds.length];
        // Instantiate ArrayLists.
        for (int i = 0; i < dateRangeEnds.length; i++) {
            apptsByDateRange[i] = new ArrayList<>();
        }
        for (HashMap.Entry<LocalDate, Long> entry : appts.entrySet()) {
            // Determine which date range this entry falls into.
            int dateRange = 0;
            
            while (dateRange < dateRangeEnds.length - 1
                   && entry.getKey().isBefore(dateRangeEnds[dateRange])) {
                dateRange++;
            }
            apptsByDateRange[dateRange].add(entry.getValue().toString());
        }
        
        // Obtain total costs incurred for each date range.
        Float[] costsByDateRange = new Float[apptsByDateRange.length];
        for (int i = 0; i < dateRangeEnds.length; i++) {
            costsByDateRange[i] = 0f;
            
            sql = "SELECT ordercodeid"
                + " FROM appointmentsordersconnector"
                + " WHERE apptid"
                + " IN ("
                + String.join(",", apptsByDateRange[i])
                + ");";

            try {
                Connection conn = ds.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql);
                //
                while (rs.next()) {
                    Long order_id = rs.getLong("ordercodeid");
                    for (Methodology methodology : methodologies) {
                        if (Objects.equals(order_id, methodology.order_id)) {
                            costsByDateRange[i] += methodology.cost;
                        }
                    }
                }
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
        }
        
        // Create a pie chart.
        ObservableList<PieChart.Data> data = FXCollections.observableArrayList();
        for (int i = 0; i < costsByDateRange.length; i++) {
            String dateRange;
            if (i == 0) {
                dateRange = earliestDate.toString()
                          + " to "
                          + dateRangeEnds[0].toString();
            } else {
                dateRange = dateRangeEnds[i-1].toString()
                          + " to "
                          + dateRangeEnds[i].toString();
            }
            data.add(new PieChart.Data(dateRange, costsByDateRange[i]));
        }
        PieChart agesChart = new PieChart(data);
        agesChart.setTitle("Total Costs by Dates");
        agesChart.setLegendVisible(false);
        // Show quantity for each status in labels.
        final Label caption = new Label("");
        for (final PieChart.Data datum : agesChart.getData()) {
            datum.nameProperty().setValue(
                datum.getName()
                + ": $"
                + datum.getPieValue()
            );
        }
        
        metricsContainer.getChildren().addAll(agesChart, caption);
    }
    
    private void billingPageView()
    {
        // Why is this even here?
        billingContainer.getChildren().clear();
        billingContainer.getChildren().addAll(table, billingButtonContainer);
        
        main.setCenter(billingContainer);
        
        // Format page.
        table.getColumns().clear();
        //Vbox to hold the table
        billingContainer.setAlignment(Pos.TOP_CENTER);
        billingContainer.setPadding(new Insets(20, 10, 10, 10));
        billingButtonContainer.setPadding(new Insets(10));
        billingButtonContainer.setSpacing(10);
        TableColumn apptIDCol = new TableColumn("Appointment ID");
        TableColumn patientIDCol = new TableColumn("Patient ID");
        TableColumn firstNameCol = new TableColumn("Full Name");
        TableColumn timeCol = new TableColumn("Time of Appt.");
        TableColumn orderCol = new TableColumn("Orders Requested");
        TableColumn status = new TableColumn("Status");
        TableColumn updateAppt = new TableColumn("Update Billing");
        TableColumn totalCost = new TableColumn("Total Cost");
        TableColumn makePayment = new TableColumn("Make Payment");
        TableColumn delAppt = new TableColumn("Remove Appointment");
        //Allow Table to read Appointment class
        apptIDCol.setCellValueFactory(new PropertyValueFactory<>("apptID"));
        patientIDCol.setCellValueFactory(new PropertyValueFactory<>("patientID"));
        firstNameCol.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        timeCol.setCellValueFactory(new PropertyValueFactory<>("time"));
        orderCol.setCellValueFactory(new PropertyValueFactory<>("order"));
        status.setCellValueFactory(new PropertyValueFactory<>("status"));
        updateAppt.setCellValueFactory(new PropertyValueFactory<>("placeholder"));
        totalCost.setCellValueFactory(new PropertyValueFactory<>("total"));
        delAppt.setCellValueFactory(new PropertyValueFactory<>("placeholder1"));
        makePayment.setCellValueFactory(new PropertyValueFactory<>("button"));
        //Set Column Widths
        apptIDCol.prefWidthProperty().bind(table.widthProperty().multiply(0.09));
        patientIDCol.prefWidthProperty().bind(table.widthProperty().multiply(0.09));
        firstNameCol.prefWidthProperty().bind(table.widthProperty().multiply(0.1));
        timeCol.prefWidthProperty().bind(table.widthProperty().multiply(0.06));
        orderCol.prefWidthProperty().bind(table.widthProperty().multiply(0.2));
        updateAppt.prefWidthProperty().bind(table.widthProperty().multiply(0.08));
        status.prefWidthProperty().bind(table.widthProperty().multiply(0.08));
        totalCost.prefWidthProperty().bind(table.widthProperty().multiply(0.1));

        //Add columns to table
        table.getColumns().addAll(apptIDCol, patientIDCol, firstNameCol, timeCol, orderCol, totalCost, status, updateAppt, makePayment, delAppt);
        table.setStyle("-fx-background-color: #25A18E; -fx-text-fill: WHITE; ");
        
        varName = populateOrders();
        //Buttons
//        addAppointment.setOnAction(new EventHandler<ActionEvent>() {
//            @Override
//            public void handle(ActionEvent e) {
//                addAppointment();
//            }
//        });

        billingRefreshTable.setOnAction((ActionEvent e) -> {
            populateTable();
        });
//         check.setOnAction(new EventHandler<ActionEvent>() {
//            @Override
//            public void handle(ActionEvent e) {
//                billWindow();
//            }
//        });

        //Searchbar Structure
        billingSearchContainer.setAlignment(Pos.TOP_RIGHT);
        HBox.setHgrow(billingSearchContainer, Priority.ALWAYS);
        billingChoiceBox.setPrefHeight(40);
        search.setPrefHeight(40);
        billingChoiceBox.getItems().clear();
        billingChoiceBox.getItems().addAll("Appointment ID", "Patient ID", "Full Name", "Date/Time", "Status");
        billingChoiceBox.setValue("Appointment ID");
        search.textProperty().addListener((obs, oldValue, newValue) -> {
            if (billingChoiceBox.getValue().equals("Appointment ID")) {
                flAppointment.setPredicate(p -> (p.getApptID() + "").contains(newValue));//filter table by Appt ID
            }
            if (billingChoiceBox.getValue().equals("Patient ID")) {
                flAppointment.setPredicate(p -> (p.getPatientID() + "").contains(newValue));//filter table by Patient Id
            }
            if (billingChoiceBox.getValue().equals("Full Name")) {
                flAppointment.setPredicate(p -> p.getFullName().toLowerCase().contains(newValue.toLowerCase()));//filter table by Full name
            }
            if (billingChoiceBox.getValue().equals("Date/Time")) {
                flAppointment.setPredicate(p -> p.getTime().contains(newValue));//filter table by Date/Time
            }
            if (billingChoiceBox.getValue().equals("Status")) {
                flAppointment.setPredicate(p -> p.getStatus().toLowerCase().contains(newValue.toLowerCase()));//filter table by Status
            }
            table.getItems().clear();
            table.getItems().addAll(flAppointment);
        });
        //End Searchbar Structure
        
        //This function populates the table, making sure all NONCOMPLETED appointments are viewable
        populateTable();
    }
    
    private void metricsPageView()
    {
        main.setCenter(metricsContainer);
        
        metricsButtonContainer.setPadding(new Insets(10));
        metricsButtonContainer.setSpacing(10);
        
        metricsDateBeginLabel.setAlignment(Pos.TOP_RIGHT);
        metricsDateEndLabel.setAlignment(Pos.TOP_RIGHT);
        
        // Populate metricsChoiceBox.
        metricsChoiceBox.getItems().clear();
        metricsChoiceBox.getItems().addAll(
            "Methodology Costs",
            "Appointment Status",
            "Costs Timeline"
        );
        metricsChoiceBox.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
                metricsContainer.getChildren().clear();
                metricsContainer.getChildren().add(metricsButtonContainer);
                if (newValue.equals("Methodology Costs")) {
                    populateMethodologyCostBreakdown();
                }
                if (newValue.equals("Appointment Status")) {
                    populateAppointmentStatusBreakdown();
                }
                if (newValue.equals("Costs Timeline")) {
                    populateCostTimeline();
                }
            }
        );
        
        // Set initial date range.
        metricsDateBegin.setValue(LocalDate.parse("1900-01-01"));
        metricsDateEnd.setValue(LocalDate.now());
        
        // Add triggers to reload view upon date range change.
        metricsDateBegin.valueProperty().addListener((obs, oldValue, newValue) -> {
            // Validation
            if (newValue.isAfter(metricsDateEnd.getValue())) {
                metricsDateBegin.setValue(oldValue);
                return;
            }
            // End validation
            String currentMetricsPageView = metricsChoiceBox.getValue();
            metricsChoiceBox.setValue("");
            metricsChoiceBox.setValue(currentMetricsPageView);
        });
        metricsDateEnd.valueProperty().addListener((obs, oldValue, newValue) -> {
            // Validation
            if (newValue.isBefore(metricsDateBegin.getValue())) {
                metricsDateEnd.setValue(oldValue);
                return;
            }
            // End validation
            String currentMetricsPageView = metricsChoiceBox.getValue();
            metricsChoiceBox.setValue("");
            metricsChoiceBox.setValue(currentMetricsPageView);
        });
        
        // Set initial metric view.
        metricsChoiceBox.setValue("Methodology Costs");
    }
}
