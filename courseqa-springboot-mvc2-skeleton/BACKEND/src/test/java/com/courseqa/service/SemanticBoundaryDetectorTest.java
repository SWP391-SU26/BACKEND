package com.courseqa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.courseqa.model.entity.EmbeddingModel;
import java.util.List;
import org.junit.jupiter.api.Test;

class SemanticBoundaryDetectorTest {
    private final EmbeddingService embeddings = mock(EmbeddingService.class);

    @Test
    void disabledDetectorNeverCallsTheEmbeddingModel() {
        SemanticBoundaryDetector detector = new SemanticBoundaryDetector(embeddings, false, 0.62, 4000);

        boolean[] boundaries = detector.detect(List.of("khoi mot", "khoi hai"));

        assertThat(boundaries).containsExactly(false, false);
        verify(embeddings, never()).embedTexts(anyList(), any());
    }

    @Test
    void marksTheBlockWhereTheTopicChanges() {
        // Blocks 0 and 1 point the same way; block 2 is orthogonal to them.
        when(embeddings.resolveModel(null)).thenReturn(new EmbeddingModel());
        when(embeddings.embedTexts(anyList(), any())).thenReturn(List.of(
                new double[] {1, 0, 0},
                new double[] {0.98, 0.02, 0},
                new double[] {0, 1, 0}));
        SemanticBoundaryDetector detector = new SemanticBoundaryDetector(embeddings, true, 0.62, 4000);

        boolean[] boundaries = detector.detect(List.of("triet hoc a", "triet hoc b", "lap trinh java"));

        assertThat(boundaries[0]).as("the first block can never be a boundary").isFalse();
        assertThat(boundaries[1]).as("similar neighbours must not split").isFalse();
        assertThat(boundaries[2]).as("an unrelated block must start a new chunk").isTrue();
    }

    @Test
    void fallsBackSilentlyWhenEmbeddingFails() {
        when(embeddings.resolveModel(null)).thenThrow(new IllegalStateException("model down"));
        SemanticBoundaryDetector detector = new SemanticBoundaryDetector(embeddings, true, 0.62, 4000);

        boolean[] boundaries = detector.detect(List.of("mot", "hai", "ba"));

        assertThat(boundaries).containsExactly(false, false, false);
    }

    @Test
    void veryLargeDocumentsSkipTheExtraEmbeddingPass() {
        SemanticBoundaryDetector detector = new SemanticBoundaryDetector(embeddings, true, 0.62, 2);

        boolean[] boundaries = detector.detect(List.of("a", "b", "c"));

        assertThat(boundaries).containsExactly(false, false, false);
        verify(embeddings, never()).embedTexts(anyList(), any());
    }
}
