package com.freedom.ping;

import com.freedom.limit.PingRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@RestController
//@Service
public class PingController {

    private static final Logger log = LoggerFactory.getLogger(PingController.class);
    private final WebClient webClient;
    private final PingRateLimiter globalRateLimiter;

    public PingController(WebClient.Builder webClientBuilder, PingRateLimiter globalRateLimiter) {
        this.webClient = webClientBuilder.baseUrl("http://localhost:8081").build();
        this.globalRateLimiter = globalRateLimiter;
    }

    @Scheduled(fixedRate = 1000)
    //@Scheduled(fixedRate = 100)
    public void pingPongService() {
        try {
            log.debug("Attempting to acquire rate limit");
            boolean acquired = globalRateLimiter.tryAcquire();
            log.debug("Rate limit acquired: {}", acquired);
            if (acquired) {
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
        } catch (Exception e) {
            e.printStackTrace();
            log.error("System error:{} ", e.getMessage());
        }
    }

    @RequestMapping("/ping")
    public Mono<String> ping() {
        return Mono.just("Ping service is running");
    }
}
