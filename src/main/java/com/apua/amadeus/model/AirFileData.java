package com.apua.amadeus.model;

import com.apua.amadeus.dto.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private String statusComision = "sell";
    private String signIn;
    private Double percentageCommission;

    private String gender;
    private String carSize;
    private String bedType;
    private String jobTitle;
    private String costCenter;
    private String department;

    private Passenger passenger;
    private List<Passenger> passengers = new ArrayList<>();

    @JsonIgnore // Oculto en JSON
    private List<FlightSegment> segments = new ArrayList<>();

    private FareInfo fare;
    private List<HotelSegment> hotels = new ArrayList<>();

    @JsonIgnore // Oculto en JSON
    private List<String> remarks = new ArrayList<>();

    @JsonIgnore // Oculto en JSON
    private List<Map<String, String>> detailRemarks = new ArrayList<>();

    private List<String> ssr = new ArrayList<>();
    private List<String> osi = new ArrayList<>();
    private List<String> rawLines = new ArrayList<>();
    private List<String> miscSegments = new ArrayList<>();

    private StockBoletoDTO stockBoleto = new StockBoletoDTO();
    private List<StockBoletoSegmentoDTO> tablaSegmentos = new ArrayList<>();
    private List<StockBoletoAhorroDTO> tablaAhorros = new ArrayList<>();
    private List<StockBoletoTarjetaDTO> tablaTarjetas = new ArrayList<>();

    private List<TranslatedInfo> structuredRemarks = new ArrayList<>();
    private List<TranslatedInfo> structuredSSR = new ArrayList<>();
    private boolean isExchange;
    private String operationType;
}