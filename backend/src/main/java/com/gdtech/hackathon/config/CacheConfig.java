package com.gdtech.hackathon.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * 缓存配置
 * 使用Caffeine本地缓存实现
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * 配置多个缓存,每个缓存有独立的过期时间和容量
     */
    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();

        cacheManager.setCaches(Arrays.asList(
                // 飞书Token缓存 - 5分钟过期
                buildCache("feishuToken", 1, 5, TimeUnit.MINUTES),

                // 比赛阶段缓存 - 1小时过期
                buildCache("currentStage", 1, 60, TimeUnit.MINUTES),

                // 项目列表缓存 - 5分钟过期
                buildCache("projects", 1, 5, TimeUnit.MINUTES),

                // 单个投资人缓存 - 5分钟过期,最多缓存100个投资人
                buildCache("investor", 100, 5, TimeUnit.MINUTES),

                // 单个项目缓存 - 5分钟过期,最多缓存50个项目
                buildCache("project", 50, 5, TimeUnit.MINUTES)
        ));

        return cacheManager;
    }

    /**
     * 构建单个缓存实例
     *
     * @param name 缓存名称
     * @param maxSize 最大容量
     * @param duration 过期时间
     * @param timeUnit 时间单位
     */
    private CaffeineCache buildCache(String name, int maxSize, long duration, TimeUnit timeUnit) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(duration, timeUnit)
                .recordStats()  // 开启统计,方便监控
                .build());
    }
}
