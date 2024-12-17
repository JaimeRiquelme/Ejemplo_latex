package com.latexprueba.latexprueba.controller;

import com.latexprueba.latexprueba.entity.DocumentData;
import com.latexprueba.latexprueba.service.LatexService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    @Autowired
    private LatexService latexService;

    @Value("${latex.output.path}")
    private String outputPath;

    @PostMapping("/latex")
    public ResponseEntity<?> createLatexDocument(@RequestBody DocumentData request) {
        try {
            DocumentData document = latexService.createLatexDocument(request);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + document.getFileName() + "\"")
                    .body(document.getPdfContent());
                    
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getDocument(@PathVariable Long id) {
        try {
            DocumentData document = latexService.getDocumentById(id);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + document.getFileName() + "\"")
                    .body(document.getPdfContent());
                    
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}