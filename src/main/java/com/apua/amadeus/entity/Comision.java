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
    @Column(name = "FacSerie") private String facSerie;
    @Column(name = "FacNumero") private String facNumero;
    @Column(name = "RecNumero") private Integer recNumero;
    @Column(name = "PnrId") private String pnrId;
    @Column(name = "idHotel") private Integer idHotel;
    @Column(name = "PorComision") private BigDecimal porComision;
    @Column(name = "ComisionTotal") private BigDecimal comisionTotal;
    @Column(name = "Estado") private String estado;
    @Column(name = "Recibo") private String recibo;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "FechaCreacion") private Date fechaCreacion;

    @Column(name = "FormaPago") private String formaPago;
    @Column(name = "Banco") private String banco;
    @Column(name = "Cuenta") private String cuenta;
    @Column(name = "Moneda") private String moneda;
    @Column(name = "Monto") private BigDecimal monto;
    @Column(name = "TipoCambio") private String tipoCambio;

    @Column(name = "Observaciones", columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "AgencyName") private String agencyName;
    @Column(name = "SignIn") private String signIn;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "CheckInDate") private Date checkInDate;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "CheckOutDate") private Date checkOutDate;

    @Column(name = "ConfirmationCode") private String confirmationCode;
    @Column(name = "HotelChainName") private String hotelChainName;
    @Column(name = "HotelName") private String hotelName;
    @Column(name = "HotelPrice") private BigDecimal hotelPrice;
    @Column(name = "HotelPriceCurrency") private String hotelPriceCurrency;
    @Column(name = "CityName") private String cityName;

    @Column(name = "total") private BigDecimal total;

    @Column(name = "RoomDescription", columnDefinition = "TEXT")
    private String roomDescription;

    @Column(name = "GuestFirstName") private String guestFirstName;
    @Column(name = "GuestLastName") private String guestLastName;

    @Column(name = "OfficeID") private String officeId;
    @Column(name = "OfficeName") private String officeName;
    @Column(name = "IataNumber") private String iataNumber;
    @Column(name = "HotelCityCode") private String hotelCityCode;
    @Column(name = "HotelCountryCode") private String hotelCountryCode;
    @Column(name = "HotelCountry") private String hotelCountry;
    @Column(name = "ChainCode") private String chainCode;
    @Column(name = "NumberOfNights") private Integer numberOfNights;
    @Column(name = "RoomType") private String roomType;
    @Column(name = "RateCode") private String rateCode;
    @Column(name = "RateplanTotalPrice") private BigDecimal rateplanTotalPrice;
    @Column(name = "RateplanCurrencyCode") private String rateplanCurrencyCode;
    @Column(name = "StatusComision") private String statusComision;
    @Column(name = "NumberOfBooking") private Integer numberOfBooking;
    @Column(name = "NumberOfCancel") private Integer numberOfCancel;
    @Column(name = "IsCommisionable") private String isCommisionable;
    @Column(name = "CommissionAmountInEuro") private BigDecimal commissionAmountInEuro;
    @Column(name = "NombreArchivo") private String nombreArchivo;
    @Column(name = "Fee") private BigDecimal fee;
    @Column(name = "GbaI") private BigDecimal gbaI;
    @Column(name = "RecBanco") private BigDecimal recBanco;
    @Column(name = "GbaL") private BigDecimal gbaL;
    @Column(name = "ComisionDistribuir") private BigDecimal comisionDistribuir;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "FechaFacturacion") private Date fechaFacturacion;

    @Column(name = "ComisionTotalReal") private BigDecimal comisionTotalReal;
    @Column(name = "ComisionOtraMoneda") private BigDecimal comisionOtraMoneda;
    @Column(name = "TotalOtraMoneda") private BigDecimal totalOtraMoneda;

    @Column(name = "de_vendedor") private String de_vendedor;
    @Column(name = "Anulado") private String anulado;
}