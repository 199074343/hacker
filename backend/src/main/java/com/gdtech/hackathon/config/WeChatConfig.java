package com.gdtech.hackathon.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 微信公众号配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "wechat")
public class WeChatConfig {

    /**
     * 微信公众号 AppID
     */
    private String appId;

    /**
     * 微信公众号 AppSecret
     */
    private String appSecret;

    /**
     * Access Token 缓存时间（秒），默认7000秒（微信官方有效期7200秒，提前200秒刷新）
     */
    private int accessTokenExpire = 7000;

    /**
     * JS API Ticket 缓存时间（秒），默认7000秒（微信官方有效期7200秒，提前200秒刷新）
     */
    private int jsApiTicketExpire = 7000;
}
