package com.vectordb.algo;

import java.util.ArrayList;
import java.util.List;

import com.vectordb.algo.DistanceMetrics.DistFn;

/**
 * O(N*d) exact nearest-neighbor search. The baseline every other index is
 * measured against.
 */
public class BruteForceIndex {

    private final List<IndexedItem> items = new ArrayList<>();

    public void insert(IndexedItem item) {
        items.add(item);
    }

    public void remove(int id) {
        items.removeIf(v -> v.id() == id);
    }

    public List<ScoredHit> knn(float[] query, int k, DistFn dist) {
        List<ScoredHit> results = new ArrayList<>(items.size());
        for (IndexedItem item : items) {
            results.add(new ScoredHit(dist.apply(query, item.embedding()), item.id()));
        }
        results.sort(null);
        if (results.size() > k) {
            return new ArrayList<>(results.subList(0, k));
        }
        return results;
    }

    public int size() {
        return items.size();
    }
}
