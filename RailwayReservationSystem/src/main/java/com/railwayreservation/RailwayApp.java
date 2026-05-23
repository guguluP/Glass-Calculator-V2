package com.railwayreservation;

import com.railwayreservation.model.Booking;
import com.railwayreservation.model.Passenger;
import com.railwayreservation.model.Train;
import com.railwayreservation.service.DataService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;

import javafx.animation.PauseTransition;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class RailwayApp extends Application {

    private final DataService dataService = new DataService();
    private String currentUser = "Guest";

    private ComboBox<String> fromBox;
    private ComboBox<String> toBox;
    private DatePicker datePicker;
    private ListView<Train> resultsListView;
    private ListView<Booking> bookingsListView;
    private Label statusLabel;
    private TabPane tabPane;
    private Label userLabel;

    private static final List<String> CLASS_OPTIONS = List.of("SL", "3A", "2A", "1A");

    @Override
    public void start(Stage primaryStage) {
        dataService.load();

        BorderPane root = new BorderPane();
        root.setTop(buildHeader());
        root.setCenter(buildMainContent());
        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root, 1100, 740);
        var cssUrl = getClass().getResource("/styles/railway-reservation.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            System.out.println("⚠️ CSS not found at /styles/railway-reservation.css — UI will be unstyled");
        }

        primaryStage.setTitle("RailReserve • Railway Reservation System");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> dataService.saveAll());
        primaryStage.show();

        // Initial load
        refreshStations();
        updateStatus();
    }

    private Node buildHeader() {
        HBox header = new HBox(15);
        header.setPadding(new Insets(12, 20, 12, 20));
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("rail-header");

        Label title = new Label("🚂 RailReserve");
        title.getStyleClass().add("app-title");
        title.setFont(Font.font(26));

        Label subtitle = new Label("JavaFX Railway Reservation");
        subtitle.getStyleClass().add("app-subtitle");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        userLabel = new Label("👤 " + currentUser);
        userLabel.getStyleClass().add("user-label");

        Button changeUserBtn = new Button("Change User");
        changeUserBtn.getStyleClass().add("secondary-button");
        changeUserBtn.setOnAction(e -> promptChangeUser());

        Button refreshBtn = new Button("⟳ Refresh Data");
        refreshBtn.getStyleClass().add("secondary-button");
        refreshBtn.setOnAction(e -> {
            dataService.load();
            refreshStations();
            updateStatus();
            if (resultsListView != null) resultsListView.getItems().clear();
            if (bookingsListView != null) refreshBookingsView();
        });

        header.getChildren().addAll(title, subtitle, spacer, userLabel, changeUserBtn, refreshBtn);
        return header;
    }

    private void promptChangeUser() {
        TextInputDialog dialog = new TextInputDialog(currentUser);
        dialog.setTitle("Change User");
        dialog.setHeaderText("Enter your name for this session");
        dialog.setContentText("Name:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                currentUser = name.trim();
                userLabel.setText("👤 " + currentUser);
                refreshBookingsView();
            }
        });
    }

    private Node buildMainContent() {
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab searchTab = new Tab("🔍 Search & Book Trains", buildSearchTab());
        Tab bookingsTab = new Tab("🎫 My Bookings", buildBookingsTab());
        Tab adminTab = new Tab("🛠️ Manage Trains", buildAdminTab());

        tabPane.getTabs().addAll(searchTab, bookingsTab, adminTab);
        return tabPane;
    }

    private Node buildSearchTab() {
        VBox container = new VBox(15);
        container.setPadding(new Insets(20));
        container.getStyleClass().add("search-container");

        // Search form
        HBox form = new HBox(12);
        form.setAlignment(Pos.CENTER_LEFT);
        form.getStyleClass().add("search-form");

        Label fromLbl = new Label("From:");
        fromBox = new ComboBox<>();
        fromBox.setEditable(true);
        fromBox.setPrefWidth(180);

        Label toLbl = new Label("To:");
        toBox = new ComboBox<>();
        toBox.setEditable(true);
        toBox.setPrefWidth(180);

        Label dateLbl = new Label("Date:");
        datePicker = new DatePicker(LocalDate.now().plusDays(1));
        datePicker.setPrefWidth(140);

        Button searchBtn = new Button("🔍 SEARCH TRAINS");
        searchBtn.getStyleClass().add("primary-button");
        searchBtn.setOnAction(e -> performSearch());

        form.getChildren().addAll(fromLbl, fromBox, toLbl, toBox, dateLbl, datePicker, searchBtn);

        // Results
        Label resultsTitle = new Label("Available Trains");
        resultsTitle.getStyleClass().add("section-title");

        resultsListView = new ListView<>();
        resultsListView.setPrefHeight(420);
        resultsListView.setCellFactory(createTrainCellFactory());

        VBox.setVgrow(resultsListView, Priority.ALWAYS);
        container.getChildren().addAll(form, resultsTitle, resultsListView);
        return container;
    }

    private Callback<ListView<Train>, ListCell<Train>> createTrainCellFactory() {
        return lv -> new ListCell<>() {
            @Override
            protected void updateItem(Train train, boolean empty) {
                super.updateItem(train, empty);
                if (empty || train == null) {
                    setGraphic(null);
                    return;
                }
                HBox card = new HBox(12);
                card.setAlignment(Pos.CENTER_LEFT);
                card.getStyleClass().add("train-card");

                VBox info = new VBox(4);
                Label trainId = new Label(train.getTrainNo() + " • " + train.getName());
                trainId.getStyleClass().add("train-name");

                Label route = new Label(train.getSource() + " → " + train.getDestination());
                route.getStyleClass().add("train-route");

                String dep = train.getDeparture();
                String arr = train.getArrival();
                String timesText = ("TBD".equals(dep) || "TBD".equals(arr))
                    ? "Timings: Check IRCTC (real data coming soon)"
                    : "⏰ " + dep + "  →  " + arr;
                Label times = new Label(timesText);
                times.getStyleClass().add("train-times");

                HBox pills = new HBox(6);
                for (Map.Entry<String, Integer> e : train.getAvailableSeats().entrySet()) {
                    Label pill = new Label(e.getKey() + ": " + e.getValue());
                    pill.getStyleClass().add("availability-pill");
                    pills.getChildren().add(pill);
                }

                // Frequency badge from real timetable data (Phase 2)
                Label freqPill = new Label(train.getFrequency());
                freqPill.getStyleClass().add("frequency-pill");
                pills.getChildren().add(freqPill);

                info.getChildren().addAll(trainId, route, times, pills);

                Region spacer2 = new Region();
                HBox.setHgrow(spacer2, Priority.ALWAYS);

                Button bookBtn = new Button("Book Now →");
                bookBtn.getStyleClass().add("primary-button");
                bookBtn.setOnAction(ev -> openBookingDialog(train));

                card.getChildren().addAll(info, spacer2, bookBtn);
                setGraphic(card);
            }
        };
    }

    private void performSearch() {
        String from = fromBox.getValue();
        String to = toBox.getValue();
        LocalDate date = datePicker.getValue();

        if (from == null || to == null || from.isBlank() || to.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Missing Info", "Please enter From and To stations.");
            return;
        }

        List<Train> results = dataService.searchTrains(from, to, date);
        resultsListView.setItems(FXCollections.observableArrayList(results));

        if (results.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "No Trains Found", "No matching trains for the selected route.");
        }
        updateStatus();
    }

    private void openBookingDialog(Train train) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Book Ticket • " + train.getTrainNo());

        VBox root = new VBox(14);
        root.setPadding(new Insets(20));
        root.getStyleClass().add("booking-dialog");

        // Train summary card
        HBox summary = new HBox(10);
        summary.getStyleClass().add("train-summary");
        Label sum = new Label(train.getTrainNo() + " • " + train.getName() + "\n" +
                train.getSource() + " → " + train.getDestination() + "  |  " + train.getDeparture() + " - " + train.getArrival());
        summary.getChildren().add(sum);

        // Class selector
        HBox classRow = new HBox(8);
        classRow.setAlignment(Pos.CENTER_LEFT);
        Label clsLbl = new Label("Class:");
        ToggleGroup classGroup = new ToggleGroup();
        List<ToggleButton> classBtns = new ArrayList<>();
        for (String c : CLASS_OPTIONS) {
            ToggleButton tb = new ToggleButton(c);
            tb.setToggleGroup(classGroup);
            tb.getStyleClass().add("class-toggle");
            classBtns.add(tb);
        }
        classBtns.get(0).setSelected(true); // default SL
        classRow.getChildren().add(clsLbl);
        classRow.getChildren().addAll(classBtns);

        // Num passengers
        HBox numRow = new HBox(8);
        numRow.setAlignment(Pos.CENTER_LEFT);
        Label numLbl = new Label("Passengers:");
        ComboBox<Integer> numBox = new ComboBox<>();
        numBox.getItems().addAll(1,2,3,4,5,6);
        numBox.setValue(1);

        numRow.getChildren().addAll(numLbl, numBox);

        // Seat map
        Label seatTitle = new Label("Select Seats (visual coach map)");
        seatTitle.getStyleClass().add("section-title-small");

        GridPane seatGrid = new GridPane();
        seatGrid.setHgap(4);
        seatGrid.setVgap(4);
        seatGrid.getStyleClass().add("seat-grid");
        List<ToggleButton> seatToggles = new ArrayList<>();
        final int SEAT_COLS = 10;
        final int SEAT_ROWS = 4;
        for (int r = 0; r < SEAT_ROWS; r++) {
            for (int c = 0; c < SEAT_COLS; c++) {
                ToggleButton seat = new ToggleButton((r * SEAT_COLS + c + 1) + "");
                seat.getStyleClass().addAll("seat", "seat-available");
                seat.setPrefSize(26, 26);
                seatGrid.add(seat, c, r);
                seatToggles.add(seat);
            }
        }

        // Live selected count + fare
        Label selectionInfo = new Label("Selected: 0 / 1");
        Label fareLabel = new Label("Total Fare: ₹0");
        fareLabel.getStyleClass().add("fare-label");

        // Passenger form container (dynamic)
        VBox paxContainer = new VBox(6);
        paxContainer.getStyleClass().add("pax-container");
        Label paxTitle = new Label("Passenger Details");
        paxTitle.getStyleClass().add("section-title-small");

        Runnable updatePaxForm = () -> {
            int n = numBox.getValue();
            paxContainer.getChildren().clear();
            paxContainer.getChildren().add(paxTitle);
            for (int i = 0; i < n; i++) {
                HBox row = new HBox(6);
                row.setAlignment(Pos.CENTER_LEFT);
                TextField nameF = new TextField("Passenger " + (i+1));
                nameF.setPrefWidth(160);
                TextField ageF = new TextField("28");
                ageF.setPrefWidth(50);
                ComboBox<String> genderC = new ComboBox<>();
                genderC.getItems().addAll("M", "F", "O");
                genderC.setValue("M");
                ComboBox<String> berthC = new ComboBox<>();
                berthC.getItems().addAll("Lower", "Middle", "Upper", "Side Lower", "Side Upper");
                berthC.setValue("Lower");
                row.getChildren().addAll(new Label("Name:"), nameF, new Label("Age:"), ageF, new Label("Gender:"), genderC, new Label("Berth:"), berthC);
                paxContainer.getChildren().add(row);
            }
        };

        numBox.setOnAction(e -> {
            updatePaxForm.run();
            updateSelectionInfo(selectionInfo, seatToggles, numBox, fareLabel, train, classGroup);
        });

        // Wire class and seat selection
        classGroup.selectedToggleProperty().addListener((obs, old, sel) -> {
            updateSelectionInfo(selectionInfo, seatToggles, numBox, fareLabel, train, classGroup);
            // reset seat selection when class changes
            seatToggles.forEach(s -> s.setSelected(false));
            seatToggles.forEach(s -> s.getStyleClass().remove("seat-selected"));
        });

        for (ToggleButton s : seatToggles) {
            s.setOnAction(ev -> {
                updateSelectionInfo(selectionInfo, seatToggles, numBox, fareLabel, train, classGroup);
            });
        }

        // Initial
        updatePaxForm.run();
        updateSelectionInfo(selectionInfo, seatToggles, numBox, fareLabel, train, classGroup);

        // Confirm / Cancel
        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);
        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("secondary-button");
        Button confirmBtn = new Button("💳 Proceed to Pay");
        confirmBtn.getStyleClass().add("primary-button");

        confirmBtn.setOnAction(e -> {
            String selectedClass = ((ToggleButton) classGroup.getSelectedToggle()).getText();
            int num = numBox.getValue();
            long selectedCount = seatToggles.stream().filter(ToggleButton::isSelected).count();

            if (selectedCount != num) {
                showAlert(Alert.AlertType.WARNING, "Seat Selection", "Please select exactly " + num + " seat(s) from the map.");
                return;
            }

            // Collect passengers from form
            List<Passenger> paxList = new ArrayList<>();
            int idx = 0;
            for (Node node : paxContainer.getChildren()) {
                if (node instanceof HBox) {
                    HBox row = (HBox) node;
                    TextField nameF = (TextField) row.getChildren().get(1);
                    TextField ageF = (TextField) row.getChildren().get(3);
                    ComboBox<String> gC = (ComboBox<String>) row.getChildren().get(5);
                    ComboBox<String> bC = (ComboBox<String>) row.getChildren().get(7);
                    paxList.add(new Passenger(nameF.getText().trim(), ageF.getText().trim(), gC.getValue(), bC.getValue()));
                    idx++;
                    if (idx >= num) break;
                }
            }

            String jDate = datePicker.getValue() != null ? datePicker.getValue().format(DateTimeFormatter.ISO_LOCAL_DATE) : LocalDate.now().plusDays(1).toString();

            double fare = dataService.calculateFare(train, selectedClass, num);

            // Close booking details dialog and open payment gateway (Phase 3)
            dialog.close();
            openPaymentGateway(train, selectedClass, num, paxList, jDate, fare);
        });

        cancelBtn.setOnAction(e -> dialog.close());

        actions.getChildren().addAll(cancelBtn, confirmBtn);

        root.getChildren().addAll(
            new Label("Booking for: " + currentUser),
            summary,
            classRow,
            numRow,
            seatTitle, seatGrid, selectionInfo, fareLabel,
            paxContainer,
            actions
        );

        Scene dialogScene = new Scene(root, 720, 620);
        var css = getClass().getResource("/styles/railway-reservation.css");
        if (css != null) dialogScene.getStylesheets().add(css.toExternalForm());
        dialog.setScene(dialogScene);
        dialog.showAndWait();
    }

    private void updateSelectionInfo(Label info, List<ToggleButton> seats, ComboBox<Integer> numBox,
                                     Label fareLbl, Train train, ToggleGroup classGroup) {
        int max = numBox.getValue();
        long sel = seats.stream().filter(ToggleButton::isSelected).count();

        if (sel > max) {
            // auto deselect last ones
            seats.stream().filter(ToggleButton::isSelected).skip(max).forEach(s -> s.setSelected(false));
            sel = max;
        }

        info.setText("Selected: " + sel + " / " + max);

        String cls = classGroup.getSelectedToggle() != null ?
            ((ToggleButton) classGroup.getSelectedToggle()).getText() : "SL";
        double fare = dataService.calculateFare(train, cls, max);
        fareLbl.setText("Total Fare: ₹" + String.format("%.0f", fare));
    }

    private void showSuccessPNR(Train train, String cls, List<Passenger> pax, String journeyDate) {
        // Find the latest booking for this user/train
        List<Booking> userBookings = dataService.getBookingsForUser(currentUser);
        Booking latest = userBookings.isEmpty() ? null : userBookings.get(userBookings.size() - 1);

        Stage success = new Stage();
        success.initModality(Modality.APPLICATION_MODAL);
        success.setTitle("Booking Confirmed");

        VBox box = new VBox(12);
        box.setPadding(new Insets(25));
        box.getStyleClass().add("success-box");

        Label title = new Label("🎉 Booking Confirmed!");
        title.getStyleClass().add("success-title");

        Label pnrLabel = new Label(latest != null ? "PNR: " + latest.getPnr() : "PNR: N/A");
        pnrLabel.getStyleClass().add("pnr-display");

        Label details = new Label(train.getName() + " (" + cls + ")\n" +
                train.getSource() + " → " + train.getDestination() + " on " + journeyDate + "\n" +
                pax.size() + " Passenger(s) • ₹" + (latest != null ? String.format("%.0f", latest.getTotalFare()) : "0"));
        details.getStyleClass().add("success-details");

        Button close = new Button("Done");
        close.getStyleClass().add("primary-button");
        close.setOnAction(e -> success.close());

        box.getChildren().addAll(title, pnrLabel, details, close);
        Scene sc = new Scene(box, 480, 260);
        var css = getClass().getResource("/styles/railway-reservation.css");
        if (css != null) sc.getStylesheets().add(css.toExternalForm());
        success.setScene(sc);
        success.showAndWait();
    }

    // ==================== PHASE 3: PAYMENT GATEWAY ====================

    private String generateTransactionId() {
        return "RAILTXN" + (System.currentTimeMillis() % 1000000000000L);
    }

    /**
     * Opens a beautiful simulated payment gateway (IRCTC style).
     * On successful payment, books the ticket and shows receipt + PNR.
     */
    private void openPaymentGateway(Train train, String cls, int numPassengers, List<Passenger> paxList,
                                    String journeyDate, double totalFare) {

        Stage payDialog = new Stage();
        payDialog.initModality(Modality.APPLICATION_MODAL);
        payDialog.setTitle("RailPay • Secure Payment");

        VBox root = new VBox(14);
        root.setPadding(new Insets(18, 22, 18, 22));
        root.getStyleClass().add("payment-gateway");

        // Header
        Label header = new Label("Pay ₹" + String.format("%.0f", totalFare) +
                " for " + numPassengers + " passenger(s) on " + train.getTrainNo());
        header.getStyleClass().add("payment-header");

        Label sub = new Label(train.getName() + " • " + train.getSource() + " → " + train.getDestination() +
                " • " + journeyDate);
        sub.getStyleClass().add("payment-subheader");

        // Method selection
        HBox methodBar = new HBox(6);
        methodBar.setAlignment(Pos.CENTER);
        methodBar.getStyleClass().add("payment-method-bar");

        ToggleGroup methodGroup = new ToggleGroup();
        String[] labels = {"💳 Card", "📱 UPI", "🏦 Net Banking", "👛 Wallet"};
        String[] keys   = {"Credit Card", "UPI", "Net Banking", "Wallet"};

        final ToggleButton[] methodBtns = new ToggleButton[4];
        for (int i = 0; i < 4; i++) {
            ToggleButton b = new ToggleButton(labels[i]);
            b.setToggleGroup(methodGroup);
            b.setUserData(keys[i]);
            b.getStyleClass().add("payment-method-btn");
            methodBtns[i] = b;
            methodBar.getChildren().add(b);
        }
        methodBtns[0].setSelected(true); // default Card

        // Dynamic form area
        VBox formArea = new VBox(10);
        formArea.getStyleClass().add("payment-form-area");

        // We will rebuild the form when method changes
        Runnable rebuildForm = new Runnable() {
            VBox currentForm = null;

            @Override
            public void run() {
                formArea.getChildren().clear();

                String method = (String) methodGroup.getSelectedToggle().getUserData();

                VBox form = new VBox(8);
                form.getStyleClass().add("payment-form");

                if ("Credit Card".equals(method)) {
                    Label l = new Label("Card Details");
                    l.getStyleClass().add("form-section");

                    TextField cardNo = new TextField("4242 4242 4242 4242");
                    cardNo.setPromptText("Card Number");
                    cardNo.getStyleClass().add("payment-input");

                    TextField name = new TextField("Test User");
                    name.setPromptText("Cardholder Name");

                    HBox row = new HBox(8);
                    TextField exp = new TextField("12/28");
                    exp.setPromptText("MM/YY");
                    exp.setPrefWidth(90);
                    PasswordField cvv = new PasswordField();
                    cvv.setPromptText("CVV");
                    cvv.setPrefWidth(70);
                    row.getChildren().addAll(exp, cvv);

                    form.getChildren().addAll(l, cardNo, name, row);

                    // store references for simulation
                    form.setUserData(new Object[]{cardNo, name, exp, cvv});

                } else if ("UPI".equals(method)) {
                    Label l = new Label("UPI Payment");
                    l.getStyleClass().add("form-section");

                    TextField vpa = new TextField("testuser@oksbi");
                    vpa.setPromptText("yourname@upi");
                    vpa.getStyleClass().add("payment-input");

                    Label qr = new Label("📱 Scan QR or enter VPA above\n(Fake QR for demo)");
                    qr.getStyleClass().add("fake-qr");

                    form.getChildren().addAll(l, vpa, qr);
                    form.setUserData(new Object[]{vpa});

                } else if ("Net Banking".equals(method)) {
                    Label l = new Label("Net Banking");
                    l.getStyleClass().add("form-section");

                    ComboBox<String> bank = new ComboBox<>();
                    bank.getItems().addAll("State Bank of India", "HDFC Bank", "ICICI Bank", "Axis Bank", "Punjab National Bank", "Bank of Baroda");
                    bank.setValue("State Bank of India");

                    Label note = new Label("You will be redirected to the bank's secure page (simulated).");
                    note.getStyleClass().add("note");

                    form.getChildren().addAll(l, bank, note);
                    form.setUserData(new Object[]{bank});

                } else { // Wallet
                    Label l = new Label("Digital Wallet");
                    l.getStyleClass().add("form-section");

                    ComboBox<String> wallet = new ComboBox<>();
                    wallet.getItems().addAll("Paytm", "PhonePe", "Google Pay", "Amazon Pay");
                    wallet.setValue("Paytm");

                    Label bal = new Label("Available Balance: ₹12,450 (demo)");
                    bal.getStyleClass().add("note");

                    form.getChildren().addAll(l, wallet, bal);
                    form.setUserData(new Object[]{wallet});
                }

                formArea.getChildren().add(form);
                currentForm = form;
            }
        };

        // initial form
        rebuildForm.run();

        // listen for method change
        methodGroup.selectedToggleProperty().addListener((obs, old, sel) -> {
            if (sel != null) rebuildForm.run();
        });

        // Amount and secure note
        Label amountLabel = new Label("Total Amount: ₹" + String.format("%.0f", totalFare));
        amountLabel.getStyleClass().add("payment-amount");

        Label secure = new Label("🔒 256-bit SSL Secured  •  PCI DSS Compliant  •  RailPay");
        secure.getStyleClass().add("secure-note");

        // Action buttons
        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button cancelPay = new Button("Cancel");
        cancelPay.getStyleClass().add("secondary-button");
        cancelPay.setOnAction(e -> payDialog.close());

        Button payBtn = new Button("Pay ₹" + String.format("%.0f", totalFare) + " Securely");
        payBtn.getStyleClass().add("primary-button");

        payBtn.setOnAction(e -> {
            ToggleButton selected = (ToggleButton) methodGroup.getSelectedToggle();
            String method = selected != null ? (String) selected.getUserData() : "Credit Card";

            VBox currentForm = (VBox) formArea.getChildren().get(0);
            Object[] data = (Object[]) currentForm.getUserData();

            // Simulate payment decision
            boolean success = simulatePayment(method, data);

            payBtn.setDisable(true);
            Label proc = new Label("⏳ Processing payment via " + method + "...");
            proc.getStyleClass().add("processing-label");
            actions.getChildren().add(proc);

            PauseTransition pt = new PauseTransition(Duration.millis(1350));
            pt.setOnFinished(ev -> {
                actions.getChildren().remove(proc);
                payBtn.setDisable(false);

                if (success) {
                    String txnId = generateTransactionId();
                    boolean booked = dataService.bookTicket(train, cls, numPassengers, paxList, currentUser, journeyDate, method, txnId);

                    if (booked) {
                        payDialog.close();
                        showPaymentReceipt(train, cls, paxList, journeyDate, totalFare, method, txnId);
                        resultsListView.refresh();
                        refreshBookingsView();
                        updateStatus();
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Booking Failed", "Seats no longer available after payment simulation.");
                        payDialog.close();
                    }
                } else {
                    showAlert(Alert.AlertType.ERROR, "Payment Failed",
                            "Your transaction was declined by the bank / gateway.\nPlease try another method or card.");
                    // allow retry
                }
            });
            pt.play();
        });

        actions.getChildren().addAll(cancelPay, payBtn);

        root.getChildren().addAll(header, sub, methodBar, formArea, amountLabel, secure, actions);

        Scene scene = new Scene(root, 620, 520);
        var cssUrl = getClass().getResource("/styles/railway-reservation.css");
        if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
        payDialog.setScene(scene);
        payDialog.showAndWait();
    }

    private boolean simulatePayment(String method, Object[] formData) {
        if ("Credit Card".equals(method) && formData != null && formData.length > 0) {
            String card = ((TextField) formData[0]).getText().replaceAll("\\s", "");
            if (card.startsWith("4242")) return true;           // success
            if (card.startsWith("4000")) return false;          // fail
            return Math.random() > 0.15;                        // mostly success
        }
        // For other methods, 85% success rate for demo
        return Math.random() > 0.15;
    }

    private void showPaymentReceipt(Train train, String cls, List<Passenger> pax, String journeyDate,
                                    double totalFare, String method, String txnId) {
        Stage receipt = new Stage();
        receipt.initModality(Modality.APPLICATION_MODAL);
        receipt.setTitle("Payment Receipt • RailPay");

        VBox box = new VBox(10);
        box.setPadding(new Insets(22));
        box.getStyleClass().add("receipt-box");

        Label title = new Label("✅ Payment Successful");
        title.getStyleClass().add("receipt-title");

        Label txn = new Label("Transaction ID: " + txnId);
        txn.getStyleClass().add("txn-id");

        Label details = new Label("Method: " + method + "\n" +
                "Amount: ₹" + String.format("%.0f", totalFare) + "\n" +
                train.getName() + " (" + cls + ")\n" +
                train.getSource() + " → " + train.getDestination() + " on " + journeyDate + "\n" +
                pax.size() + " Passenger(s)");
        details.getStyleClass().add("receipt-details");

        Button viewTicket = new Button("View PNR & Ticket");
        viewTicket.getStyleClass().add("primary-button");
        viewTicket.setOnAction(e -> {
            receipt.close();
            showSuccessPNR(train, cls, pax, journeyDate);   // reuse existing PNR dialog
        });

        Button done = new Button("Done");
        done.getStyleClass().add("secondary-button");
        done.setOnAction(e -> receipt.close());

        HBox btns = new HBox(10, viewTicket, done);
        btns.setAlignment(Pos.CENTER_RIGHT);

        box.getChildren().addAll(title, txn, details, btns);

        Scene sc = new Scene(box, 460, 280);
        var css = getClass().getResource("/styles/railway-reservation.css");
        if (css != null) sc.getStylesheets().add(css.toExternalForm());
        receipt.setScene(sc);
        receipt.showAndWait();
    }

    // ==================== END PHASE 3 ====================

    private Node buildBookingsTab() {
        VBox container = new VBox(12);
        container.setPadding(new Insets(20));
        container.getStyleClass().add("bookings-container");

        Label title = new Label("Your Bookings");
        title.getStyleClass().add("section-title");

        bookingsListView = new ListView<>();
        bookingsListView.setPrefHeight(480);
        bookingsListView.setCellFactory(createBookingCellFactory());

        Button cancelSelected = new Button("Cancel Selected Booking");
        cancelSelected.getStyleClass().add("danger-button");
        cancelSelected.setOnAction(e -> {
            Booking selected = bookingsListView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showAlert(Alert.AlertType.INFORMATION, "No Selection", "Select a booking to cancel.");
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Cancel PNR " + selected.getPnr() + "?", ButtonType.YES, ButtonType.NO);
            confirm.showAndWait().ifPresent(bt -> {
                if (bt == ButtonType.YES) {
                    boolean ok = dataService.cancelBooking(selected.getPnr());
                    if (ok) {
                        refreshBookingsView();
                        updateStatus();
                        if (resultsListView != null) resultsListView.refresh();
                    }
                }
            });
        });

        container.getChildren().addAll(title, bookingsListView, cancelSelected);
        VBox.setVgrow(bookingsListView, Priority.ALWAYS);

        // Initial load
        Platform.runLater(this::refreshBookingsView);

        return container;
    }

    private Callback<ListView<Booking>, ListCell<Booking>> createBookingCellFactory() {
        return lv -> new ListCell<>() {
            @Override
            protected void updateItem(Booking b, boolean empty) {
                super.updateItem(b, empty);
                if (empty || b == null) {
                    setGraphic(null);
                    return;
                }
                HBox card = new HBox(10);
                card.setAlignment(Pos.CENTER_LEFT);
                card.getStyleClass().add("booking-card");

                VBox info = new VBox(3);
                Label pnr = new Label("PNR: " + b.getPnr());
                pnr.getStyleClass().add("pnr-small");
                Label main = new Label(b.getTrainName() + " (" + b.getCls() + ") • " + b.getJourneyDate());
                Label pax = new Label(b.getPassengers().size() + " pax • ₹" + String.format("%.0f", b.getTotalFare()) + " • " + b.getUserName());
                info.getChildren().addAll(pnr, main, pax);

                if (b.getPaymentMethod() != null && b.getTransactionId() != null) {
                    Label payInfo = new Label("Paid via " + b.getPaymentMethod() + " • " + b.getTransactionId());
                    payInfo.getStyleClass().add("payment-info-small");
                    info.getChildren().add(payInfo);
                }

                Region sp = new Region();
                HBox.setHgrow(sp, Priority.ALWAYS);

                Button cancel = new Button("Cancel");
                cancel.getStyleClass().add("danger-button");
                cancel.setOnAction(e -> {
                    Alert c = new Alert(Alert.AlertType.CONFIRMATION, "Cancel booking " + b.getPnr() + "?", ButtonType.YES, ButtonType.NO);
                    c.showAndWait().ifPresent(bt -> {
                        if (bt == ButtonType.YES) {
                            dataService.cancelBooking(b.getPnr());
                            refreshBookingsView();
                            updateStatus();
                            if (resultsListView != null) resultsListView.refresh();
                        }
                    });
                });

                card.getChildren().addAll(info, sp, cancel);
                setGraphic(card);
            }
        };
    }

    private void refreshBookingsView() {
        if (bookingsListView == null) return;
        List<Booking> list = dataService.getBookingsForUser(currentUser);
        bookingsListView.setItems(FXCollections.observableArrayList(list));
    }

    private Node buildAdminTab() {
        VBox container = new VBox(12);
        container.setPadding(new Insets(20));
        container.getStyleClass().add("admin-container");

        Label title = new Label("Manage Trains (Admin Demo)");
        title.getStyleClass().add("section-title");

        // Add form
        GridPane form = new GridPane();
        form.setHgap(8); form.setVgap(6);

        TextField noF = new TextField("12999");
        TextField nameF = new TextField("New Express");
        TextField srcF = new TextField("New Delhi");
        TextField dstF = new TextField("Mumbai Central");
        TextField depF = new TextField("20:00");
        TextField arrF = new TextField("07:30");
        TextField slF = new TextField("100");
        TextField a3F = new TextField("40");
        TextField a2F = new TextField("20");
        TextField a1F = new TextField("8");
        TextField fareF = new TextField("2100");

        form.add(new Label("Train No:"), 0, 0); form.add(noF, 1, 0);
        form.add(new Label("Name:"), 2, 0); form.add(nameF, 3, 0);
        form.add(new Label("Source:"), 0, 1); form.add(srcF, 1, 1);
        form.add(new Label("Dest:"), 2, 1); form.add(dstF, 3, 1);
        form.add(new Label("Dep:"), 0, 2); form.add(depF, 1, 2);
        form.add(new Label("Arr:"), 2, 2); form.add(arrF, 3, 2);
        form.add(new Label("SL:"), 0, 3); form.add(slF, 1, 3);
        form.add(new Label("3A:"), 2, 3); form.add(a3F, 3, 3);
        form.add(new Label("2A:"), 0, 4); form.add(a2F, 1, 4);
        form.add(new Label("1A:"), 2, 4); form.add(a1F, 3, 4);
        form.add(new Label("Base Fare:"), 0, 5); form.add(fareF, 1, 5);

        Button addBtn = new Button("➕ Add / Update Train");
        addBtn.getStyleClass().add("primary-button");

        addBtn.setOnAction(e -> {
            try {
                Map<String, Integer> seats = new LinkedHashMap<>();
                seats.put("SL", Integer.parseInt(slF.getText().trim()));
                seats.put("3A", Integer.parseInt(a3F.getText().trim()));
                seats.put("2A", Integer.parseInt(a2F.getText().trim()));
                seats.put("1A", Integer.parseInt(a1F.getText().trim()));

                Train t = new Train(
                    noF.getText().trim(),
                    nameF.getText().trim(),
                    srcF.getText().trim(),
                    dstF.getText().trim(),
                    depF.getText().trim(),
                    arrF.getText().trim(),
                    seats,
                    Double.parseDouble(fareF.getText().trim())
                );
                dataService.addOrUpdateTrain(t);
                refreshAdminTable();
                refreshStations();
                showAlert(Alert.AlertType.INFORMATION, "Saved", "Train " + t.getTrainNo() + " saved.");
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Invalid Input", "Please check all numeric fields.");
            }
        });

        Button reloadBtn = new Button("Reload Sample Trains");
        reloadBtn.getStyleClass().add("secondary-button");
        reloadBtn.setOnAction(e -> {
            dataService.reloadSamples();
            refreshAdminTable();
            refreshStations();
        });

        HBox adminActions = new HBox(8, addBtn, reloadBtn);

        // Current trains table (simple ListView for admin too for consistency)
        ListView<Train> adminList = new ListView<>();
        adminList.setPrefHeight(280);
        adminList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Train t, boolean empty) {
                super.updateItem(t, empty);
                if (empty || t == null) { setText(null); return; }
                setText(t.getTrainNo() + " | " + t.getName() + " | " + t.getSource() + "→" + t.getDestination() +
                        " | SL:" + t.getAvailableSeats().get("SL"));
            }
        });

        adminList.setOnMouseClicked(e -> {
            Train sel = adminList.getSelectionModel().getSelectedItem();
            if (sel != null) {
                // prefill form
                // (omitted full two-way for brevity in this version, user can manually edit)
            }
        });

        container.getChildren().addAll(title, form, adminActions, new Label("Current Trains:"), adminList);
        VBox.setVgrow(adminList, Priority.ALWAYS);

        // load initial
        Platform.runLater(() -> {
            adminList.setItems(FXCollections.observableArrayList(dataService.getAllTrains()));
        });

        return container;
    }

    private void refreshAdminTable() {
        // Since admin tab uses local ListView, we can find it but for simplicity just reload data
        // In real would use better architecture; here we refresh search results if open
        if (resultsListView != null) {
            resultsListView.refresh();
        }
    }

    private void refreshStations() {
        if (fromBox == null || toBox == null) return;
        List<String> stations = dataService.getAllStations();
        fromBox.setItems(FXCollections.observableArrayList(stations));
        toBox.setItems(FXCollections.observableArrayList(stations));
        if (!stations.isEmpty()) {
            fromBox.setValue(stations.get(0));
            toBox.setValue(stations.size() > 1 ? stations.get(1) : stations.get(0));
        }
    }

    private Node buildStatusBar() {
        statusLabel = new Label();
        statusLabel.getStyleClass().add("status-bar");
        return statusLabel;
    }

    private void updateStatus() {
        if (statusLabel == null) return;
        int tCount = dataService.getAllTrains().size();
        int bCount = dataService.getAllBookings().size();
        statusLabel.setText("📁 Data: ~/.railway-reservation/  •  " + tCount + " trains loaded  •  " + bCount + " total bookings  •  JavaFX + Heavy CSS");
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
