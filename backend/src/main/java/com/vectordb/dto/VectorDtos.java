package com.vectordb.dto;

import java.util.List;

public final class VectorDtos {

    private VectorDtos() {}

    public record InsertRequest(String metadata, String category, float[] embedding) {}

    public record InsertResponse(int id) {}

    public record HitDto(int id, String metadata, String category, float distance, float[] embedding) {}

    public record SearchResponse(List<HitDto> results, long latencyUs, String algo, String metric) {}

    public record ItemDto(int id, String metadata, String category, float[] embedding) {}

    public record BenchmarkResponse(long bruteforceUs, long kdtreeUs, long hnswUs, int itemCount) {}

    public record NodeDto(int id, String metadata, String category, int maxLyr) {}

    public record EdgeDto(int src, int dst, int lyr) {}

    public record HnswInfoResponse(int topLayer, int nodeCount, List<Integer> nodesPerLayer,
                                    List<Integer> edgesPerLayer, List<NodeDto> nodes, List<EdgeDto> edges) {}

    public record StatsResponse(int count, int dims, List<String> algorithms, List<String> metrics) {}

    public record DeleteResponse(boolean ok) {}
}
