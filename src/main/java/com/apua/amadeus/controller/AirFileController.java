package com.apua.amadeus.controller;

import com.apua.amadeus.model.AirFileData;
import com.apua.amadeus.service.AirParserService;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/amadeus")
@CrossOrigin(origins = "*")
public class AirFileController {

    private final AirParserService airParserService;

    public AirFileController(AirParserService airParserService) {
        this.airParserService = airParserService;
    }

    @GetMapping("/files")
    public List<AirFileData> getAllFiles() throws IOException {
        return airParserService.parseAllFiles();
    }
}