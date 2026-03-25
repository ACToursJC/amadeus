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
        airData.setOperationType("sell");
        FareInfo fare = new FareInfo();
        fare.setTaxes(new ArrayList<>());
        Map<String, Passenger> passengerMap = new LinkedHashMap<>();
        List<String> fareBasisList = new ArrayList<>();

        try {
            List<String> lines = Files.readAllLines(path);

            for (String line : lines) {
                if (line.contains("EJECUTIVA")) {
                    String cleanExec = line.split("EJECUTIVA")[1].replaceAll("^[^A-Z]+", "").replaceAll("[;]+$", "").trim();
                    airData.setJobTitle(cleanExec);
                    break;
                }
            }

            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                if (line.startsWith("MUC1A")) parseHeaderMUC(line, airData);
                else if (line.startsWith("A-")) parseAgency(line, airData);
                else if (line.startsWith("C-")) parseLineC(line, airData);
                else if (line.startsWith("D-")) parseLineD(line, airData);
                else if (line.startsWith("I-")) {
                    Passenger p = parsePassengerLine(line, airData, passengerMap);
                    if (p != null) {
                        passengerMap.put("P1", p);
                        airData.setPassenger(p);
                        airData.getPassengers().add(p);
                        airData.getStockBoleto().setDe_pasajero(p.getLastName() + " . " + p.getFirstName());
                    }
                }
                else if (line.startsWith("H-")) parseFlightSegment(line, airData);
                else if (line.startsWith("M-")) fareBasisList = parseFareBasis(line);
                else if (line.startsWith("U-")) parseAuxiliarySegment(line, airData);
                else if (line.startsWith("K-")) parseFare(line, fare, airData);
                else if (line.startsWith("TAX-")) parseTaxes(line, fare);
                else if (line.startsWith("T-K") || line.startsWith("FH")) parseTicketResilient(line, airData);
                else if (line.startsWith("FP")) parseFormOfPayment(line, airData);
                else if (line.startsWith("FM")) airData.setCommission(line.substring(2).replaceAll("[^0-9.]", ""));
                else if (line.startsWith("RM")) processRemark(line, airData);
                else if (line.startsWith("SSR")) processSSR(line, airData, passengerMap);
                else if (line.startsWith("Q-")) parseFareCalculation(line, fare, airData);
            }

            mapFareBasisToSegments(airData, fareBasisList);
            buildRouteArray(airData);
            enrichDataPostParsing(airData);
            airData.setFare(fare);
        } catch (Exception e) { e.printStackTrace(); }
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
                main.getHorasLlegada().add(s.getArrivalTime());
            }

            s.setOrigin(segDTO.getCiu_salida());
            s.setDestination(segDTO.getCiu_llegada());
            data.getSegments().add(s);
            data.getTablaSegmentos().add(segDTO);
        } catch (Exception e) {}
    }

    private void parseAuxiliarySegment(String line, AirFileData data) {
        if (!line.contains("HHL") && !line.contains("HTL") && !line.contains("GK")) return;
        try {
            String[] parts = line.split(";");
            HotelSegment h = new HotelSegment();

            String rawSt = parts[1].trim();
            Matcher mSt = Pattern.compile("([A-Z]{2})").matcher(rawSt);
            if (mSt.find()) h.setStatus(STATUS_CODES.getOrDefault(mSt.group(1), mSt.group(1)));

            h.setCheckInDate(normalizeShortDate(extractDate(parts[2]), data.getIssuingDate(), "-"));
            h.setCheckOutDate(normalizeShortDate(extractDate(parts[3]), data.getIssuingDate(), "-"));
            h.setCityCode(parts[4].trim());
            h.setCityName(parts[5].trim());

            for (String p : parts) {
                p = p.trim();
                if (p.startsWith("CF-")) h.setConfirmationNumber(p.substring(3));
                if (p.startsWith("RO-")) h.setRoomType(p.substring(3));
                if (p.startsWith("TTL-")) h.setTtlAmount(p.substring(4).replaceAll("[^0-9.]", ""));
                if (p.startsWith("COM-")) h.setHotelCommission(p.substring(4));
                if (p.startsWith("**-")) {
                    h.setHotelName(p.substring(3).trim());
                    h.setChainName(h.getHotelName().split("\\s+")[0]);
                }
                if (p.matches("\\d+\\.\\d+\\+\\d{2}[A-Z]{3}\\+\\d+")) {
                    h.getDailyRates().add(p);
                    String[] drParts = p.split("\\+");
                    HotelSegment.DailyRateDetail detail = new HotelSegment.DailyRateDetail();
                    detail.setRate(drParts[0]);
                    detail.setDate(normalizeShortDate(drParts[1], data.getIssuingDate(), "/"));
                    detail.setNights(drParts[2]);
                    h.getDailyRateDetails().add(detail);
                    if (h.getPricePerNight() == null) h.setPricePerNight(drParts[0]);
                }
                if (p.contains("USD") || p.contains("PEN")) h.setCurrency(p.replaceAll("[0-9.+]", ""));
            }
            if (h.getCheckInDate() != null && h.getCheckOutDate() != null) {
                h.setNumberOfNights((int) ChronoUnit.DAYS.between(LocalDate.parse(h.getCheckInDate()), LocalDate.parse(h.getCheckOutDate())));
            }
            data.getHotels().add(h);
        } catch (Exception e) {}
    }

    private void processRemark(String line, AirFileData data) {
        String raw = line.substring(2).trim().replaceAll("^[*+/\\s]+", "");
        Map<String, String> detail = new HashMap<>();

        // Captura de metadatos básicos
        if (raw.startsWith("GENDER-")) {
            data.setGender(raw.split("-")[1].split(";")[0].trim());
            detail.put("gender", data.getGender());
        } else if (raw.startsWith("BED TYPE-")) {
            data.setBedType(raw.split("-")[1].split(";")[0].trim());
            detail.put("bedType", data.getBedType());
        } else if (raw.startsWith("CAR SIZE-")) {
            data.setCarSize(raw.split("-")[1].split(";")[0].trim());
            detail.put("carSize", data.getCarSize());
        } else if (raw.startsWith("DP=")) {
            data.setDepartment(raw.substring(3).split(";")[0].trim());
            detail.put("department", data.getDepartment());
        } else if (raw.startsWith("CC=")) {
            data.setCostCenter(raw.substring(3).split(";")[0].trim());
            detail.put("costCenter", data.getCostCenter());
        } else if (raw.startsWith("SOLICITUD=")) {
            String sol = raw.split("=")[1].trim();
            data.getStockBoleto().setNu_file(sol);
            detail.put("solicitudId", sol);
        } else if (raw.contains("VALORFEE")) {
            String fee = raw.replaceAll("[^0-9.]", "");
            data.getStructuredRemarks().add(new TranslatedInfo("FEE_AEREO_VALOR", "RM", fee, ""));
            detail.put("valorFee", fee);
        }

        // LÓGICA EXTENDIDA PARA tablaAhorros
        // 1. UDIDs Tradicionales (S*BREAK)
        if (raw.startsWith("DP=") || raw.startsWith("CC=") || raw.startsWith("EN=")) {
            String key = raw.startsWith("DP") ? "1" : raw.startsWith("CC") ? "2" : "3";
            addAhorro(data, "S*BREAK" + key + "-" + raw.substring(raw.indexOf("=")+1), 0.0);
        }

        // 2. UDIDs de Negocio (TRE, CC2, SSA)
        if (raw.startsWith("TRE=")) {
            addAhorro(data, "FCM UDID 1-" + raw.split("=")[1].trim(), 0.0);
        } else if (raw.startsWith("CC2=")) {
            addAhorro(data, "FCM UDID 2-" + raw.split("=")[1].trim(), 0.0);
        } else if (raw.startsWith("SSA=")) {
            addAhorro(data, "FCM UDID 3-" + raw.split("=")[1].trim(), 0.0);
        }

        // 3. Benchmarks de Tarifas (QCHF, QCMK, QCLF) con montos
        if (raw.startsWith("QCHF/") || raw.startsWith("QCMK/") || raw.startsWith("QCLF/")) {
            String[] p = raw.split("/");
            if(p.length > 1) {
                addAhorro(data, p[0], Double.parseDouble(p[1].replaceAll("[^0-9.]", "")));
            }
        }

        // 4. Códigos de razón (QCRC)
        if (raw.startsWith("QCRC-")) {
            addAhorro(data, raw, 0.0);
        }

        // Remarks Estructurados
        if (raw.startsWith("APPFCM/")) {
            data.getStructuredRemarks().add(new TranslatedInfo("APPFCM_DATA", "RM", raw, ""));
            detail.put("fcmMetadata", raw);
        } else {
            detail.put("info", raw);
        }

        data.getRemarks().add(raw);
        data.getDetailRemarks().add(detail);
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
        if (raw.contains("DOCS") && pMap.containsKey("P1")) {
            String[] docs = raw.split("/");
            if (docs.length >= 7) {
                Passenger p = pMap.get("P1");
                p.setBirthDate(normalizeBirthDate(docs[5]));
                p.setGender(docs[6].equals("M") ? "MASCULINO" : "FEMENINO");
                if (data.getGender() == null) data.setGender(docs[6]);
            }
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
        String[] parts = line.split(";");
        if (parts.length > 2) data.getStockBoleto().setCo_seudo(parts[2].trim());
        Matcher m = Pattern.compile("MUC1A\\s+([A-Z0-9]{6})").matcher(line);
        if (m.find()) data.getStockBoleto().setNu_pnr(m.group(1));
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
        String tk = line.split(";")[0].replaceAll("[^0-9]", "");
        if (tk.length() > 10) tk = tk.substring(tk.length() - 10);
        data.setTicketNumber(tk);
        data.getStockBoleto().setDe_boleto(tk);
    }
    private void parseFare(String line, FareInfo fare, AirFileData data) {
        String[] parts = line.split(";");
        String totalStr = parts[parts.length - 1].trim();
        String num = totalStr.replaceAll("[^0-9.]", "");
        if (!num.isEmpty()) {
            Double mo = Double.parseDouble(num);
            fare.setTotalFare(mo);
            String curr = totalStr.replaceAll("[0-9.]", "").trim();
            fare.setCurrency(curr.length() > 2 ? curr.substring(0, 2) : curr);
            data.getStockBoleto().setMo_monto(mo);
            data.getStockBoleto().setCo_moneda_boleto(fare.getCurrency());
        }
    }
    private void parseTaxes(String line, FareInfo fare) {
        for (String t : line.substring(4).split(";")) if (!t.trim().isEmpty()) fare.getTaxes().add(t.trim());
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
    private String formatYear20(String d) { return "20" + d.substring(0, 2) + "-" + d.substring(2, 4) + "-" + d.substring(4, 6); }
    private String extractDate(String text) { Matcher m = Pattern.compile("(\\d{2}[A-Z]{3})").matcher(text); return m.find() ? m.group(1) : text.trim(); }
    private String translateStatus(String code) { return STATUS_CODES.getOrDefault(code.replaceAll("[0-9]", "").toUpperCase().trim(), code); }
}