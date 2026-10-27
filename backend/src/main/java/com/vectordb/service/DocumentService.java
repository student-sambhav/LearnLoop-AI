package com.vectordb.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.vectordb.algo.BruteForceIndex;
import com.vectordb.algo.DistanceMetrics;
import com.vectordb.algo.HnswIndex;
import com.vectordb.algo.IndexedItem;
import com.vectordb.algo.ScoredHit;
import com.vectordb.model.DocumentChunk;
import com.vectordb.repository.DocumentChunkRepository;

import jakarta.annotation.PostConstruct;

/**
 * Handles document ingestion (chunk -> embed -> store) and semantic
 * retrieval over real Ollama embeddings (768D for nomic-embed-text).
 * Mirrors the original C++ DocumentDB: HNSW for larger sets, brute force
 * fallback for small ones (search quality parity, not worth the graph
 * overhead below ~10 chunks).
 */
@Service
public class DocumentService {

    private static final int CHUNK_WORDS = 250;
    private static final int OVERLAP_WORDS = 30;
    private static final float MAX_DISTANCE = 0.7f;

    private final DocumentChunkRepository repository;
    private final OllamaClient ollama;

    private HnswIndex hnsw;
    private BruteForceIndex bruteForce;
    private int dims = 0;

    public DocumentService(DocumentChunkRepository repository, OllamaClient ollama) {
        this.repository = repository;
        this.ollama = ollama;
    }

    @PostConstruct
    public void init() {
        hnsw = new HnswIndex(16, 200);
        bruteForce = new BruteForceIndex();
        for (DocumentChunk c : repository.findAll()) {
            float[] emb = c.getEmbedding();
            if (dims == 0) dims = emb.length;
            IndexedItem item = new IndexedItem(c.getId(), c.getTitle(), "doc", emb);
            hnsw.insert(item, DistanceMetrics::cosine);
            bruteForce.insert(item);
        }
    }

    public record InsertOutcome(List<Integer> ids, int chunkCount, int dims, boolean ollamaUnavailable) {}

    public synchronized InsertOutcome insertDocument(String title, String text) {
        List<String> chunks = chunkText(text, CHUNK_WORDS, OVERLAP_WORDS);
        List<Integer> ids = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            float[] emb = ollama.embed(chunks.get(i));
            if (emb.length == 0) {
                return new InsertOutcome(List.of(), 0, dims, true);
            }
            String chunkTitle = chunks.size() > 1
                    ? title + " [" + (i + 1) + "/" + chunks.size() + "]"
                    : title;
            DocumentChunk saved = repository.save(new DocumentChunk(chunkTitle, chunks.get(i), emb));
            if (dims == 0) dims = emb.length;
            IndexedItem item = new IndexedItem(saved.getId(), chunkTitle, "doc", emb);
            hnsw.insert(item, DistanceMetrics::cosine);
            bruteForce.insert(item);
            ids.add(saved.getId());
        }
        return new InsertOutcome(ids, chunks.size(), dims, false);
    }

    public synchronized boolean removeChunk(int id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        hnsw.remove(id);
        bruteForce.remove(id);
        return true;
    }

    public List<DocumentChunk> all() {
        return new ArrayList<>(repository.findAll());
    }

    public synchronized List<ScoredHit> search(float[] queryEmbedding, int k) {
        List<ScoredHit> raw = (repository.count() < 10)
                ? bruteForce.knn(queryEmbedding, k, DistanceMetrics::cosine)
                : hnsw.knn(queryEmbedding, k, 50, DistanceMetrics::cosine);
        List<ScoredHit> filtered = new ArrayList<>();
        for (ScoredHit h : raw) {
            if (h.distance() <= MAX_DISTANCE) filtered.add(h);
        }
        return filtered;
    }

    public long count() { return repository.count(); }

    public int getDims() { return dims; }

    /** 250-word chunks with 30-word overlap, ported from the original chunkText(). */
    static List<String> chunkText(String text, int chunkWords, int overlapWords) {
        String[] words = text.trim().isEmpty() ? new String[0] : text.trim().split("\\s+");
        if (words.length == 0) return List.of();
        if (words.length <= chunkWords) return List.of(text);

        List<String> chunks = new ArrayList<>();
        int step = chunkWords - overlapWords;
        for (int i = 0; i < words.length; i += step) {
            int end = Math.min(i + chunkWords, words.length);
            StringBuilder chunk = new StringBuilder();
            for (int j = i; j < end; j++) {
                if (j > i) chunk.append(' ');
                chunk.append(words[j]);
            }
            chunks.add(chunk.toString());
            if (end == words.length) break;
        }
        return chunks;
    }
}
