package com.courseqa.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * AIClientService - Bridge service để gọi Python AI Engine
 * Python AI Engine chạy tại port 8001
 * 
 * Timeout: 30s cho chat endpoints, 120s cho benchmark
 * Retry: 3 lần nếu connection error
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AIClientService {

    private final WebClient webClient;

    @Value("${python.ai.service.url:http://localhost:8001}")
    private String pythonAiServiceUrl;

    private static final int CHAT_TIMEOUT_SECONDS = 30;
    private static final int BENCHMARK_TIMEOUT_SECONDS = 120;
    private static final int MAX_RETRIES = 3;

    /**
     * POST /ai/chat - Gọi RAG pipeline
     * 
     * @param request ChatRequest: {question, collection_name, embedding_model, top_k, similarity_threshold, conversation_history}
     * @return ChatResponse: {rag_answer, citations, rag_score, is_out_of_scope, tokens_used, latency_ms}
     */
    public <T> T callChat(Object request, Class<T> responseType) {
        logger.info("Calling Python AI Engine /ai/chat");
        
        return webClient.post()
                .uri(pythonAiServiceUrl + "/ai/chat")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(responseType)
                .timeout(Duration.ofSeconds(CHAT_TIMEOUT_SECONDS))
                .retryWhen(Retry.backoff(MAX_RETRIES, Duration.ofMillis(200))
                        .filter(throwable -> isRetryableError(throwable))
                        .doBeforeRetry(retrySignal -> 
                            logger.warn("Retry {} /ai/chat - Error: {}", 
                                retrySignal.totalRetries() + 1, 
                                retrySignal.failure().getMessage())
                        ))
                .onErrorMap(this::handleError)
                .block();
    }

    /**
     * POST /ai/chat-finetuned - Gọi Fine-tuned pipeline
     * 
     * @param request ChatRequest: {question, conversation_history}
     * @return ChatFinetuneResponse: {finetuned_answer, finetuned_score, latency_ms}
     */
    public <T> T callChatFinetuned(Object request, Class<T> responseType) {
        logger.info("Calling Python AI Engine /ai/chat-finetuned");
        
        return webClient.post()
                .uri(pythonAiServiceUrl + "/ai/chat-finetuned")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(responseType)
                .timeout(Duration.ofSeconds(CHAT_TIMEOUT_SECONDS))
                .retryWhen(Retry.backoff(MAX_RETRIES, Duration.ofMillis(200))
                        .filter(throwable -> isRetryableError(throwable))
                        .doBeforeRetry(retrySignal ->
                            logger.warn("Retry {} /ai/chat-finetuned - Error: {}",
                                retrySignal.totalRetries() + 1,
                                retrySignal.failure().getMessage())
                        ))
                .onErrorMap(this::handleError)
                .block();
    }

    /**
     * POST /ai/evaluate - Gọi Evaluator (LLM-as-judge)
     * 
     * @param request EvaluateRequest: {question, rag_answer, finetuned_answer, rag_score, finetuned_score}
     * @return EvaluateResponse: {winner, scores, reason}
     */
    public <T> T callEvaluate(Object request, Class<T> responseType) {
        logger.info("Calling Python AI Engine /ai/evaluate");
        
        return webClient.post()
                .uri(pythonAiServiceUrl + "/ai/evaluate")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(responseType)
                .timeout(Duration.ofSeconds(CHAT_TIMEOUT_SECONDS))
                .retryWhen(Retry.backoff(MAX_RETRIES, Duration.ofMillis(200))
                        .filter(throwable -> isRetryableError(throwable))
                        .doBeforeRetry(retrySignal ->
                            logger.warn("Retry {} /ai/evaluate - Error: {}",
                                retrySignal.totalRetries() + 1,
                                retrySignal.failure().getMessage())
                        ))
                .onErrorMap(this::handleError)
                .block();
    }

    /**
     * POST /ai/benchmark - Gọi RAGAS benchmark runner
     * 
     * @param request BenchmarkRequest: {experiment_id, config, questions}
     * @return BenchmarkResponse: {experiment_id, results, summary}
     */
    public <T> T callBenchmark(Object request, Class<T> responseType) {
        logger.info("Calling Python AI Engine /ai/benchmark");
        
        return webClient.post()
                .uri(pythonAiServiceUrl + "/ai/benchmark")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(responseType)
                .timeout(Duration.ofSeconds(BENCHMARK_TIMEOUT_SECONDS))
                .retryWhen(Retry.backoff(MAX_RETRIES, Duration.ofMillis(200))
                        .filter(throwable -> isRetryableError(throwable))
                        .doBeforeRetry(retrySignal ->
                            logger.warn("Retry {} /ai/benchmark - Error: {}",
                                retrySignal.totalRetries() + 1,
                                retrySignal.failure().getMessage())
                        ))
                .onErrorMap(this::handleError)
                .block();
    }

    /**
     * Kiểm tra xem error có thể retry được không
     * Retry nếu: connection error, timeout, server error (5xx)
     * Không retry nếu: client error (4xx)
     */
    private boolean isRetryableError(Throwable throwable) {
        if (throwable instanceof WebClientResponseException) {
            WebClientResponseException ex = (WebClientResponseException) throwable;
            // Retry cho 5xx server errors
            return ex.getStatusCode().is5xxServerError();
        }
        
        // Retry cho connection/timeout errors
        return throwable instanceof java.net.ConnectException ||
               throwable instanceof java.net.SocketTimeoutException ||
               throwable instanceof io.netty.channel.ConnectTimeoutException ||
               throwable instanceof io.netty.channel.AbstractChannel.AnnotatedConnectException;
    }

    /**
     * Xử lý error và convert thành meaningful exception
     */
    private Throwable handleError(Throwable throwable) {
        if (throwable instanceof WebClientResponseException) {
            WebClientResponseException ex = (WebClientResponseException) throwable;
            String errorMessage = String.format(
                "Python AI Engine error - Status: %d, Body: %s",
                ex.getStatusCode().value(),
                ex.getResponseBodyAsString()
            );
            logger.error(errorMessage);
            return new RuntimeException(errorMessage, ex);
        } else if (throwable instanceof java.util.concurrent.TimeoutException) {
            String errorMessage = "Python AI Engine timeout - took longer than expected";
            logger.error(errorMessage);
            return new RuntimeException(errorMessage, throwable);
        } else {
            logger.error("Python AI Engine connection error: {}", throwable.getMessage(), throwable);
            return new RuntimeException("Failed to connect to Python AI Engine: " + throwable.getMessage(), throwable);
        }
    }
}
