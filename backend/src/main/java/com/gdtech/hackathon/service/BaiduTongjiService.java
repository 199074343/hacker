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

import jakarta.annotation.PostConstruct;
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
     * 服务启动时初始化所有账号的Token
     */
    @PostConstruct
    public void initializeTokens() {
        log.info("开始初始化百度统计账号的Token...");
        for (Map.Entry<String, BaiduConfig.AccountCredentials> entry : baiduConfig.getAccounts().entrySet()) {
            String accountName = entry.getKey();
            BaiduConfig.AccountCredentials credentials = entry.getValue();

            // 跳过未配置client_id的账号
            if (credentials.getClientId() == null || credentials.getClientId().startsWith("your_")) {
                log.warn("账号 {} 未配置，跳过Token初始化", accountName);
                continue;
            }

            try {
                log.info("初始化账号 {} 的Token...", accountName);

                // 如果已经配置了access_token，直接使用（来自配置文件）
                if (credentials.getAccessToken() != null && !credentials.getAccessToken().isEmpty()) {
                    log.info("账号 {} 已有预配置的access_token，跳过自动获取", accountName);
                    log.info("账号 {} Token初始化成功（使用预配置Token）", accountName);
                } else {
                    // 否则通过client_credentials获取（注意：该方式获取的token无法访问统计数据API）
                    log.warn("账号 {} 未配置access_token，尝试通过client_credentials获取（可能无法访问统计数据API）", accountName);
                    getTokenByClientCredentials(accountName);
                    log.info("账号 {} Token初始化成功", accountName);
                }
            } catch (Exception e) {
                log.error("账号 {} Token初始化失败", accountName, e);
            }
        }
        log.info("百度统计账号Token初始化完成");
    }

    /**
     * 通过Client Credentials获取初始Token
     * 使用client_id和client_secret获取access_token
     */
    public void getTokenByClientCredentials(String accountName) {
        try {
            BaiduConfig.AccountCredentials credentials = baiduConfig.getAccountCredentials(accountName);
            if (credentials == null) {
                log.error("账号 {} 配置不存在", accountName);
                return;
            }

            log.info("通过Client Credentials获取账号 {} 的token...", accountName);

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type", "client_credentials");
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
                    String accessToken = jsonNode.get("access_token").asText();
                    credentials.setAccessToken(accessToken);

                    if (jsonNode.has("refresh_token")) {
                        credentials.setRefreshToken(jsonNode.get("refresh_token").asText());
                    }

                    if (jsonNode.has("expires_in")) {
                        long expiresIn = jsonNode.get("expires_in").asLong();
                        credentials.setTokenExpires(System.currentTimeMillis() + (expiresIn * 1000));
                        log.info("账号 {} Token获取成功，过期时间: {} 秒后", accountName, expiresIn);
                    }
                } else {
                    log.error("账号 {} 获取Token失败: {}", accountName, response.getBody());
                }
            }
        } catch (Exception e) {
            log.error("账号 {} 通过Client Credentials获取Token失败", accountName, e);
        }
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
        BaiduConfig.AccountCredentials credentials = baiduConfig.getAccountCredentials(accountName);
        if (credentials == null) {
            log.error("账号 {} 配置不存在", accountName);
            return;
        }

        // 如果没有access_token，先通过client credentials获取
        if (credentials.getAccessToken() == null || credentials.getAccessToken().isEmpty()) {
            log.info("账号 {} 没有access_token，正在获取...", accountName);
            getTokenByClientCredentials(accountName);
        } else if (isTokenExpired(accountName)) {
            log.info("账号 {} Token已过期，正在刷新...", accountName);
            // 如果有refresh_token，使用refresh_token刷新，否则重新获取
            if (credentials.getRefreshToken() != null && !credentials.getRefreshToken().isEmpty()) {
                refreshAccessToken(accountName);
            } else {
                getTokenByClientCredentials(accountName);
            }
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

                // 支持两种响应格式：
                // 1. 新格式: {"result": {"items": [[dates], [uv_values]]}}
                // 2. 旧格式: {"header": {"status": 0}, "body": {"data": [[uv, ...]]}}

                // 检查新格式 (result格式)
                if (jsonNode.has("result")) {
                    JsonNode result = jsonNode.get("result");
                    if (result.has("items") && result.get("items").isArray()) {
                        JsonNode items = result.get("items");
                        // items[0] 是日期数组, items[1] 是UV数组
                        if (items.size() >= 2) {
                            JsonNode uvArray = items.get(1);
                            int totalUV = 0;
                            // 累加所有天的UV（跳过"--"等无效值）
                            for (JsonNode uvNode : uvArray) {
                                if (uvNode.isArray() && uvNode.size() > 0) {
                                    JsonNode value = uvNode.get(0);
                                    if (value.isNumber()) {
                                        totalUV += value.asInt();
                                    }
                                }
                            }
                            log.info("获取站点 {} (账号: {}) UV成功: {}", siteId, accountName, totalUV);
                            return totalUV;
                        }
                    }
                }

                // 检查旧格式 (header格式)
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
     * 获取实时访客数（使用实时接口 trend/latest/a）
     *
     * @param accountName 百度统计账号标识
     * @param siteId      站点ID
     * @return 当前实时访客数
     */
    public Integer getRealtimeVisitors(String accountName, String siteId) {
        try {
            BaiduConfig.AccountCredentials credentials = baiduConfig.getAccountCredentials(accountName);
            if (credentials == null) {
                log.error("账号 {} 配置不存在", accountName);
                return null;
            }

            ensureValidToken(accountName);

            // 使用实时接口
            String url = String.format(
                    "%s?access_token=%s&site_id=%s&method=trend/latest/a&metrics=start_time,area,source,access_page",
                    baiduConfig.getApiUrl(),
                    credentials.getAccessToken(),
                    siteId
            );

            log.debug("调用百度统计实时API: account={}, site_id={}", accountName, siteId);

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());

                // 检查响应格式
                if (jsonNode.has("header")) {
                    JsonNode header = jsonNode.get("header");
                    int status = header.has("status") ? header.get("status").asInt() : -1;

                    if (status == 0) {
                        // 成功获取数据
                        JsonNode body = jsonNode.get("body");
                        if (body != null && body.has("data")) {
                            JsonNode dataArray = body.get("data");
                            if (dataArray.isArray()) {
                                int visitorCount = dataArray.size();
                                log.info("实时访客数获取成功: account={}, site_id={}, count={}", accountName, siteId, visitorCount);
                                return visitorCount;
                            }
                        }
                        return 0; // 没有访客
                    } else {
                        log.error("百度统计实时API返回错误: account={}, status={}, message={}",
                                accountName, status, header.has("desc") ? header.get("desc").asText() : "unknown");
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取实时访客数异常: account={}, site_id={}", accountName, siteId, e);
        }

        return null;
    }

    /**
     * 获取站点当日UV（使用实时趋势接口，更快）
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
     * 获取站点累计UV（所有历史数据，不限日期范围）
     *
     * @param accountName 百度统计账号标识
     * @param siteId      站点ID
     * @return 累计UV（所有历史数据）
     */
    public Integer getCumulativeUVFromStart(String accountName, String siteId) {
        // 使用足够早的日期作为起始日期，覆盖所有历史数据
        LocalDate startDate = LocalDate.of(2020, 1, 1);
        LocalDate today = LocalDate.now();

        String startDateStr = startDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String endDateStr = today.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        log.info("获取所有历史累计UV: account={}, site={}, 日期范围={} 到 {}",
                accountName, siteId, startDateStr, endDateStr);

        return getSiteUV(accountName, siteId, startDateStr, endDateStr);
    }

    /**
     * 获取站点累计UV（最近N天）- 保留用于其他场景
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
