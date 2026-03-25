package com.apua.amadeus.service;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleXlsxReportConfiguration;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Service
public class JasperReportService {

    // Exporta a Excel (XLSX)
    public byte[] exportToExcel(List<?> data, String reportName, Map<String, Object> parameters) throws Exception {
        JasperPrint jasperPrint = getJasperPrint(data, reportName, parameters);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JRXlsxExporter exporter = new JRXlsxExporter();
        exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));

        SimpleXlsxReportConfiguration configuration = new SimpleXlsxReportConfiguration();
        configuration.setDetectCellType(true);
        configuration.setRemoveEmptySpaceBetweenRows(true);
        configuration.setWhitePageBackground(false);
        exporter.setConfiguration(configuration);

        exporter.exportReport();
        return out.toByteArray();
    }

    //Exporta a PDF
    public byte[] exportToPdf(List<?> data, String reportName, Map<String, Object> parameters) throws Exception {
        JasperPrint jasperPrint = getJasperPrint(data, reportName, parameters);
        return JasperExportManager.exportReportToPdf(jasperPrint);
    }

    //Cargar, compilar y llenar el reporte
    private JasperPrint getJasperPrint(List<?> data, String reportName, Map<String, Object> parameters) throws Exception {
        InputStream reportStream = getClass().getResourceAsStream("/reports/" + reportName + ".jrxml");
        if (reportStream == null) throw new RuntimeException("No se encontró el reporte: " + reportName);

        JasperReport jasperReport = JasperCompileManager.compileReport(reportStream);

        JRDataSource dataSource;
        if (!data.isEmpty() && data.get(0) instanceof Map) {
            dataSource = new JRMapCollectionDataSource((List<Map<String, ?>>) data);
        } else {
            dataSource = new JRBeanCollectionDataSource(data);
        }

        return JasperFillManager.fillReport(jasperReport, parameters, dataSource);
    }
}