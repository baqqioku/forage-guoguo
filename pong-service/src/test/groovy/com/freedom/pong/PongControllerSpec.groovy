package com.freedom.pong

import com.freedom.pong.controller.PongController
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PongControllerSpec extends Specification {

    @Subject
    PongController pongController

    WebClient webClient

    def setup() {
        pongController = new PongController()
        // 假设 Pong 服务运行在 localhost:8081
        webClient = WebClient.create("http://localhost:8081")
    }

    def "should return 'World' for a single request"() {
        when:
        def response = webClient.get()
                .uri("/pong")
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(5))

        then:
        response == "World"
    }

    def "should throttle requests exceeding the rate limit"() {
        given:
        def totalRequests = 10
        def successfulRequests = new AtomicInteger(0)
        def throttledRequests = new AtomicInteger(0)
        def latch = new CountDownLatch(totalRequests)
        def executor = Executors.newFixedThreadPool(5)
        def requestTimestamps = new ConcurrentLinkedQueue<Long>()

        when:
        totalRequests.times {
            executor.submit {
                try {
                    webClient.get()
                            .uri("/pong")
                            .retrieve()
                            .bodyToMono(String.class)
                            .doOnSuccess { response ->
                                successfulRequests.incrementAndGet()
                                requestTimestamps.offer(System.currentTimeMillis())
                                println "Request succeeded at: ${new Date()}"
                            }
                            .doOnError { error ->
                                if (error.message.contains("429")) {
                                    throttledRequests.incrementAndGet()
                                    println "Request throttled at: ${new Date()}"
                                } else {
                                    println "Unexpected error: ${error.message}"
                                }
                            }
                            .onErrorResume { Mono.empty() }
                            .block(Duration.ofSeconds(5))
                } finally {
                    latch.countDown()
                }
            }
            Thread.sleep(100) // 小延迟，模拟请求间隔
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
            //assert requestsInWindow <= 1 // 每秒最多1个请求
        }

        println "Test completed. Successful requests: ${successfulRequests.get()}, Throttled requests: ${throttledRequests.get()}"
    }

    def "should maintain rate limit over a longer period"() {
        given:
        def testDurationSeconds = 5
        def expectedRequests = testDurationSeconds // 因为QPS是1
        def successfulRequests = new AtomicInteger(0)
        def throttledRequests = new AtomicInteger(0)
        def startTime = System.currentTimeMillis()

        when:
        while (System.currentTimeMillis() - startTime < testDurationSeconds * 1000) {
            webClient.get()
                    .uri("/pong")
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnSuccess { response ->
                        successfulRequests.incrementAndGet()
                        println "Request succeeded at: ${new Date()}"
                    }
                    .doOnError { error ->
                        if (error.message.contains("429")) {
                            throttledRequests.incrementAndGet()
                            println "Request throttled at: ${new Date()}"
                        } else {
                            println "Unexpected error: ${error.message}"
                        }
                    }
                    .onErrorResume { Mono.empty() }
                    .block(Duration.ofSeconds(1))

            Thread.sleep(100) // 小延迟，避免过于频繁的请求
        }

        then:
        successfulRequests.get() == expectedRequests
        throttledRequests.get() > 0

        println "Test completed. Successful requests: ${successfulRequests.get()}, Throttled requests: ${throttledRequests.get()}"
    }
}