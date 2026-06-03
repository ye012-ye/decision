package com.ye.decision.tika.controller;

import com.ye.decision.tika.domain.ExtractResult;
import com.ye.decision.tika.service.TikaExtractorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/api/extract")
@RequiredArgsConstructor
public class ExtractionController {

    private final TikaExtractorService tes;

    @PostMapping(consumes = "multipart/form-data", produces = "application/json")
    public ResponseEntity<ExtractResult> extract(
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
        try (InputStream in = file.getInputStream()) {
            return ResponseEntity.ok(tes.extract(in, name));
        }
    }

}