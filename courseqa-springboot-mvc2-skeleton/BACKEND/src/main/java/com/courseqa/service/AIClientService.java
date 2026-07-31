package com.courseqa.service;

import com.courseqa.model.dto.PythonAiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;

@Service
public class AIClientService {

    private static final Logger log = LoggerFactory.getLogger(AIClientService.class);

    private final WebClient webClient;

    @Value("${python.ai.service.url:http://localhost:8001}")
    private String pythonAiServiceUrl;

    private static final int MAX_RETRIES = 3;
    private static final int MODEL_WARMUP_TIMEOUT_SECONDS = 180;

    @Value("${python.ai.service.benchmark-timeout-seconds:1800}")
    private int benchmarkTimeoutSeconds;

    @Value("${python.ai.service.finetuned-timeout-seconds:180}")
    private int finetunedTimeoutSeconds;

    @Value("${python.ai.service.chat-timeout-seconds:112}")
    private int chatTimeoutSeconds;

    @Value("${python.ai.service.embedding-timeout-seconds:600}")
    private int embeddingTimeoutSeconds;

    public AIClientService(WebClient webClient) {
        this.webClient = webClient;
    }

    public <T> T callChat(Object request, Class<T> responseType) {
        log.info("Calling Python AI Engine /api/chat");

        return webClient.post()
                .uri(pythonAiServiceUrl + "/api/chat")  // ✅ fixed
                .bodyValue(request)
                .retrieve()
                .bodyToMono(responseType)
                .timeout(Duration.ofSeconds(chatTimeoutSeconds))
                .retryWhen(Retry.backoff(MAX_RETRIES, Duration.ofMillis(200))
                        .filter(throwable -> isRetryableError(throwable))
                        .doBeforeRetry(retrySignal ->
                            log.warn("Retry {} /api/chat - Error: {}",
                                retrySignal.totalRetries() + 1,
                                retrySignal.failure().getMessage())
                        ))
                .onErrorMap(this::handleError)
                .block();
    }

    public <T> T callGenerate(Object request, Class<T> responseType) {
        log.info("Calling Python AI Engine /api/generate");

        return webClient.post()
                .uri(pythonAiServiceUrl + "/api/generate")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(responseType)
                .timeout(Duration.ofSeconds(chatTimeoutSeconds))
                .onErrorMap(this::handleError)
                .block();
    }

    public PythonAiDto.RewriteQueryResponse callRewriteQuery(PythonAiDto.RewriteQueryRequest request) {
        log.info("Calling Python AI Engine /api/rewrite-query (attempt {})", request.attempt);
        return webClient.post()
                .uri(pythonAiServiceUrl + "/api/rewrite-query")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(PythonAiDto.RewriteQueryResponse.class)
                .timeout(Duration.ofSeconds(chatTimeoutSeconds))
                .retryWhen(Retry.max(1).filter(this::isConnectionFailure))
                .onErrorMap(this::handleError)
                .block();
    }

    public PythonAiDto.EmbedResponse callEmbed(PythonAiDto.EmbedRequest request) {
        log.info("Calling Python AI Engine /api/embed for {} texts",
                request.texts == null ? 0 : request.texts.size());
        return webClient.post()
                .uri(pythonAiServiceUrl + "/api/embed")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(PythonAiDto.EmbedResponse.class)
                .timeout(Duration.ofSeconds(Math.max(MODEL_WARMUP_TIMEOUT_SECONDS, embeddingTimeoutSeconds)))
                .retryWhen(Retry.max(1).filter(this::isConnectionFailure))
                .onErrorMap(this::handleError)
                .block();
    }

    public <T> T callChatFinetuned(Object request, Class<T> responseType) {
        log.info("Calling Python AI Engine /ai/chat-finetuned");

        return webClient.post()
                .uri(pythonAiServiceUrl + "/ai/chat-finetuned")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(responseType)
                .timeout(Duration.ofSeconds(finetunedTimeoutSeconds))
                .retryWhen(Retry.backoff(MAX_RETRIES, Duration.ofMillis(200))
                        .filter(throwable -> isRetryableError(throwable))
                        .doBeforeRetry(retrySignal ->
                            log.warn("Retry {} /ai/chat-finetuned - Error: {}",
                                retrySignal.totalRetries() + 1,
                                retrySignal.failure().getMessage())
                        ))
                .onErrorMap(this::handleError)
                .block();
    }

    public <T> T callEvaluate(Object request, Class<T> responseType) {
        log.info("Calling Python AI Engine /ai/evaluate");

        return webClient.post()
                .uri(pythonAiServiceUrl + "/ai/evaluate")  // ⚠️ not implemented in Python yet
                .bodyValue(request)
                .retrieve()
                .bodyToMono(responseType)
                .timeout(Duration.ofSeconds(chatTimeoutSeconds))
                .retryWhen(Retry.backoff(MAX_RETRIES, Duration.ofMillis(200))
                        .filter(throwable -> isRetryableError(throwable))
                        .doBeforeRetry(retrySignal ->
                            log.warn("Retry {} /ai/evaluate - Error: {}",
                                retrySignal.totalRetries() + 1,
                                retrySignal.failure().getMessage())
                        ))
                .onErrorMap(this::handleError)
                .block();
    }

    public <T> T callBenchmark(Object request, Class<T> responseType) {
        log.info("Calling Python AI Engine /api/benchmarks/run");

        return webClient.post()
                .uri(pythonAiServiceUrl + "/api/benchmarks/run")  // ✅ fixed
                .bodyValue(request)
                .retrieve()
                .bodyToMono(responseType)
                .timeout(Duration.ofSeconds(benchmarkTimeoutSeconds))
                .retryWhen(Retry.backoff(MAX_RETRIES, Duration.ofMillis(200))
                        .filter(throwable -> isRetryableError(throwable))
                        .doBeforeRetry(retrySignal ->
                            log.warn("Retry {} /api/benchmarks/run - Error: {}",
                                retrySignal.totalRetries() + 1,
                                retrySignal.failure().getMessage())
                        ))
                .onErrorMap(this::handleError)
                .block();
    }

    public PythonAiDto.GenerateBatchResponse callGenerateBatch(PythonAiDto.GenerateBatchRequest request) {
        return callBenchmarkBatch("/api/generate-batch", request, PythonAiDto.GenerateBatchResponse.class);
    }

    public PythonAiDto.ChatFinetunedBatchResponse callChatFinetunedBatch(
            PythonAiDto.ChatFinetunedBatchRequest request) {
        return callBenchmarkBatch("/ai/chat-finetuned-batch", request,
                PythonAiDto.ChatFinetunedBatchResponse.class);
    }

    public PythonAiDto.OfficialRagasBatchResponse callOfficialRagasBatch(
            PythonAiDto.OfficialRagasBatchRequest request) {
        return callBenchmarkBatch("/api/evaluation/ragas/batch", request,
                PythonAiDto.OfficialRagasBatchResponse.class);
    }

    private <T> T callBenchmarkBatch(String path, Object request, Class<T> responseType) {
        log.info("Calling Python AI Engine {}", path);
        return webClient.post()
                .uri(pythonAiServiceUrl + path)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(responseType)
                .timeout(Duration.ofSeconds(benchmarkTimeoutSeconds))
                .retryWhen(Retry.max(1)
                        .filter(this::isConnectionFailure)
                        .doBeforeRetry(signal -> log.warn("Retrying {} after connection failure: {}",
                                path, signal.failure().getMessage())))
                .onErrorMap(this::handleError)
                .block();
    }

    public Map<String, Object> getModelStatus() {
        log.info("Calling Python AI Engine /api/model/status");
        return webClient.get()
                .uri(pythonAiServiceUrl + "/api/model/status")
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() { })
                .timeout(Duration.ofSeconds(MODEL_WARMUP_TIMEOUT_SECONDS))
                .onErrorMap(this::handleError)
                .block();
    }

    private boolean isRetryableError(Throwable throwable) {
        if (throwable instanceof WebClientResponseException) {
            WebClientResponseException ex = (WebClientResponseException) throwable;
            return ex.getStatusCode().is5xxServerError();
        }
        return throwable instanceof java.net.ConnectException ||
               throwable instanceof java.net.SocketTimeoutException;
    }

    private boolean isConnectionFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof java.net.ConnectException) return true;
            if (current instanceof WebClientRequestException
                    && current.getCause() instanceof java.net.ConnectException) return true;
            current = current.getCause();
        }
        return false;
    }

    private Throwable handleError(Throwable throwable) {
        if (throwable instanceof WebClientResponseException) {
            WebClientResponseException ex = (WebClientResponseException) throwable;
            String errorMessage = String.format(
                "Python AI Engine error - Status: %d, Body: %s",
                ex.getStatusCode().value(),
                ex.getResponseBodyAsString()
            );
            log.error(errorMessage);
            return new RuntimeException(errorMessage, ex);
        } else if (throwable instanceof java.util.concurrent.TimeoutException) {
            String errorMessage = "Python AI Engine timeout - took longer than expected";
            log.error(errorMessage);
            return new RuntimeException(errorMessage, throwable);
        } else {
            log.error("Python AI Engine connection error: {}", throwable.getMessage(), throwable);
            return new RuntimeException("Failed to connect to Python AI Engine: " + throwable.getMessage(), throwable);
        }
    }
}
