package com.apua.amadeus.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TranslatedInfo {
    private String category;
    private String rawCode;
    private String humanValue;
    private String icon;
}