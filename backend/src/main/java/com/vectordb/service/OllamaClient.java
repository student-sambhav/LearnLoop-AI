package com.vectordb.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Thin wrapper around a locally-running Ollama server's REST API.
 * Mirrors the original C++ OllamaClient: embeddings via nomic-embed-text,
 * generation via llama3.2.
 */
@Service
public class OllamaClient {

    private final String embedModel;
    private final String genModel;
    private final RestClient restClient;

    public OllamaClient(
            @Value("${ollama.host}") String host,
            @Value("${ollama.embed-model}") String embedModel,
            @Value("${ollama.gen-model}") String genModel,
            @Value("${ollama.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${ollama.read-timeout-ms}") int readTimeoutMs) {
        this.embedModel = embedModel;
        this.genModel = genModel;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(host)
                .requestFactory(factory)
                .build();
    }

    public String getEmbedModel() { return embedModel; }
    public String getGenModel() { return genModel; }

    public boolean isAvailable() {
        try {
            restClient.get().uri("/api/tags").retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            return false;
        }
    }

    /** Returns an empty array if Ollama is unreachable or the model isn't pulled. */
    @SuppressWarnings("unchecked")
    public float[] embed(String text) {
        try {
            Map<String, Object> body = Map.of("model", embedModel, "prompt", text);
            Map<String, Object> resp = restClient.post()
                    .uri("/api/embeddings")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (resp == null || !resp.containsKey("embedding")) return new float[0];
            List<Double> raw = (List<Double>) resp.get("embedding");
            float[] out = new float[raw.size()];
            for (int i = 0; i < raw.size(); i++) out[i] = raw.get(i).floatValue();
            return out;
        } catch (RestClientException e) {
            return new float[0];
        }
    }

    /** Returns a human-readable error message if Ollama is unavailable, otherwise the generated text. */
    @SuppressWarnings("unchecked")
    public String generate(String prompt) {
        try {
            Map<String, Object> body = Map.of(
                    "model", genModel,
                    "prompt", prompt,
                    "stream", false);
            Map<String, Object> resp = restClient.post()
                    .uri("/api/generate")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (resp == null) return "ERROR: Ollama unavailable. Run: ollama serve";
            Object response = resp.get("response");
            return response == null ? "" : response.toString();
        } catch (RestClientException e) {
            return "ERROR: Ollama unavailable. Run: ollama serve";
        }
    }
}
