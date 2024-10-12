package com.freedom.limit

import spock.lang.Specification

import java.nio.channels.FileLock
import java.util.concurrent.atomic.AtomicInteger

class PingRateLimiterSpec extends Specification {

    PingRateLimiter rateLimiter
    PingProperties properties
    FileAccessWrapper fileAccessWrapper

    def setup() {
        properties = Mock(PingProperties)
        properties.getLimitQps() >> 2
        fileAccessWrapper = Mock(FileAccessWrapper)
        rateLimiter = new PingRateLimiter(properties) {
            @Override
            protected FileAccessWrapper createFileAccess() {
                return fileAccessWrapper
            }
        }
    }

    def "test should allow requests within rate limit"() {
        given:
        fileAccessWrapper.length() >> 0
        fileAccessWrapper.tryLock(_, _, _) >> Mock(FileLock)

        when:
        def result1 = rateLimiter.tryAcquire()
        def result2 = rateLimiter.tryAcquire()

        then:
        result1
        result2
    }

    def "test should deny requests exceeding rate limit"() {
        given:
        fileAccessWrapper.length() >> 12 // 8 bytes for long, 4 bytes for int
        fileAccessWrapper.readLong() >> System.currentTimeMillis() / 1000
        fileAccessWrapper.readInt() >> 2
        fileAccessWrapper.tryLock(_, _, _) >> Mock(FileLock)

        when:
        def result = rateLimiter.tryAcquire()

        then:
        !result
    }

    def "test should reset rate limit after one second"() {
        given:
        def currentTime = System.currentTimeMillis() / 1000
        fileAccessWrapper.length() >> 12
        fileAccessWrapper.readLong() >> (currentTime - 1)
        fileAccessWrapper.readInt() >> 2
        fileAccessWrapper.tryLock(_, _, _) >> Mock(FileLock)

        when:
        def result = rateLimiter.tryAcquire()

        then:
        result
    }

    def "test should handle file lock contention"() {
        given:
        fileAccessWrapper.tryLock(_, _, _) >> null

        when:
        def result = rateLimiter.tryAcquire()

        then:
        !result
    }

    def "test should handle IOException during file operations"() {
        given:
        fileAccessWrapper.tryLock(_, _, _) >> { throw new IOException("Test IO exception") }

        when:
        def result = rateLimiter.tryAcquire()

        then:
        !result
    }

    def "test should use correct MAX_REQUESTS_PER_SECOND from properties"() {
        given:
        def customProperties = Mock(PingProperties)
        customProperties.getLimitQps() >> 5
        def currentTime = System.currentTimeMillis() / 1000
        def requestCount = new AtomicInteger(0)

        fileAccessWrapper = Mock(FileAccessWrapper) {
            length() >> { requestCount.get() > 0 ? 12 : 0 }
            readLong() >> currentTime
            readInt() >> { requestCount.get() }
            tryLock(_, _, _) >> Mock(FileLock)
        }

        def customRateLimiter = new PingRateLimiter(customProperties) {
            @Override
            protected FileAccessWrapper createFileAccess() {
                return fileAccessWrapper
            }
        }

        when:
        def results = (1..6).collect {
            def result = customRateLimiter.tryAcquire()
            if (result) {
                requestCount.incrementAndGet()
            }
            result
        }

        then:
        results[0..4].every { it }
        !results[5]
        requestCount.get() == 5
    }
}