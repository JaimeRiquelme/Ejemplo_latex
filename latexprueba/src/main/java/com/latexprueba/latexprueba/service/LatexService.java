package com.latexprueba.latexprueba.service;

import com.latexprueba.latexprueba.entity.DocumentData;
import com.latexprueba.latexprueba.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class LatexService {
    private static final Logger log = LoggerFactory.getLogger(LatexService.class);
    private final DocumentRepository documentRepository;
    private final String outputPath;
    private final String templatePath;

    public LatexService(DocumentRepository documentRepository,
                       @Value("${latex.output.path}") String outputPath,
                       @Value("${latex.template.path}") String templatePath) {
        this.documentRepository = documentRepository;
        this.outputPath = outputPath.trim().replace("\\", "/");
        this.templatePath = templatePath;
    }

    public DocumentData createLatexDocument(DocumentData request) throws IOException {
        String content = populateTemplate(Files.readString(Paths.get(templatePath)), request);
        request.setContent(content);

        Path tempDir = Files.createDirectories(Paths.get(outputPath, "temp_" + System.currentTimeMillis()));
        try {
            Path pdfPath = generatePDF(content, tempDir);
            request.setFileName(saveAndStorePDF(pdfPath, request));
            return documentRepository.save(request);
        } finally {
            FileUtils.deleteDirectory(tempDir.toFile());
        }
    }

    private Path generatePDF(String content, Path tempDir) throws IOException {
        Path texFile = tempDir.resolve("document.tex");
        Files.writeString(texFile, content);

        ProcessBuilder pb = new ProcessBuilder("pdflatex", "-interaction=nonstopmode",
                "-output-directory=" + tempDir, texFile.toString())
                .redirectErrorStream(true);

        for (int i = 0; i < 2; i++) {
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                reader.lines().forEach(line -> log.debug("pdflatex: {}", line));
                if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
                    throw new RuntimeException("Error en PDF generación");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Proceso interrumpido", e);
            }
        }

        Path pdfFile = tempDir.resolve("document.pdf");
        if (!Files.exists(pdfFile)) throw new RuntimeException("PDF no generado");
        return pdfFile;
    }

    private String saveAndStorePDF(Path pdfFile, DocumentData request) throws IOException {
        String filename = request.getNombreProyecto().replaceAll("\\s+", "_") + ".pdf";
        request.setPdfContent(Files.readAllBytes(pdfFile));
        Files.copy(pdfFile, Paths.get(outputPath, filename), StandardCopyOption.REPLACE_EXISTING);
        return filename;
    }

    private String populateTemplate(String template, DocumentData data) {
        String tableContent = data.getRolesResponsabilidades() == null ? "" : 
            "\\begin{center}\n\\begin{longtable}{|p{2cm}|p{4cm}|p{4cm}|p{4cm}|}\n\\hline\n" +
            "\\textbf{ID} & \\textbf{Rol} & \\textbf{Función} & \\textbf{Responsabilidad} \\\\\n\\hline\\endhead\n" +
            String.join("\n", data.getRolesResponsabilidades().split("&")).lines()
                .map(row -> row.trim().isEmpty() ? "" : 
                    String.join(" & ", row.split(",")) + " \\\\\n\\hline")
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.joining("\n")) +
            "\\end{longtable}\n\\end{center}\n";

        return Map.of(
            "{{nombreProyecto}}", data.getNombreProyecto(),
            "{{idProyecto}}", data.getIdProyecto(),
            "{{fechaElaboracion}}", data.getFechaElaboracion(),
            "{{empresaNombre}}", data.getEmpresaNombre(),
            "{{clienteNombre}}", data.getClienteNombre(),
            "{{patrocinador}}", data.getPatrocinador(),
            "{{director}}", data.getDirector(),
            "{{tablaRoles}}", tableContent
        ).entrySet().stream()
            .reduce(template,
                (acc, e) -> acc.replace(e.getKey(), e.getValue()),
                (s1, s2) -> s1);
    }

    public DocumentData getDocumentById(Long id) {
        return documentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Documento no encontrado con ID: " + id));
    }
}
