package com.courseqa.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class EvaluationAsyncConfigTest {
    @Test
    void flow5UsesTwoOrchestrationWorkersWhilePythonSerializesGpuInference() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new EvaluationAsyncConfig()
                .evaluationTaskExecutor();
        try {
            assertEquals(2, executor.getCorePoolSize());
            assertEquals(2, executor.getMaxPoolSize());
        } finally {
            executor.shutdown();
        }
    }
}
