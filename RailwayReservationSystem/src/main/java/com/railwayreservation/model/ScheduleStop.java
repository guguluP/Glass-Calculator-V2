package com.railwayreservation.model;

public class ScheduleStop {
    private String station;
    private String time;

    public ScheduleStop() {}

    public ScheduleStop(String station, String time) {
        this.station = station;
        this.time = time;
    }

    public String getStation() { return station; }
    public void setStation(String station) { this.station = station; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
}
