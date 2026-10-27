package com.vectordb.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vectordb.dto.DocumentDtos.StatusResponse;
import com.vectordb.service.DocumentService;
import com.vectordb.service.OllamaClient;
import com.vectordb.service.VectorDbService;

@RestController
public class StatusController {

    private final OllamaClient ollama;
    private final VectorDbService vectorDbService;
    private final DocumentService documentService;

    public StatusController(OllamaClient ollama, VectorDbService vectorDbService, DocumentService documentService) {
        this.ollama = ollama;
        this.vectorDbService = vectorDbService;
        this.documentService = documentService;
    }

    @GetMapping("/api/status")
    public StatusResponse status() {
        return new StatusResponse(
                ollama.isAvailable(),
                ollama.getEmbedModel(),
                ollama.getGenModel(),
                (int) documentService.count(),
                documentService.getDims(),
                vectorDbService.getDims(),
                (int) vectorDbService.size());
    }
}
