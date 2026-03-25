package com.apua.amadeus.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@Entity
@Table(name = "comisiones")
public class Comision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "idUsuario") private String idUsuario;
    @Column(name = "FacSerie") private String FacSerie;
    @Column(name = "FacNumero") private String FacNumero;
    @Column(name = "RecNumero") private Integer RecNumero;
    @Column(name = "PnrId") private String pnrId;
    @Column(name = "idHotel") private Integer idHotel;
    @Column(name = "PorComision") private BigDecimal PorComision;
    @Column(name = "ComisionTotal") private BigDecimal ComisionTotal;
    @Column(name = "Estado") private String Estado;
    @Column(name = "Recibo") private String Recibo;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "FechaCreacion") private Date FechaCreacion;

    @Column(name = "FormaPago") private String FormaPago;
    @Column(name = "Banco") private String Banco;
    @Column(name = "Cuenta") private String Cuenta;
    @Column(name = "Moneda") private String Moneda;
    @Column(name = "Monto") private BigDecimal Monto;
    @Column(name = "TipoCambio") private String TipoCambio;

    @Column(name = "Observaciones", columnDefinition = "TEXT")
    private String Observaciones;

    @Column(name = "AgencyName") private String AgencyName;
    @Column(name = "SignIn") private String SignIn;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "CheckInDate") private Date CheckInDate;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "CheckOutDate") private Date CheckOutDate;

    @Column(name = "ConfirmationCode") private String ConfirmationCode;
    @Column(name = "HotelChainName") private String HotelChainName;
    @Column(name = "HotelName") private String HotelName;
    @Column(name = "HotelPrice") private BigDecimal HotelPrice;
    @Column(name = "HotelPriceCurrency") private String HotelPriceCurrency;
    @Column(name = "CityName") private String CityName;

    @Column(name = "total") private BigDecimal total;

    @Column(name = "RoomDescription", columnDefinition = "TEXT")
    private String RoomDescription;

    @Column(name = "GuestFirstName") private String GuestFirstName;
    @Column(name = "GuestLastName") private String GuestLastName;

    @Column(name = "OfficeID") private String officeId;
    @Column(name = "OfficeName") private String OfficeName;
    @Column(name = "IataNumber") private String IataNumber;
    @Column(name = "HotelCityCode") private String HotelCityCode;
    @Column(name = "HotelCountryCode") private String HotelCountryCode;
    @Column(name = "HotelCountry") private String HotelCountry;
    @Column(name = "ChainCode") private String ChainCode;
    @Column(name = "NumberOfNights") private Integer NumberOfNights;
    @Column(name = "RoomType") private String RoomType;
    @Column(name = "RateCode") private String RateCode;
    @Column(name = "RateplanTotalPrice") private BigDecimal RateplanTotalPrice;
    @Column(name = "RateplanCurrencyCode") private String RateplanCurrencyCode;
    @Column(name = "StatusComision") private String StatusComision;
    @Column(name = "NumberOfBooking") private Integer NumberOfBooking;
    @Column(name = "NumberOfCancel") private Integer NumberOfCancel;
    @Column(name = "IsCommisionable") private String IsCommisionable;
    @Column(name = "CommissionAmountInEuro") private BigDecimal CommissionAmountInEuro;
    @Column(name = "NombreArchivo") private String NombreArchivo;
    @Column(name = "Fee") private BigDecimal Fee;
    @Column(name = "GbaI") private BigDecimal GbaI;
    @Column(name = "RecBanco") private BigDecimal RecBanco;
    @Column(name = "GbaL") private BigDecimal GbaL;
    @Column(name = "ComisionDistribuir") private BigDecimal ComisionDistribuir;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "FechaFacturacion") private Date FechaFacturacion;

    @Column(name = "ComisionTotalReal") private BigDecimal ComisionTotalReal;
    @Column(name = "ComisionOtraMoneda") private BigDecimal ComisionOtraMoneda;
    @Column(name = "TotalOtraMoneda") private BigDecimal TotalOtraMoneda;

    @Column(name = "de_vendedor") private String de_vendedor;
    @Column(name = "Anulado") private String Anulado;
}