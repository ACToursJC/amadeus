package com.apua.amadeus.service;

import com.apua.amadeus.entity.Comision;
import com.apua.amadeus.model.*;
import com.apua.amadeus.repository.ComisionRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.*;
import java.util.List;

@Service
public class AirImportService {

    private final AirParserService airParserService;
    private final ComisionRepository comisionRepository;

    // Definición de rutas para el movimiento de archivos
    private final Path sourcePath = Paths.get("F:/Amadeus/air4");
    private final Path targetPath = Paths.get("F:/Amadeus/air5");

    public AirImportService(AirParserService airParserService, ComisionRepository comisionRepository) {
        this.airParserService = airParserService;
        this.comisionRepository = comisionRepository;
    }

    public void processAndSaveAllFiles() throws Exception {
        List<AirFileData> airList = airParserService.parseAllFiles();

        for (AirFileData data : airList) {
            String fileName = data.getFileName();
            try {
                // 1. OBTENCIÓN DEL PNR (Búsqueda robusta)
                String pnrReal = (data.getPnr() != null) ? data.getPnr() :
                        (data.getStockBoleto() != null ? data.getStockBoleto().getNu_pnr() : null);

                // Si no hay PNR o ya existe en la base de datos, movemos el archivo y saltamos
                if (pnrReal == null || comisionRepository.existsByPnrId(pnrReal)) {
                    moveFileToProcessed(fileName);
                    continue;
                }

                Comision c = new Comision();

                // 2. DATOS DE AGENCIA E IDENTIFICACIÓN
                c.setPnrId(pnrReal);
                c.setNombreArchivo(fileName);
                c.setSignIn(data.getSignIn());
                c.setOfficeId(data.getOfficeId() != null ? data.getOfficeId() :
                        (data.getStockBoleto() != null ? data.getStockBoleto().getCo_seudo() : null));

                // IATA y Office Name (Capturados desde la línea A- en el parser)
                c.setIataNumber(data.getIataNumber());
                c.setOfficeName(data.getAgencyName());
                c.setAgencyName(data.getAgencyName());

                c.setEstado("PEN");
                c.setStatusComision(data.getStatusComision() != null ? data.getStatusComision() : "sell");
                c.setDe_vendedor(data.getJobTitle());
                c.setFechaCreacion(data.getIssuingDate() != null ? java.sql.Date.valueOf(data.getIssuingDate()) : new java.util.Date());

                // 3. PASAJERO
                if (data.getPassenger() != null) {
                    c.setGuestFirstName(data.getPassenger().getFirstName());
                    c.setGuestLastName(data.getPassenger().getLastName());
                }

                // 4. LÓGICA FINANCIERA (Iniciamos en 0 para asegurar que solo sume Hotel si existe)
                BigDecimal hotelTotalFinal = BigDecimal.ZERO;
                String monedaFinal = "USD";

                // 5. LÓGICA DE HOTEL (CORREGIDA PARA SOLO TOTAL HOTEL)
                if (data.getHotels() != null && !data.getHotels().isEmpty()) {
                    HotelSegment h = data.getHotels().get(0);

                    c.setHotelName(h.getHotelName());
                    c.setHotelChainName(h.getChainName());
                    c.setChainCode(h.getChainCode());
                    c.setHotelCityCode(h.getCityCode());
                    c.setCityName(h.getCityName());
                    c.setRoomType(h.getRoomType());
                    c.setConfirmationCode(h.getConfirmationNumber());
                    c.setNumberOfNights(h.getNumberOfNights());

                    if (h.getCheckInDate() != null) c.setCheckInDate(java.sql.Date.valueOf(h.getCheckInDate()));
                    if (h.getCheckOutDate() != null) c.setCheckOutDate(java.sql.Date.valueOf(h.getCheckOutDate()));

                    c.setRoomDescription(String.join(" | ", h.getDailyRates()));

                    // TRATAMIENTO DE MONEDA DEL HOTEL
                    String rawHotelCurr = h.getCurrency() != null ? h.getCurrency() : "USD";
                    String cleanHotelCurr = rawHotelCurr.replace("TTL-", "").replace("USDUSD", "USD").trim();

                    c.setHotelPriceCurrency(cleanHotelCurr);
                    c.setRateplanCurrencyCode(cleanHotelCurr.length() > 3 ? cleanHotelCurr.substring(0, 3) : cleanHotelCurr);
                    monedaFinal = c.getRateplanCurrencyCode();

                    // TOTAL DEL HOTEL (HotelPrice debe ser el total acumulado de todas las noches)
                    if (h.getTtlAmount() != null && !h.getTtlAmount().isEmpty()) {
                        hotelTotalFinal = new BigDecimal(h.getTtlAmount());
                    } else if (h.getPricePerNight() != null) {
                        // Si no hay TTL, calculamos: Precio noche * cantidad de noches
                        hotelTotalFinal = new BigDecimal(h.getPricePerNight())
                                .multiply(new BigDecimal(h.getNumberOfNights() > 0 ? h.getNumberOfNights() : 1));
                    }

                    c.setHotelPrice(hotelTotalFinal);
                    c.setRateplanTotalPrice(hotelTotalFinal);

                    // COMISIÓN (Calculada sobre el total del hotel)
                    if (h.getHotelCommission() != null) {
                        String commVal = h.getHotelCommission().replaceAll("[^0-9.]", "");
                        if (!commVal.isEmpty()) {
                            BigDecimal pct = new BigDecimal(commVal);
                            c.setPorComision(pct);
                            BigDecimal montoCom = hotelTotalFinal.multiply(pct).divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
                            c.setComisionTotal(montoCom);
                            c.setComisionTotalReal(montoCom);
                        }
                    }
                }

                // 6. REMARKS ESTRUCTURADOS (País, Fee, Cambio)
                for (TranslatedInfo rem : data.getStructuredRemarks()) {
                    String val = rem.getHumanValue();

                    if (val.contains("HOTCOUNTRY-")) {
                        String country = val.split("HOTCOUNTRY-")[1].split("/")[0].trim();
                        if (!country.equalsIgnoreCase("SUNDEFINED")) {
                            c.setHotelCountryCode(country);
                            c.setHotelCountry(country.equals("PE") ? "PERU" : country);
                        }
                    }

                    if ("FEE_AEREO_VALOR".equals(rem.getCategory())) {
                        c.setFee(new BigDecimal(val));
                    }

                    if ("TIPO_CAMBIO".equals(rem.getCategory())) {
                        c.setTipoCambio(val.length() > 2 ? val.substring(0, 2) : val);
                    }
                }

                // 7. ASIGNACIÓN FINAL DE MONTOS (SOLO HOTEL)
                if (data.getFormOfPayment() != null) {
                    String fp = data.getFormOfPayment();
                    c.setFormaPago(fp.length() > 5 ? fp.substring(0, 5) : fp);
                }

                // Seteamos el monto y total únicamente con el valor del hotel
                c.setMoneda(monedaFinal);
                c.setMonto(hotelTotalFinal);
                c.setTotal(hotelTotalFinal);

                c.setObservaciones(String.join(" | ", data.getRemarks()));

                // Guardar registro
                comisionRepository.save(c);

                // Mover archivo procesado a air5
                moveFileToProcessed(fileName);

            } catch (Exception e) {
                System.err.println("Error procesando " + fileName + ": " + e.getMessage());
            }
        }
    }

    //Mueve el archivo de la carpeta air4 a air5
    private void moveFileToProcessed(String fileName) {
        try {
            if (!Files.exists(targetPath)) {
                Files.createDirectories(targetPath);
            }
            Path source = sourcePath.resolve(fileName);
            Path target = targetPath.resolve(fileName);

            if (Files.exists(source)) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Archivo movido exitosamente a air5: " + fileName);
            }
        } catch (IOException e) {
            System.err.println("Error al mover el archivo " + fileName + ": " + e.getMessage());
        }
    }
}