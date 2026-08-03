package com.courseqa.service;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Counts tokens the way the embedding model actually will.
 *
 * <p>Chunk budgets used to be measured with OpenAI's CL100K encoding while the
 * embeddings are produced by BGE-M3. For Vietnamese the two disagree by roughly
 * 1.75-2.1x, so a "450 token" chunk was really only ~215 BGE-M3 tokens: chunks
 * came out half the intended size, doubling both the chunk count and embedding
 * time. When the model's own {@code tokenizer.json} is reachable this class uses
 * it directly; otherwise it falls back to CL100K scaled by a measured factor so
 * budgets stay in roughly the right units instead of silently drifting.
 */
@Slf4j
@Component
public class ChunkTokenCounter {
    private static final Encoding FALLBACK_ENCODING =
            Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
    /** Measured CL100K-to-BGE-M3 ratio on Vietnamese course material. */
    private static final double FALLBACK_SCALE = 1.0 / 1.9;

    private final HuggingFaceTokenizer tokenizer;
    private final String source;

    public ChunkTokenCounter(
            @Value("${app.chunking.tokenizer-path:}") String tokenizerPath,
            @Value("${app.chunking.tokenizer-enabled:true}") boolean enabled) {
        HuggingFaceTokenizer loaded = null;
        String loadedFrom = "cl100k-scaled";
        if (enabled) {
            Path path = resolveTokenizerPath(tokenizerPath);
            if (path != null) {
                try {
                    // Truncation must be off: the tokenizer defaults to the model's
                    // 512-token limit and would report 512 for any longer text, which
                    // silently breaks every budget comparison above that size.
                    loaded = HuggingFaceTokenizer.newInstance(path,
                            Map.of("truncation", "false", "padding", "false"));
                    loadedFrom = path.toString();
                } catch (IOException | RuntimeException exception) {
                    log.warn("Could not load tokenizer at {}; falling back to scaled CL100K counts.",
                            path, exception);
                }
            }
        }
        this.tokenizer = loaded;
        this.source = loadedFrom;
        log.info("Chunk token counting uses: {}", loadedFrom);
    }

    /** True when counts come from the embedding model's own tokenizer. */
    public boolean isExact() {
        return tokenizer != null;
    }

    public String source() {
        return source;
    }

    public int count(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        if (tokenizer != null) {
            try {
                return tokenizer.encode(text, false, false).getIds().length;
            } catch (RuntimeException exception) {
                // Fall through to the estimate rather than failing a whole upload.
                log.debug("Tokenizer failed on a segment; using the scaled estimate.", exception);
            }
        }
        return (int) Math.ceil(FALLBACK_ENCODING.countTokens(text) * FALLBACK_SCALE);
    }

    private Path resolveTokenizerPath(String configured) {
        if (configured != null && !configured.isBlank()) {
            Path explicit = Path.of(configured);
            return Files.isRegularFile(explicit) ? explicit : null;
        }
        // Default to the Hugging Face cache the Python AI service already populates.
        Path cacheRoot = Path.of("..", "..", "data", "models_cache", "hub",
                "models--BAAI--bge-m3", "snapshots").normalize();
        if (!Files.isDirectory(cacheRoot)) {
            return null;
        }
        try (var snapshots = Files.list(cacheRoot)) {
            return snapshots
                    .map(snapshot -> snapshot.resolve("tokenizer.json"))
                    .filter(Files::isRegularFile)
                    .findFirst()
                    .orElse(null);
        } catch (IOException exception) {
            return null;
        }
    }

    @PreDestroy
    void close() {
        if (tokenizer != null) {
            tokenizer.close();
        }
    }
}
