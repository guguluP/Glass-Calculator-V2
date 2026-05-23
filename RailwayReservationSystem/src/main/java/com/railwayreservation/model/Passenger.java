package com.railwayreservation.model;

import java.util.Objects;

public class Passenger {
    private String name;
    private String age;
    private String gender;
    private String berthPref;

    public Passenger() {
    }

    public Passenger(String name, String age, String gender, String berthPref) {
        this.name = name;
        this.age = age;
        this.gender = gender;
        this.berthPref = berthPref;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAge() { return age; }
    public void setAge(String age) { this.age = age; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getBerthPref() { return berthPref; }
    public void setBerthPref(String berthPref) { this.berthPref = berthPref; }

    @Override
    public String toString() {
        return name + " (" + age + ", " + gender + (berthPref != null ? ", " + berthPref : "") + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Passenger passenger = (Passenger) o;
        return Objects.equals(name, passenger.name) && Objects.equals(age, passenger.age) && Objects.equals(gender, passenger.gender) && Objects.equals(berthPref, passenger.berthPref);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, age, gender, berthPref);
    }
}
