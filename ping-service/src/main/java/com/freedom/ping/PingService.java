package com.freedom.ping;

import com.freedom.limit.GlobalRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;

@Service
public class PingService {

    private static final Logger log = LoggerFactory.getLogger(PingService.class);
    private final WebClient webClient;
    private final GlobalRateLimiter globalRateLimiter;

    public PingService(WebClient.Builder webClientBuilder, GlobalRateLimiter globalRateLimiter) {
        this.webClient = webClientBuilder.baseUrl("http://localhost:8081").build();
        this.globalRateLimiter = globalRateLimiter;
    }

    //@Scheduled(fixedRate = 1000)
    @Scheduled(fixedRate = 100)
    public void pingPongService() {
        try {
            if (globalRateLimiter.canSendRequest()) {
                webClient.get()
                        .uri("/pong?message=Hello")
                        .retrieve()
                        .bodyToMono(String.class)
                        .subscribe(
                                response -> log.info("Request sent & Pong responded: {}", response),
                                error -> {
                                    if (error instanceof WebClientResponseException &&
                                            ((WebClientResponseException) error).getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                                        log.info("Request sent & Pong throttled it");
                                    } else {
                                        log.error("Error occurred: {}", error.getMessage());
                                    }
                                }
                        );
            } else {
                log.info("Request not sent as being rate limited");
            }
        } catch (IOException e) {
            log.error("Error while checking rate limit: {} ", e.getMessage());
        } catch (Exception e) {
            log.error("System error:{} ", e.getMessage());
        }
    }
}
