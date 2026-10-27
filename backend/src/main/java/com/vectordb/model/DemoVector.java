package com.vectordb.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * A 16-dimensional demo vector (CS / Math / Food / Sports categories).
 *
 * The embedding is stored as a comma-separated TEXT column rather than a
 * Postgres array type or pgvector column - that keeps the entity mapping
 * dependency-free (plain Spring Data JPA), since the actual similarity
 * search runs in the Java algorithm layer (BruteForceIndex / KdTreeIndex /
 * HnswIndex), not in SQL. Postgres is used purely for durable storage here.
 */
@Entity
@Table(name = "demo_vectors")
public class DemoVector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String metadata;

    @Column(nullable = false)
    private String category;

    @Lob
    @Column(name = "embedding_csv", nullable = false)
    private String embeddingCsv;

    protected DemoVector() {}

    public DemoVector(String metadata, String category, float[] embedding) {
        this.metadata = metadata;
        this.category = category;
        this.embeddingCsv = toCsv(embedding);
    }

    public static String toCsv(float[] embedding) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(embedding[i]);
        }
        return sb.toString();
    }

    public static float[] fromCsv(String csv) {
        String[] parts = csv.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) result[i] = Float.parseFloat(parts[i]);
        return result;
    }

    public Integer getId() { return id; }
    public String getMetadata() { return metadata; }
    public String getCategory() { return category; }
    public String getEmbeddingCsv() { return embeddingCsv; }
    public float[] getEmbedding() { return fromCsv(embeddingCsv); }
}
