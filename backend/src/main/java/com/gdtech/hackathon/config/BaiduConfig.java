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

        /**
         * 上次刷新失败的时间（毫秒时间戳）
         */
        private Long lastRefreshFailedTime;

        /**
         * 连续刷新失败次数
         */
        private Integer refreshFailedCount = 0;

        /**
         * 检查是否应该跳过刷新（刷新失败次数过多）
         */
        public boolean shouldSkipRefresh() {
            // 如果连续失败3次以上，且距离上次失败不到1小时，则跳过
            if (refreshFailedCount != null && refreshFailedCount >= 3) {
                if (lastRefreshFailedTime != null) {
                    long hourInMillis = 60 * 60 * 1000; // 1小时
                    return System.currentTimeMillis() - lastRefreshFailedTime < hourInMillis;
                }
            }
            return false;
        }

        /**
         * 记录刷新成功
         */
        public void markRefreshSuccess() {
            this.refreshFailedCount = 0;
            this.lastRefreshFailedTime = null;
        }

        /**
         * 记录刷新失败
         */
        public void markRefreshFailed() {
            this.lastRefreshFailedTime = System.currentTimeMillis();
            this.refreshFailedCount = (this.refreshFailedCount == null ? 0 : this.refreshFailedCount) + 1;
        }
    }

    /**
     * 获取指定账号的凭证
     */
    public AccountCredentials getAccountCredentials(String accountName) {
        return accounts.get(accountName);
    }
}
