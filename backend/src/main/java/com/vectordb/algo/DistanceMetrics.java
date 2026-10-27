package com.vectordb.algo;

import java.util.function.BiFunction;

/**
 * The three distance/similarity metrics the original C++ engine supports.
 * All are expressed as "smaller = more similar" so every index (brute force,
 * KD-Tree, HNSW) can share the same top-k selection logic.
 */
public final class DistanceMetrics {

    private DistanceMetrics() {}

    public static float euclidean(float[] a, float[] b) {
        float s = 0f;
        for (int i = 0; i < a.length; i++) {
            float d = a[i] - b[i];
            s += d * d;
        }
        return (float) Math.sqrt(s);
    }

    public static float cosine(float[] a, float[] b) {
        float dot = 0f, na = 0f, nb = 0f;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na < 1e-9f || nb < 1e-9f) return 1.0f;
        return 1.0f - dot / (float) (Math.sqrt(na) * Math.sqrt(nb));
    }

    public static float manhattan(float[] a, float[] b) {
        float s = 0f;
        for (int i = 0; i < a.length; i++) {
            s += Math.abs(a[i] - b[i]);
        }
        return s;
    }

    /** Distance function type shared by all three index implementations. */
    public interface DistFn extends BiFunction<float[], float[], Float> {}

    public static DistFn get(String metric) {
        if (metric == null) return DistanceMetrics::cosine;
        return switch (metric.toLowerCase()) {
            case "cosine" -> DistanceMetrics::cosine;
            case "manhattan" -> DistanceMetrics::manhattan;
            default -> DistanceMetrics::euclidean;
        };
    }
}
