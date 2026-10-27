# VectorDB — Spring Boot + Postgres + React

A full-stack reimplementation of the original C++ vector database project, in
your stack: **Java 17 / Spring Boot 3** backend, **PostgreSQL** for durable
storage, and a **React (Vite)** frontend. It builds Brute Force, KD-Tree, and
HNSW similarity search *from scratch* in plain Java (no pgvector, no vector
search library) — the point of the original project was to learn how these
algorithms work internally, and that's preserved here.

It also includes a small RAG (Retrieval-Augmented Generation) pipeline over a
local [Ollama](https://ollama.com) instance: paste in a document, it gets
chunked and embedded, and you can ask questions answered from your own text.

## What's inside

```
backend/    Spring Boot API — algorithms, JPA entities, REST controllers
frontend/   React app — search UI, PCA scatter plot, document upload, RAG chat
docker-compose.yml   One-command Postgres for local dev
```

### Backend — `com.vectordb`

- `algo/` — **the core of the project**: `BruteForceIndex`, `KdTreeIndex`,
  and `HnswIndex`, each a plain-Java, dependency-free implementation, ported
  line-for-line in structure from the original C++ (`searchLayer`, layer
  selection, neighbor pruning — all of it). `DistanceMetrics` provides
  cosine / euclidean / manhattan.
- `model/` — JPA entities. `DemoVector` (16D hand-crafted demo vectors) and
  `DocumentChunk` (real Ollama embeddings from uploaded documents). Embeddings
  are stored as CSV text columns rather than a Postgres array or pgvector
  type — search happens in the Java algorithm layer, not in SQL, so Postgres
  is used purely for durable storage.
- `service/` — `VectorDbService` keeps the three in-memory indices in sync
  with Postgres; `DocumentService` handles chunking (250 words, 30-word
  overlap) + embedding; `OllamaClient` wraps the local Ollama REST API;
  `RagService` orchestrates the embed → retrieve → generate pipeline.
- `controller/` — REST endpoints under `/api`, same shapes as the original
  server (`/api/search`, `/api/insert`, `/api/benchmark`, `/api/hnsw-info`,
  `/api/doc/insert`, `/api/doc/ask`, `/api/status`, etc).

### Frontend — Vite + React

Three tabs, same as the original single-page UI:

- **Search** — type a concept ("binary tree", "sushi", "basketball"), pick an
  algorithm and distance metric, see results plus a live PCA-projected
  scatter plot of the 16D demo vectors. "Compare all algos" runs a benchmark
  across all three.
- **Documents** — paste text, it's chunked and embedded via Ollama, stored,
  and listed.
- **Ask AI** — a small chat UI that runs the full RAG pipeline against your
  uploaded documents.

The demo tab's text→vector mapping is a simple keyword classifier (ported
from the original), not a real embedding model — that's intentional, it lets
you explore algorithm behavior without needing Ollama running. Real
embeddings (via Ollama's `nomic-embed-text`) are used for the Documents/Ask
AI tabs.

## Running it

### 1. Postgres

```bash
docker compose up -d
```

This starts Postgres on `localhost:5432` with database `vectordb`,
user/password `postgres`/`postgres` (matching `application.yml` — change
both if you deploy this anywhere real).

### 2. Ollama (optional, only needed for Documents / Ask AI tabs)

```bash
# https://ollama.com
ollama pull nomic-embed-text
ollama pull llama3.2
ollama serve
```

The Search tab and benchmark work fully without Ollama running.

### 3. Backend

```bash
cd backend
mvn spring-boot:run
```

Runs on `http://localhost:8080`. On first startup it seeds the 20 demo
vectors into Postgres (only if the table is empty) and builds all three
in-memory indices from whatever's already stored — so restarts don't
re-seed or lose data.

### 4. Frontend

```bash
cd frontend
npm install
npm run dev
```

Runs on `http://localhost:5173` and proxies `/api/*` to the backend
(configured in `vite.config.js`) — no CORS setup needed in dev. `CorsConfig`
on the backend also allows any origin, for convenience if you run the
frontend elsewhere.

## Design notes / things worth knowing

- **KD-Tree has no incremental delete** in the original either — removing a
  demo vector rebuilds the KD-Tree from what's left in Postgres. Fine at
  demo scale; would need a proper deletion strategy at larger scale.
- **HNSW parameters** (`M=16`, `efConstruction=200`, `efSearch=50`) match the
  original and are configurable in `application.yml` under `vectordb.hnsw`.
- **Storage tradeoff**: embeddings are CSV text, not a native array/vector
  column — simplest possible JPA mapping, dependency-free. Fine for a
  learning-scale project; if you scale to real production data volumes,
  either move filtering/pre-selection into SQL or switch to `pgvector` for
  the storage layer (keeping your own HNSW for the parts you want to keep
  understanding/controlling).
- **`nomic-embed-text` produces 768D vectors** — the document index's
  dimensionality is inferred from the first embedding it sees, same as the
  original.
