package com.freedom.pong

import com.freedom.limit.PongProperties
import spock.lang.Specification

class PongPropertiesSpec extends Specification {
    def "should set and get limit QPS"() {
        given:
        def properties = new PongProperties()

        when:
        properties.setLimitQps(100)

        then:
        properties.getLimitQps() == 100
    }
}