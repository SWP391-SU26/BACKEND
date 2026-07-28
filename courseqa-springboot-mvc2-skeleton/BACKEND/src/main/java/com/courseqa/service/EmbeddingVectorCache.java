package com.courseqa.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingVectorCache {
    private static final int MAX_ENTRIES = 10_000;

    private final Map<VectorKey, double[]> vectors = new ConcurrentHashMap<>();

    public double[] get(UUID modelId, UUID chunkId) {
        return vectors.get(new VectorKey(modelId, chunkId));
    }

    public void put(UUID modelId, UUID chunkId, double[] vector) {
        if (modelId == null || chunkId == null || vector == null || vector.length == 0) {
            return;
        }
        if (vectors.size() >= MAX_ENTRIES) {
            vectors.clear();
        }
        vectors.put(new VectorKey(modelId, chunkId), vector);
    }

    public void invalidateChunks(Collection<UUID> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return;
        }
        vectors.keySet().removeIf(key -> chunkIds.contains(key.chunkId()));
    }

    int size() {
        return vectors.size();
    }

    private record VectorKey(UUID modelId, UUID chunkId) { }
}
