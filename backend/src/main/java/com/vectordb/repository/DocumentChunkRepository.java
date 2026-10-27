package com.vectordb.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vectordb.model.DocumentChunk;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Integer> {
}
