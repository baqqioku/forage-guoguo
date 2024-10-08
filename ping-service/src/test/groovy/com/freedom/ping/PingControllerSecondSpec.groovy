package com.freedom.ping

import com.freedom.limit.GlobalRateLimiter
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

class PingControllerSecondSpec extends Specification {

    @Subject
    PingController pingController

    WebClient.Builder webClientBuilder = Mock(WebClient.Builder)
    GlobalRateLimiter globalRateLimiter = Mock(GlobalRateLimiter)
    WebClient webClient = Mock(WebClient)

    def setup() {
        webClientBuilder.baseUrl(_) >> webClientBuilder
        webClientBuilder.build() >> webClient
        pingController = new PingController(webClientBuilder, globalRateLimiter)
    }

    def "test should send request to Pong service when not rate limited"() {
        given:
        globalRateLimiter.tryAcquire() >> true
        def uriSpec = Mock(WebClient.RequestHeadersUriSpec)
        def responseSpec = Mock(WebClient.ResponseSpec)

        webClient.get() >> uriSpec
        uriSpec.uri("/pong?message=Hello") >> uriSpec
        uriSpec.retrieve() >> responseSpec
        responseSpec.bodyToMono(String.class) >> Mono.just("World")

        when:
        pingController.pingPongService()

        then:
        1 * globalRateLimiter.tryAcquire() // 检查令牌获取
        1 * webClient.get() // 确保get方法被调用
        1 * uriSpec.uri("/pong?message=Hello") // 确保uri被设置
        1 * responseSpec.bodyToMono(String.class) // 确保bodyToMono被调用
    }

    def "test should not send request when rate limited"() {
        given:
        globalRateLimiter.tryAcquire() >> false

        when:
        pingController.pingPongService()

        then:
        1 * globalRateLimiter.tryAcquire() // 仅检查令牌获取
        0 * webClient.get() // No request sent
    }

    def "should handle 429 response from Pong service"() {
        given:
        globalRateLimiter.tryAcquire() >> true
        def uriSpec = Mock(WebClient.RequestHeadersUriSpec)
        def responseSpec = Mock(WebClient.ResponseSpec)

        webClient.get() >> uriSpec
        uriSpec.uri("/pong?message=Hello") >> uriSpec
        uriSpec.retrieve() >> responseSpec
        responseSpec.bodyToMono(String.class) >> Mono.error(new WebClientResponseException(HttpStatus.TOO_MANY_REQUESTS.value(), "Too Many Requests", null, null, null))

        when:
        pingController.pingPongService()

        then:
        1 * globalRateLimiter.tryAcquire() // 检查令牌获取
        1 * webClient.get() // 确保get方法被调用
        1 * uriSpec.uri("/pong?message=Hello") // 确保uri被设置
        1 * responseSpec.bodyToMono(String.class) // 确保bodyToMono被调用
    }

    def "should log error on other exceptions"() {
        given:
        globalRateLimiter.tryAcquire() >> true
        def uriSpec = Mock(WebClient.RequestHeadersUriSpec)
        def responseSpec = Mock(WebClient.ResponseSpec)

        webClient.get() >> uriSpec
        uriSpec.uri("/pong?message=Hello") >> uriSpec
        uriSpec.retrieve() >> responseSpec
        responseSpec.bodyToMono(String.class) >> Mono.error(new RuntimeException("Some error"))

        when:
        pingController.pingPongService()

        then:
        1 * globalRateLimiter.tryAcquire() // 检查令牌获取
        1 * webClient.get() // 确保get方法被调用
        1 * uriSpec.uri("/pong?message=Hello") // 确保uri被设置
        1 * responseSpec.bodyToMono(String.class) // 确保bodyToMono被调用
    }
}

