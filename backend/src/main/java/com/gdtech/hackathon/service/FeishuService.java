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
     * @param filter   过滤条件
     * @param pageSize 分页大小
     * @return 记录列表
     */
    public List<Map<String, Object>> listRecords(String tableId, String filter, int pageSize) {
        try {
            String token = getTenantAccessToken();
            String appToken = feishuConfig.getBase().getAppToken();

            List<Map<String, Object>> allRecords = new ArrayList<>();
            String pageToken = null;
            boolean hasMore = true;

            while (hasMore) {
                String uri = String.format("/bitable/v1/apps/%s/tables/%s/records?page_size=%d",
                        appToken, tableId, pageSize);

                if (pageToken != null) {
                    uri += "&page_token=" + pageToken;
                }
                if (filter != null) {
                    uri += "&filter=" + filter;
                }

                String response = webClient.get()
                        .uri(uri)
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                JsonNode jsonNode = objectMapper.readTree(response);
                if (jsonNode.get("code").asInt() == 0) {
                    JsonNode data = jsonNode.get("data");
                    JsonNode items = data.get("items");

                    if (items != null && items.isArray()) {
                        items.forEach(item -> {
                            Map<String, Object> record = new HashMap<>();
                            record.put("record_id", item.get("record_id").asText());

                            JsonNode fields = item.get("fields");
                            if (fields != null) {
                                fields.fields().forEachRemaining(entry -> {
                                    record.put(entry.getKey(), convertJsonNode(entry.getValue()));
                                });
                            }

                            allRecords.add(record);
                        });
                    }

                    hasMore = data.get("has_more").asBoolean();
                    if (hasMore && data.has("page_token")) {
                        pageToken = data.get("page_token").asText();
                    } else {
                        hasMore = false;
                    }
                } else {
                    log.error("查询飞书表格记录失败: {}", response);
                    throw new RuntimeException("查询飞书表格记录失败: " + jsonNode.get("msg").asText());
                }
            }

            log.debug("从表 {} 查询到 {} 条记录", tableId, allRecords.size());
            return allRecords;
        } catch (Exception e) {
            log.error("查询飞书表格记录异常", e);
            throw new RuntimeException("查询飞书表格记录异常", e);
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
