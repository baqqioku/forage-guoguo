package com.freedom.pong;

import com.freedom.pong.controller.PongController
import com.freedom.pong.limit.PongProperties
import com.freedom.pong.limit.PongRateLimiter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import spock.lang.Specification;
import spock.lang.Subject

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger;

@Deprecated
//使用webTestClient
class PongControllerNextSpec extends Specification {

    private static final Logger logger = LoggerFactory.getLogger(PongControllerNextSpec.class)


    @Subject
    PongController pongController

    WebTestClient webTestClient

    PongRateLimiter pongRateLimiter


    def setup() {
        pongRateLimiter = new PongRateLimiter(new PongProperties(limitQps:1.0))
        pongController = new PongController(pongRateLimiter)
        webTestClient = WebTestClient
                .bindToController(pongController)
                .build()
    }

    def "test should return 'World' for a single request"() {
        when:
        def response = webTestClient.get()
                .uri("/pong")
                .exchange()

        then:
        response.expectStatus().isOk()
                .expectBody(String.class).isEqualTo("World")
    }

    def "test should throttle requests exceeding the rate limit"() {
        given:
        def totalRequests = 100
        def successfulRequests = new AtomicInteger(0)
        def throttledRequests = new AtomicInteger(0)
        def latch = new CountDownLatch(totalRequests)
        def executor = Executors.newFixedThreadPool(10)
        def requestTimestamps = new ConcurrentLinkedQueue<Long>()

        when:
        totalRequests.times {
            executor.submit {
                try {
                    def result = webTestClient.get()
                            .uri("/pong")
                            .exchange()
                            .returnResult(String.class)
                    if (result.status == HttpStatus.OK) {
                        successfulRequests.incrementAndGet()
                        requestTimestamps.offer(System.currentTimeMillis())
                        println "Request ${it + 1} succeeded at: ${new Date()}"
                    } else if (result.status == HttpStatus.TOO_MANY_REQUESTS) {
                        throttledRequests.incrementAndGet()
                        println "Request ${it + 1} throttled at: ${new Date()}"
                    }else {
                        throttledRequests.incrementAndGet()
                        println "Request ${it + 1} throttled at: ${new Date()}"
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        then:
        successfulRequests.get() + throttledRequests.get() == totalRequests
        successfulRequests.get() > 0
        throttledRequests.get() > 0

        // 验证QPS
        def timestamps = requestTimestamps.toSorted()
        def windowSize = 1000 // 1秒窗口
        for (int i = 0; i < timestamps.size() - 1; i++) {
            def windowEnd = timestamps[i] + windowSize
            def requestsInWindow = timestamps.findAll { it >= timestamps[i] && it < windowEnd }.size()
            assert requestsInWindow <= 1 // 每秒最多1个请求
        }

        println "Test completed. Successful requests: ${successfulRequests.get()}, Throttled requests: ${throttledRequests.get()}"
    }

    def "test should maintain rate limit over a longer period"() {
        given:
        def testDurationSeconds = 5
        def expectedRequests = testDurationSeconds // 因为QPS是1
        def successfulRequests = new AtomicInteger(0)
        def throttledRequests = new AtomicInteger(0)
        def startTime = System.currentTimeMillis()

        when:
        while (System.currentTimeMillis() - startTime < testDurationSeconds * 1000) {
            def result = webTestClient.get()
                    .uri("/pong")
                    .exchange()
                    .returnResult(String.class)
            logger.info("result.status+",result.status)
            if (result.status == HttpStatus.OK) {
                successfulRequests.incrementAndGet()
                println "Request succeeded at: ${new Date()}"
            } else if (result.status == HttpStatus.TOO_MANY_REQUESTS) {
                throttledRequests.incrementAndGet()
                println "Request throttled at: ${new Date()}"
            } else {
                throttledRequests.incrementAndGet()
                println "Request throttled at: ${new Date()}"
            }

            Thread.sleep(1000) // 小延迟，避免过于频繁的请求
        }

        then:
        successfulRequests.get() == expectedRequests
        throttledRequests.get() >= 0

        println "Test completed. Successful requests: ${successfulRequests.get()}, Throttled requests: ${throttledRequests.get()}"
    }
}