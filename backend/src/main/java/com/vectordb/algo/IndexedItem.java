package com.vectordb.algo;

/** A single vector plus its id and free-text metadata, as stored in an index. */
public record IndexedItem(int id, String metadata, String category, float[] embedding) {
}
