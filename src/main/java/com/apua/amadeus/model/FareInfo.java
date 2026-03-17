package com.apua.amadeus.model;
import lombok.Data;
import java.util.List;

@Data
public class FareInfo {
    private String currency;
    private Double baseFare;
    private Double totalFare;
    private List<String> taxes;
}