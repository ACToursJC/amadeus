package com.apua.amadeus.model;
import lombok.Data;

@Data
public class FlightSegment {
    private String origin;
    private String destination;
    private String airline;
    private String flightNumber;
    private String departureDate;
    private String departureTime;
    private String arrivalTime;
}