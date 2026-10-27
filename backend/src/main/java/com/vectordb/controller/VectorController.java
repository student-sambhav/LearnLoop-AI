package com.vectordb.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vectordb.algo.HnswIndex;
import com.vectordb.algo.ScoredHit;
import com.vectordb.dto.VectorDtos.BenchmarkResponse;
import com.vectordb.dto.VectorDtos.DeleteResponse;
import com.vectordb.dto.VectorDtos.EdgeDto;
import com.vectordb.dto.VectorDtos.HitDto;
import com.vectordb.dto.VectorDtos.HnswInfoResponse;
import com.vectordb.dto.VectorDtos.InsertRequest;
import com.vectordb.dto.VectorDtos.InsertResponse;
import com.vectordb.dto.VectorDtos.ItemDto;
import com.vectordb.dto.VectorDtos.NodeDto;
import com.vectordb.dto.VectorDtos.SearchResponse;
import com.vectordb.dto.VectorDtos.StatsResponse;
import com.vectordb.model.DemoVector;
import com.vectordb.service.VectorDbService;
import com.vectordb.service.VectorDbService.SearchOutcome;

/** Same endpoint shapes as the original C++ server's demo-vector routes, under /api. */
@RestController
public class VectorController {

    private final VectorDbService vectorDbService;

    public VectorController(VectorDbService vectorDbService) {
        this.vectorDbService = vectorDbService;
    }

    @GetMapping("/api/search")
    public SearchResponse search(
            @RequestParam("v") String vCsv,
            @RequestParam(value = "k", defaultValue = "5") int k,
            @RequestParam(value = "metric", defaultValue = "cosine") String metric,
            @RequestParam(value = "algo", defaultValue = "hnsw") String algo) {
        float[] q = parseVec(vCsv);
        SearchOutcome outcome = vectorDbService.search(q, k, metric, algo);

        // Resolve full items for the response, same fields as the original API.
        List<DemoVector> allVectors = vectorDbService.all();
        List<HitDto> hits = new ArrayList<>();
        for (ScoredHit h : outcome.hits()) {
            allVectors.stream()
                    .filter(v -> v.getId() == h.id())
                    .findFirst()
                    .ifPresent(v -> hits.add(new HitDto(v.getId(), v.getMetadata(), v.getCategory(),
                            h.distance(), v.getEmbedding())));
        }
        return new SearchResponse(hits, outcome.micros(), outcome.algo(), outcome.metric());
    }

    @PostMapping("/api/insert")
    public InsertResponse insert(@RequestBody InsertRequest req) {
        int id = vectorDbService.insert(req.metadata(), req.category(), req.embedding());
        return new InsertResponse(id);
    }

    @DeleteMapping("/api/delete/{id}")
    public DeleteResponse delete(@PathVariable int id) {
        return new DeleteResponse(vectorDbService.remove(id));
    }

    @GetMapping("/api/items")
    public List<ItemDto> items() {
        List<ItemDto> out = new ArrayList<>();
        for (DemoVector v : vectorDbService.all()) {
            out.add(new ItemDto(v.getId(), v.getMetadata(), v.getCategory(), v.getEmbedding()));
        }
        return out;
    }

    @GetMapping("/api/benchmark")
    public BenchmarkResponse benchmark(
            @RequestParam("v") String vCsv,
            @RequestParam(value = "k", defaultValue = "5") int k,
            @RequestParam(value = "metric", defaultValue = "cosine") String metric) {
        float[] q = parseVec(vCsv);
        var b = vectorDbService.benchmark(q, k, metric);
        return new BenchmarkResponse(b.bruteforceUs(), b.kdtreeUs(), b.hnswUs(), b.itemCount());
    }

    @GetMapping("/api/hnsw-info")
    public HnswInfoResponse hnswInfo() {
        HnswIndex.GraphInfo gi = vectorDbService.hnswInfo();
        List<NodeDto> nodes = gi.nodes().stream()
                .map(n -> new NodeDto(n.id(), n.metadata(), n.category(), n.maxLayer()))
                .toList();
        List<EdgeDto> edges = gi.edges().stream()
                .map(e -> new EdgeDto(e.src(), e.dst(), e.layer()))
                .toList();
        return new HnswInfoResponse(gi.topLayer(), gi.nodeCount(), gi.nodesPerLayer(), gi.edgesPerLayer(), nodes, edges);
    }

    @GetMapping("/api/stats")
    public StatsResponse stats() {
        return new StatsResponse((int) vectorDbService.size(), vectorDbService.getDims(),
                List.of("bruteforce", "kdtree", "hnsw"), List.of("euclidean", "cosine", "manhattan"));
    }

    private static float[] parseVec(String csv) {
        if (csv == null || csv.isBlank()) return new float[0];
        String[] parts = csv.split(",");
        float[] out = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Float.parseFloat(parts[i].trim());
            } catch (NumberFormatException e) {
                out[i] = 0f;
            }
        }
        return out;
    }
}
