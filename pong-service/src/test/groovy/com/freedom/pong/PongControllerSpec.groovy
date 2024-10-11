package com.freedom.pong

import com.freedom.limit.PongProperties
import com.freedom.limit.PongRateLimiter
import org.springframework.http.HttpStatus
import reactor.test.StepVerifier
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PongControllerSpec extends Specification {

    PongProperties mockPongProperties;

    PongController pongController
    PongRateLimiter mockPongRateLimiter
    def setup() {

        mockPongProperties = Mock(PongProperties)
        mockPongProperties.getLimitQps() >> 1  // 假设我们想要设置 QPS 限制为 10

        mockPongRateLimiter = Mock(PongRateLimiter)
        pongController = new PongController(mockPongRateLimiter)
    }

    def "test should return 'World' when rate limit is not exceeded"() {
        given:
        mockPongRateLimiter.tryAcquire() >> true

        when:
        def result = pongController.pong()

        then:
        StepVerifier.create(result)
                .expectNextMatches { response ->
                response.statusCode == HttpStatus.OK &&
                        response.body == "World"
        }
            .verifyComplete()
    }

    def "test should return '429' when rate limit is exceeded"() {
        given:
        mockPongRateLimiter.tryAcquire() >> false

        when:
        def result = pongController.pong()

        then:
        StepVerifier.create(result)
                .expectNextMatches { response ->
                response.statusCode == HttpStatus.TOO_MANY_REQUESTS &&
                        response.body == "Rate limit exceeded"
        }
            .verifyComplete()
    }

    def "test should return 'Internal Server Error' when an exception occurs"() {
        given:
        mockPongRateLimiter.tryAcquire() >> { throw new RuntimeException("Test exception") }

        when:
        def result = pongController.pong()

        then:
        StepVerifier.create(result)
                .expectNextMatches { response ->
                response.statusCode == HttpStatus.INTERNAL_SERVER_ERROR &&
                        response.body == "Internal Server Error"
        }
            .verifyComplete()
    }

    def "test should log appropriate messages for different scenarios"() {
        given:
        def logCapture = new ByteArrayOutputStream()
        def originalOut = System.out
        def originalErr = System.err
        System.setOut(new PrintStream(logCapture))
        System.setErr(new PrintStream(logCapture))

        when: "Rate limit is not exceeded"
        mockPongRateLimiter.tryAcquire() >> true
        pongController.pong().block()

        then:
        logCapture.toString().contains("ping Successful")
        1 * mockPongRateLimiter.tryAcquire() >> true

        when: "Rate limit is exceeded"
        logCapture.reset()
        mockPongRateLimiter.tryAcquire() >> false
        pongController.pong().block()

        then:
        logCapture.toString().contains("Rate limit exceeded")
        1 * mockPongRateLimiter.tryAcquire() >> false


        cleanup:
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    def "test should correctly limit concurrent requests"() {
        given:
        def pongProperties = Mock(PongProperties)
        pongProperties.getLimitQps() >> 1 // 设置QPS限制为10
        def rateLimiter = new PongRateLimiter(pongProperties)
        def threadCount = 10
        def executionCount = 100
        def successCount = new AtomicInteger(0)
        def latch = new CountDownLatch(threadCount)
        def executor = Executors.newFixedThreadPool(threadCount)

        when:
        threadCount.times {
            executor.submit {
                try {
                    executionCount.times {
                        if (rateLimiter.tryAcquire()) {
                            successCount.incrementAndGet()
                        }

                    }
                } finally {
                    latch.countDown()
                }
            }
            // 模拟请求间隔
            Thread.sleep(1000)
        }
        latch.await(30, TimeUnit.SECONDS)

        then:
        successCount.get() > 0
        successCount.get() <= 10 // 假设测试运行了10秒，最多允许10个成功请求

        cleanup:
        executor.shutdownNow()
    }
}
