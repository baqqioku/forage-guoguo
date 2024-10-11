package com.freedom.ping

import com.freedom.limit.PingProperties
import com.freedom.limit.PingRateLimiter
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class PingRateLimiterSpec extends Specification {
    @Shared
    PingRateLimiter rateLimiter

    @Shared
    File lockFile

    def setupSpec() {
        System.setProperty("LOCK_FILE", "/data/ping_rate_limit.lock")
    }

    def setup() {
        PingProperties properties = Mock(PingProperties)
        properties.getLimitQps() >> 2
        rateLimiter = new PingRateLimiter(properties)
        def lockFile = new File(System.getProperty("LOCK_FILE"))
        if (lockFile.exists()) {
            lockFile.delete()
        }
    }

    def cleanup() {
        def lockFile = new File(System.getProperty("LOCK_FILE"))
        if (lockFile.exists()) {
            lockFile.delete()
        }
    }

    def "should allow requests within rate limit"() {
        when:
        def result1 = rateLimiter.tryAcquire()
        def result2 = rateLimiter.tryAcquire()

        then:
        result1
        result2
    }

    def "should deny requests exceeding rate limit"() {
        given:
        rateLimiter.tryAcquire()
        rateLimiter.tryAcquire()

        when:
        def result = rateLimiter.tryAcquire()

        then:
        !result
    }

    def "should reset rate limit after one second"() {
        given:
        rateLimiter.tryAcquire()
        rateLimiter.tryAcquire()
        sleep(1000)

        when:
        def result = rateLimiter.tryAcquire()

        then:
        result
    }

    def "should handle file lock contention"() {
        given:
        def lockFile = new File(System.getProperty("LOCK_FILE"))
        def channel1 = new RandomAccessFile(lockFile, "rw").getChannel()
        def lock1 = channel1.tryLock()

        when:
        def result = rateLimiter.tryAcquire()

        then:
        !result

        cleanup:
        lock1.release()
        channel1.close()
    }

    def "should retry on IO exception"() {
        given:
        def mockFile = Mock(RandomAccessFile)
        mockFile.getChannel() >> { throw new IOException("Test IO exception") }

        when:
        def result = rateLimiter.tryAcquire()

        then:
        !result
    }

    def "should handle interrupted exception during retry"() {
        given:
        def mockFile = Mock(RandomAccessFile)
        mockFile.getChannel() >> { throw new IOException("Test IO exception") }
        Thread.currentThread().interrupt()

        when:
        def result = rateLimiter.tryAcquire()

        then:
        !result
        Thread.interrupted() // clear the interrupt flag
    }





    def "should handle concurrent access"() {
        given:
        def threads = (1..10).collect { Thread.start { rateLimiter.tryAcquire() } }

        when:
        threads*.join()

        then:
        new PollingConditions(timeout: 5).eventually {
            def content = new RandomAccessFile(lockFile, "r").withCloseable { raf ->
                raf.seek(8) // Skip timestamp
                raf.readInt()
            }
            assert content == 2
        }
    }
}