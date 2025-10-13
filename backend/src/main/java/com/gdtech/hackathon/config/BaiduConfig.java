package com.gdtech.hackathon.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 百度统计配置
 * 支持多个百度统计账号（每个账号最多60个站点）
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "baidu.tongji")
public class BaiduConfig {

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

    /**
     * 多个百度统计账号配置
     * Key: 账号标识 (account1, account2, ...)
     * Value: 账号凭证信息
     */
    private Map<String, AccountCredentials> accounts = new HashMap<>();

    /**
     * 账号凭证信息
     */
    @Data
    public static class AccountCredentials {
        /**
         * API Key (AK)
         */
        private String clientId;

        /**
         * Secret Key (SK)
         */
        private String clientSecret;

        /**
         * 百度统计用户名（用于OAuth授权）
         */
        private String username;

        /**
         * 百度统计密码（用于OAuth授权）
         */
        private String password;

        /**
         * Access Token（运行时动态生成）
         */
        private String accessToken;

        /**
         * Refresh Token（运行时动态生成）
         */
        private String refreshToken;

        /**
         * Token过期时间（毫秒时间戳）
         */
        private Long tokenExpires;
    }

    /**
     * 获取指定账号的凭证
     */
    public AccountCredentials getAccountCredentials(String accountName) {
        return accounts.get(accountName);
    }
}
