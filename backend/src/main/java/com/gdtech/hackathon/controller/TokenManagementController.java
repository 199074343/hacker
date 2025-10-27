package com.gdtech.hackathon.controller;

import com.gdtech.hackathon.config.BaiduConfig;
import com.gdtech.hackathon.service.BaiduTongjiService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Token管理接口
 * 用于手动更新百度统计Token
 */
@Slf4j
@RestController
@RequestMapping("/hackathon/token")
@RequiredArgsConstructor
public class TokenManagementController {

    private final BaiduConfig baiduConfig;
    private final BaiduTongjiService baiduTongjiService;

    /**
     * 更新账号的access_token和refresh_token
     */
    @PostMapping("/update/{accountName}")
    public Map<String, Object> updateToken(
            @PathVariable String accountName,
            @RequestBody TokenUpdateRequest request) {

        Map<String, Object> result = new HashMap<>();

        try {
            BaiduConfig.AccountCredentials credentials = baiduConfig.getAccountCredentials(accountName);
            if (credentials == null) {
                result.put("success", false);
                result.put("message", "账号 " + accountName + " 不存在");
                return result;
            }

            // 更新token
            credentials.setAccessToken(request.getAccessToken());
            if (request.getRefreshToken() != null && !request.getRefreshToken().isEmpty()) {
                credentials.setRefreshToken(request.getRefreshToken());
            }

            // 重置失败计数
            credentials.markRefreshSuccess();

            // 设置过期时间（默认30天）
            long expiresIn = request.getExpiresIn() != null ? request.getExpiresIn() : 2592000L;
            credentials.setTokenExpires(System.currentTimeMillis() + (expiresIn * 1000));

            log.info("账号 {} Token已手动更新", accountName);

            result.put("success", true);
            result.put("message", "Token更新成功");
            result.put("accountName", accountName);
            return result;

        } catch (Exception e) {
            log.error("更新Token失败", e);
            result.put("success", false);
            result.put("message", "更新失败: " + e.getMessage());
            return result;
        }
    }

    /**
     * 查询账号Token状态
     */
    @GetMapping("/status/{accountName}")
    public Map<String, Object> getTokenStatus(@PathVariable String accountName) {
        Map<String, Object> result = new HashMap<>();

        BaiduConfig.AccountCredentials credentials = baiduConfig.getAccountCredentials(accountName);
        if (credentials == null) {
            result.put("success", false);
            result.put("message", "账号 " + accountName + " 不存在");
            return result;
        }

        result.put("success", true);
        result.put("accountName", accountName);
        result.put("hasAccessToken", credentials.getAccessToken() != null && !credentials.getAccessToken().isEmpty());
        result.put("hasRefreshToken", credentials.getRefreshToken() != null && !credentials.getRefreshToken().isEmpty());
        result.put("refreshFailedCount", credentials.getRefreshFailedCount());
        result.put("lastRefreshFailedTime", credentials.getLastRefreshFailedTime());
        result.put("shouldSkipRefresh", credentials.shouldSkipRefresh());

        if (credentials.getTokenExpires() != null) {
            long remainingTime = credentials.getTokenExpires() - System.currentTimeMillis();
            result.put("tokenExpires", credentials.getTokenExpires());
            result.put("isExpired", remainingTime <= 0);
            result.put("remainingSeconds", remainingTime / 1000);
        }

        return result;
    }

    /**
     * 使用authorization code换取token
     */
    @PostMapping("/exchange/{accountName}")
    public Map<String, Object> exchangeToken(
            @PathVariable String accountName,
            @RequestBody CodeExchangeRequest request) {

        Map<String, Object> result = new HashMap<>();

        try {
            BaiduConfig.AccountCredentials credentials = baiduConfig.getAccountCredentials(accountName);
            if (credentials == null) {
                result.put("success", false);
                result.put("message", "账号 " + accountName + " 不存在");
                return result;
            }

            // 调用百度API换取token
            String code = request.getCode();
            // TODO: 实现authorization code换取token的逻辑
            // 目前需要手动使用脚本换取，然后调用updateToken接口

            result.put("success", false);
            result.put("message", "请使用 /tmp/exchange-token.sh 脚本换取token，然后调用 /update 接口更新");
            result.put("authUrl", String.format(
                    "https://openapi.baidu.com/oauth/2.0/authorize?response_type=code&client_id=%s&redirect_uri=oob&scope=basic",
                    credentials.getClientId()
            ));

            return result;

        } catch (Exception e) {
            log.error("换取Token失败", e);
            result.put("success", false);
            result.put("message", "换取失败: " + e.getMessage());
            return result;
        }
    }

    @Data
    public static class TokenUpdateRequest {
        private String accessToken;
        private String refreshToken;
        private Long expiresIn; // 秒
    }

    @Data
    public static class CodeExchangeRequest {
        private String code;
    }
}
