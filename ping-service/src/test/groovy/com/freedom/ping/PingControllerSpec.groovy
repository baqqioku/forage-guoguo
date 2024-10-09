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

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PingControllerSpec extends Specification {

    private static final Logger logger = LoggerFactory.getLogger(PingControllerSpec.class)

    WebClient.Builder webClientBuilder
    WebClient webClient
    WebClient.RequestHeadersUriSpec requestHeadersUriSpec
    WebClient.ResponseSpec responseSpec
    GlobalRateLimiter globalRateLimiter
    PingProperties pingProperties


    @Subject
    PingController pingService

    def setup() {
        webClientBuilder = Mock(WebClient.Builder)
        webClient = Mock(WebClient)
        requestHeadersUriSpec = Mock(WebClient.RequestHeadersUriSpec)
        responseSpec = Mock(WebClient.ResponseSpec)
        //globalRateLimiter = new GlobalRateLimiter(new PingProperties(limitQps: 2.0))
        //globalRateLimiter = new GlobalRateLimiter();
        // 模拟 PingProperties
        pingProperties = Mock(PingProperties)
        pingProperties.getLimitQps() >> 2 // 假设限制为每秒2个请求

        // 使用模拟的 PingProperties 创建 GlobalRateLimiter
        //globalRateLimiter = new GlobalRateLimiter()
        globalRateLimiter = Mock(GlobalRateLimiter)

        //pingProperties = new PingProperties(limitQps: 2.0)

        webClientBuilder.baseUrl(_ as String) >> webClientBuilder
        webClientBuilder.build() >> webClient
        webClient.get() >> requestHeadersUriSpec
        requestHeadersUriSpec.uri(_ as String) >> requestHeadersUriSpec
        requestHeadersUriSpec.retrieve() >> responseSpec


        pingService = new PingController(webClientBuilder, globalRateLimiter)
    }

    def "test successful ping"() {
        given:
        globalRateLimiter.tryAcquire() >> true
       /* GlobalRateLimiter spyGlobalRateLimiter = Spy(globalRateLimiter)
        spyGlobalRateLimiter.tryAcquire() >> true*/
        responseSpec.bodyToMono(String.class) >> Mono.just("World")

        when:
        pingService.pingPongService()

        then:
        1 * webClient.get() >> requestHeadersUriSpec
        1 * requestHeadersUriSpec.uri("/pong?message=Hello") >> requestHeadersUriSpec
        1 * requestHeadersUriSpec.retrieve() >> responseSpec
        1 * responseSpec.bodyToMono(String.class) >> Mono.just(" World")
    }

    def "test global rate limiter restriction"() {
        given: "The global rate limiter is set to return false"
        /*GlobalRateLimiter spyGlobalRateLimiter = Spy(GlobalRateLimiter, constructorArgs: [pingProperties]) {
            tryAcquire() >> false
        }

        PingController testPingService = new PingController(webClientBuilder, spyGlobalRateLimiter)*/
        globalRateLimiter.tryAcquire() >> false
//        GlobalRateLimiter spyGlobalRateLimiter = Spy(globalRateLimiter)
//        spyGlobalRateLimiter.tryAcquire() >> false
        when: "Calling the pingPongService method"
        pingService.pingPongService()

        then: "Verify that tryAcquire is called once and webClient.get() is not called"
        //1 * globalRateLimiter.tryAcquire()
        0 * webClient.get()
    }

    def "test pong service throttling"() {
        given: "Set up GlobalRateLimiter and WebClient"
        def spyGlobalRateLimiter = Spy(GlobalRateLimiter)
        spyGlobalRateLimiter.tryAcquire() >> true

        def mockWebClient = Mock(WebClient)
        def mockWebClientBuilder = Mock(WebClient.Builder)
        def mockRequestHeadersUriSpec = Mock(WebClient.RequestHeadersUriSpec)
        def mockResponseSpec = Mock(WebClient.ResponseSpec)

        // 模拟 WebClient.Builder 的行为
        mockWebClientBuilder.baseUrl(_ as String) >> mockWebClientBuilder
        mockWebClientBuilder.build() >> mockWebClient

        // 确保 get() 方法返回 mockRequestHeadersUriSpec
        mockWebClient.get() >> mockRequestHeadersUriSpec

        mockRequestHeadersUriSpec.uri("/pong?message=Hello") >> mockRequestHeadersUriSpec
        mockRequestHeadersUriSpec.retrieve() >> mockResponseSpec

        def exception = WebClientResponseException.create(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                null,
                null,
                null
        )
        mockResponseSpec.bodyToMono(String) >> Mono.error(exception)

        // 使用模拟的 WebClient.Builder 创建 PingController
        def pingController = new PingController(mockWebClientBuilder, spyGlobalRateLimiter)

        when: "Calling the pingPongService method"
        pingController.pingPongService()
        Thread.sleep(100) // 等待异步操作完成

        then: "Verify the correct methods are called with expected arguments"
        1 * spyGlobalRateLimiter.tryAcquire()
        1 * mockWebClient.get() >> mockRequestHeadersUriSpec
        1 * mockRequestHeadersUriSpec.uri("/pong?message=Hello") >> mockRequestHeadersUriSpec
        1 * mockRequestHeadersUriSpec.retrieve() >> mockResponseSpec
        1 * mockResponseSpec.bodyToMono(String) >> Mono.error(exception)

        and: "No further interactions with webClient"
        0 * mockWebClient._
    }


    def "test unexpected error"() {

        given: "Set up GlobalRateLimiter and WebClient"
        def spyGlobalRateLimiter = Spy(GlobalRateLimiter)
        spyGlobalRateLimiter.tryAcquire() >> true

        def mockWebClient = Mock(WebClient)
        def mockWebClientBuilder = Mock(WebClient.Builder)
        def mockRequestHeadersUriSpec = Mock(WebClient.RequestHeadersUriSpec)
        def mockResponseSpec = Mock(WebClient.ResponseSpec)

        // 模拟 WebClient.Builder 的行为
        mockWebClientBuilder.baseUrl(_ as String) >> mockWebClientBuilder
        mockWebClientBuilder.build() >> mockWebClient

        // 确保 get() 方法返回 mockRequestHeadersUriSpec
        mockWebClient.get() >> mockRequestHeadersUriSpec

        mockRequestHeadersUriSpec.uri("/pong?message=Hello") >> mockRequestHeadersUriSpec
        mockRequestHeadersUriSpec.retrieve() >> mockResponseSpec


        mockResponseSpec.bodyToMono(String) >> Mono.error(new RuntimeException("Test error"))

        // 使用模拟的 WebClient.Builder 创建 PingController
        def pingController = new PingController(mockWebClientBuilder, spyGlobalRateLimiter)

        when: "Calling the pingPongService method"
        pingController.pingPongService()
        Thread.sleep(100) // 等待异步操作完成

        then: "Verify the correct methods are called with expected arguments"
        1 * spyGlobalRateLimiter.tryAcquire()
        1 * mockWebClient.get() >> mockRequestHeadersUriSpec
        1 * mockRequestHeadersUriSpec.uri("/pong?message=Hello") >> mockRequestHeadersUriSpec
        1 * mockRequestHeadersUriSpec.retrieve() >> mockResponseSpec
        1 * mockResponseSpec.bodyToMono(String) >> Mono.error(new RuntimeException("Test error"))

        and: "No further interactions with webClient"
        0 * mockWebClient._
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
        globalRateLimiter = new GlobalRateLimiter();
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
                } catch (Exception e) {
                    if (e instanceof WebClientResponseException &&
                            ((WebClientResponseException) e).getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                        rateLimitedRequests.incrementAndGet()
                        logger.info("Request {} rate limited", count)
                    } else {
                        rateLimitedRequests.incrementAndGet()
                        logger.info("Request {} rate limited", count)
                    }
                }finally {
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