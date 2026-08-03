package com.courseqa.service;

import com.courseqa.model.entity.EmbeddingModel;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Finds where a document changes subject, so chunks can end on a topic shift
 * instead of only when the token budget runs out.
 *
 * <p>Each block is embedded once and compared with its neighbour; a similarity
 * dip below the configured threshold marks a boundary. This is genuinely more
 * expensive than structural chunking — it embeds every block on top of the
 * embeddings the chunks themselves need — so it stays opt-in and the structural
 * rules remain the default. When it is disabled, or embeddings are unavailable,
 * callers fall back to size-based splitting alone.
 */
@Slf4j
@Component
public class SemanticBoundaryDetector {
    private final EmbeddingService embeddingService;
    private final boolean enabled;
    private final double threshold;
    private final int maxBlocks;

    public SemanticBoundaryDetector(
            EmbeddingService embeddingService,
            @Value("${app.chunking.semantic-enabled:false}") boolean enabled,
            @Value("${app.chunking.semantic-threshold:0.62}") double threshold,
            @Value("${app.chunking.semantic-max-blocks:4000}") int maxBlocks) {
        this.embeddingService = embeddingService;
        this.enabled = enabled;
        this.threshold = threshold;
        this.maxBlocks = maxBlocks;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Threshold rendered for the chunk strategy identifier, e.g. "062". */
    public String thresholdLabel() {
        return String.valueOf(Math.round(threshold * 100));
    }

    /**
     * Marks, for each block, whether a new topic starts there. Index 0 is always
     * false. Returns all-false when disabled or when embeddings cannot be produced,
     * so the caller simply keeps its size-based behaviour.
     */
    public boolean[] detect(List<String> blocks) {
        boolean[] boundaries = new boolean[blocks == null ? 0 : blocks.size()];
        if (!enabled || blocks == null || blocks.size() < 2) {
            return boundaries;
        }
        if (blocks.size() > maxBlocks) {
            log.info("Skipping semantic boundaries: {} blocks exceeds the {} limit.",
                    blocks.size(), maxBlocks);
            return boundaries;
        }

        List<double[]> vectors;
        try {
            EmbeddingModel model = embeddingService.resolveModel(null);
            vectors = embeddingService.embedTexts(blocks, model);
        } catch (RuntimeException exception) {
            log.warn("Semantic boundary detection unavailable; using structural splits only.", exception);
            return boundaries;
        }
        if (vectors == null || vectors.size() != blocks.size()) {
            return boundaries;
        }

        for (int i = 1; i < vectors.size(); i++) {
            if (cosine(vectors.get(i - 1), vectors.get(i)) < threshold) {
                boundaries[i] = true;
            }
        }
        return boundaries;
    }

    static double cosine(double[] left, double[] right) {
        if (left == null || right == null || left.length == 0 || left.length != right.length) {
            return 1.0;
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 1.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    /** Convenience for callers that hold segments rather than raw strings. */
    static List<String> textsOf(List<String> blocks) {
        return blocks == null ? new ArrayList<>() : blocks;
    }
}
