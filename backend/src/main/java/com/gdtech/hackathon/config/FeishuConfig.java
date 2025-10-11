package com.gdtech.hackathon.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * 飞书配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "feishu")
public class FeishuConfig {

    /**
     * 应用 ID
     */
    private String appId;

    /**
     * 应用密钥
     */
    private String appSecret;

    /**
     * 多维表格配置
     */
    private BaseConfig base;

    @Data
    public static class BaseConfig {
        /**
         * 多维表格 App Token
         */
        private String appToken;

        /**
         * 各个表的 Table ID
         */
        private Map<String, String> tables;
    }

    /**
     * 获取项目表 Table ID
     */
    public String getProjectsTableId() {
        return base.getTables().get("projects");
    }

    /**
     * 获取投资人表 Table ID
     */
    public String getInvestorsTableId() {
        return base.getTables().get("investors");
    }

    /**
     * 获取投资记录表 Table ID
     */
    public String getInvestmentsTableId() {
        return base.getTables().get("investments");
    }

    /**
     * 获取配置表 Table ID
     */
    public String getConfigTableId() {
        return base.getTables().get("config");
    }
}
