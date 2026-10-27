package com.vectordb.algo;

/** A search result: an item id paired with its distance from the query. Comparable by distance. */
public record ScoredHit(float distance, int id) implements Comparable<ScoredHit> {
    @Override
    public int compareTo(ScoredHit o) {
        return Float.compare(this.distance, o.distance);
    }
}
