package com.vectordb.algo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.PriorityQueue;

import com.vectordb.algo.DistanceMetrics.DistFn;

/**
 * Binary space partitioning tree. Splits on one dimension at a time (cycling
 * through all dims), pruning subtrees that can't beat the current worst
 * candidate. Exact results, but degrades toward brute force at high
 * dimensionality — kept here (as in the original) for side-by-side comparison
 * against HNSW.
 */
public class KdTreeIndex {

    private static final class Node {
        final IndexedItem item;
        Node left;
        Node right;
        Node(IndexedItem item) { this.item = item; }
    }

    private final int dims;
    private Node root;

    public KdTreeIndex(int dims) {
        this.dims = dims;
    }

    public void insert(IndexedItem item) {
        root = insert(root, item, 0);
    }

    private Node insert(Node n, IndexedItem item, int depth) {
        if (n == null) return new Node(item);
        int axis = depth % dims;
        if (item.embedding()[axis] < n.item.embedding()[axis]) {
            n.left = insert(n.left, item, depth + 1);
        } else {
            n.right = insert(n.right, item, depth + 1);
        }
        return n;
    }

    public List<ScoredHit> knn(float[] query, int k, DistFn dist) {
        // Max-heap on distance so we can cheaply evict the worst candidate.
        PriorityQueue<ScoredHit> heap = new PriorityQueue<>(
                (a, b) -> Float.compare(b.distance(), a.distance()));
        knn(root, query, k, 0, dist, heap);
        List<ScoredHit> result = new ArrayList<>(heap);
        result.sort(null);
        return result;
    }

    private void knn(Node n, float[] query, int k, int depth, DistFn dist,
                      PriorityQueue<ScoredHit> heap) {
        if (n == null) return;

        float dn = dist.apply(query, n.item.embedding());
        if (heap.size() < k || dn < heap.peek().distance()) {
            heap.offer(new ScoredHit(dn, n.item.id()));
            if (heap.size() > k) heap.poll();
        }

        int axis = depth % dims;
        float diff = query[axis] - n.item.embedding()[axis];
        Node closer = diff < 0 ? n.left : n.right;
        Node farther = diff < 0 ? n.right : n.left;

        knn(closer, query, k, depth + 1, dist, heap);
        // Only descend into the far side if it could still contain something closer
        // than our current worst candidate — this is the pruning step.
        if (heap.size() < k || Math.abs(diff) < heap.peek().distance()) {
            knn(farther, query, k, depth + 1, dist, heap);
        }
    }

    public void rebuild(Collection<IndexedItem> items) {
        root = null;
        for (IndexedItem item : items) insert(item);
    }
}
