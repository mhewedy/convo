package com.github.mhewedy.convo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "convo")
public class ConvoProperties {

    private Store store = Store.REDIS;
    private Jdbc jdbc = new Jdbc();

    public enum Store {
        JDBC, REDIS
    }

    @Data
    public static class Jdbc {
        private Cleanup cleanup = new Cleanup();

        @Data
        public static class Cleanup {
            private Boolean enabled = true;
            private Duration interval = Duration.ofMinutes(15);
        }
    }
}
