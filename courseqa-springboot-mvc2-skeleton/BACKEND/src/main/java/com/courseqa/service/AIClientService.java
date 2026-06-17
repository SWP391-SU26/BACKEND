package com.courseqa.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;

@Service
public class AIClientService {

    private static final Logger log = LoggerFactory.getLogger(AIClientService.class);

    private final WebClient webClient;

    @Value("${python.ai.service.url:http://localhost:8001}")
    private String pythonAiServiceUrl;

    private static final int CHAT_TIMEOUT_SECONDS = 30;
    private static final int BENCHMARK_TIMEOUT_SECONDS = 120;
    private static final int MAX_RETRIES = 3;

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
                .timeout(Duration.ofSeconds(CHAT_TIMEOUT_SECONDS))
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

    public <T> T callChatFinetuned(Object request, Class<T> responseType) {
        log.info("Calling Python AI Engine /ai/chat-finetuned");

        return webClient.post()
                .uri(pythonAiServiceUrl + "/ai/chat-finetuned")  // ⚠️ not implemented in Python yet
                .bodyValue(request)
                .retrieve()
                .bodyToMono(responseType)
                .timeout(Duration.ofSeconds(CHAT_TIMEOUT_SECONDS))
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
                .timeout(Duration.ofSeconds(CHAT_TIMEOUT_SECONDS))
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
                .timeout(Duration.ofSeconds(BENCHMARK_TIMEOUT_SECONDS))
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

    private boolean isRetryableError(Throwable throwable) {
        if (throwable instanceof WebClientResponseException) {
            WebClientResponseException ex = (WebClientResponseException) throwable;
            return ex.getStatusCode().is5xxServerError();
        }
        return throwable instanceof java.net.ConnectException ||
               throwable instanceof java.net.SocketTimeoutException;
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