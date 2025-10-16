package com.gdtech.hackathon.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdtech.hackathon.config.FeishuConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

/**
 * 飞书API服务
 */
@Slf4j
@Service
public class FeishuService {

    private final FeishuConfig feishuConfig;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    // 飞书 API 基础URL
    private static final String FEISHU_API_BASE = "https://open.feishu.cn/open-apis";

    public FeishuService(FeishuConfig feishuConfig, ObjectMapper objectMapper) {
        this.feishuConfig = feishuConfig;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(FEISHU_API_BASE)
                .build();
    }

    /**
     * 获取 tenant_access_token（租户访问凭证）
     * 缓存5分钟
     */
    @Cacheable(value = "feishuToken", unless = "#result == null")
    public String getTenantAccessToken() {
        try {
            Map<String, String> body = new HashMap<>();
            body.put("app_id", feishuConfig.getAppId());
            body.put("app_secret", feishuConfig.getAppSecret());

            String response = webClient.post()
                    .uri("/auth/v3/tenant_access_token/internal")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode jsonNode = objectMapper.readTree(response);
            if (jsonNode.get("code").asInt() == 0) {
                String token = jsonNode.get("tenant_access_token").asText();
                log.debug("获取飞书token成功: {}", token);
                return token;
            } else {
                log.error("获取飞书token失败: {}", response);
                throw new RuntimeException("获取飞书token失败: " + jsonNode.get("msg").asText());
            }
        } catch (Exception e) {
            log.error("获取飞书token异常", e);
            throw new RuntimeException("获取飞书token异常", e);
        }
    }

    /**
     * 查询多维表格记录列表
     *
     * @param tableId 表ID
     * @return 记录列表
     */
    public List<Map<String, Object>> listRecords(String tableId) {
        return listRecords(tableId, null, 500);
    }

    /**
     * 查询多维表格记录列表（带过滤条件）
     *
     * @param tableId  表ID
     * @param filter   过滤条件（目前未使用，保留用于未来扩展）
     * @param pageSize 分页大小
     * @return 记录列表
     */
    public List<Map<String, Object>> listRecords(String tableId, String filter, int pageSize) {
        // 参数校验
        if (tableId == null || tableId.trim().isEmpty()) {
            log.error("tableId不能为空");
            return Collections.emptyList();
        }

        // 限制单次查询最大分页大小，防止飞书API超时
        if (pageSize > 500) {
            log.warn("分页大小{}超过上限，调整为500", pageSize);
            pageSize = 500;
        }
        if (pageSize < 1) {
            log.warn("分页大小{}无效，调整为100", pageSize);
            pageSize = 100;
        }

        try {
            String token = getTenantAccessToken();
            String appToken = feishuConfig.getBase().getAppToken();

            if (token == null || token.isEmpty()) {
                log.error("无法获取飞书访问令牌");
                return Collections.emptyList();
            }
            if (appToken == null || appToken.isEmpty()) {
                log.error("飞书应用令牌未配置");
                return Collections.emptyList();
            }

            List<Map<String, Object>> allRecords = new ArrayList<>();
            String pageToken = null;
            boolean hasMore = true;
            int pageCount = 0;
            int maxPages = 100; // 防止无限循环，最多查询100页（50000条记录）

            while (hasMore && pageCount < maxPages) {
                pageCount++;

                String uri = String.format("/bitable/v1/apps/%s/tables/%s/records?page_size=%d",
                        appToken, tableId, pageSize);

                if (pageToken != null && !pageToken.isEmpty()) {
                    uri += "&page_token=" + pageToken;
                }
                if (filter != null && !filter.trim().isEmpty()) {
                    uri += "&filter=" + java.net.URLEncoder.encode(filter, java.nio.charset.StandardCharsets.UTF_8);
                }

                String response = webClient.get()
                        .uri(uri)
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                if (response == null || response.isEmpty()) {
                    log.error("飞书API返回空响应");
                    break;
                }

                JsonNode jsonNode = objectMapper.readTree(response);
                JsonNode codeNode = jsonNode.get("code");
                if (codeNode == null) {
                    log.error("飞书API响应缺少code字段: {}", response);
                    break;
                }

                if (codeNode.asInt() == 0) {
                    JsonNode data = jsonNode.get("data");
                    if (data == null) {
                        log.warn("飞书API返回的data字段为空");
                        break;
                    }

                    JsonNode items = data.get("items");
                    if (items != null && items.isArray()) {
                        items.forEach(item -> {
                            try {
                                Map<String, Object> record = new HashMap<>();
                                JsonNode recordIdNode = item.get("record_id");
                                if (recordIdNode != null) {
                                    record.put("record_id", recordIdNode.asText());
                                }

                                JsonNode fields = item.get("fields");
                                if (fields != null && fields.isObject()) {
                                    fields.fields().forEachRemaining(entry -> {
                                        try {
                                            record.put(entry.getKey(), convertJsonNode(entry.getValue()));
                                        } catch (Exception e) {
                                            log.warn("转换字段{}失败: {}", entry.getKey(), e.getMessage());
                                        }
                                    });
                                }

                                allRecords.add(record);
                            } catch (Exception e) {
                                log.warn("处理记录失败: {}", e.getMessage());
                            }
                        });
                    }

                    JsonNode hasMoreNode = data.get("has_more");
                    hasMore = hasMoreNode != null && hasMoreNode.asBoolean();

                    if (hasMore && data.has("page_token")) {
                        JsonNode pageTokenNode = data.get("page_token");
                        pageToken = pageTokenNode != null ? pageTokenNode.asText() : null;
                        if (pageToken == null || pageToken.isEmpty()) {
                            log.warn("飞书API返回has_more=true但page_token为空，停止分页");
                            hasMore = false;
                        }
                    } else {
                        hasMore = false;
                    }
                } else {
                    String msg = jsonNode.has("msg") ? jsonNode.get("msg").asText() : "未知错误";
                    log.error("查询飞书表格记录失败(code={}): {}", codeNode.asInt(), msg);
                    throw new RuntimeException("查询飞书表格记录失败: " + msg);
                }
            }

            if (pageCount >= maxPages) {
                log.warn("查询表{}已达到最大分页数{}，可能存在更多数据未获取", tableId, maxPages);
            }

            log.debug("从表 {} 查询到 {} 条记录（共{}页）", tableId, allRecords.size(), pageCount);
            return allRecords;
        } catch (Exception e) {
            log.error("查询飞书表格记录异常，tableId={}", tableId, e);
            throw new RuntimeException("查询飞书表格记录异常: " + e.getMessage(), e);
        }
    }

    /**
     * 新增多维表格记录
     *
     * @param tableId 表ID
     * @param fields  字段数据
     * @return 新增记录的ID
     */
    public String createRecord(String tableId, Map<String, Object> fields) {
        try {
            String token = getTenantAccessToken();
            String appToken = feishuConfig.getBase().getAppToken();

            Map<String, Object> body = new HashMap<>();
            body.put("fields", fields);

            String response = webClient.post()
                    .uri(String.format("/bitable/v1/apps/%s/tables/%s/records", appToken, tableId))
                    .header("Authorization", "Bearer " + token)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode jsonNode = objectMapper.readTree(response);
            if (jsonNode.get("code").asInt() == 0) {
                String recordId = jsonNode.get("data").get("record").get("record_id").asText();
                log.debug("新增飞书表格记录成功: {}", recordId);
                return recordId;
            } else {
                log.error("新增飞书表格记录失败: {}", response);
                throw new RuntimeException("新增飞书表格记录失败: " + jsonNode.get("msg").asText());
            }
        } catch (Exception e) {
            log.error("新增飞书表格记录异常", e);
            throw new RuntimeException("新增飞书表格记录异常", e);
        }
    }

    /**
     * 更新多维表格记录
     *
     * @param tableId  表ID
     * @param recordId 记录ID
     * @param fields   字段数据
     */
    public void updateRecord(String tableId, String recordId, Map<String, Object> fields) {
        try {
            String token = getTenantAccessToken();
            String appToken = feishuConfig.getBase().getAppToken();

            Map<String, Object> body = new HashMap<>();
            body.put("fields", fields);

            String response = webClient.put()
                    .uri(String.format("/bitable/v1/apps/%s/tables/%s/records/%s", appToken, tableId, recordId))
                    .header("Authorization", "Bearer " + token)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode jsonNode = objectMapper.readTree(response);
            if (jsonNode.get("code").asInt() == 0) {
                log.debug("更新飞书表格记录成功: {}", recordId);
            } else {
                log.error("更新飞书表格记录失败: {}", response);
                throw new RuntimeException("更新飞书表格记录失败: " + jsonNode.get("msg").asText());
            }
        } catch (Exception e) {
            log.error("更新飞书表格记录异常", e);
            throw new RuntimeException("更新飞书表格记录异常", e);
        }
    }

    /**
     * 转换 JsonNode 为Java对象
     */
    private Object convertJsonNode(JsonNode node) {
        if (node.isNull()) {
            return null;
        } else if (node.isBoolean()) {
            return node.asBoolean();
        } else if (node.isInt() || node.isLong()) {
            return node.asLong();
        } else if (node.isDouble() || node.isFloat()) {
            return node.asDouble();
        } else if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(item -> list.add(convertJsonNode(item)));
            return list;
        } else if (node.isObject()) {
            Map<String, Object> map = new HashMap<>();
            node.fields().forEachRemaining(entry ->
                map.put(entry.getKey(), convertJsonNode(entry.getValue())));
            return map;
        } else {
            return node.asText();
        }
    }
}
