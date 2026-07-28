package com.courseqa.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

class AppConfigTest {

    @Test
    void webClientAcceptsAiResponsesLargerThanTheDefault256KbBuffer() {
        String payload = "x".repeat(600_000);
        DisposableServer server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(routes -> routes.get("/large",
                        (request, response) -> response.sendString(Mono.just(payload))))
                .bindNow();
        try {
            String response = new AppConfig().webClient(8 * 1024 * 1024)
                    .get()
                    .uri("http://127.0.0.1:" + server.port() + "/large")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(10));

            assertEquals(payload.length(), response == null ? 0 : response.length());
        } finally {
            server.disposeNow();
        }
    }
}
