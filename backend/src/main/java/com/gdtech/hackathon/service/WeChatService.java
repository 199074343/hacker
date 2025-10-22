package com.gdtech.hackathon.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdtech.hackathon.config.WeChatConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * 微信服务
 * 提供微信JS-SDK所需的签名配置
 */
@Slf4j
@Service
public class WeChatService {

    private final WeChatConfig weChatConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // Access Token 缓存
    private String cachedAccessToken;
    private long accessTokenExpireTime = 0;

    // JS API Ticket 缓存
    private String cachedJsApiTicket;
    private long jsApiTicketExpireTime = 0;

    public WeChatService(WeChatConfig weChatConfig, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.weChatConfig = weChatConfig;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取微信 Access Token
     * 使用缓存机制，避免频繁请求
     */
    public String getAccessToken() {
        long now = System.currentTimeMillis();

        // 如果缓存有效，直接返回
        if (cachedAccessToken != null && now < accessTokenExpireTime) {
            log.debug("使用缓存的 Access Token");
            return cachedAccessToken;
        }

        // 请求新的 Access Token
        try {
            String url = String.format(
                "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s",
                weChatConfig.getAppId(),
                weChatConfig.getAppSecret()
            );

            log.info("请求微信 Access Token: appid={}", weChatConfig.getAppId());
            String response = restTemplate.getForObject(url, String.class);
            JsonNode jsonNode = objectMapper.readTree(response);

            if (jsonNode.has("access_token")) {
                cachedAccessToken = jsonNode.get("access_token").asText();
                accessTokenExpireTime = now + weChatConfig.getAccessTokenExpire() * 1000L;
                log.info("获取 Access Token 成功，有效期至: {}", new Date(accessTokenExpireTime));
                return cachedAccessToken;
            } else {
                String errcode = jsonNode.has("errcode") ? jsonNode.get("errcode").asText() : "unknown";
                String errmsg = jsonNode.has("errmsg") ? jsonNode.get("errmsg").asText() : "unknown";
                log.error("获取 Access Token 失败: errcode={}, errmsg={}", errcode, errmsg);
                throw new RuntimeException("获取微信 Access Token 失败: " + errmsg);
            }
        } catch (Exception e) {
            log.error("获取 Access Token 异常", e);
            throw new RuntimeException("获取微信 Access Token 异常", e);
        }
    }

    /**
     * 获取 JS API Ticket
     * 使用缓存机制，避免频繁请求
     */
    public String getJsApiTicket() {
        long now = System.currentTimeMillis();

        // 如果缓存有效，直接返回
        if (cachedJsApiTicket != null && now < jsApiTicketExpireTime) {
            log.debug("使用缓存的 JS API Ticket");
            return cachedJsApiTicket;
        }

        // 获取 Access Token
        String accessToken = getAccessToken();

        // 请求新的 JS API Ticket
        try {
            // 使用正确的API端点（需要type=jsapi参数）
            String url = String.format(
                "https://api.weixin.qq.com/cgi-bin/ticket/getticket?access_token=%s&type=jsapi",
                accessToken
            );

            log.info("请求微信 JS API Ticket");
            String response = restTemplate.getForObject(url, String.class);
            JsonNode jsonNode = objectMapper.readTree(response);

            if (jsonNode.has("ticket") && jsonNode.get("errcode").asInt() == 0) {
                cachedJsApiTicket = jsonNode.get("ticket").asText();
                jsApiTicketExpireTime = now + weChatConfig.getJsApiTicketExpire() * 1000L;
                log.info("获取 JS API Ticket 成功，有效期至: {}", new Date(jsApiTicketExpireTime));
                return cachedJsApiTicket;
            } else {
                int errcode = jsonNode.has("errcode") ? jsonNode.get("errcode").asInt() : -1;
                String errmsg = jsonNode.has("errmsg") ? jsonNode.get("errmsg").asText() : "unknown";
                log.error("获取 JS API Ticket 失败: errcode={}, errmsg={}", errcode, errmsg);
                throw new RuntimeException("获取微信 JS API Ticket 失败: " + errmsg);
            }
        } catch (Exception e) {
            log.error("获取 JS API Ticket 异常", e);
            throw new RuntimeException("获取微信 JS API Ticket 异常", e);
        }
    }

    /**
     * 生成微信 JS-SDK 签名配置
     *
     * @param url 当前页面URL（不包含#及其后面部分）
     * @return 签名配置 Map，包含 appId, timestamp, nonceStr, signature
     */
    public Map<String, String> generateSignature(String url) {
        try {
            // 获取 JS API Ticket
            String jsApiTicket = getJsApiTicket();

            // 生成随机字符串
            String nonceStr = generateNonceStr();

            // 生成时间戳
            long timestamp = System.currentTimeMillis() / 1000;

            // 按照微信要求的格式拼接字符串
            String signStr = String.format(
                "jsapi_ticket=%s&noncestr=%s&timestamp=%d&url=%s",
                jsApiTicket,
                nonceStr,
                timestamp,
                url
            );

            log.info("签名字符串: {}", signStr);

            // SHA1 加密
            String signature = sha1(signStr);

            log.info("生成签名成功: url={}, signature={}", url, signature);

            // 返回配置
            Map<String, String> config = new HashMap<>();
            config.put("appId", weChatConfig.getAppId());
            config.put("timestamp", String.valueOf(timestamp));
            config.put("nonceStr", nonceStr);
            config.put("signature", signature);

            return config;
        } catch (Exception e) {
            log.error("生成微信签名异常", e);
            throw new RuntimeException("生成微信签名失败", e);
        }
    }

    /**
     * 生成随机字符串
     */
    private String generateNonceStr() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * SHA1 加密
     */
    private String sha1(String str) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(str.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA1加密失败", e);
        }
    }
}
