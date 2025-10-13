package com.gdtech.hackathon.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 百度统计配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "baidu.tongji")
public class BaiduConfig {

    /**
     * API Key (AK)
     */
    private String clientId;

    /**
     * Secret Key (SK)
     */
    private String clientSecret;

    /**
     * Access Token
     */
    private String accessToken;

    /**
     * Refresh Token
     */
    private String refreshToken;

    /**
     * Token URL
     */
    private String tokenUrl = "https://openapi.baidu.com/oauth/2.0/token";

    /**
     * API URL
     */
    private String apiUrl = "https://openapi.baidu.com/rest/2.0/tongji/report/getData";

    /**
     * UV同步间隔（分钟）
     */
    private int syncInterval = 10;
}
