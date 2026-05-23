package com.railwayreservation.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Booking {
    private String pnr;
    private String userName;
    private String trainNo;
    private String trainName;
    private String journeyDate;
    private String cls;
    private List<Passenger> passengers = new ArrayList<>();
    private double totalFare;
    private String bookedAt; // ISO string for simplicity (no extra Jackson modules)
    private String paymentMethod;   // "Credit Card", "UPI", "Net Banking", "Wallet"
    private String transactionId;   // e.g. RAILTXN123456789012
    private String paymentStatus;   // "SUCCESS"

    public Booking() {
    }

    public String getPnr() { return pnr; }
    public void setPnr(String pnr) { this.pnr = pnr; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getTrainNo() { return trainNo; }
    public void setTrainNo(String trainNo) { this.trainNo = trainNo; }

    public String getTrainName() { return trainName; }
    public void setTrainName(String trainName) { this.trainName = trainName; }

    public String getJourneyDate() { return journeyDate; }
    public void setJourneyDate(String journeyDate) { this.journeyDate = journeyDate; }

    public String getCls() { return cls; }
    public void setCls(String cls) { this.cls = cls; }

    public List<Passenger> getPassengers() { return passengers; }
    public void setPassengers(List<Passenger> passengers) { this.passengers = passengers; }

    public double getTotalFare() { return totalFare; }
    public void setTotalFare(double totalFare) { this.totalFare = totalFare; }

    public String getBookedAt() { return bookedAt; }
    public void setBookedAt(String bookedAt) { this.bookedAt = bookedAt; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    @Override
    public String toString() {
        String payInfo = (paymentMethod != null) ? " | Paid via " + paymentMethod + " (" + transactionId + ")" : "";
        return "PNR: " + pnr + " | " + trainName + " (" + cls + ") on " + journeyDate + " | " + passengers.size() + " pax | ₹" + String.format("%.0f", totalFare) + payInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Booking booking = (Booking) o;
        return Objects.equals(pnr, booking.pnr);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pnr);
    }
}
