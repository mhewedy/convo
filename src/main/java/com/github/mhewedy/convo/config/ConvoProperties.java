package com.github.mhewedy.convo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "convo")
public class ConvoProperties {

    private Store store = Store.REDIS;

    public enum Store {
        JDBC, REDIS
    }
}
