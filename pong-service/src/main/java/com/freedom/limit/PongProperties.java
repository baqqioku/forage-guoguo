package com.freedom.limit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "pong")
public class PongProperties {

    private Integer limitQps;

    public Integer getLimitQps() {
        return limitQps;
    }

    public void setLimitQps(Integer limitQps) {
        this.limitQps = limitQps;
    }
}