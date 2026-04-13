package com.apua.amadeus.service;

import com.apua.amadeus.entity.Comision;
import com.apua.amadeus.model.*;
import com.apua.amadeus.repository.ComisionRepository;
import java.io.IOException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.*;
import java.util.List;

@Service
public class AirImportService {

    private final AirParserService airParserService;
    private final ComisionRepository comisionRepository;

    private final Path sourcePath = Paths.get("F:/Amadeus/air4");
    private final Path targetPath = Paths.get("F:/Amadeus/air5");

    public AirImportService(AirParserService airParserService, ComisionRepository comisionRepository) {
        this.airParserService = airParserService;
        this.comisionRepository = comisionRepository;
    }

    public void processAndSaveAllFiles() throws Exception {
        List<AirFileData> airList = airParserService.parseAllFiles();

        for (AirFileData data : airList) {
            try {
                // 1. Obtener PNR
                String pnrReal = (data.getPnr() != null) ? data.getPnr() :
                        (data.getStockBoleto() != null ? data.getStockBoleto().getNu_pnr() : null);

                if (pnrReal == null) {
                    moveFileToProcessed(data.getFileName());
                    continue;
                }

                // 2. Validación de Duplicados (PNR + Ticket ó PNR + ConfirmaciónCode)
                //    Permite PNR duplicado pero valida por boleto ó servicio
                boolean yaExiste = false;
                String ticketFull = data.getTicketNumber();
                String ticketShort = (ticketFull != null && ticketFull.length() > 10)
                        ? ticketFull.substring(ticketFull.length() - 10) : ticketFull;

                if (ticketShort != null && !ticketShort.isEmpty()) {
                    yaExiste = comisionRepository.existsByPnrIdAndFacNumero(pnrReal, ticketShort);
                } else if (data.getHotels() != null && !data.getHotels().isEmpty()) {
                    String conf = data.getHotels().get(0).getConfirmationNumber();
                    if (conf != null) {
                        yaExiste = comisionRepository.existsByPnrIdAndConfirmationCode(pnrReal, safeTruncate(conf, 115));
                    }
                } else {
                    yaExiste = comisionRepository.existsByPnrId(pnrReal);
                }

                if (yaExiste) {
                    System.out.println("⏭️ Saltando duplicado: PNR " + pnrReal + " (" + data.getFileName() + ")");
                    moveFileToProcessed(data.getFileName());
                    continue;
                }

                // 3. Crear Entidad y mapear Metadatos
                Comision c = new Comision();
                c.setPnrId(safeTruncate(pnrReal, 10));
                c.setNombreArchivo(safeTruncate(data.getFileName(), 255));
                c.setSignIn(safeTruncate(data.getSignIn(), 10));
                c.setOfficeId(safeTruncate(data.getOfficeId() != null ? data.getOfficeId() : (data.getStockBoleto() != null ? data.getStockBoleto().getCo_seudo() : ""), 10));
                c.setAgencyName(safeTruncate(data.getAgencyName(), 40));
                c.setIataNumber(safeTruncate(data.getIataNumber(), 20));
                c.setDe_vendedor(safeTruncate(data.getJobTitle(), 255));
                c.setFechaCreacion(new java.util.Date());
                c.setEstado(safeTruncate("PEN", 10));
                c.setIdUsuario(safeTruncate(data.getSignIn(), 6));
                c.setStatusComision(safeTruncate("sell", 50));

                if (data.getPassenger() != null) {
                    c.setGuestFirstName(safeTruncate(data.getPassenger().getFirstName(), 50));
                    c.setGuestLastName(safeTruncate(data.getPassenger().getLastName(), 50));
                }

                // 4. Lógica Financiera Acumulativa (Aéreo + Hotel)
                BigDecimal montoAereo = BigDecimal.ZERO;
                BigDecimal montoHotel = BigDecimal.ZERO;
                String monedaFinal = "USD";

                // Datos Aéreos
                if (data.getFare() != null && data.getFare().getTotalFare() != null) {
                    montoAereo = BigDecimal.valueOf(data.getFare().getTotalFare());
                    monedaFinal = data.getFare().getCurrency();
                    c.setFacNumero(ticketShort);
                    c.setFormaPago(safeTruncate(data.getFormOfPayment(), 5));
                }

                // Datos de Hotel
                if (data.getHotels() != null && !data.getHotels().isEmpty()) {
                    HotelSegment h = data.getHotels().get(0);
                    c.setHotelName(safeTruncate(h.getHotelName(), 150));
                    c.setHotelCityCode(safeTruncate(h.getCityCode(), 3));
                    c.setCityName(safeTruncate(h.getCityName(), 100));
                    c.setConfirmationCode(safeTruncate(h.getConfirmationNumber(), 115));
                    c.setNumberOfNights(h.getNumberOfNights());
                    c.setHotelChainName(safeTruncate(h.getChainName(), 150));
                    c.setChainCode(safeTruncate(h.getChainCode(), 2));
                    c.setRoomType(safeTruncate(h.getRoomType(), 3));
                    c.setRoomDescription(h.getRoomType());

                    if (h.getCheckInDate() != null) c.setCheckInDate(java.sql.Date.valueOf(h.getCheckInDate()));
                    if (h.getCheckOutDate() != null) c.setCheckOutDate(java.sql.Date.valueOf(h.getCheckOutDate()));

                    if (h.getTotalAmount() != null && !h.getTotalAmount().isEmpty()) {
                        montoHotel = new BigDecimal(h.getTotalAmount());
                    } else if (h.getPricePerNight() != null) {
                        montoHotel = new BigDecimal(h.getPricePerNight()).multiply(new BigDecimal(h.getNumberOfNights() > 0 ? h.getNumberOfNights() : 1));
                    }
                    c.setHotelPrice(montoHotel);
                    c.setHotelPriceCurrency(safeTruncate(h.getCurrency(), 4));

                    if (montoAereo.compareTo(BigDecimal.ZERO) == 0) {
                        monedaFinal = h.getCurrency();
                    }
                }

                // Totales Finales
                c.setMoneda(safeTruncate(monedaFinal, 3));
                c.setMonto(montoAereo.add(montoHotel));
                c.setTotal(montoAereo.add(montoHotel));

                // 5. Comisiones y Remarks Estructurados
                calculateAndSetCommissions(data, c);

                for (TranslatedInfo rem : data.getStructuredRemarks()) {
                    if ("TIPO_CAMBIO".equals(rem.getCategory())) {
                        c.setTipoCambio(safeTruncate(rem.getHumanValue(), 2));
                    }
                    if ("FEE_AEREO_VALOR".equals(rem.getCategory())) {
                        try { c.setFee(new BigDecimal(rem.getHumanValue())); } catch (Exception e) {}
                    }
                }

                // 6. Observaciones Combinadas (TEXT en DB)
                StringBuilder sb = new StringBuilder();
                if (data.getPassenger() != null && data.getPassenger().getEmergencyContactPhone() != null) {
                    sb.append("EMER: ").append(data.getPassenger().getEmergencyContactPhone()).append(" | ");
                }
                sb.append("CC: ").append(data.getCostCenter()).append(" | DEPT: ").append(data.getDepartment()).append(" | ");

                if (!data.getSegments().isEmpty()) {
                    sb.append("RUTA: ");
                    data.getSegments().forEach(s -> sb.append(s.getOrigin()).append("-").append(s.getDestination()).append(" "));
                    sb.append("| ");
                }
                sb.append("RMK: ").append(String.join("/", data.getRemarks()));

                c.setObservaciones(sb.toString());

                // 7. Guardar y Procesar Archivo
                comisionRepository.save(c);
                System.out.println("✅ Importado con éxito: " + data.getFileName());
                moveFileToProcessed(data.getFileName());

            } catch (Exception e) {
                System.err.println("❌ Error procesando " + data.getFileName() + " -> " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private String safeTruncate(String value, int maxLength) {
        if (value == null || value.trim().isEmpty()) return null;
        String cleaned = value.trim();
        return (cleaned.length() <= maxLength) ? cleaned : cleaned.substring(0, maxLength);
    }

    private void calculateAndSetCommissions(AirFileData data, Comision c) {
        BigDecimal total = BigDecimal.ZERO;
        try {
            if (data.getCommission() != null && data.getFare() != null && data.getFare().getBaseFare() != null) {
                BigDecimal porc = new BigDecimal(data.getCommission());
                c.setPorComision(porc);
                total = BigDecimal.valueOf(data.getFare().getBaseFare()).multiply(porc).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            }
        } catch (Exception e) {}
        c.setComisionTotal(total);
        c.setComisionTotalReal(total);
    }

    private void moveFileToProcessed(String fileName) {
        try {
            if (!Files.exists(targetPath)) Files.createDirectories(targetPath);
            Path source = sourcePath.resolve(fileName);
            Path target = targetPath.resolve(fileName);
            if (Files.exists(source)) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Archivo movido: " + fileName);
            }
        } catch (IOException e) {
            System.err.println("Error moviendo archivo: " + fileName);
        }
    }
}