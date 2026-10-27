package com.vectordb.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.vectordb.algo.BruteForceIndex;
import com.vectordb.algo.DistanceMetrics;
import com.vectordb.algo.DistanceMetrics.DistFn;
import com.vectordb.algo.HnswIndex;
import com.vectordb.algo.IndexedItem;
import com.vectordb.algo.KdTreeIndex;
import com.vectordb.algo.ScoredHit;
import com.vectordb.model.DemoVector;
import com.vectordb.repository.DemoVectorRepository;

import jakarta.annotation.PostConstruct;

/**
 * Owns the three demo-vector indices (brute force, KD-Tree, HNSW) and keeps
 * them in sync with the demo_vectors table in Postgres. Postgres is the
 * source of truth / durable store; the indices live in memory for fast
 * search, same as the original C++ VectorDB class.
 */
@Service
public class VectorDbService {

    private final DemoVectorRepository repository;
    private final int dims;
    private final int hnswM;
    private final int hnswEfConstruction;
    private final int hnswEfSearch;

    private BruteForceIndex bruteForce;
    private KdTreeIndex kdTree;
    private HnswIndex hnsw;

    public VectorDbService(
            DemoVectorRepository repository,
            @Value("${vectordb.demo-dims}") int dims,
            @Value("${vectordb.hnsw.m}") int hnswM,
            @Value("${vectordb.hnsw.ef-construction}") int hnswEfConstruction,
            @Value("${vectordb.hnsw.ef-search}") int hnswEfSearch) {
        this.repository = repository;
        this.dims = dims;
        this.hnswM = hnswM;
        this.hnswEfConstruction = hnswEfConstruction;
        this.hnswEfSearch = hnswEfSearch;
    }

    @PostConstruct
    public void init() {
        rebuildIndices();
        if (repository.count() == 0) {
            DemoData.seed(this);
        }
    }

    private void rebuildIndices() {
        bruteForce = new BruteForceIndex();
        kdTree = new KdTreeIndex(dims);
        hnsw = new HnswIndex(hnswM, hnswEfConstruction);
        DistFn cosine = DistanceMetrics::cosine;
        for (DemoVector v : repository.findAll()) {
            IndexedItem item = new IndexedItem(v.getId(), v.getMetadata(), v.getCategory(), v.getEmbedding());
            bruteForce.insert(item);
            kdTree.insert(item);
            hnsw.insert(item, cosine);
        }
    }

    public synchronized int insert(String metadata, String category, float[] embedding) {
        DemoVector saved = repository.save(new DemoVector(metadata, category, embedding));
        IndexedItem item = new IndexedItem(saved.getId(), metadata, category, embedding);
        bruteForce.insert(item);
        kdTree.insert(item);
        hnsw.insert(item, DistanceMetrics::cosine);
        return saved.getId();
    }

    public synchronized boolean remove(int id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        bruteForce.remove(id);
        hnsw.remove(id);
        // KD-Tree has no incremental delete in the original implementation either - rebuild it.
        kdTree = new KdTreeIndex(dims);
        for (DemoVector v : repository.findAll()) {
            kdTree.insert(new IndexedItem(v.getId(), v.getMetadata(), v.getCategory(), v.getEmbedding()));
        }
        return true;
    }

    public record SearchOutcome(List<ScoredHit> hits, long micros, String algo, String metric) {}

    public synchronized SearchOutcome search(float[] query, int k, String metric, String algo) {
        DistFn dist = DistanceMetrics.get(metric);
        long t0 = System.nanoTime();
        List<ScoredHit> raw;
        String usedAlgo = (algo == null) ? "hnsw" : algo;
        raw = switch (usedAlgo) {
            case "bruteforce" -> bruteForce.knn(query, k, dist);
            case "kdtree" -> kdTree.knn(query, k, dist);
            default -> hnsw.knn(query, k, hnswEfSearch, dist);
        };
        long micros = (System.nanoTime() - t0) / 1000;
        return new SearchOutcome(raw, micros, usedAlgo, metric == null ? "cosine" : metric);
    }

    public record BenchmarkOutcome(long bruteforceUs, long kdtreeUs, long hnswUs, int itemCount) {}

    public synchronized BenchmarkOutcome benchmark(float[] query, int k, String metric) {
        DistFn dist = DistanceMetrics.get(metric);
        long t0 = System.nanoTime();
        bruteForce.knn(query, k, dist);
        long bfUs = (System.nanoTime() - t0) / 1000;

        t0 = System.nanoTime();
        kdTree.knn(query, k, dist);
        long kdUs = (System.nanoTime() - t0) / 1000;

        t0 = System.nanoTime();
        hnsw.knn(query, k, hnswEfSearch, dist);
        long hnswUs = (System.nanoTime() - t0) / 1000;

        return new BenchmarkOutcome(bfUs, kdUs, hnswUs, (int) repository.count());
    }

    public List<DemoVector> all() {
        return new ArrayList<>(repository.findAll());
    }

    public HnswIndex.GraphInfo hnswInfo() {
        return hnsw.getInfo();
    }

    public long size() {
        return repository.count();
    }

    public int getDims() { return dims; }
}
