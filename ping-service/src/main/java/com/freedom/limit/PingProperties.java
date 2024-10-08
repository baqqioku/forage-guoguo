package com.freedom.limit;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ping")
public class PingProperties {

    private Integer limitQps;

    public Integer getLimitQps() {
        return limitQps;
    }

    public void setLimitQps(Integer limitQps) {
        this.limitQps = limitQps;
    }
}
