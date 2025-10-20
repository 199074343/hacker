package com.gdtech.hackathon.schedule;

import com.gdtech.hackathon.config.BaiduConfig;
import com.gdtech.hackathon.config.FeishuConfig;
import com.gdtech.hackathon.model.CompetitionStage;
import com.gdtech.hackathon.service.BaiduTongjiService;
import com.gdtech.hackathon.service.FeishuService;
import com.gdtech.hackathon.service.HackathonService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UV数据同步定时任务
 * 定期从百度统计API获取UV数据并更新到飞书表格
 * 支持多个百度统计账号
 */
@Slf4j
@Component
public class UVSyncScheduler {

    private final BaiduTongjiService baiduTongjiService;
    private final FeishuService feishuService;
    private final FeishuConfig feishuConfig;
    private final BaiduConfig baiduConfig;
    private final HackathonService hackathonService;

    public UVSyncScheduler(
            BaiduTongjiService baiduTongjiService,
            FeishuService feishuService,
            FeishuConfig feishuConfig,
            BaiduConfig baiduConfig,
            HackathonService hackathonService
    ) {
        this.baiduTongjiService = baiduTongjiService;
        this.feishuService = feishuService;
        this.feishuConfig = feishuConfig;
        this.baiduConfig = baiduConfig;
        this.hackathonService = hackathonService;
    }

    /**
     * 同步所有项目的UV数据
     * 根据配置的同步间隔定期执行（默认10分钟）
     * UV更新后清除所有项目相关缓存 (afterInvocation=true确保在同步完成后清除)
     */
    @Scheduled(fixedDelayString = "${baidu.tongji.sync-interval:10}000", initialDelay = 60000)
    @Caching(evict = {
            @CacheEvict(value = "projects", allEntries = true, beforeInvocation = false),
            @CacheEvict(value = "project", allEntries = true, beforeInvocation = false)
    })
    public void syncAllProjectUV() {
        try {
            // 检查当前阶段，如果是结束阶段则跳过同步
            CompetitionStage currentStage = hackathonService.getCurrentStage();
            if (currentStage == CompetitionStage.ENDED) {
                log.info("当前为结束阶段，跳过UV数据同步");
                return;
            }

            log.info("开始同步项目UV数据...");

            // 获取所有项目
            String tableId = feishuConfig.getProjectsTableId();
            List<Map<String, Object>> records = feishuService.listRecords(tableId);

            if (records.isEmpty()) {
                log.info("没有需要同步的项目");
                return;
            }

            int successCount = 0;
            int failCount = 0;

            for (Map<String, Object> record : records) {
                try {
                    String recordId = (String) record.get("record_id");
                    Long projectId = getLong(record, "项目ID");
                    String projectName = (String) record.get("项目名称");
                    String baiduAccount = (String) record.get("百度统计账号");
                    String baiduSiteId = (String) record.get("百度统计SiteID");
                    Boolean enabled = (Boolean) record.getOrDefault("是否启用", true);

                    if (!enabled) {
                        log.debug("项目 {} 已禁用，跳过", projectName);
                        continue;
                    }

                    if (baiduAccount == null || baiduAccount.trim().isEmpty()) {
                        log.debug("项目 {} 没有配置百度统计账号，跳过", projectName);
                        continue;
                    }

                    if (baiduSiteId == null || baiduSiteId.trim().isEmpty()) {
                        log.debug("项目 {} 没有配置百度统计SiteID，跳过", projectName);
                        continue;
                    }

                    // 检查账号配置是否存在
                    BaiduConfig.AccountCredentials credentials = baiduConfig.getAccountCredentials(baiduAccount);
                    if (credentials == null) {
                        log.warn("项目 {} 的百度统计账号 {} 未配置，跳过", projectName, baiduAccount);
                        continue;
                    }

                    // 获取累计UV（最近30天）
                    Integer uv = baiduTongjiService.getCumulativeUV(baiduAccount, baiduSiteId, 30);

                    if (uv != null) {
                        // 更新飞书表格中的UV值
                        Map<String, Object> fields = new HashMap<>();
                        fields.put("累计UV", uv.longValue());

                        feishuService.updateRecord(tableId, recordId, fields);

                        log.info("项目 {} (ID:{}, 账号:{}) UV更新成功: {}", projectName, projectId, baiduAccount, uv);
                        successCount++;
                    } else {
                        log.warn("项目 {} (ID:{}, 账号:{}) 获取UV失败", projectName, projectId, baiduAccount);
                        failCount++;
                    }

                } catch (Exception e) {
                    log.error("同步项目UV失败", e);
                    failCount++;
                }
            }

            log.info("UV数据同步完成: 成功 {}, 失败 {}", successCount, failCount);

        } catch (Exception e) {
            log.error("UV同步任务异常", e);
        }
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0L;
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }
}
