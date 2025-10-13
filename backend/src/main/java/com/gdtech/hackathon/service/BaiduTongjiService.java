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
 */
@Slf4j
@Service
public class BaiduTongjiService {

    private final BaiduConfig baiduConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // Token过期时间
    private Long tokenExpires;

    public BaiduTongjiService(BaiduConfig baiduConfig) {
        this.baiduConfig = baiduConfig;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 检查token是否过期
     */
    private boolean isTokenExpired() {
        if (tokenExpires == null) {
            return false;
        }
        return System.currentTimeMillis() >= tokenExpires;
    }

    /**
     * 刷新Access Token
     */
    public void refreshAccessToken() {
        try {
            log.info("开始刷新百度统计access_token...");

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type", "refresh_token");
            params.add("refresh_token", baiduConfig.getRefreshToken());
            params.add("client_id", baiduConfig.getClientId());
            params.add("client_secret", baiduConfig.getClientSecret());

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
                    baiduConfig.setAccessToken(newAccessToken);

                    if (jsonNode.has("refresh_token")) {
                        baiduConfig.setRefreshToken(jsonNode.get("refresh_token").asText());
                    }

                    if (jsonNode.has("expires_in")) {
                        long expiresIn = jsonNode.get("expires_in").asLong();
                        tokenExpires = System.currentTimeMillis() + (expiresIn * 1000);
                    }

                    log.info("Token刷新成功");
                } else {
                    log.error("刷新令牌失败: {}", response.getBody());
                }
            }
        } catch (Exception e) {
            log.error("刷新访问令牌失败", e);
        }
    }

    /**
     * 确保Token有效
     */
    private void ensureValidToken() {
        if (isTokenExpired()) {
            log.info("Token已过期，正在刷新...");
            refreshAccessToken();
        }
    }

    /**
     * 获取站点UV数据
     *
     * @param siteId    站点ID
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return UV数据
     */
    public Integer getSiteUV(String siteId, String startDate, String endDate) {
        try {
            ensureValidToken();

            String url = String.format(
                    "%s?access_token=%s&site_id=%s&start_date=%s&end_date=%s&metrics=visitor_count&method=overview/getTimeTrendRpt&gran=day&max_results=0",
                    baiduConfig.getApiUrl(),
                    baiduConfig.getAccessToken(),
                    siteId,
                    startDate,
                    endDate
            );

            log.debug("调用百度统计API: site_id={}, dates={}-{}", siteId, startDate, endDate);

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
                                    log.info("获取站点 {} UV成功: {}", siteId, uv);
                                    return uv;
                                }
                            }
                        }
                    } else if (status == 200106 || status == 2) {
                        // Token无效，刷新后重试
                        log.warn("Token可能无效，尝试刷新后重试...");
                        refreshAccessToken();
                        return getSiteUV(siteId, startDate, endDate);
                    } else {
                        log.error("百度统计API返回错误: status={}, message={}",
                                status,
                                header.has("desc") ? header.get("desc").asText() : "unknown");
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取站点UV失败: site_id={}", siteId, e);
        }

        return null;
    }

    /**
     * 获取站点当日UV
     *
     * @param siteId 站点ID
     * @return UV数
     */
    public Integer getTodayUV(String siteId) {
        LocalDate today = LocalDate.now();
        String dateStr = today.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return getSiteUV(siteId, dateStr, dateStr);
    }

    /**
     * 获取站点累计UV（最近N天）
     *
     * @param siteId 站点ID
     * @param days   天数
     * @return 累计UV
     */
    public Integer getCumulativeUV(String siteId, int days) {
        LocalDate today = LocalDate.now();
        LocalDate startDay = today.minusDays(days - 1);

        String startDate = startDay.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String endDate = today.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        return getSiteUV(siteId, startDate, endDate);
    }

    /**
     * 批量获取所有项目的UV数据
     *
     * @param siteIds 站点ID列表（Map<项目ID, 站点ID>）
     * @return Map<项目ID, UV数>
     */
    public Map<Long, Integer> batchGetUV(Map<Long, String> siteIds) {
        Map<Long, Integer> uvMap = new HashMap<>();

        for (Map.Entry<Long, String> entry : siteIds.entrySet()) {
            Long projectId = entry.getKey();
            String siteId = entry.getValue();

            try {
                // 获取累计UV（比如最近30天）
                Integer uv = getCumulativeUV(siteId, 30);
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
}
