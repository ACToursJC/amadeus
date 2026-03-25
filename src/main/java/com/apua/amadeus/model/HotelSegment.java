package com.apua.amadeus.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class HotelSegment {
    private String hotelName;
    private String cityCode;
    private String cityName;
    private String checkInDate;
    private String checkOutDate;
    private String status;
    private String quantity;
    private String hotelCode;
    private String confirmationNumber;
    private String address;
    private String phoneNumber;
    private String roomType;
    private String pricePerNight;
    private String currency;
    private String totalAmount;
    private String hotelType;
    private Integer numberOfNights;
    private String isCommissionable;
    private String chainName;
    private String chainCode;

    private List<String> dailyRates = new ArrayList<>();
    private List<DailyRateDetail> dailyRateDetails = new ArrayList<>();

    private String ttlAmount;
    private String hotelCommission;
    private String taxInfo;
    private String checkInTime;
    private String checkOutTime;

    @Data
    public static class DailyRateDetail {
        private String rate;
        private String date;
        private String nights;
    }
}