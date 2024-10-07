package com.freedom.limit;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ping")
public class PingProperties {

    private Double limitQps;

    public Double getLimitQps() {
        return limitQps;
    }

    public void setLimitQps(Double limitQps) {
        this.limitQps = limitQps;
    }
}
