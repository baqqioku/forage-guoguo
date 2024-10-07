package com.freedom.pong.controller;

import com.freedom.pong.limit.PongRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class PongController {

    private static final Logger log = LoggerFactory.getLogger(PongController.class);

    private PongRateLimiter pongRateLimiter;

    public PongController (PongRateLimiter pongRateLimiter){
        this.pongRateLimiter = pongRateLimiter;
    }


    @GetMapping("/pong")
    public Mono<ResponseEntity<String>> pong() {
        if (pongRateLimiter.tryAcquire()) {
            return Mono.just(ResponseEntity.ok("World"));
        } else {
            log.error("fall back");
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Rate limit ， fallback now"));
        }
    }
}
