package com.courseqa.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmbeddingVectorCacheTest {
    @Test
    void storesByModelAndChunkAndSupportsInvalidation() {
        EmbeddingVectorCache cache = new EmbeddingVectorCache();
        UUID modelId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();
        double[] vector = {0.1, 0.2, 0.3};

        cache.put(modelId, chunkId, vector);

        assertArrayEquals(vector, cache.get(modelId, chunkId));
        cache.invalidateChunks(List.of(chunkId));
        assertNull(cache.get(modelId, chunkId));
    }
}
