package com.latexprueba.latexprueba.repository;

import com.latexprueba.latexprueba.entity.DocumentData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentData, Long> {
}