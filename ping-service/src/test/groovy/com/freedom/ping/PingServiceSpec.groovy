package com.freedom.ping

import com.freedom.limit.GlobalRateLimiter
import com.freedom.limit.PingProperties
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Timeout
import spock.util.concurrent.PollingConditions

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PingServiceSpec extends Specification {

    private static final Logger logger = LoggerFactory.getLogger(PingServiceSpec.class)

    WebClient.Builder webClientBuilder
    WebClient webClient
    WebClient.RequestHeadersUriSpec requestHeadersUriSpec
    WebClient.ResponseSpec responseSpec
    GlobalRateLimiter globalRateLimiter
    PingProperties pingProperties


    @Subject
    PingService pingService

    def setup() {
        webClientBuilder = Mock(WebClient.Builder)
        webClient = Mock(WebClient)
        requestHeadersUriSpec = Mock(WebClient.RequestHeadersUriSpec)
        responseSpec = Mock(WebClient.ResponseSpec)
        globalRateLimiter = new GlobalRateLimiter(new PingProperties(limitQps: 2.0))
        pingProperties = new PingProperties(limitQps: 2.0)

        webClientBuilder.baseUrl(_ as String) >> webClientBuilder
        webClientBuilder.build() >> webClient
        webClient.get() >> requestHeadersUriSpec
        requestHeadersUriSpec.uri(_ as String) >> requestHeadersUriSpec
        requestHeadersUriSpec.retrieve() >> responseSpec


        pingService = new PingService(webClientBuilder, globalRateLimiter)
    }

    def "test successful ping"() {
        given:
        globalRateLimiter.tryAcquire() >> true
        responseSpec.bodyToMono(String.class) >> Mono.just("World")

        when:
        pingService.pingPongService()

        then:
        1 * webClient.get() >> requestHeadersUriSpec
        1 * requestHeadersUriSpec.uri("/pong") >> requestHeadersUriSpec
        1 * requestHeadersUriSpec.retrieve() >> responseSpec
        1 * responseSpec.bodyToMono(String.class) >> Mono.just("World")
    }

    def "test global rate limiter restriction"() {
        given:
        globalRateLimiter.tryAcquire() >> false

        when:
        pingService.pingPongService()

        then:
        0 * webClient.get()
    }

    def "test pong service throttling"() {
        given:
        globalRateLimiter.tryAcquire() >> true
        def exception = WebClientResponseException.create(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                null,
                null,
                null
        )
        responseSpec.bodyToMono(String.class) >> Mono.error(exception)

        when:
        pingService.pingPongService()

        then:
        1 * webClient.get() >> requestHeadersUriSpec
        1 * requestHeadersUriSpec.uri("/pong") >> requestHeadersUriSpec
        1 * requestHeadersUriSpec.retrieve() >> responseSpec
        1 * responseSpec.bodyToMono(String.class) >> Mono.error(exception)
    }

    def "test unexpected error"() {
        given:
        globalRateLimiter.tryAcquire() >> true
        def exception = new RuntimeException("Unexpected error")
        responseSpec.bodyToMono(String.class) >> Mono.error(exception)

        when:
        pingService.pingPongService()

        then:
        1 * webClient.get() >> requestHeadersUriSpec
        1 * requestHeadersUriSpec.uri("/pong") >> requestHeadersUriSpec
        1 * requestHeadersUriSpec.retrieve() >> responseSpec
        1 * responseSpec.bodyToMono(String.class) >> Mono.error(exception)
    }

    //并发测试 持续时间5秒，一秒的时间窗口 通过的请求数量
    def "test concurrent requests with configured QPS limit"() {
        given:
        def testDurationSeconds = 5
        def configuredQps = pingProperties.limitQps
        def totalExpectedRequests = (testDurationSeconds * configuredQps) as int
        def successfulRequests = new AtomicInteger(0)
        def rateLimitedRequests = new AtomicInteger(0)
        def requestTimestamps = new ConcurrentLinkedQueue<Long>()

        responseSpec.bodyToMono(String.class) >> Mono.just("World")

        def executor = Executors.newFixedThreadPool(10)

        logger.info("Starting concurrent request test with QPS: {}, duration: {} seconds", configuredQps, testDurationSeconds)

        when:
        def startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < testDurationSeconds * 1000) {
            executor.submit {
                if (globalRateLimiter.tryAcquire()) {
                    pingService.pingPongService()
                    def timestamp = System.currentTimeMillis()
                    requestTimestamps.offer(timestamp)
                    successfulRequests.incrementAndGet()
                    logger.info("Successful request at: {}", formatTimestamp(timestamp))
                } else {
                    rateLimitedRequests.incrementAndGet()
                    logger.debug("Request rate limited at: {}", formatTimestamp(System.currentTimeMillis()))
                }
            }
            Thread.sleep(10) // Small delay to prevent overwhelming the CPU
        }
        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.SECONDS)

        then:
        def conditions = new PollingConditions(timeout: 10, delay: 0.1)
        conditions.eventually {
            assert successfulRequests.get() == totalExpectedRequests
            assert rateLimitedRequests.get() > 0 // Ensure some requests were rate limited

            logger.info("Test completed. Successful requests: {}, Rate limited requests: {}",
                    successfulRequests.get(), rateLimitedRequests.get())

            // Verify QPS
            def timestamps = requestTimestamps.toSorted()
            def windowSize = 1000 // 1 second window
            for (int i = 0; i < timestamps.size() - 1; i++) {
                def windowEnd = timestamps[i] + windowSize
                def requestsInWindow = timestamps.findAll { it >= timestamps[i] && it < windowEnd }.size()
                assert requestsInWindow <= configuredQps + 1 // Allow for small timing inconsistencies
                logger.info("Requests in 1s window starting at : {}", requestsInWindow)
            }
        }

        logger.info("All assertions passed successfully")
    }

    //直接100次并发请求
    def "test concurrent in request times"() {
        given:
        def totalRequests = 100
        def successfulRequests = new AtomicInteger(0)
        def rateLimitedRequests = new AtomicInteger(0)
        def latch = new CountDownLatch(totalRequests)
        def executor = Executors.newFixedThreadPool(10)

        responseSpec.bodyToMono(String.class) >> Mono.just("World")

        logger.info("Starting concurrent request test with {} total requests", totalRequests)

        when:
        totalRequests.times {
            executor.submit {
                try {
                    if (globalRateLimiter.tryAcquire()) {
                        pingService.pingPongService()
                        def count = successfulRequests.incrementAndGet()
                        logger.info("Request {} succeeded", count)
                    } else {
                        def count = rateLimitedRequests.incrementAndGet()
                        logger.info("Request {} rate limited", count)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        then:
        logger.info("Test completed. Successful requests: {}, Rate limited requests: {}",
                successfulRequests.get(), rateLimitedRequests.get())

        successfulRequests.get() + rateLimitedRequests.get() == totalRequests
        successfulRequests.get() <= pingProperties.limitQps * 2 // 允许一些误差
        rateLimitedRequests.get() >= totalRequests - (pingProperties.limitQps * 2)

        logger.info("All assertions passed successfully")
    }




}