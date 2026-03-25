package com.apua.amadeus.model;
import lombok.Data;

@Data
public class Passenger {
    private String fullName;
    private String firstName;
    private String lastName;
    private String passengerType;   // ADT CHD INF
    private String birthDate;
    private String gender;
    private String documentId;
    private String nationality;
    private String email;
    private String phone;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String cuil;
}