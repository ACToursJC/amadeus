package com.apua.amadeus.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class StockBoletoSegmentoDTO {
    public Integer nu_correl;
    public String ciu_salida;
    public String ciu_llegada;
    public String cod_la;
    public String nu_vuelo;
    public String clase;
    public String fe_salida;
    public String fe_llegada;
    public String cod_estado;
    public Double co2;
    public String co_fare_basis;
    public String tipo_equipo;
    public Float tiempo_vuelo;
}