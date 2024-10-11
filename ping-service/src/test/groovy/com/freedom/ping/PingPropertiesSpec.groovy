package com.freedom.ping

import com.freedom.limit.PingProperties
import spock.lang.Specification

class PingPropertiesSpec extends Specification {
    def "should set and get limit QPS"() {
        given:
        def properties = new PingProperties()

        when:
        properties.setLimitQps(100)

        then:
        properties.getLimitQps() == 100
    }
}