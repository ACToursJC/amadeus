package com.apua.amadeus.model;
import lombok.Data;

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
}