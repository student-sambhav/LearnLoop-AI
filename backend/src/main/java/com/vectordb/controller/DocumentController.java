package com.vectordb.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.vectordb.dto.DocumentDtos.AskContextDto;
import com.vectordb.dto.DocumentDtos.AskRequest;
import com.vectordb.dto.DocumentDtos.AskResponse;
import com.vectordb.dto.DocumentDtos.ContextDto;
import com.vectordb.dto.DocumentDtos.DeleteResponse;
import com.vectordb.dto.DocumentDtos.DocInsertRequest;
import com.vectordb.dto.DocumentDtos.DocInsertResponse;
import com.vectordb.dto.DocumentDtos.DocListItem;
import com.vectordb.dto.DocumentDtos.DocSearchRequest;
import com.vectordb.dto.DocumentDtos.DocSearchResponse;
import com.vectordb.model.DocumentChunk;
import com.vectordb.service.DocumentService;
import com.vectordb.service.RagService;

@RestController
public class DocumentController {

    private final DocumentService documentService;
    private final RagService ragService;

    public DocumentController(DocumentService documentService, RagService ragService) {
        this.documentService = documentService;
        this.ragService = ragService;
    }

    @PostMapping("/api/doc/insert")
    public ResponseEntity<?> insert(@RequestBody DocInsertRequest req) {
        if (req.title() == null || req.title().isBlank() || req.text() == null || req.text().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "need title and text"));
        }
        DocumentService.InsertOutcome outcome = documentService.insertDocument(req.title(), req.text());
        if (outcome.ollamaUnavailable()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", "Ollama unavailable. Install from https://ollama.com then run: "
                            + "ollama pull nomic-embed-text && ollama pull llama3.2"));
        }
        return ResponseEntity.ok(new DocInsertResponse(outcome.ids(), outcome.chunkCount(), outcome.dims()));
    }

    @DeleteMapping("/api/doc/delete/{id}")
    public DeleteResponse delete(@PathVariable int id) {
        return new DeleteResponse(documentService.removeChunk(id));
    }

    @GetMapping("/api/doc/list")
    public List<DocListItem> list() {
        return documentService.all().stream()
                .map(c -> {
                    String preview = c.getText().length() > 120
                            ? c.getText().substring(0, 120) + "…"
                            : c.getText();
                    int words = c.getText().isBlank() ? 0 : c.getText().trim().split("\\s+").length;
                    return new DocListItem(c.getId(), c.getTitle(), preview, words);
                })
                .toList();
    }

    @PostMapping("/api/doc/search")
    public ResponseEntity<?> search(@RequestBody DocSearchRequest req) {
        if (req.question() == null || req.question().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "need question"));
        }
        int k = req.k() == null ? 3 : req.k();
        RagService.RetrieveOutcome outcome = ragService.retrieve(req.question(), k);
        if (outcome.ollamaUnavailable()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "Ollama unavailable"));
        }
        List<ContextDto> contexts = outcome.contexts().stream()
                .map(c -> new ContextDto(c.id(), c.title(), c.distance()))
                .toList();
        return ResponseEntity.ok(new DocSearchResponse(contexts));
    }

    @PostMapping("/api/doc/ask")
    public ResponseEntity<?> ask(@RequestBody AskRequest req) {
        if (req.question() == null || req.question().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "need question"));
        }
        int k = req.k() == null ? 3 : req.k();
        RagService.AskOutcome outcome = ragService.ask(req.question(), k);
        if (outcome.ollamaUnavailable()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "Ollama unavailable"));
        }
        List<AskContextDto> contexts = outcome.contexts().stream()
                .map(c -> new AskContextDto(c.id(), c.title(), c.text(), c.distance()))
                .toList();
        return ResponseEntity.ok(new AskResponse(outcome.answer(), outcome.model(), contexts, outcome.docCount()));
    }
}
