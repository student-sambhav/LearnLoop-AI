package com.vectordb.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * One chunk of a user-submitted document, with its real Ollama-generated
 * embedding (768D for nomic-embed-text). Indexed by HnswIndex for RAG
 * retrieval; stored here in Postgres for durability and listing.
 */
@Entity
@Table(name = "document_chunks")
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String title;

    @Lob
    @Column(nullable = false)
    private String text;

    @Lob
    @Column(name = "embedding_csv", nullable = false)
    private String embeddingCsv;

    protected DocumentChunk() {}

    public DocumentChunk(String title, String text, float[] embedding) {
        this.title = title;
        this.text = text;
        this.embeddingCsv = DemoVector.toCsv(embedding);
    }

    public Integer getId() { return id; }
    public String getTitle() { return title; }
    public String getText() { return text; }
    public float[] getEmbedding() { return DemoVector.fromCsv(embeddingCsv); }
}
