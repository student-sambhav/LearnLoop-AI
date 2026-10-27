package com.vectordb.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.vectordb.algo.ScoredHit;
import com.vectordb.model.DocumentChunk;
import com.vectordb.repository.DocumentChunkRepository;

/**
 * The RAG pipeline: embed the question, retrieve the top-k relevant chunks
 * via HNSW, stuff them into a prompt, and ask the local LLM to answer.
 */
@Service
public class RagService {

    private final OllamaClient ollama;
    private final DocumentService documentService;
    private final DocumentChunkRepository chunkRepository;

    public RagService(OllamaClient ollama, DocumentService documentService,
                       DocumentChunkRepository chunkRepository) {
        this.ollama = ollama;
        this.documentService = documentService;
        this.chunkRepository = chunkRepository;
    }

    public record RetrievedContext(int id, String title, String text, float distance) {}

    public record AskOutcome(boolean ollamaUnavailable, String answer, String model,
                              List<RetrievedContext> contexts, int docCount) {}

    public AskOutcome ask(String question, int k) {
        float[] qEmbedding = ollama.embed(question);
        if (qEmbedding.length == 0) {
            return new AskOutcome(true, null, null, List.of(), (int) documentService.count());
        }

        List<ScoredHit> hits = documentService.search(qEmbedding, k);
        Map<Integer, DocumentChunk> chunksById = new java.util.HashMap<>();
        for (ScoredHit h : hits) {
            chunkRepository.findById(h.id()).ifPresent(c -> chunksById.put(h.id(), c));
        }

        List<RetrievedContext> contexts = new ArrayList<>();
        StringBuilder ctxBlock = new StringBuilder();
        int idx = 1;
        for (ScoredHit h : hits) {
            DocumentChunk c = chunksById.get(h.id());
            if (c == null) continue;
            contexts.add(new RetrievedContext(c.getId(), c.getTitle(), c.getText(), h.distance()));
            ctxBlock.append('[').append(idx++).append("] ").append(c.getTitle()).append(":\n")
                    .append(c.getText()).append("\n\n");
        }

        String prompt = "You are a helpful assistant. Answer the user's question directly. "
                + "Use the provided context if it contains relevant information. "
                + "If it doesn't, just use your own general knowledge. "
                + "IMPORTANT: Do NOT mention the 'context', 'provided text', or say things like "
                + "'the context doesn't mention'. Just answer the question naturally.\n\n"
                + "Context:\n" + ctxBlock
                + "Question: " + question + "\n\n"
                + "Answer:";

        String answer = ollama.generate(prompt);
        return new AskOutcome(false, answer, ollama.getGenModel(), contexts, (int) documentService.count());
    }

    public record RetrieveOutcome(boolean ollamaUnavailable, List<RetrievedContext> contexts) {}

    public RetrieveOutcome retrieve(String question, int k) {
        float[] qEmbedding = ollama.embed(question);
        if (qEmbedding.length == 0) {
            return new RetrieveOutcome(true, List.of());
        }
        List<ScoredHit> hits = documentService.search(qEmbedding, k);
        List<RetrievedContext> contexts = new ArrayList<>();
        for (ScoredHit h : hits) {
            chunkRepository.findById(h.id()).ifPresent(c ->
                    contexts.add(new RetrievedContext(c.getId(), c.getTitle(), null, h.distance())));
        }
        return new RetrieveOutcome(false, contexts);
    }
}
