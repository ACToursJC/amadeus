package com.apua.amadeus.service;

import com.apua.amadeus.model.*;
import com.apua.amadeus.dto.*;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AirParserService {

    private final Path rootPath = Paths.get("F:/Amadeus/air4");

    private static final Map<String, String> MONTH_MAP = new HashMap<>();
    private static final Map<String, String> STATUS_CODES = new HashMap<>();
    private static final Map<String, String> CHAIN_NAMES = new HashMap<>();

    static {
        STATUS_CODES.put("HK", "CONFIRMADO");
        STATUS_CODES.put("GK", "CONFIRMADO");
        STATUS_CODES.put("TK", "CAMBIO HORARIO");
        STATUS_CODES.put("HX", "CANCELADO");
        STATUS_CODES.put("UC", "NO DISPONIBLE");
        STATUS_CODES.put("UN", "CANCELADO/NO OPERA");

        MONTH_MAP.put("JAN", "01"); MONTH_MAP.put("FEB", "02"); MONTH_MAP.put("MAR", "03");
        MONTH_MAP.put("APR", "04"); MONTH_MAP.put("MAY", "05"); MONTH_MAP.put("JUN", "06");
        MONTH_MAP.put("JUL", "07"); MONTH_MAP.put("AUG", "08"); MONTH_MAP.put("SEP", "09");
        MONTH_MAP.put("OCT", "10"); MONTH_MAP.put("NOV", "11"); MONTH_MAP.put("DEC", "12");

        CHAIN_NAMES.put("WY", "WYNDHAM");
        CHAIN_NAMES.put("HY", "HYATT");
        CHAIN_NAMES.put("NH", "NH HOTELS");
        CHAIN_NAMES.put("MC", "MARRIOTT");
    }

    public List<AirFileData> parseAllFiles() throws IOException {
        if (!Files.exists(rootPath)) Files.createDirectories(rootPath);
        return Files.list(rootPath).filter(Files::isRegularFile).map(this::parseSingleFile).collect(Collectors.toList());
    }

    private AirFileData parseSingleFile(Path path) {
        AirFileData airData = new AirFileData();
        airData.setFileName(path.getFileName().toString());
        FareInfo fare = new FareInfo();
        fare.setTaxes(new ArrayList<>());
        airData.setFare(fare);
        Map<String, Passenger> passengerMap = new LinkedHashMap<>();
        List<String> fareBasisList = new ArrayList<>();

        try {
            List<String> lines = Files.readAllLines(path);
            airData.setRawLines(lines);

            // 1. Metadatos rápidos (Ejecutiva)
            for (String line : lines) {
                if (line.contains("EJECUTIVA")) {
                    String[] parts = line.split("EJECUTIVA");
                    if (parts.length > 1) {
                        airData.setJobTitle(parts[1].replaceAll("^[^A-Z]+", "").replaceAll("[;]+$", "").trim());
                    }
                    break;
                }
            }

            // 2. Procesamiento línea por línea
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;

                if (line.startsWith("MUC1A")) parseHeaderMUC(line, airData);
                else if (line.startsWith("A-")) parseAgency(line, airData);
                else if (line.startsWith("C-")) parseLineC(line, airData);
                else if (line.startsWith("D-")) parseLineD(line, airData);
                else if (line.startsWith("I-")) {
                    Passenger p = parsePassengerLine(line, airData, passengerMap);
                    if (p != null) {
                        passengerMap.put("P" + (airData.getPassengers().size() + 1), p);
                        if (airData.getPassenger() == null) airData.setPassenger(p);
                        airData.getPassengers().add(p);
                        airData.getStockBoleto().setDe_pasajero(p.getLastName() + " / " + p.getFirstName());
                    }
                }
                else if (line.startsWith("H-")) parseFlightSegment(line, airData);
                else if (line.startsWith("M-")) fareBasisList.addAll(parseFareBasis(line));
                else if (line.startsWith("U-")) parseAuxiliarySegment(line, airData);
                else if (line.startsWith("K-")) parseFare(line, fare, airData);
                else if (line.startsWith("TAX-") || line.startsWith("KFTF") || line.startsWith("KNTF") || line.startsWith("KSTF")) {
                    parseTaxes(line, fare);
                }
                else if (line.startsWith("T-K") || line.startsWith("T-B") || line.startsWith("FH")) parseTicketResilient(line, airData);
                else if (line.startsWith("FP")) parseFormOfPayment(line, airData);
                else if (line.startsWith("FM")) airData.setCommission(line.substring(2).replaceAll("[^0-9.]", ""));
                else if (line.startsWith("RM")) processRemark(line, airData);
                else if (line.startsWith("SSR")) processSSR(line, airData, passengerMap);
                else if (line.startsWith("OSI")) processOSI(line, airData);
                else if (line.startsWith("FE")) airData.setEndorsements(line.substring(2).trim());
                else if (line.startsWith("FV")) {
                    String carrier = line.substring(2).replaceAll("[^A-Z0-9*]", "").trim();
                    if (carrier.contains("*")) {
                        String[] fvParts = carrier.split("\\*");
                        String code = fvParts[fvParts.length - 1];
                        if (code.length() >= 2) airData.setValidatingCarrier(code.substring(0, 2));
                    }
                }
                else if (line.startsWith("FOI")) {
                    String id = line.replaceAll(".*FOID-", "").split(";")[0].replaceAll("[^0-9]", "");
                    if (airData.getPassenger() != null && airData.getPassenger().getDocumentId() == null) airData.getPassenger().setDocumentId(id);
                }
                else if (line.startsWith("O-")) {
                    String[] oParts = line.split(";");
                    for (String op : oParts) if (op.contains("LD")) airData.setTicketTimeLimit(op.replace("LD", "").trim());
                }
                else if (line.startsWith("Q-")) {
                    airData.setFareCalculation(line.substring(2).trim());
                    parseFareCalculation(line, fare, airData);
                }
            }

            mapFareBasisToSegments(airData, fareBasisList);
            buildRouteArray(airData);
            enrichDataPostParsing(airData);

            airData.setOperationType(!airData.getSegments().isEmpty() && !airData.getHotels().isEmpty() ? "MIXTO" :
                    !airData.getHotels().isEmpty() ? "HOTEL" : "AEREO");

        } catch (Exception e) { System.err.println("Error crítico: " + e.getMessage()); }
        return airData;
    }

    private void parseFlightSegment(String line, AirFileData data) {
        try {
            String[] parts = line.split(";");
            StockBoletoSegmentoDTO segDTO = new StockBoletoSegmentoDTO();
            FlightSegment s = new FlightSegment();
            segDTO.setNu_correl(data.getTablaSegmentos().size() + 1);

            String originRaw = parts[1].trim();
            String originCode = originRaw.length() >= 3 ? originRaw.substring(originRaw.length() - 3) : originRaw;
            segDTO.setCiu_salida(originCode);
            segDTO.setCiu_llegada(parts[3].trim());

            String[] f = parts[5].trim().split("\\s+");
            if (f.length >= 6) {
                segDTO.setCod_la(f[0]);
                segDTO.setNu_vuelo(f[1]);
                segDTO.setClase(f[2]);

                String depDate = normalizeShortDate(f[4].substring(0, 5), data.getIssuingDate(), "-");
                String depTime = f[4].substring(5, 7) + ":" + f[4].substring(7, 9) + ":00.000";
                segDTO.setFe_salida(depDate + " " + depTime);

                String arrTimeReal = f[5];
                String arrDate = normalizeShortDate(f[6], data.getIssuingDate(), "-");
                segDTO.setFe_llegada(arrDate + " " + arrTimeReal.substring(0, 2) + ":" + arrTimeReal.substring(2, 4) + ":00.000");

                if (parts.length > 17 && parts[17].trim().length() >= 4) {
                    String dur = parts[17].trim();
                    segDTO.setTiempo_vuelo(Float.parseFloat(dur.substring(0, 2) + "." + dur.substring(2, 4)));
                }
                segDTO.setTiempo_vuelo(calculateFlightDuration(segDTO.getFe_salida(), segDTO.getFe_llegada()));
                s.setAirline(f[0]);
                s.setFlightNumber(f[1]);
                s.setBookingClass(f[2]);
                s.setDepartureDate(depDate);
                s.setDepartureTime(f[4].substring(5, 9));
                s.setArrivalDate(arrDate);
                s.setArrivalTime(arrTimeReal);
            }

            String statusField = (parts.length > 8 && !parts[8].trim().isEmpty()) ? parts[8] : (parts.length > 7 ? parts[7] : "HK");
            segDTO.setCod_estado(statusField.trim().substring(0, Math.min(2, statusField.trim().length())));
            segDTO.setTipo_equipo(parts.length > 10 ? parts[10].trim() : "");
            s.setStatus(segDTO.getCod_estado());
            s.setEquipment(segDTO.getTipo_equipo());

            if (line.contains("CO2-")) {
                String co2Val = line.split("CO2-")[1].split(";")[0].replaceAll("[^0-9.]", "").trim();
                segDTO.setCo2(Double.parseDouble(co2Val));
                s.setCo2Emission(co2Val);
            }

            StockBoletoDTO main = data.getStockBoleto();
            if (main.getNroVuelos().size() < 8) {
                main.getNroVuelos().add(segDTO.getNu_vuelo());
                main.getClases().add(segDTO.getClase());
                main.getEstadosVuelo().add(segDTO.getCod_estado());
                main.getFechasVuelo().add(segDTO.getFe_salida());
                main.getCo2Valores().add(segDTO.getCo2());
                main.getHorasLlegada().add(f[5].substring(0,2) + ":" + f[5].substring(2,4));
            }

            s.setOrigin(segDTO.getCiu_salida());
            s.setDestination(segDTO.getCiu_llegada());
            data.getSegments().add(s);
            data.getTablaSegmentos().add(segDTO);
        } catch (Exception e) {}
    }

    private void parseAuxiliarySegment(String line, AirFileData data) {
        try {
            String[] parts = line.split(";");
            if (parts.length < 6) return;
            String segmentType = parts[1].trim();

            // --- CASO HOTEL ---
            if (segmentType.contains("HHL") || segmentType.contains("HTL")) {
                HotelSegment h = new HotelSegment();
                String[] typeParts = segmentType.split("\\s+");
                if (typeParts.length >= 3) {
                    h.setChainCode(typeParts[1]);
                    h.setChainName(CHAIN_NAMES.getOrDefault(typeParts[1], ""));
                    h.setStatus(STATUS_CODES.getOrDefault(typeParts[2].substring(0, 2), typeParts[2]));
                }
                h.setCheckInDate(normalizeShortDate(extractDate(parts[2]), data.getIssuingDate(), "-"));
                h.setCheckOutDate(normalizeShortDate(extractDate(parts[3]), data.getIssuingDate(), "-"));
                h.setCityCode(parts[4].trim());
                h.setCityName(parts[5].trim());

                for (int i = 6; i < parts.length; i++) {
                    String p = parts[i].trim();
                    if (p.isEmpty()) continue;

                    if (p.startsWith("**-")) {
                        String val = p.substring(3).trim();
                        if (val.isEmpty() && i + 1 < parts.length) val = parts[++i].trim();
                        h.setHotelName(val);
                    } else if (p.startsWith("CF-")) {
                        String val = p.substring(3).trim();
                        if (val.isEmpty() && i + 1 < parts.length) val = parts[++i].trim();
                        h.setConfirmationNumber(val);
                    } else if (p.startsWith("TTL-")) {
                        String val = p.substring(4).trim();
                        if (val.isEmpty() && i + 1 < parts.length) val = parts[++i].trim();
                        String amt = val.replaceAll("[^0-9.]", "");
                        h.setTtlAmount(amt); h.setTotalAmount(amt);
                        if (val.contains("USD")) h.setCurrency("USD");
                        else if (val.contains("PEN")) h.setCurrency("PEN");
                    } else if (p.startsWith("COM-")) {
                        String val = p.substring(4).trim();
                        if (val.isEmpty() && i + 1 < parts.length) val = parts[++i].trim();
                        h.setHotelCommission(val);
                    } else if (p.startsWith("NGT-")) {
                        String val = p.substring(4).trim();
                        if (val.isEmpty() && i + 1 < parts.length) val = parts[++i].trim();
                        try { h.setNumberOfNights(Integer.parseInt(val.replaceAll("[^0-9]", ""))); } catch (Exception e) {}
                    } else if (p.startsWith("RO-") || p.startsWith("DES-")) {
                        String val = p.substring(p.indexOf("-") + 1).trim();
                        if (val.isEmpty() && i + 1 < parts.length && !parts[i+1].contains("-")) val = parts[++i].trim();
                        if (h.getRoomType() == null || h.getRoomType().isEmpty()) h.setRoomType(val);
                    } else if (p.matches("^[0-9]{8,20}$") || p.matches("^[0-9\\s\\-]{8,20}$") || p.startsWith("PH-")) {
                        if (h.getPhoneNumber() == null) h.setPhoneNumber(p.replace("PH-", "").trim());
                    } else if (!p.matches("^[A-Z]{2,3}-.*") && h.getHotelName() != null && h.getAddress() == null) {
                        if (p.equals("+") || p.length() < 3 || p.matches("^[0-9]{7}$")) continue;
                        StringBuilder addr = new StringBuilder(p);
                        while (i + 1 < parts.length && !parts[i+1].matches("^[A-Z]{2,3}-.*")) {
                            String next = parts[i+1].trim();
                            if (next.matches("^[0-9\\s\\-]{8,20}$")) { if (h.getPhoneNumber() == null) h.setPhoneNumber(next); i++; break; }
                            if (next.contains("BED") || next.contains("PUBLISHED") || next.contains("EJECUTIVA") || next.equals("+")) break;
                            addr.append("  ").append(next); i++;
                        }
                        h.setAddress(addr.toString().trim());
                    } else if (p.matches("\\d+\\.\\d+\\+\\d{2}[A-Z]{3}\\+\\d+")) {
                        h.getDailyRates().add(p);
                        String[] dr = p.split("\\+");
                        HotelSegment.DailyRateDetail detail = new HotelSegment.DailyRateDetail();
                        detail.setRate(dr[0]); detail.setDate(normalizeShortDate(dr[1], data.getIssuingDate(), "/")); detail.setNights(dr[2]);
                        h.getDailyRateDetails().add(detail);
                        if (h.getPricePerNight() == null) h.setPricePerNight(dr[0]);
                    }
                }
                if (h.getNumberOfNights() == null && h.getCheckInDate() != null) {
                    try { h.setNumberOfNights((int) java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(h.getCheckInDate()), java.time.LocalDate.parse(h.getCheckOutDate()))); } catch(Exception e) {}
                }
                data.getHotels().add(h);
            }

            // --- CASO VUELO ---
            else if (parts.length >= 7 && (parts[5].contains(" ") && (parts[5].contains("LA ") || parts[5].matches(".*[A-Z]{2}\\s+\\d{4}.*")))) {
                StockBoletoSegmentoDTO segDTO = new StockBoletoSegmentoDTO();
                FlightSegment s = new FlightSegment();
                StockBoletoDTO main = data.getStockBoleto();
                segDTO.setNu_correl(data.getTablaSegmentos().size() + 1);

                String originRaw = parts[1].trim();
                String originCode = originRaw.length() >= 3 ? originRaw.substring(originRaw.length() - 3) : originRaw;
                segDTO.setCiu_salida(originCode);
                segDTO.setCiu_llegada(parts[3].trim());

                String[] f = parts[5].trim().split("\\s+");
                if (f.length >= 7) {
                    segDTO.setCod_la(f[0]); segDTO.setNu_vuelo(f[1]); segDTO.setClase(f[2]);
                    if (parts.length > 10) { segDTO.setTipo_equipo(parts[10].trim()); s.setEquipment(parts[10].trim()); }
                    String depDate = normalizeShortDate(f[4].substring(0, 5), data.getIssuingDate(), "-");
                    String depTime = f[4].substring(5, 7) + ":" + f[4].substring(7, 9);
                    segDTO.setFe_salida(depDate + " " + depTime + ":00.000");
                    String arrTimeStr = f[5].substring(0, 2) + ":" + f[5].substring(2, 4);
                    String arrDate = normalizeShortDate(f[6], data.getIssuingDate(), "-");
                    segDTO.setFe_llegada(arrDate + " " + arrTimeStr + ":00.000");
                    segDTO.setTiempo_vuelo(calculateFlightDuration(segDTO.getFe_salida(), segDTO.getFe_llegada()));
                    s.setAirline(f[0]); s.setFlightNumber(f[1]); s.setBookingClass(f[2]);
                    s.setDepartureDate(depDate); s.setArrivalDate(arrDate); s.setArrivalTime(f[5]);
                }
                segDTO.setCod_estado(parts[6].trim().substring(0, Math.min(2, parts[6].trim().length())));
                if (line.contains("CO2-")) segDTO.setCo2(Double.parseDouble(line.split("CO2-")[1].split("KG")[0].replaceAll("[^0-9.]", "")));
                if (!main.getCiudades().contains(segDTO.getCiu_salida())) main.getCiudades().add(segDTO.getCiu_salida());
                if (!main.getCiudades().contains(segDTO.getCiu_llegada())) main.getCiudades().add(segDTO.getCiu_llegada());
                main.getNroVuelos().add(segDTO.getNu_vuelo()); main.getClases().add(segDTO.getClase());
                main.getFechasVuelo().add(segDTO.getFe_salida()); main.getEstadosVuelo().add(segDTO.getCod_estado());
                main.getCo2Valores().add(segDTO.getCo2() != null ? segDTO.getCo2() : 0.0);
                if (s.getArrivalTime() != null && s.getArrivalTime().length() >= 4) main.getHorasLlegada().add(s.getArrivalTime().substring(0,2) + s.getArrivalTime().substring(2,4));
                data.getSegments().add(s); data.getTablaSegmentos().add(segDTO);
            }

            // --- CASO MISCELÁNEOS (TODOS) ---
            else if (segmentType.contains("MIS")) {
                String miscStr = parts.length > 5 ? parts[5].trim() : parts[parts.length-1].trim();
                if (!miscStr.isEmpty()) data.getMiscSegments().add(miscStr);
            }
        } catch (Exception e) { System.err.println("Error en Auxiliary: " + e.getMessage()); }
    }

    private void processRemark(String line, AirFileData data) {
        String raw = line.substring(2).trim();
        String clean = raw.replaceAll("^[*+/\\s]+", "").replace("RM*", "");

        // 1. Campos raíz
        if (clean.startsWith("DP=")) data.setDepartment(clean.split("=")[1].split(";")[0].trim());
        else if (clean.startsWith("CC=")) data.setCostCenter(clean.split("=")[1].split(";")[0].trim());
        else if (clean.startsWith("CAR SIZE-")) data.setCarSize(clean.split("-")[1].split(";")[0].trim());
        else if (clean.startsWith("BED TYPE-")) data.setBedType(clean.split("-")[1].split(";")[0].trim());
        else if (clean.startsWith("GENDER-")) data.setGender(clean.split("-")[1].split(";")[0].trim());
        else if (clean.startsWith("JOB TITLE-")) data.setJobTitle(clean.split("-", 2)[1].split(";")[0].replace("TITLE -", "").trim());
        else if (clean.startsWith("SOLICITUD=")) data.getStockBoleto().setNu_file(clean.split("=")[1].trim());
        else if (clean.contains("RUC")) {
            String ruc = clean.replaceAll("[^0-9]", "");
            if (ruc.length() >= 11) data.getStructuredRemarks().add(new TranslatedInfo("COMPANY_RUC", "RM", ruc, "bi-building-check"));
        } else if (clean.startsWith("SOLICITANTE")) {
            data.getStructuredRemarks().add(new TranslatedInfo("REQUESTER", "RM", clean.replace("SOLICITANTE", "").trim(), "bi-person-gear"));
        } else if (clean.startsWith("EN=")) {
            data.getStructuredRemarks().add(new TranslatedInfo("EMPLOYEE_NUMBER", "RM", clean.split("=")[1].split(";")[0].trim(), "bi-person-vcard"));
        } else if (clean.contains("DOC DNI:")) {
            String dni = clean.split("DNI:")[1].split(";")[0].trim();
            if (data.getPassenger() != null) data.getPassenger().setDocumentId(dni);
        }

        // 2. Benchmarks (QCHF, etc.)
        if (clean.startsWith("QCHF/") || clean.startsWith("QCMK/") || clean.startsWith("QCLF/")) {
            String[] parts = clean.split("/");
            if (parts.length > 1) addAhorro(data, parts[0], Double.parseDouble(parts[1].replaceAll("[^0-9.]", "")));
        }

        // 3. Fallbacks de Hotel (APPFCM)
        if (clean.startsWith("APPFCM/") && !data.getHotels().isEmpty()) {
            HotelSegment lastH = data.getHotels().get(data.getHotels().size() - 1);
            if (clean.contains("TOTALRATE-") && (lastH.getTotalAmount() == null || lastH.getTotalAmount().isEmpty())) {
                String rate = clean.split("TOTALRATE-")[1].split("/")[0].trim();
                lastH.setTotalAmount(rate); lastH.setTtlAmount(rate);
            }
        }

        data.getRemarks().add(raw);
    }

    private void processOSI(String line, AirFileData data) {
        String raw = line.substring(4).trim();
        // Capturar Contacto de Emergencia
        if (data.getPassenger() != null && (raw.contains("EMERGENCIAS") || raw.contains("CTC"))) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\d{7,15}");
            java.util.regex.Matcher m = p.matcher(raw);
            if (m.find()) {
                data.getPassenger().setEmergencyContactPhone(m.group());
                data.getPassenger().setEmergencyContactName("AC TOURS EMERGENCIAS");
            }
        }
        data.getOsi().add(raw);
    }

    private void addAhorro(AirFileData data, String desc, Double monto) {
        StockBoletoAhorroDTO a = new StockBoletoAhorroDTO();
        a.setNu_correl(data.getTablaAhorros().size() + 1);
        a.setCo_codigo("01");
        a.setDe_valor(desc);
        a.setMo_monto(monto);
        data.getTablaAhorros().add(a);
    }

    private void processSSR(String line, AirFileData data, Map<String, Passenger> pMap) {
        String raw = line.substring(4).trim();
        Passenger p = data.getPassenger();
        if (p == null) return;

        // 1. SSR DOCS: Fecha Nacimiento y Género
        if (raw.contains("DOCS")) {
            // Formato: SSR DOCS LA HK1/////30AUG65/M//SOTO/FRANZ/ABEL
            String[] docsParts = raw.split("/");
            for (String part : docsParts) {
                // Buscar Fecha de Nacimiento (DDMMMYY)
                if (part.matches("\\d{2}[A-Z]{3}\\d{2}")) {
                    p.setBirthDate(normalizeBirthDate(part));
                }
                // Buscar Género (M o F)
                if (part.equals("M") || part.equals("F")) {
                    p.setGender(part);
                    data.setGender(part);
                }
            }
        }

        // 2. SSR CTCE: Email
        if (raw.contains("CTCE")) {
            // Formato: SSR CTCE LA HK1/FRANZ.SOTO//NEWMONT.COM
            String email = raw.substring(raw.indexOf("/") + 1).replace("//", "@").split(";")[0].split("\\s+")[0];
            p.setEmail(email);
        }

        // 3. SSR CTCM: Teléfono
        if (raw.contains("CTCM")) {
            // Formato: SSR CTCM LA HK1/51976222568
            String phone = raw.substring(raw.indexOf("/") + 1).split(";")[0].split("\\s+")[0];
            p.setPhone(phone);
        }

        data.getStructuredSSR().add(new TranslatedInfo("SISTEMA_SSR_RAW", "SSR", raw, "bi-info-square"));
    }

    private String normalizeBirthDate(String raw) {
        if (raw == null || raw.length() < 7) return raw;
        try {
            int yearShort = Integer.parseInt(raw.substring(5, 7));
            String century = (yearShort > 25) ? "19" : "20";
            return raw.substring(0, 2) + "/" + MONTH_MAP.get(raw.substring(2, 5).toUpperCase()) + "/" + century + raw.substring(5, 7);
        } catch (Exception e) { return raw; }
    }

    private String normalizeShortDate(String raw, String ref, String sep) {
        if (raw == null || raw.length() < 5) return null;
        try {
            String day = raw.substring(0, 2);
            String month = MONTH_MAP.get(raw.substring(2, 5).toUpperCase());
            String year = (ref != null && ref.length() >= 4) ? ref.substring(0, 4) : String.valueOf(LocalDate.now().getYear());
            return sep.equals("-") ? year + "-" + month + "-" + day : day + "/" + month + "/" + year;
        } catch (Exception e) { return null; }
    }

    private void buildRouteArray(AirFileData data) {
        List<String> cities = new ArrayList<>();
        for (StockBoletoSegmentoDTO s : data.getTablaSegmentos()) {
            if (cities.isEmpty()) cities.add(s.getCiu_salida());
            cities.add(s.getCiu_llegada());
        }
        data.getStockBoleto().setCiudades(cities);
    }

    private void enrichDataPostParsing(AirFileData airData) {
        for (HotelSegment hotel : airData.getHotels()) {

            // 1. BUSCAR EN REMARKS ESTRUCTURADOS (Metadata de FCM)
            for (TranslatedInfo remark : airData.getStructuredRemarks()) {
                if ("APPFCM_DATA".equals(remark.getCategory())) {
                    String val = remark.getHumanValue();

                    // Extraer Nombre del Hotel si está vacío
                    if ((hotel.getHotelName() == null || hotel.getHotelName().isEmpty()) && val.contains("HOTELNAM-")) {
                        hotel.setHotelName(val.split("HOTELNAM-")[1].split("/")[0].trim());
                    }

                    // Extraer Código de Cadena (CHAINCOD-XX)
                    if (val.contains("CHAINCOD-")) {
                        String code = val.split("CHAINCOD-")[1].split("/")[0].trim();
                        // Evitar "SUNDEFINED" o valores basura
                        if (!code.equalsIgnoreCase("SUNDEFINED") && !code.isEmpty()) {
                            hotel.setChainCode(code);
                            // Si el código existe en nuestro mapa, asignamos el nombre
                            if (hotel.getChainName() == null || hotel.getChainName().isEmpty()) {
                                hotel.setChainName(CHAIN_NAMES.getOrDefault(code, ""));
                            }
                        }
                    }
                }
            }

            // 2. BÚSQUEDA EN REMARKS GENERALES (Texto plano)
            // Si después de lo anterior aún no tenemos el nombre de la cadena:
            if (hotel.getChainName() == null || hotel.getChainName().isEmpty()) {
                for (String rem : airData.getRemarks()) {
                    String upperRemark = rem.toUpperCase();
                    for (Map.Entry<String, String> entry : CHAIN_NAMES.entrySet()) {
                        // Si el remark contiene "WYNDHAM", "MARRIOTT", etc.
                        if (upperRemark.contains(entry.getValue())) {
                            hotel.setChainName(entry.getValue());
                            if (hotel.getChainCode() == null) hotel.setChainCode(entry.getKey());
                            break;
                        }
                    }
                }
            }

            // 3. LIMPIEZA FINAL: Si tiene hotelName pero no chainName, intentar deducirlo del nombre del hotel
            if ((hotel.getChainName() == null || hotel.getChainName().isEmpty()) && hotel.getHotelName() != null) {
                String hName = hotel.getHotelName().toUpperCase();
                for (Map.Entry<String, String> entry : CHAIN_NAMES.entrySet()) {
                    if (hName.contains(entry.getValue())) {
                        hotel.setChainName(entry.getValue());
                        hotel.setChainCode(entry.getKey());
                        break;
                    }
                }
            }
        }
    }


    private void parseHeaderMUC(String line, AirFileData data) {
        try {
            String[] parts = line.split(";");
            // El PNR (6 caracteres después de MUC1A + espacio)
            if (line.startsWith("MUC1A")) {
                String pnr = line.substring(6, 12).trim();
                data.setPnr(pnr);
                data.getStockBoleto().setNu_pnr(pnr);
            }

            if (parts.length > 2) {
                // El Office ID (Pseudo) suele ser la tercera parte
                String officeId = parts[2].trim();
                data.setOfficeId(officeId);
                data.getStockBoleto().setCo_seudo(officeId);

                // CAPTURA DE IATA (El número de 8 dígitos que sigue al Office ID)
                if (parts.length > 3 && parts[3].trim().matches("\\d{8}")) {
                    data.setIataNumber(parts[3].trim());
                }
            }

            // CAPTURA DE VALIDATING CARRIER (Al final de la línea MUC1A)
            // Ejemplo: ...;LA KZMHKR
            String lastPart = parts[parts.length - 1].trim();
            if (lastPart.length() >= 2) {
                String carrier = lastPart.substring(0, 2);
                if (carrier.matches("[A-Z0-9]{2}")) {
                    data.setValidatingCarrier(carrier);
                }
            }
        } catch (Exception e) {}
    }

    private void parseLineC(String line, AirFileData data) {
        if (line.length() >= 15) {
            String si = line.substring(12, 14);
            data.setSignIn(si);
            data.getStockBoleto().setCo_usuario(si);
        }
    }
    private void parseLineD(String line, AirFileData data) {
        String[] dates = line.substring(2).split(";");
        if (dates.length > 0) {
            String f = formatYear20(dates[0]);
            data.setIssuingDate(f);
            data.getStockBoleto().setFe_emision(f + " 00:00:00.000");
        }
    }

    private void parseAgency(String line, AirFileData data) {
        String[] parts = line.substring(2).split(";");
        if (parts.length > 0) data.setAgencyName(parts[0].trim());

        Matcher m = Pattern.compile("\\d{8}").matcher(line);
        if (m.find()) {
            data.setIataNumber(m.group());
        }
    }

    private void parseTicketResilient(String line, AirFileData data) {
        Pattern pattern = Pattern.compile("(\\d{10,13})");
        Matcher matcher = pattern.matcher(line);

        if (matcher.find()) {
            String tk = matcher.group(1);
            // Si tiene 13 dígitos, los últimos 10 suelen ser el número correlativo
            String tkShort = tk.length() > 10 ? tk.substring(tk.length() - 10) : tk;

            if (line.startsWith("T-K")) {
                // AGREGAR COMO DATO ADICIONAL ESTRUCTURADO
                data.getStructuredRemarks().add(new TranslatedInfo(
                        "TICKET_CONTROL_TK",
                        "T-K",
                        tk,
                        "bi-ticket-detailed"
                ));

                // Solo si no se ha asignado un ticket aún (como fallback)
                if (data.getTicketNumber() == null) {
                    data.setTicketNumber(tk);
                    data.getStockBoleto().setDe_boleto(tkShort);
                }
            }
            else if (line.startsWith("FH")) {
                // PRIORIDAD: El FH es el registro financiero real del pasajero
                data.setTicketNumber(tk);
                data.getStockBoleto().setDe_boleto(tkShort);
            }
        }
    }

    private void parseFare(String line, FareInfo fare, AirFileData data) {
        try {
            String[] parts = line.split(";");

            // 1. Extraer Tarifa Base (Primera parte)
            String basePart = parts[0].replace("K-F", "").trim();
            String baseNum = basePart.replaceAll("[^0-9.]", "");
            if (!baseNum.isEmpty()) {
                fare.setBaseFare(Double.parseDouble(baseNum));
            }

            // 2. Extraer Tarifa Total (Última parte no vacía)
            String totalPart = parts[parts.length - 1].trim();
            String totalNum = totalPart.replaceAll("[^0-9.]", "");
            String currency = totalPart.replaceAll("[0-9.]", "").trim();

            if (!totalNum.isEmpty()) {
                Double totalVal = Double.parseDouble(totalNum);
                fare.setTotalFare(totalVal);
                fare.setCurrency(currency);

                // Actualizar StockBoleto
                data.getStockBoleto().setMo_monto(totalVal);
                data.getStockBoleto().setCo_moneda_boleto(currency);
            }
        } catch (Exception e) {
            System.err.println("Error en parseFare: " + e.getMessage());
        }
    }

    private void parseTaxes(String line, FareInfo fare) {
        // 1. Procesar desgloses detallados (Líneas KFTF, KNTF, KSTF)
        if (line.startsWith("KFTF") || line.startsWith("KNTF") || line.startsWith("KSTF")) {
            String[] components = line.substring(5).split(";");
            for (String comp : components) {
                String val = comp.trim();
                if (!val.isEmpty()) {
                    // Limpiamos espacios múltiples: "USD22.10    YR VB" -> "USD22.10 YR"
                    String cleanedTax = val.replaceAll("\\s{2,}", " ");
                    // Si la tasa tiene un código de 2 letras al final (ej: YR, HW, PE)
                    if (!fare.getTaxes().contains(cleanedTax)) {
                        fare.getTaxes().add(cleanedTax);
                    }
                }
            }
        }
        // 2. Procesar línea de resumen TAX-
        else if (line.startsWith("TAX-")) {
            String[] tParts = line.substring(4).split(";");
            for (String t : tParts) {
                String val = t.trim();
                if (!val.isEmpty()) {
                    // Si es XT, lo agregamos siempre. Si es otro, solo si no está en el desglose KFTF
                    if (val.contains("XT")) {
                        if (!fare.getTaxes().contains(val)) fare.getTaxes().add(val);
                    } else {
                        if (!fare.getTaxes().contains(val)) fare.getTaxes().add(val);
                    }
                }
            }
        }
    }

    private void parseFormOfPayment(String line, AirFileData data) {
        String fp = line.substring(2).split(";")[0].trim();
        data.setFormOfPayment(fp);
        if (fp.startsWith("CC")) {
            StockBoletoTarjetaDTO t = new StockBoletoTarjetaDTO();
            t.setNu_correl(data.getTablaTarjetas().size() + 1);
            t.setCo_tip_tarj(fp.substring(2, 4));
            t.setNu_tarj(fp.substring(4).split("/")[0]);
            t.setMo_monto(data.getStockBoleto().getMo_monto());
            data.getTablaTarjetas().add(t);
        }
    }
    private List<String> parseFareBasis(String line) { return Arrays.stream(line.substring(2).split(";")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList()); }
    private void mapFareBasisToSegments(AirFileData data, List<String> list) {
        for (int i = 0; i < data.getTablaSegmentos().size(); i++) {
            if (i < list.size()) data.getTablaSegmentos().get(i).setCo_fare_basis(list.get(i));
        }
    }
    private void parseFareCalculation(String line, FareInfo fare, AirFileData data) {
        Matcher mRoe = Pattern.compile("ROE(\\d+\\.\\d+)").matcher(line);
        if (mRoe.find()) data.getStructuredRemarks().add(new TranslatedInfo("TIPO_CAMBIO", "Q-", mRoe.group(1), ""));
    }
    private Passenger parsePassengerLine(String line, AirFileData data, Map<String, Passenger> pMap) {
        String[] parts = line.split(";");
        if (parts.length <= 1) return null;
        Passenger p = new Passenger();
        String rawName = parts[1].replaceAll("^\\d+", "").trim();
        p.setFullName(rawName);
        if (rawName.contains("/")) {
            p.setLastName(rawName.split("/")[0].trim());
            if (rawName.split("/").length > 1) p.setFirstName(rawName.split("/")[1].replaceAll("\\s+(MR|MRS|MS|MSTR|MISS)$", "").trim());
        }
        return p;
    }
    private Float calculateFlightDuration(String start, String end) {
        if (start == null || end == null) return 0f;
        try {
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
            java.time.LocalDateTime departure = java.time.LocalDateTime.parse(start, formatter);
            java.time.LocalDateTime arrival = java.time.LocalDateTime.parse(end, formatter);
            java.time.Duration duration = java.time.Duration.between(departure, arrival);
            float hours = duration.toMinutes() / 60f;
            return Math.round(hours * 100f) / 100f; // Redondeo a 2 decimales
        } catch (Exception e) {
            return 0f;
        }
    }
    private String formatYear20(String d) { return "20" + d.substring(0, 2) + "-" + d.substring(2, 4) + "-" + d.substring(4, 6); }
    private String extractDate(String text) { Matcher m = Pattern.compile("(\\d{2}[A-Z]{3})").matcher(text); return m.find() ? m.group(1) : text.trim(); }
    private String translateStatus(String code) { return STATUS_CODES.getOrDefault(code.replaceAll("[0-9]", "").toUpperCase().trim(), code); }
}