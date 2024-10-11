package com.freedom.pong

import com.freedom.limit.PongProperties
import com.freedom.limit.PongRateLimiter
import spock.lang.Specification

class PongRateLimiterSpec extends Specification {
    PongRateLimiter limiter
    PongProperties properties

    def setup() {
        properties = Mock(PongProperties)
        properties.getLimitQps() >> 10
        limiter = new PongRateLimiter(properties)
    }

    def "should allow requests within rate limit"() {
        when:
        def results = (1..10).collect { limiter.tryAcquire() }

        then:
        results.every { it }
    }

    def "should deny requests exceeding rate limit"() {
        given:
        10.times { limiter.tryAcquire() }

        when:
        def result = limiter.tryAcquire()

        then:
        !result
    }

    def "should reset rate limit after one second"() {
        given:
        10.times { limiter.tryAcquire() }

        when:
        sleep(1000)
        def result = limiter.tryAcquire()

        then:
        result
    }
}