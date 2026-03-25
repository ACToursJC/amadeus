package com.apua.amadeus.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class StockBoletoDTO {
    // Cabecera principal
    public String co_cia = "01";
    public String co_tipo = "B";
    public String de_forma = "ET";
    public String de_boleto;
    public String fe_emision;
    public String co_moneda_boleto;
    public Double mo_monto;
    public String nu_pnr;
    public String de_pasajero;
    public String co_usuario; // SignIn
    public String co_seudo; // OfficeID
    public String nu_file; // Solicitud

    // Arreglo de Ciudades
    public List<String> ciudades = new ArrayList<>();
    // Arreglo de Vuelos
    public List<String> nroVuelos = new ArrayList<>();
    // Arreglo de Clases
    public List<String> clases = new ArrayList<>();
    // Arreglo de Fechas
    public List<String> fechasVuelo = new ArrayList<>();
    // Arreglo de Status
    public List<String> estadosVuelo = new ArrayList<>();
    // Arreglo de Horas LLegada y CO2
    public List<String> horasLlegada = new ArrayList<>();
    public List<Double> co2Valores = new ArrayList<>();
}