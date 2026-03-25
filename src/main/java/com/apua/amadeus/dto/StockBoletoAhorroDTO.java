package com.apua.amadeus.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class StockBoletoAhorroDTO {
    public Integer nu_correl;
    public String co_codigo;
    public Double mo_monto = 0.0;
    public String de_valor;
}