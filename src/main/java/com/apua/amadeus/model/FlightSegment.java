package com.apua.amadeus.model;
import lombok.Data;

@Data
public class FlightSegment {
    private String origin;
    private String destination;
    private String airline;
    private String flightNumber;
    private String bookingClass;      // Q, P, Z
    private String departureDate;     // 22FEB
    private String departureTime;     // 0700
    private String arrivalDate;       // 22FEB
    private String arrivalTime;       // 1239
    private String equipment;         // 321, 738
    private String co2Emission;       // 201.10KG
    private String status;            // HK01
}