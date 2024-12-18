package com.latexprueba.latexprueba.entity;

import lombok.Data;
import jakarta.persistence.*;

@Data
@Entity
public class DocumentData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombreProyecto;
    private String idProyecto;
    private String fechaElaboracion;
    private String empresaNombre;
    private String clienteNombre;
    private String patrocinador;
    private String director;

    @Lob
    private String content;

    private String fileName;

    @Lob
    @Column(length = 1000000)
    private byte[] pdfContent;

    private String rolesResponsabilidades;

    public String getRolesResponsabilidades() {
        return rolesResponsabilidades;
    }

    public void setRolesResponsabilidades(String rolesResponsabilidades) {
        this.rolesResponsabilidades = rolesResponsabilidades;
    }
}