package com.latexprueba.latexprueba.service;

// Importaciones necesarias para el funcionamiento del servicio
import com.latexprueba.latexprueba.entity.DocumentData;
import com.latexprueba.latexprueba.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Service // Indica que esta clase es un servicio de Spring
public class LatexService {
    // Logger para registrar eventos y errores
    private static final Logger logger = LoggerFactory.getLogger(LatexService.class);

    // Inyección de dependencias necesarias
    @Autowired
    private DocumentRepository documentRepository; // Repositorio para operaciones con la base de datos

    @Autowired
    private ResourceLoader resourceLoader; // Cargador de recursos de Spring

    // Configuración de rutas desde application.properties
    @Value("${latex.output.path}")
    private String outputPath; // Ruta donde se guardarán los PDFs generados

    @Value("${latex.template.path}")
    private String templatePath; // Ruta donde se encuentra la plantilla LaTeX

    public DocumentData createLatexDocument(DocumentData request) throws IOException {
        logger.info("Iniciando proceso de creación de documento LaTeX");
        
        String content = populateTemplate(
            new String(Files.readAllBytes(Paths.get(templatePath)), StandardCharsets.UTF_8),
            request
        );
        request.setContent(content);

        Path tempDirPath = createTempDirectory();
        try {
            Path pdfPath = generatePDF(content, tempDirPath);
            String finalPdfName = saveAndStorePDF(pdfPath, request);
            
            request.setFileName(finalPdfName);
            return documentRepository.save(request);
        } finally {
            FileUtils.deleteDirectory(tempDirPath.toFile());
        }
    }

    private Path createTempDirectory() throws IOException {
        String cleanOutputPath = outputPath.trim().replace("\\", "/");
        Files.createDirectories(Paths.get(cleanOutputPath));
        return Files.createDirectories(Paths.get(cleanOutputPath, "temp_" + System.currentTimeMillis()));
    }

    private Path generatePDF(String content, Path tempDirPath) throws IOException {
        Path texFile = tempDirPath.resolve("document.tex");
        Files.write(texFile, content.getBytes(StandardCharsets.UTF_8));

        for (int i = 0; i < 2; i++) {
            int exitCode = runPdfLatex(texFile, tempDirPath);
            if (exitCode != 0 && i == 1) {
                throw new RuntimeException("Error en la generación del PDF. Código de salida: " + exitCode);
            }
        }

        Path pdfFile = tempDirPath.resolve("document.pdf");
        if (!Files.exists(pdfFile)) {
            throw new RuntimeException("PDF no fue generado");
        }
        return pdfFile;
    }

    private int runPdfLatex(Path texFile, Path tempDirPath) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(
            "pdflatex",
            "-interaction=nonstopmode",
            "-output-directory=" + tempDirPath.toString(),
            texFile.toString()
        );
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            reader.lines().forEach(line -> logger.debug("pdflatex output: {}", line));
            return process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Proceso interrumpido", e);
        }
    }

    private String saveAndStorePDF(Path pdfFile, DocumentData request) throws IOException {
        String finalPdfName = request.getNombreProyecto().replaceAll("\\s+", "_") + ".pdf";
        Path destinationFile = Paths.get(outputPath.trim().replace("\\", "/"), finalPdfName);
        
        // Leer los bytes del PDF y guardarlos en la entidad
        byte[] pdfContent = Files.readAllBytes(pdfFile);
        request.setPdfContent(pdfContent);
        
        // Guardar también una copia en el sistema de archivos
        Files.copy(pdfFile, destinationFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return finalPdfName;
    }

    private String populateTemplate(String template, DocumentData data) {
        return Map.of(
            "{{nombreProyecto}}", data.getNombreProyecto(),
            "{{idProyecto}}", data.getIdProyecto(),
            "{{fechaElaboracion}}", data.getFechaElaboracion(),
            "{{empresaNombre}}", data.getEmpresaNombre(),
            "{{clienteNombre}}", data.getClienteNombre(),
            "{{patrocinador}}", data.getPatrocinador(),
            "{{director}}", data.getDirector()
        ).entrySet().stream()
        .reduce(template,
            (acc, entry) -> acc.replace(entry.getKey(), entry.getValue()),
            (s1, s2) -> s1);
    }

    public DocumentData getDocumentById(Long id) {
        return documentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Documento no encontrado con ID: " + id));
    }
}
