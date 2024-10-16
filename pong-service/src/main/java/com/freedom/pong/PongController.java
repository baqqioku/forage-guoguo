package com.freedom.pong;

import com.freedom.limit.PongRateLimiter;
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

    public PongController(PongRateLimiter pongRateLimiter) {
        this.pongRateLimiter = pongRateLimiter;
    }


    @GetMapping("/pong")
    public Mono<ResponseEntity<String>> pong() {
        try {
            log.debug("Attempting to acquire rate limit");
            boolean acquired = pongRateLimiter.tryAcquire();
            log.debug("Rate limit acquired: {}", acquired);
            if (acquired) {
                log.info("ping Successful ");
                return Mono.just(ResponseEntity.ok("World"));
            } else {
                log.error("Rate limit exceeded");
                return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Rate limit exceeded"));
            }
        }  catch (Exception e) {
            e.printStackTrace();
            log.error("System error:{} ", e.getMessage());
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Server Error"));
        }
    }
}
