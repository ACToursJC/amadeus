package com.apua.amadeus.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class StockBoletoTarjetaDTO {
    public Integer nu_correl;
    public String co_tip_tarj;
    public String nu_tarj;
    public Double mo_monto;
}