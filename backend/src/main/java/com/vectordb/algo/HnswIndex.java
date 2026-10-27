package com.vectordb.algo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

import com.vectordb.algo.DistanceMetrics.DistFn;

/**
 * Hierarchical Navigable Small World graph — the same algorithm used in
 * production vector databases (Pinecone, Weaviate, Chroma, Milvus).
 *
 * Nodes are inserted into a multilayer graph; each node is randomly assigned
 * a max layer (fewer nodes survive at higher layers, each with longer-range
 * links). Search greedily descends from the top layer to find a good entry
 * point, then does a beam search at layer 0 to find the true nearest
 * neighbors. This gives O(log N) search instead of brute force's O(N).
 */
public class HnswIndex {

    private static final class Node {
        final IndexedItem item;
        final int maxLayer;
        final List<List<Integer>> neighbors; // neighbors[layer] = list of neighbor ids

        Node(IndexedItem item, int maxLayer) {
            this.item = item;
            this.maxLayer = maxLayer;
            this.neighbors = new ArrayList<>();
            for (int i = 0; i <= maxLayer; i++) neighbors.add(new ArrayList<>());
        }
    }

    private final Map<Integer, Node> graph = new HashMap<>();
    private final int m;
    private final int m0;
    private final int efConstruction;
    private final double mL;
    private final Random rng;

    private int topLayer = -1;
    private int entryPoint = -1;

    public HnswIndex(int m, int efConstruction) {
        this(m, efConstruction, 42L);
    }

    public HnswIndex(int m, int efConstruction, long seed) {
        this.m = m;
        this.m0 = 2 * m;
        this.efConstruction = efConstruction;
        this.mL = 1.0 / Math.log(m);
        this.rng = new Random(seed);
    }

    private int randomLevel() {
        double u = rng.nextDouble();
        // avoid log(0)
        if (u <= 0) u = 1e-12;
        return (int) Math.floor(-Math.log(u) * mL);
    }

    public synchronized void insert(IndexedItem item, DistFn dist) {
        int id = item.id();
        int level = randomLevel();
        graph.put(id, new Node(item, level));

        if (entryPoint == -1) {
            entryPoint = id;
            topLayer = level;
            return;
        }

        int ep = entryPoint;
        // Descend greedily from the top down to just above our own level.
        for (int lc = topLayer; lc > level; lc--) {
            List<ScoredHit> w = searchLayer(item.embedding(), ep, 1, lc, dist);
            if (!w.isEmpty()) ep = w.get(0).id();
        }

        // From min(topLayer, level) down to 0, connect to nearest neighbors.
        for (int lc = Math.min(topLayer, level); lc >= 0; lc--) {
            List<ScoredHit> w = searchLayer(item.embedding(), ep, efConstruction, lc, dist);
            int maxM = (lc == 0) ? m0 : m;
            List<Integer> selected = selectNeighbors(w, maxM);
            graph.get(id).neighbors.set(lc, selected);

            for (int neighborId : selected) {
                Node neighbor = graph.get(neighborId);
                if (neighbor == null) continue;
                while (neighbor.neighbors.size() <= lc) neighbor.neighbors.add(new ArrayList<>());
                List<Integer> conn = neighbor.neighbors.get(lc);
                conn.add(id);
                if (conn.size() > maxM) {
                    List<ScoredHit> ranked = new ArrayList<>();
                    for (int c : conn) {
                        Node cn = graph.get(c);
                        if (cn != null) {
                            ranked.add(new ScoredHit(dist.apply(neighbor.item.embedding(), cn.item.embedding()), c));
                        }
                    }
                    ranked.sort(null);
                    conn.clear();
                    for (int i = 0; i < Math.min(maxM, ranked.size()); i++) {
                        conn.add(ranked.get(i).id());
                    }
                }
            }
            if (!w.isEmpty()) ep = w.get(0).id();
        }

        if (level > topLayer) {
            topLayer = level;
            entryPoint = id;
        }
    }

    /** Greedy beam search within a single layer. Mirrors the original C++ searchLayer. */
    private List<ScoredHit> searchLayer(float[] query, int entryId, int ef, int layer, DistFn dist) {
        Set<Integer> visited = new HashSet<>();
        // min-heap: closest candidates to explore next
        PriorityQueue<ScoredHit> candidates = new PriorityQueue<>();
        // max-heap: best `ef` found so far (worst on top so we can evict)
        PriorityQueue<ScoredHit> found = new PriorityQueue<>(Comparator.reverseOrder());

        Node entryNode = graph.get(entryId);
        if (entryNode == null) return new ArrayList<>();

        float d0 = dist.apply(query, entryNode.item.embedding());
        visited.add(entryId);
        candidates.offer(new ScoredHit(d0, entryId));
        found.offer(new ScoredHit(d0, entryId));

        while (!candidates.isEmpty()) {
            ScoredHit current = candidates.poll();
            if (found.size() >= ef && current.distance() > found.peek().distance()) break;

            Node currentNode = graph.get(current.id());
            if (currentNode == null || layer >= currentNode.neighbors.size()) continue;

            for (int neighborId : currentNode.neighbors.get(layer)) {
                if (visited.contains(neighborId) || !graph.containsKey(neighborId)) continue;
                visited.add(neighborId);
                Node neighborNode = graph.get(neighborId);
                float nd = dist.apply(query, neighborNode.item.embedding());
                if (found.size() < ef || nd < found.peek().distance()) {
                    candidates.offer(new ScoredHit(nd, neighborId));
                    found.offer(new ScoredHit(nd, neighborId));
                    if (found.size() > ef) found.poll();
                }
            }
        }

        List<ScoredHit> result = new ArrayList<>(found);
        result.sort(null);
        return result;
    }

    private List<Integer> selectNeighbors(List<ScoredHit> candidates, int maxM) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < Math.min(candidates.size(), maxM); i++) {
            result.add(candidates.get(i).id());
        }
        return result;
    }

    public synchronized List<ScoredHit> knn(float[] query, int k, int ef, DistFn dist) {
        if (entryPoint == -1) return new ArrayList<>();
        int ep = entryPoint;
        for (int lc = topLayer; lc > 0; lc--) {
            List<ScoredHit> w = searchLayer(query, ep, 1, lc, dist);
            if (!w.isEmpty()) ep = w.get(0).id();
        }
        List<ScoredHit> w = searchLayer(query, ep, Math.max(ef, k), 0, dist);
        if (w.size() > k) return new ArrayList<>(w.subList(0, k));
        return w;
    }

    public synchronized void remove(int id) {
        if (!graph.containsKey(id)) return;
        for (Node node : graph.values()) {
            for (List<Integer> layer : node.neighbors) {
                layer.remove(Integer.valueOf(id));
            }
        }
        if (entryPoint == id) {
            entryPoint = -1;
            for (Integer otherId : graph.keySet()) {
                if (otherId != id) { entryPoint = otherId; break; }
            }
        }
        graph.remove(id);
    }

    public synchronized int size() {
        return graph.size();
    }

    // ---- Graph introspection, mirrors the original /hnsw-info endpoint ----

    public record NodeView(int id, String metadata, String category, int maxLayer) {}
    public record EdgeView(int src, int dst, int layer) {}
    public record GraphInfo(int topLayer, int nodeCount, List<Integer> nodesPerLayer,
                             List<Integer> edgesPerLayer, List<NodeView> nodes, List<EdgeView> edges) {}

    public synchronized GraphInfo getInfo() {
        int maxL = Math.max(topLayer + 1, 1);
        List<Integer> nodesPerLayer = new ArrayList<>();
        List<Integer> edgesPerLayer = new ArrayList<>();
        for (int i = 0; i < maxL; i++) { nodesPerLayer.add(0); edgesPerLayer.add(0); }

        List<NodeView> nodes = new ArrayList<>();
        List<EdgeView> edges = new ArrayList<>();

        for (Map.Entry<Integer, Node> e : graph.entrySet()) {
            int id = e.getKey();
            Node node = e.getValue();
            nodes.add(new NodeView(id, node.item.metadata(), node.item.category(), node.maxLayer));
            for (int lc = 0; lc <= node.maxLayer && lc < maxL; lc++) {
                nodesPerLayer.set(lc, nodesPerLayer.get(lc) + 1);
                if (lc < node.neighbors.size()) {
                    for (int nid : node.neighbors.get(lc)) {
                        if (id < nid) {
                            edgesPerLayer.set(lc, edgesPerLayer.get(lc) + 1);
                            edges.add(new EdgeView(id, nid, lc));
                        }
                    }
                }
            }
        }
        return new GraphInfo(topLayer, graph.size(), nodesPerLayer, edgesPerLayer, nodes, edges);
    }
}
