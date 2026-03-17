package com.apua.amadeus.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class AirFileData {
    private String fileName;
    private String pnr;
    private String agencyName;
    private String officeId;
    private String iataNumber;
    private String issuingDate;
    private String validatingCarrier;
    private String endorsements;
    private String formOfPayment;
    private String fareCalculation;
    private String commission;
    private String ticketNumber;
    private String ticketTimeLimit;

    // Campos de metadatos extraídos de Remarks
    private String gender;
    private String carSize;
    private String bedType;
    private String jobTitle;
    private String costCenter;
    private String department;

    // OBJETOS Y LISTAS (Nuevas y antiguas coexisten)
    private Passenger passenger; // Se mantiene para no romper el código existente
    private List<Passenger> passengers = new ArrayList<>();
    private List<FlightSegment> segments = new ArrayList<>();
    private FareInfo fare;
    private List<HotelSegment> hotels = new ArrayList<>();
    private List<String> remarks = new ArrayList<>();
    private List<String> ssr = new ArrayList<>();
    private List<String> osi = new ArrayList<>();
    private List<String> rawLines = new ArrayList<>();
    private List<String> miscSegments = new ArrayList<>();

    // Traducción Estructurada para el Frontend
    private List<TranslatedInfo> structuredRemarks = new ArrayList<>();
    private List<TranslatedInfo> structuredSSR = new ArrayList<>();
    private boolean isExchange;
    private String operationType;
}