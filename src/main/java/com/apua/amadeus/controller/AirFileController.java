package com.apua.amadeus.controller;

import com.apua.amadeus.dto.StockBoletoAhorroDTO;
import com.apua.amadeus.dto.StockBoletoSegmentoDTO;
import com.apua.amadeus.model.AirFileData;
import com.apua.amadeus.service.AirImportService;
import com.apua.amadeus.service.AirParserService;
import com.apua.amadeus.service.JasperReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/amadeus")
@CrossOrigin(origins = "*")
public class AirFileController {

    private final AirParserService airParserService;
    private final AirImportService airImportService;
    private final JasperReportService jasperReportService;

    public AirFileController(AirParserService airParserService,
                             AirImportService airImportService,
                             JasperReportService jasperReportService) {
        this.airParserService = airParserService;
        this.airImportService = airImportService;
        this.jasperReportService = jasperReportService;
    }

    @GetMapping("/files")
    public List<AirFileData> getAllFiles() throws IOException {
        return airParserService.parseAllFiles();
    }

    @GetMapping("/files/import")
    public ResponseEntity<String> importToDatabase() {
        try {
            airImportService.processAndSaveAllFiles();
            return ResponseEntity.ok("Importación a SQL Server completada con éxito.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error en la importación: " + e.getMessage());
        }
    }

    // 1. Exportar Itinerario y Vuelos (Arreglo Stock Boleto)
    @PostMapping("/files/export/vuelos")
    public ResponseEntity<byte[]> exportVuelos(@RequestBody List<Map<String, Object>> data) {
        try {
            // LOG para saber si la petición entró
            System.out.println("Iniciando exportación de vuelos. Registros: " + data.size());

            byte[] excelContent = jasperReportService.exportToExcel(data, "rpt_vuelos", null);
            return downloadExcel(excelContent, "itinerario_vuelos.xlsx");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    // 2. Exportar Arreglo Detalle de Segmentos
    @PostMapping("/files/export/segmentos")
    public ResponseEntity<byte[]> exportSegmentos(@RequestBody List<StockBoletoSegmentoDTO> data) {
        try {
            byte[] excelContent = jasperReportService.exportToExcel(data, "rpt_segmentos", null);
            return downloadExcel(excelContent, "detalle_segmentos.xlsx");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // 3. Exportar Arreglo Tabla Ahorros
    @PostMapping("/files/export/ahorros")
    public ResponseEntity<byte[]> exportAhorros(@RequestBody List<StockBoletoAhorroDTO> data) {
        try {
            byte[] excelContent = jasperReportService.exportToExcel(data, "rpt_ahorros", null);
            return downloadExcel(excelContent, "tabla_ahorros.xlsx");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // Uutilitario para configurar las cabeceras de descarga
    private ResponseEntity<byte[]> downloadExcel(byte[] content, String fileName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", fileName);
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
        return ResponseEntity.ok().headers(headers).body(content);
    }

    // Exportar Vuelos a PDF
    @PostMapping("/files/export/vuelos/pdf")
    public ResponseEntity<byte[]> exportVuelosPdf(@RequestBody List<Map<String, Object>> data) {
        try {
            byte[] content = jasperReportService.exportToPdf(data, "rpt_vuelos", null);
            return downloadFile(content, "itinerario.pdf", MediaType.APPLICATION_PDF);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    // Para descargas
    private ResponseEntity<byte[]> downloadFile(byte[] content, String fileName, MediaType mediaType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentDispositionFormData("attachment", fileName);
        return ResponseEntity.ok().headers(headers).body(content);
    }
}