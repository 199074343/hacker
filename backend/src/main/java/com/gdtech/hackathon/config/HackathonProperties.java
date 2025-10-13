package com.gdtech.hackathon.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Hackathon业务相关配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "hackathon")
public class HackathonProperties {

    /**
     * 晋级名额
     */
    private int qualifiedCount = 15;

    /**
     * 各阶段的时间配置
     */
    private Map<String, StageTimeline> stages = new HashMap<>();

    @Data
    public static class StageTimeline {
        /**
         * 阶段开始时间，格式：yyyy-MM-dd HH:mm:ss
         */
        private String start;

        /**
         * 阶段结束时间，格式：yyyy-MM-dd HH:mm:ss
         */
        private String end;
    }

    /**
     * 获取指定阶段的时间配置
     */
    public StageTimeline getStageTimeline(String stageCode) {
        return stages.get(stageCode);
    }
}
