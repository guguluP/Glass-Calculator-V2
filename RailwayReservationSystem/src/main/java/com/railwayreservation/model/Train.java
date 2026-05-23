package com.railwayreservation.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Train {
    private String trainNo;
    private String name;
    private String source;
    private String destination;
    private String departure;
    private String arrival;
    private Map<String, Integer> availableSeats = new LinkedHashMap<>();
    private double baseFare;
    private String frequency = "Daily";   // e.g. "Daily", "Tu.W.F.Su", "M.Th.Sa", "W.Sa" from real timetable data
    private List<ScheduleStop> schedule = new ArrayList<>();  // Full timetable stops with times (Phase 3 enhancement)

    public Train() {
        // Jackson needs no-arg ctor
    }

    public Train(String trainNo, String name, String source, String destination,
                  String departure, String arrival, Map<String, Integer> availableSeats, double baseFare) {
        this(trainNo, name, source, destination, departure, arrival, availableSeats, baseFare, "Daily");
    }

    public Train(String trainNo, String name, String source, String destination,
                  String departure, String arrival, Map<String, Integer> availableSeats, double baseFare, String frequency) {
        this.trainNo = trainNo;
        this.name = name;
        this.source = source;
        this.destination = destination;
        this.departure = departure;
        this.arrival = arrival;
        this.availableSeats = availableSeats != null ? availableSeats : new LinkedHashMap<>();
        this.baseFare = baseFare;
        this.frequency = (frequency != null && !frequency.isBlank()) ? frequency : "Daily";
    }

    public String getTrainNo() { return trainNo; }
    public void setTrainNo(String trainNo) { this.trainNo = trainNo; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public String getDeparture() { return departure; }
    public void setDeparture(String departure) { this.departure = departure; }

    public String getArrival() { return arrival; }
    public void setArrival(String arrival) { this.arrival = arrival; }

    public Map<String, Integer> getAvailableSeats() { return availableSeats; }
    public void setAvailableSeats(Map<String, Integer> availableSeats) { this.availableSeats = availableSeats; }

    public double getBaseFare() { return baseFare; }
    public void setBaseFare(double baseFare) { this.baseFare = baseFare; }

    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = (frequency != null && !frequency.isBlank()) ? frequency : "Daily"; }

    public List<ScheduleStop> getSchedule() { return schedule; }
    public void setSchedule(List<ScheduleStop> schedule) { this.schedule = schedule != null ? schedule : new ArrayList<>(); }

    // Helpers
    public int getTotalAvailable() {
        return availableSeats.values().stream().mapToInt(Integer::intValue).sum();
    }

    public boolean hasAvailability(String cls, int count) {
        Integer avail = availableSeats.get(cls);
        return avail != null && avail >= count;
    }

    public void decrementSeats(String cls, int count) {
        availableSeats.computeIfPresent(cls, (k, v) -> Math.max(0, v - count));
    }

    public void incrementSeats(String cls, int count) {
        availableSeats.compute(cls, (k, v) -> (v == null ? 0 : v) + count);
    }

    @Override
    public String toString() {
        return trainNo + " - " + name + " | " + source + " → " + destination + " (" + departure + " - " + arrival + ") [" + frequency + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Train train = (Train) o;
        return Objects.equals(trainNo, train.trainNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(trainNo);
    }
}
