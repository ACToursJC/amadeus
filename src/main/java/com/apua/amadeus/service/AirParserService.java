package com.apua.amadeus.service;

import com.apua.amadeus.model.*;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AirParserService {

    private final Path rootPath = Paths.get("F:/Amadeus/air3");

    private static final Map<String, String> BED_TYPES = new HashMap<>();
    private static final Map<String, String> CAR_SIZES = new HashMap<>();
    private static final Map<String, String> CC_PROVIDERS = new HashMap<>();
    private static final Map<String, String> STATUS_CODES = new HashMap<>();
    private static final Map<String, String> MONTH_MAP = new HashMap<>();

    static {
        BED_TYPES.put("K", "KING SIZE");
        BED_TYPES.put("Q", "QUEEN SIZE");
        BED_TYPES.put("D", "DOBLE");
        BED_TYPES.put("T", "TWIN");
        BED_TYPES.put("2P", "DOBLE PLAZA");
        BED_TYPES.put("1.5P", "PLAZA Y MEDIA");

        CAR_SIZES.put("M", "MINI");
        CAR_SIZES.put("E", "ECONÓMICO");
        CAR_SIZES.put("C", "COMPACTO");
        CAR_SIZES.put("I", "INTERMEDIO");

        CC_PROVIDERS.put("VI", "VISA");
        CC_PROVIDERS.put("AX", "AMERICAN EXPRESS");
        CC_PROVIDERS.put("MC", "MASTERCARD");

        STATUS_CODES.put("HK", "CONFIRMADO");
        STATUS_CODES.put("TK", "CAMBIO DE HORARIO");
        STATUS_CODES.put("GK", "CONFIRMADO (AUX)");

        MONTH_MAP.put("JAN", "01"); MONTH_MAP.put("FEB", "02"); MONTH_MAP.put("MAR", "03");
        MONTH_MAP.put("APR", "04"); MONTH_MAP.put("MAY", "05"); MONTH_MAP.put("JUN", "06");
        MONTH_MAP.put("JUL", "07"); MONTH_MAP.put("AUG", "08"); MONTH_MAP.put("SEP", "09");
        MONTH_MAP.put("OCT", "10"); MONTH_MAP.put("NOV", "11"); MONTH_MAP.put("DEC", "12");
    }

    public List<AirFileData> parseAllFiles() throws IOException {
        if (!Files.exists(rootPath)) Files.createDirectories(rootPath);
        return Files.list(rootPath)
                .filter(Files::isRegularFile)
                .map(this::parseSingleFile)
                .collect(Collectors.toList());
    }

    private AirFileData parseSingleFile(Path path) {
        AirFileData airData = new AirFileData();
        airData.setFileName(path.getFileName().toString());
        FareInfo fare = new FareInfo();
        fare.setTaxes(new ArrayList<>());
        Map<String, Passenger> passengerMap = new LinkedHashMap<>();

        try {
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;

                if (line.startsWith("A-")) parseAgency(line, airData);
                else if (line.startsWith("MUC1A")) parseHeaderMUC(line, airData);
                else if (line.startsWith("D-")) parseDates(line, airData);
                else if (line.startsWith("I-")) {
                    Passenger p = parsePassengerLine(line, airData);
                    passengerMap.put("P1", p);
                    airData.getPassengers().add(p);
                    airData.setPassenger(p);
                }
                else if (line.startsWith("H-")) parseFlightSegment(line, airData);
                else if (line.startsWith("U-")) parseAuxiliarySegment(line, airData);
                else if (line.startsWith("K-")) parseFare(line, fare);
                else if (line.startsWith("TAX-")) parseTaxes(line, fare);
                else if (line.startsWith("T-K") || line.startsWith("FH")) parseTicketResilient(line, airData);
                else if (line.startsWith("FP")) parseFormOfPayment(line, airData);
                else if (line.startsWith("FV")) airData.setValidatingCarrier(line.substring(2).split(";")[0]);
                else if (line.startsWith("RM")) processRemark(line, airData);
                else if (line.startsWith("SSR")) processSSR(line, airData, passengerMap);
                else if (line.startsWith("Q-")) parseFareCalculation(line, fare, airData);
            }
            airData.setFare(fare);
            airData.setRawLines(null);
        } catch (Exception e) { e.printStackTrace(); }
        return airData;
    }

    private String formatAirDate(String date) {
        if (date == null || date.length() < 5) return date;
        try {
            String day = date.substring(0, 2);
            String monthLabel = date.substring(2, 5).toUpperCase();
            String month = MONTH_MAP.getOrDefault(monthLabel, "01");

            // Caso Fecha Completa (Nacimiento): 14MAY69
            if (date.length() == 7) {
                int yearShort = Integer.parseInt(date.substring(5));
                // Lógica de siglo Amadeus: 00-30 es 2000, 31-99 es 1900
                String century = (yearShort <= 30) ? "20" : "19";
                return day + "/" + month + "/" + century + yearShort;
            }

            // Caso Fecha Corta (Segmentos): 22FEB
            return day + "/" + month;
        } catch (Exception e) {
            return date;
        }
    }

    private Passenger parsePassengerLine(String line, AirFileData data) {
        Passenger p = new Passenger();
        String[] parts = line.split(";");
        if (parts.length > 1) {
            p.setFullName(parts[1].replaceAll("^\\d+", "").trim());
        }

        if (parts.length > 3) {
            String[] segments = parts[3].split("//");
            for (String segment : segments) {
                String clean = segment.trim();
                if (clean.isEmpty()) continue;

                if (clean.contains("EJECUTIVA")) {
                    String val = clean.replaceAll("(?i).*EJECUTIVA\\s*[-\\s]*", "").trim();
                    data.getStructuredRemarks().add(new TranslatedInfo("CONTACTO_EJECUTIVA", "I-", val, "bi-person-workspace"));
                }
                else if (clean.contains("CEL CTC")) {
                    Pattern pCounter = Pattern.compile("([BMH]?\\s*\\d+)\\s+CEL CTC\\s*(?:24HRS)?\\s*(.*)", Pattern.CASE_INSENSITIVE);
                    Matcher mCounter = pCounter.matcher(clean);
                    if (mCounter.find()) {
                        data.getStructuredRemarks().add(new TranslatedInfo("COUNTER_TELEFONO", "I-", mCounter.group(1).trim(), "bi-telephone"));
                        data.getStructuredRemarks().add(new TranslatedInfo("COUNTER_NOMBRE", "I-", mCounter.group(2).trim(), "bi-headset"));
                    }
                }
                else if (clean.startsWith("A--")) {
                    data.getStructuredRemarks().add(new TranslatedInfo("AGENCIA_NOMBRE", "I-", clean.substring(3).trim(), "bi-building-check"));
                }
                else if (clean.contains("SOLIC")) {
                    Pattern pSolic = Pattern.compile("SOLIC\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
                    Matcher mSolic = pSolic.matcher(clean);
                    if (mSolic.find()) {
                        data.getStructuredRemarks().add(new TranslatedInfo("SOLICITUD_ID", "I-", mSolic.group(1), "bi-file-earmark-text"));
                    }
                    if (clean.contains("**")) {
                        String name = clean.replaceAll("\\*\\*|SOLIC\\s*\\d+", "").trim();
                        if (!name.isEmpty()) data.getStructuredRemarks().add(new TranslatedInfo("SOLICITANTE_NOMBRE", "I-", name, "bi-person-check"));
                    }
                }
                else if (clean.startsWith("E-")) {
                    String email = clean.substring(2).toLowerCase();
                    if (p.getEmail() == null && !email.contains("fcm") && !email.contains("travel")) {
                        p.setEmail(email);
                    } else {
                        data.getStructuredRemarks().add(new TranslatedInfo("CONTACTO_EMAIL_ADICIONAL", "I-", email, "bi-envelope-at"));
                    }
                }
                else if (clean.matches("^[BMH]-\\d+.*")) {
                    String tel = clean.substring(2).trim();
                    if (p.getPhone() == null) p.setPhone(tel);
                    else data.getStructuredRemarks().add(new TranslatedInfo("CONTACTO_TEL_ADICIONAL", "I-", tel, "bi-phone"));
                }
            }
        }
        return p;
    }

    private void processRemark(String line, AirFileData data) {
        String raw = line.substring(2).trim().replaceAll("^[*+/\\s]+", "");
        if (raw.isEmpty()) return;

        TranslatedInfo info = new TranslatedInfo();
        info.setRawCode("RM");
        info.setHumanValue(raw);
        info.setIcon("bi-info-circle");

        if (raw.startsWith("GENDER-")) {
            info.setCategory("PASAJERO_GENERO");
            info.setHumanValue(raw.split("-")[1]);
            info.setIcon("bi-gender-ambiguous");
        } else if (raw.startsWith("BED TYPE-")) {
            info.setCategory("HOTEL_TIPO_CAMA");
            String val = raw.split("-")[1].split(";")[0].trim();
            info.setHumanValue(translateBedType(val));
        } else if (raw.matches("(?i).*(RUC|ID|TAX|IDP).*\\d{8,}.*")) {
            info.setCategory("IDENTIFICACION_FISCAL");
            info.setIcon("bi-receipt");
        } else if (raw.startsWith("CC=") || raw.startsWith("DP=")) {
            info.setCategory("FINANZAS_CONTABILIDAD");
            info.setIcon("bi-calculator");
        } else if (raw.startsWith("QCH") || raw.startsWith("QCM") || raw.contains("BENCH")) {
            info.setCategory("COSTOS_INTERNOS_FEE");
            info.setIcon("bi-currency-dollar text-success");
        } else {
            info.setCategory("NOTA_GENERAL");
        }

        data.getStructuredRemarks().add(info);
    }

    private void parseFormOfPayment(String line, AirFileData data) {
        String raw = line.substring(2).trim();
        data.setFormOfPayment(raw);
        if (raw.startsWith("CC")) {
            String code = raw.substring(2, 4);
            String provider = CC_PROVIDERS.getOrDefault(code, "CARD");
            String number = raw.substring(4).split("/")[0];
            String mask = number.length() > 4 ? "****" + number.substring(number.length()-4) : number;
            data.getStructuredRemarks().add(new TranslatedInfo("PAGO_CREDITO", "FP", provider + " " + mask, "bi-credit-card-2-front"));
        }
    }

    private String translateBedType(String code) {
        if (code == null) return "ESTÁNDAR";
        String clean = code.toUpperCase();
        if (BED_TYPES.containsKey(clean)) return BED_TYPES.get(clean);
        if (BED_TYPES.containsKey(clean.substring(0, 1))) return BED_TYPES.get(clean.substring(0, 1));
        return code;
    }

    private String translateStatus(String code) {
        if (code == null) return "PENDIENTE";
        String clean = code.replaceAll("[0-9]", "").toUpperCase();
        for (String key : STATUS_CODES.keySet()) {
            if (clean.contains(key)) return STATUS_CODES.get(key);
        }
        return code;
    }

    private void parseFlightSegment(String line, AirFileData data) {
        try {
            String[] parts = line.split(";");
            if (parts.length < 6) return;
            FlightSegment s = new FlightSegment();
            s.setOrigin(parts[2].trim());
            s.setDestination(parts[4].trim());
            String[] infoParts = parts[5].trim().split("\\s+");
            if (infoParts.length >= 6) {
                s.setAirline(infoParts[0]);
                s.setFlightNumber(infoParts[1]);
                String depPart = infoParts[4];
                // Traducción de Fecha de Salida (Ej: 22FEB)
                s.setDepartureDate(formatAirDate(depPart.substring(0, 5)));
                s.setDepartureTime(depPart.substring(5));
                s.setArrivalTime(infoParts[5]);
            }
            data.getSegments().add(s);
        } catch (Exception e) {}
    }

    private void parseAuxiliarySegment(String line, AirFileData data) {
        try {
            String[] parts = line.split(";");
            if (line.contains("HHL") || line.contains("HTL") || line.contains("GK")) {
                HotelSegment h = new HotelSegment();

                if (line.contains("HHL")) h.setHotelType("HHL");
                else if (line.contains("HTL")) h.setHotelType("HTL");
                else if (line.contains("GK")) h.setHotelType("GK");
                else h.setHotelType("OTRO");

                h.setStatus(translateStatus(parts[1].trim()));
                // Traducción de Fechas Hotel (Ej: 22FEB)
                h.setCheckInDate(formatAirDate(extractDate(parts[2])));
                h.setCheckOutDate(formatAirDate(extractDate(parts[3])));
                h.setCityCode(parts[4].trim());
                h.setCityName(parts[5].trim());

                for (String p : parts) {
                    if (p.contains("USD")) {
                        h.setCurrency("USD");
                        h.setPricePerNight(p.replaceAll("[A-Z]", "").split("\\+")[0]);
                    }
                    if (p.startsWith("CF-")) h.setConfirmationNumber(p.substring(3));
                    if (p.startsWith("RO-")) h.setRoomType(translateBedType(p.substring(3)));
                    if (p.startsWith("BS-")) h.setHotelCode(p.substring(3));
                }

                for (String part : parts) {
                    String up = part.toUpperCase();
                    if (part.length() > 8 && (up.contains("HOTEL") || up.contains("GRAND") || up.contains("STAY") || up.contains("INN") || up.contains("WYNDHAM"))) {
                        h.setHotelName(part.trim());
                        break;
                    }
                }
                if (h.getHotelName() != null) data.getHotels().add(h);
            }
        } catch (Exception e) {}
    }

    private void parseHeaderMUC(String line, AirFileData data) {
        String[] parts = line.split(";");
        if (parts.length > 2) data.setOfficeId(parts[2]);
        Pattern p = Pattern.compile("MUC1A\\s+([A-Z0-9]{6})");
        Matcher m = p.matcher(line);
        if (m.find()) data.setPnr(m.group(1));
        if (line.contains("91843732")) data.setIataNumber("91843732");
    }

    private void parseDates(String line, AirFileData data) {
        try {
            String d = line.substring(2).split(";")[0].trim();
            if (d.length() >= 6) data.setIssuingDate(d.substring(4,6)+"/"+d.substring(2,4)+"/20"+d.substring(0,2));
        } catch (Exception e) {}
    }

    private void parseFareCalculation(String line, FareInfo fare, AirFileData data) {
        String rawCalculation = line.substring(2).trim();
        data.setFareCalculation(rawCalculation);

        // 1. Extraer NUC Total
        Pattern pNuc = Pattern.compile("NUC(\\d+\\.\\d+)");
        Matcher mNuc = pNuc.matcher(rawCalculation);
        if (mNuc.find()) fare.setBaseFare(Double.parseDouble(mNuc.group(1)));

        // 2. Extraer ROE (Rate of Exchange)
        Pattern pRoe = Pattern.compile("ROE(\\d+\\.\\d+)");
        Matcher mRoe = pRoe.matcher(rawCalculation);
        if (mRoe.find()) {
            data.getStructuredRemarks().add(new TranslatedInfo("TIPO_CAMBIO", "Q-", "ROE (Factor de Conversión): " + mRoe.group(1), "bi-currency-exchange"));
        }

        // 3. EXTRAER IMPUESTOS ADICIONALES (XT, AY, US, etc.)
        // Buscamos patrones como AY23.40 o US23.40
        Pattern pTaxes = Pattern.compile("([A-Z]{2})(\\d+\\.\\d+)");
        Matcher mTaxes = pTaxes.matcher(rawCalculation);

        while (mTaxes.find()) {
            String taxCode = mTaxes.group(1);
            String taxAmount = mTaxes.group(2);
            // Si no es NUC o ROE, es un impuesto
            if (!taxCode.equals("UC") && !taxCode.equals("OE")) {
                String taxFormatted = "Impuesto " + taxCode + ": USD " + taxAmount;
                if (!fare.getTaxes().contains(taxFormatted)) {
                    fare.getTaxes().add(taxFormatted);
                }
            }
        }

        // Agregar remarks estructurados
        data.getStructuredRemarks().add(new TranslatedInfo("TARIFA_LINEAL", "Q-",
                "CONSTRUCCIÓN: " + rawCalculation.split("END")[0], "bi-list-columns"));
    }

    private void parseFare(String line, FareInfo fare) {
        try {
            String[] parts = line.split(";");
            String totalPart = parts[parts.length - 1].trim();
            if (totalPart.contains("USD")) fare.setTotalFare(Double.parseDouble(totalPart.replaceAll("[^0-9.]", "")));
            fare.setCurrency("USD");
        } catch (Exception e) {}
    }

    private String extractDate(String text) {
        Pattern p = Pattern.compile("(\\d{2}[A-Z]{3})");
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1) : text.trim();
    }

    private void parseTaxes(String line, FareInfo fare) {
        String[] taxes = line.substring(4).split(";");
        for (String t : taxes) if (!t.trim().isEmpty()) fare.getTaxes().add(t.trim());
    }

    private void parseTicketResilient(String line, AirFileData data) {
        String clean = line.replace("T-K", "").replace("FH", "").trim();
        data.setTicketNumber(clean.split(";")[0].replace("-", ""));
    }

    private void parseAgency(String line, AirFileData data) {
        String[] parts = line.substring(2).split(";");
        if (parts.length > 0) data.setAgencyName(parts[0]);
    }

    private void processSSR(String line, AirFileData data, Map<String, Passenger> pMap) {
        String raw = line.substring(4).trim();
        Passenger p = pMap.get("P1");
        if (raw.contains("DOCS")) {
            String[] docs = raw.split("/");
            if (docs.length >= 7 && p != null) {
                p.setDocumentId(docs[3]); p.setNationality(docs[4]);
                // Traducción de Fecha de Nacimiento (Ej: 14MAY69)
                p.setBirthDate(formatAirDate(docs[5]));
                p.setGender(docs[6].startsWith("M") ? "MASCULINO" : "FEMENINO");
            }
        }
        data.getStructuredSSR().add(new TranslatedInfo("SISTEMA_SSR", "SSR", raw, "bi-info-square"));
    }

}