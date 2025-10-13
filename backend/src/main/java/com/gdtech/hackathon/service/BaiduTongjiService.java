package com.gdtech.hackathon.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdtech.hackathon.config.BaiduConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 百度统计服务
 * 用于从百度统计API获取UV数据
 * 支持多个百度统计账号
 */
@Slf4j
@Service
public class BaiduTongjiService {

    private final BaiduConfig baiduConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public BaiduTongjiService(BaiduConfig baiduConfig) {
        this.baiduConfig = baiduConfig;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 检查指定账号的token是否过期
     */
    private boolean isTokenExpired(String accountName) {
        BaiduConfig.AccountCredentials credentials = baiduConfig.getAccountCredentials(accountName);
        if (credentials == null || credentials.getTokenExpires() == null) {
            return false;
        }
        return System.currentTimeMillis() >= credentials.getTokenExpires();
    }

    /**
     * 刷新指定账号的Access Token
     */
    public void refreshAccessToken(String accountName) {
        try {
            BaiduConfig.AccountCredentials credentials = baiduConfig.getAccountCredentials(accountName);
            if (credentials == null) {
                log.error("账号 {} 配置不存在", accountName);
                return;
            }

            log.info("开始刷新账号 {} 的access_token...", accountName);

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type", "refresh_token");
            params.add("refresh_token", credentials.getRefreshToken());
            params.add("client_id", credentials.getClientId());
            params.add("client_secret", credentials.getClientSecret());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    baiduConfig.getTokenUrl(),
                    request,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());

                if (jsonNode.has("access_token")) {
                    String newAccessToken = jsonNode.get("access_token").asText();
                    credentials.setAccessToken(newAccessToken);

                    if (jsonNode.has("refresh_token")) {
                        credentials.setRefreshToken(jsonNode.get("refresh_token").asText());
                    }

                    if (jsonNode.has("expires_in")) {
                        long expiresIn = jsonNode.get("expires_in").asLong();
                        credentials.setTokenExpires(System.currentTimeMillis() + (expiresIn * 1000));
                    }

                    log.info("账号 {} Token刷新成功", accountName);
                } else {
                    log.error("账号 {} 刷新令牌失败: {}", accountName, response.getBody());
                }
            }
        } catch (Exception e) {
            log.error("账号 {} 刷新访问令牌失败", accountName, e);
        }
    }

    /**
     * 确保指定账号的Token有效
     */
    private void ensureValidToken(String accountName) {
        if (isTokenExpired(accountName)) {
            log.info("账号 {} Token已过期，正在刷新...", accountName);
            refreshAccessToken(accountName);
        }
    }

    /**
     * 获取站点UV数据
     *
     * @param accountName 百度统计账号标识
     * @param siteId      站点ID
     * @param startDate   开始日期
     * @param endDate     结束日期
     * @return UV数据
     */
    public Integer getSiteUV(String accountName, String siteId, String startDate, String endDate) {
        try {
            BaiduConfig.AccountCredentials credentials = baiduConfig.getAccountCredentials(accountName);
            if (credentials == null) {
                log.error("账号 {} 配置不存在", accountName);
                return null;
            }

            ensureValidToken(accountName);

            String url = String.format(
                    "%s?access_token=%s&site_id=%s&start_date=%s&end_date=%s&metrics=visitor_count&method=overview/getTimeTrendRpt&gran=day&max_results=0",
                    baiduConfig.getApiUrl(),
                    credentials.getAccessToken(),
                    siteId,
                    startDate,
                    endDate
            );

            log.debug("调用百度统计API: account={}, site_id={}, dates={}-{}", accountName, siteId, startDate, endDate);

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());

                // 检查返回状态
                if (jsonNode.has("header")) {
                    JsonNode header = jsonNode.get("header");
                    int status = header.get("status").asInt();

                    if (status == 0) {
                        // 成功获取数据
                        JsonNode body = jsonNode.get("body");
                        if (body != null && body.has("data") && body.get("data").isArray()) {
                            JsonNode dataArray = body.get("data");
                            if (dataArray.size() > 0) {
                                JsonNode firstRow = dataArray.get(0);
                                if (firstRow.isArray() && firstRow.size() > 0) {
                                    // 第一个元素是总UV
                                    int uv = firstRow.get(0).asInt();
                                    log.info("获取站点 {} (账号: {}) UV成功: {}", siteId, accountName, uv);
                                    return uv;
                                }
                            }
                        }
                    } else if (status == 200106 || status == 2) {
                        // Token无效，刷新后重试
                        log.warn("账号 {} Token可能无效，尝试刷新后重试...", accountName);
                        refreshAccessToken(accountName);
                        return getSiteUV(accountName, siteId, startDate, endDate);
                    } else {
                        log.error("百度统计API返回错误: account={}, status={}, message={}",
                                accountName,
                                status,
                                header.has("desc") ? header.get("desc").asText() : "unknown");
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取站点UV失败: account={}, site_id={}", accountName, siteId, e);
        }

        return null;
    }

    /**
     * 获取站点当日UV
     *
     * @param accountName 百度统计账号标识
     * @param siteId      站点ID
     * @return UV数
     */
    public Integer getTodayUV(String accountName, String siteId) {
        LocalDate today = LocalDate.now();
        String dateStr = today.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return getSiteUV(accountName, siteId, dateStr, dateStr);
    }

    /**
     * 获取站点累计UV（最近N天）
     *
     * @param accountName 百度统计账号标识
     * @param siteId      站点ID
     * @param days        天数
     * @return 累计UV
     */
    public Integer getCumulativeUV(String accountName, String siteId, int days) {
        LocalDate today = LocalDate.now();
        LocalDate startDay = today.minusDays(days - 1);

        String startDate = startDay.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String endDate = today.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        return getSiteUV(accountName, siteId, startDate, endDate);
    }

    /**
     * 批量获取所有项目的UV数据
     *
     * @param projectSites Map<项目ID, {accountName, siteId}>
     * @return Map<项目ID, UV数>
     */
    public Map<Long, Integer> batchGetUV(Map<Long, ProjectSiteInfo> projectSites) {
        Map<Long, Integer> uvMap = new HashMap<>();

        for (Map.Entry<Long, ProjectSiteInfo> entry : projectSites.entrySet()) {
            Long projectId = entry.getKey();
            ProjectSiteInfo siteInfo = entry.getValue();

            try {
                // 获取累计UV（比如最近30天）
                Integer uv = getCumulativeUV(siteInfo.getAccountName(), siteInfo.getSiteId(), 30);
                if (uv != null) {
                    uvMap.put(projectId, uv);
                    log.debug("项目 {} UV: {}", projectId, uv);
                }
            } catch (Exception e) {
                log.error("获取项目 {} UV失败", projectId, e);
            }
        }

        return uvMap;
    }

    /**
     * 项目站点信息
     */
    public static class ProjectSiteInfo {
        private String accountName;
        private String siteId;

        public ProjectSiteInfo(String accountName, String siteId) {
            this.accountName = accountName;
            this.siteId = siteId;
        }

        public String getAccountName() {
            return accountName;
        }

        public String getSiteId() {
            return siteId;
        }
    }
}
