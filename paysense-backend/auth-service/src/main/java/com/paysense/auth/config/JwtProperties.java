package com.paysense.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {
    private String secret;
    private long expiration;
    private RefreshToken refreshToken = new RefreshToken();

    @Data
    public static class RefreshToken {
        private long expiration;
    }
}
