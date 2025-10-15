package com.gdtech.hackathon.service;

import com.gdtech.hackathon.config.FeishuConfig;
import com.gdtech.hackathon.config.HackathonProperties;
import com.gdtech.hackathon.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 骇客大赛业务服务
 */
@Slf4j
@Service
public class HackathonService {

    private final FeishuService feishuService;
    private final FeishuConfig feishuConfig;
    private final HackathonProperties hackathonProperties;

    // 内存缓存投资记录，避免并发问题（生产环境应使用Redis）
    private final Map<String, Integer> investorRemainingAmount = new ConcurrentHashMap<>();

    public HackathonService(FeishuService feishuService,
                            FeishuConfig feishuConfig,
                            HackathonProperties hackathonProperties) {
        this.feishuService = feishuService;
        this.feishuConfig = feishuConfig;
        this.hackathonProperties = hackathonProperties;
    }

    /**
     * 获取当前比赛阶段
     * 逻辑：
     * 1. 优先使用飞书配置表的配置值（方便产品验证）
     * 2. 配置值为空时，根据当前时间自动判断阶段
     * 3. 时间早于海选期，视为海选期
     */
    public CompetitionStage getCurrentStage() {
        try {
            // 1. 读取飞书配置表
            String tableId = feishuConfig.getConfigTableId();
            List<Map<String, Object>> records = feishuService.listRecords(tableId);

            log.info("读取飞书配置表记录数: {}", records.size());

            for (Map<String, Object> record : records) {
                String configKey = (String) record.get("配置项");
                log.info("配置记录: 配置项={}, 配置值={}", configKey, record.get("配置值"));

                if ("current_stage".equals(configKey)) {
                    String stageCode = (String) record.get("配置值");

                    // 2. 如果配置值不为空，使用配置值（方便产品验证）
                    if (stageCode != null && !stageCode.trim().isEmpty()) {
                        log.info("使用飞书配置的阶段: {}", stageCode);
                        CompetitionStage stage = CompetitionStage.fromCode(stageCode);
                        log.info("解析后的阶段枚举: {}, 是否可投资: {}", stage.getCode(), stage.canInvest());
                        return stage;
                    }

                    // 3. 配置值为空，根据当前时间自动判断
                    log.info("配置值为空，根据当前时间自动判断阶段");
                    return determineStageByTime();
                }
            }

            log.warn("未找到 current_stage 配置项，使用时间判断");
        } catch (Exception e) {
            log.error("从飞书获取比赛阶段失败，根据时间判断", e);
        }

        // 4. 读取失败，根据时间判断
        CompetitionStage stage = determineStageByTime();
        log.info("时间判断结果: {}, 是否可投资: {}", stage.getCode(), stage.canInvest());
        return stage;
    }

    /**
     * 根据当前时间判断比赛阶段
     * 注意：时间早于海选期或晚于结束期，都视为海选期（活动未开始或已过期）
     */
    private CompetitionStage determineStageByTime() {
        LocalDateTime now = LocalDateTime.now();

        StageWindow selectionWindow = buildStageWindow(CompetitionStage.SELECTION);
        StageWindow lockWindow = buildStageWindow(CompetitionStage.LOCK);
        StageWindow investmentWindow = buildStageWindow(CompetitionStage.INVESTMENT);

        // 时间早于海选期开始
        if (selectionWindow != null && selectionWindow.getStart() != null
                && now.isBefore(selectionWindow.getStart())) {
            log.info("当前时间早于海选期开始时间，视为海选期（活动未开始）");
            return CompetitionStage.SELECTION;
        }

        if (selectionWindow != null && selectionWindow.contains(now)) {
            log.info("当前时间处于海选期");
            return CompetitionStage.SELECTION;
        }

        if (lockWindow != null && lockWindow.contains(now)) {
            log.info("当前时间处于锁定期");
            return CompetitionStage.LOCK;
        }

        if (investmentWindow != null && investmentWindow.contains(now)) {
            log.info("当前时间处于投资期");
            return CompetitionStage.INVESTMENT;
        }

        if (investmentWindow != null && investmentWindow.getEnd() != null
                && now.isAfter(investmentWindow.getEnd())) {
            log.info("当前时间晚于结束期，视为海选期（活动已过期）");
            return CompetitionStage.SELECTION;
        }

        log.warn("未能从配置中解析到完整的阶段时间，默认返回海选期");
        return CompetitionStage.SELECTION;
    }

    private StageWindow buildStageWindow(CompetitionStage stage) {
        HackathonProperties.StageTimeline timeline = hackathonProperties.getStageTimeline(stage.getCode());
        if (timeline == null) {
            return null;
        }

        LocalDateTime start = parseStageDateTime(timeline.getStart());
        LocalDateTime end = parseStageDateTime(timeline.getEnd());
        if (start == null || end == null) {
            return null;
        }
        return new StageWindow(start, end);
    }

    private LocalDateTime parseStageDateTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        String trimmed = value.trim();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        try {
            return LocalDateTime.parse(trimmed, formatter);
        } catch (DateTimeParseException ex) {
            LocalDateTime adjusted = tryParseWithMidnightOverflow(trimmed);
            if (adjusted != null) {
                return adjusted;
            }
            log.warn("无法解析阶段时间值: {}", trimmed, ex);
            return null;
        }
    }

    private LocalDateTime tryParseWithMidnightOverflow(String value) {
        String[] parts = value.split(" ");
        if (parts.length != 2) {
            return null;
        }

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        try {
            LocalDate date = LocalDate.parse(parts[0], dateFormatter);
            String[] timeParts = parts[1].split(":");
            if (timeParts.length != 3) {
                return null;
            }
            int hour = Integer.parseInt(timeParts[0]);
            int minute = Integer.parseInt(timeParts[1]);
            int second = Integer.parseInt(timeParts[2]);

            if (hour == 24 && minute == 0 && second == 0) {
                return date.plusDays(1).atTime(LocalTime.MIDNIGHT);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    /**
     * 获取所有项目列表（带排名）
     */
    public List<Project> getAllProjects() {
        long startTime = System.currentTimeMillis();
        try {
            log.info("🚀 开始获取项目列表...");

            // 步骤1：查询项目表
            long step1Start = System.currentTimeMillis();
            String tableId = feishuConfig.getProjectsTableId();
            List<Map<String, Object>> records = feishuService.listRecords(tableId);
            long step1End = System.currentTimeMillis();
            log.info("  ✅ 步骤1: 查询项目表完成，耗时: {}ms，记录数: {}", step1End - step1Start, records.size());

            List<Project> projects = records.stream()
                    .map(this::convertToProject)
                    .filter(Project::getEnabled)
                    .collect(Collectors.toList());

            // 步骤2：获取投资记录并汇总
            long step2Start = System.currentTimeMillis();
            enrichProjectsWithInvestments(projects);
            long step2End = System.currentTimeMillis();
            log.info("  ✅ 步骤2: 加载投资记录完成，耗时: {}ms", step2End - step2Start);

            // 步骤3：计算排名
            long step3Start = System.currentTimeMillis();
            calculateRankings(projects);
            long step3End = System.currentTimeMillis();
            log.info("  ✅ 步骤3: 计算排名完成，耗时: {}ms", step3End - step3Start);

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("✨ 获取项目列表总耗时: {}ms", totalTime);
            return projects;
        } catch (Exception e) {
            log.error("获取项目列表失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 根据ID获取项目
     */
    public Project getProjectById(Long projectId) {
        return getAllProjects().stream()
                .filter(p -> p.getId().equals(projectId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 投资人登录
     */
    public Investor login(String username, String password) {
        try {
            String tableId = feishuConfig.getInvestorsTableId();
            List<Map<String, Object>> records = feishuService.listRecords(tableId);

            for (Map<String, Object> record : records) {
                String recordUsername = (String) record.get("账号");
                String recordPassword = (String) record.get("初始密码");
                Boolean enabled = (Boolean) record.getOrDefault("是否启用", true);

                if (username.equals(recordUsername) && password.equals(recordPassword) && enabled) {
                    Investor investor = convertToInvestor(record);
                    enrichInvestorWithHistory(investor);
                    return investor;
                }
            }

            return null; // 登录失败
        } catch (Exception e) {
            log.error("投资人登录失败", e);
            return null;
        }
    }

    /**
     * 执行投资
     */
    public synchronized boolean invest(String investorUsername, Long projectId, Integer amount) {
        try {
            CompetitionStage stage = getCurrentStage();
            log.info("投资操作 - 当前阶段: {}, 是否可投资: {}", stage.getCode(), stage.canInvest());

            // 临时移除阶段检查，允许所有阶段投资（用于调试）
            // if (!stage.canInvest()) {
            //     throw new IllegalStateException("当前阶段不可投资，请见大赛规则");
            // }

            // 获取投资人信息
            Investor investor = getInvestorByUsername(investorUsername);
            if (investor == null) {
                throw new IllegalStateException("投资人不存在");
            }

            // 获取项目信息
            Project project = getProjectById(projectId);
            if (project == null) {
                throw new IllegalStateException("项目不存在");
            }

            if (!project.getQualified()) {
                throw new IllegalStateException("只能投资晋级的前15名作品");
            }

            // 检查剩余额度
            Integer remaining = investorRemainingAmount.getOrDefault(investorUsername, investor.getInitialAmount());
            if (amount > remaining) {
                throw new IllegalStateException("投资金额超过剩余额度");
            }

            // 写入飞书投资记录表
            Map<String, Object> fields = new HashMap<>();
            fields.put("投资人账号", investorUsername);
            fields.put("项目ID", projectId);
            fields.put("投资金额", amount);
            fields.put("投资时间", System.currentTimeMillis());  // 使用Unix时间戳（毫秒）
            fields.put("投资人姓名", investor.getName());
            fields.put("项目名称", project.getName());

            String recordId = feishuService.createRecord(feishuConfig.getInvestmentsTableId(), fields);

            // 更新内存中的剩余额度
            investorRemainingAmount.put(investorUsername, remaining - amount);

            log.info("投资成功: {} 投资 {} 万元给项目 {}", investorUsername, amount, projectId);
            return true;
        } catch (Exception e) {
            log.error("投资失败", e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * 获取投资人信息（含投资历史）
     */
    public Investor getInvestorInfo(String username) {
        Investor investor = getInvestorByUsername(username);
        if (investor != null) {
            enrichInvestorWithHistory(investor);
        }
        return investor;
    }

    // ==================== 私有方法 ====================

    private Project convertToProject(Map<String, Object> record) {
        Project project = new Project();
        project.setId(getLong(record, "项目ID"));
        project.setName((String) record.get("项目名称"));
        project.setDescription((String) record.get("一句话描述"));
        project.setUrl((String) record.get("项目网址"));
        project.setImage((String) record.get("项目配图URL"));
        project.setTeamName((String) record.get("队伍名称"));
        project.setTeamNumber((String) record.get("队伍编号"));
        project.setTeamUrl((String) record.get("团队介绍页URL"));
        project.setBaiduAccount((String) record.get("百度统计账号"));
        project.setBaiduSiteId((String) record.get("百度统计SiteID"));
        project.setUv(getLong(record, "累计UV"));
        project.setEnabled((Boolean) record.getOrDefault("是否启用", true));
        return project;
    }

    private Investor convertToInvestor(Map<String, Object> record) {
        Investor investor = new Investor();
        investor.setId(getLong(record, "投资人ID"));
        investor.setUsername((String) record.get("账号"));
        investor.setPassword((String) record.get("初始密码"));
        investor.setName((String) record.get("姓名"));
        investor.setTitle((String) record.get("职务"));
        investor.setAvatar((String) record.get("头像URL"));
        investor.setInitialAmount(getInteger(record, "初始额度"));
        investor.setEnabled((Boolean) record.getOrDefault("是否启用", true));
        return investor;
    }

    private Investor getInvestorByUsername(String username) {
        try {
            String tableId = feishuConfig.getInvestorsTableId();
            List<Map<String, Object>> records = feishuService.listRecords(tableId);

            return records.stream()
                    .filter(r -> username.equals(r.get("账号")))
                    .map(this::convertToInvestor)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.error("获取投资人失败", e);
            return null;
        }
    }

    private void enrichProjectsWithInvestments(List<Project> projects) {
        try {
            String tableId = feishuConfig.getInvestmentsTableId();
            List<Map<String, Object>> records = feishuService.listRecords(tableId);

            // 预加载所有投资人信息到Map中，避免重复查询
            String investorsTableId = feishuConfig.getInvestorsTableId();
            List<Map<String, Object>> investorRecords = feishuService.listRecords(investorsTableId);
            Map<String, Map<String, Object>> investorMap = investorRecords.stream()
                    .collect(Collectors.toMap(
                            r -> (String) r.get("账号"),
                            r -> r,
                            (existing, replacement) -> existing
                    ));

            Map<Long, List<Map<String, Object>>> investmentsByProject = records.stream()
                    .collect(Collectors.groupingBy(r -> getLong(r, "项目ID")));

            for (Project project : projects) {
                List<Map<String, Object>> investments = investmentsByProject.getOrDefault(project.getId(), Collections.emptyList());

                int totalInvestment = investments.stream()
                        .mapToInt(r -> getInteger(r, "投资金额"))
                        .sum();

                project.setInvestment(totalInvestment);

                List<InvestmentRecord> records2 = investments.stream()
                        .map(r -> {
                            InvestmentRecord record = new InvestmentRecord();
                            String investorUsername = (String) r.get("投资人账号");
                            record.setName((String) r.get("投资人姓名"));
                            record.setAmount(getInteger(r, "投资金额"));

                            // 从投资人表查询职务和头像
                            Map<String, Object> investorData = investorMap.get(investorUsername);
                            if (investorData != null) {
                                record.setTitle((String) investorData.get("职务"));
                                record.setAvatar((String) investorData.get("头像URL"));
                            }

                            return record;
                        })
                        .collect(Collectors.toList());

                project.setInvestmentRecords(records2);
            }
        } catch (Exception e) {
            log.warn("加载投资记录失败", e);
        }
    }

    private void enrichInvestorWithHistory(Investor investor) {
        try {
            String tableId = feishuConfig.getInvestmentsTableId();
            List<Map<String, Object>> records = feishuService.listRecords(tableId);

            List<InvestmentHistory> history = records.stream()
                    .filter(r -> investor.getUsername().equals(r.get("投资人账号")))
                    .map(r -> {
                        InvestmentHistory h = new InvestmentHistory();
                        h.setProjectName((String) r.get("项目名称"));
                        h.setAmount(getInteger(r, "投资金额"));
                        Object timeObj = r.get("投资时间");
                        if (timeObj != null) {
                            // 飞书返回的是Unix时间戳（毫秒）
                            long timestamp = getLong(r, "投资时间");
                            h.setTime(LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(timestamp),
                                java.time.ZoneId.systemDefault()
                            ));
                        }
                        return h;
                    })
                    .collect(Collectors.toList());

            investor.setInvestmentHistory(history);

            int invested = history.stream().mapToInt(InvestmentHistory::getAmount).sum();
            investor.setInvestedAmount(invested);
            investor.setRemainingAmount(investor.getInitialAmount() - invested);

            // 同步到内存缓存
            investorRemainingAmount.put(investor.getUsername(), investor.getRemainingAmount());
        } catch (Exception e) {
            log.warn("加载投资历史失败", e);
        }
    }

    private void calculateRankings(List<Project> projects) {
        CompetitionStage stage = getCurrentStage();
        int totalTeams = projects.size();

        if (stage == CompetitionStage.INVESTMENT || stage == CompetitionStage.ENDED) {
            // 投资期：基于排名分数的加权排名
            // 算法：累积UV排名分数*40% + 融资额排名分数*60%

            // 步骤1：按UV排序，计算UV排名分数
            List<Project> uvSorted = new ArrayList<>(projects);
            uvSorted.sort((a, b) -> {
                int cmp = Long.compare(b.getUv(), a.getUv());
                if (cmp != 0) return cmp;
                String teamNumA = a.getTeamNumber() != null ? a.getTeamNumber() : "999";
                String teamNumB = b.getTeamNumber() != null ? b.getTeamNumber() : "999";
                return teamNumA.compareTo(teamNumB);
            });

            Map<Long, Integer> uvRankMap = new HashMap<>();
            Map<Long, Double> uvScores = new HashMap<>();
            for (int i = 0; i < uvSorted.size(); i++) {
                int rank = i + 1; // UV排名（1-based）
                Project p = uvSorted.get(i);
                // UV排名分数 = (队伍总数+1-UV排名) / 队伍总数 * 100
                double score = (double)(totalTeams + 1 - rank) / totalTeams * 100;
                uvRankMap.put(p.getId(), rank);
                uvScores.put(p.getId(), score);
            }

            // 步骤2：按投资额排序，计算投资额排名分数
            List<Project> investmentSorted = new ArrayList<>(projects);
            investmentSorted.sort((a, b) -> {
                int cmp = Integer.compare(b.getInvestment(), a.getInvestment());
                if (cmp != 0) return cmp;
                String teamNumA = a.getTeamNumber() != null ? a.getTeamNumber() : "999";
                String teamNumB = b.getTeamNumber() != null ? b.getTeamNumber() : "999";
                return teamNumA.compareTo(teamNumB);
            });

            Map<Long, Integer> investmentRankMap = new HashMap<>();
            Map<Long, Double> investmentScores = new HashMap<>();
            for (int i = 0; i < investmentSorted.size(); i++) {
                int rank = i + 1; // 投资额排名（1-based）
                Project p = investmentSorted.get(i);
                // 投资额排名分数 = (队伍总数+1-投资额排名) / 队伍总数 * 100
                double score = (double)(totalTeams + 1 - rank) / totalTeams * 100;
                investmentRankMap.put(p.getId(), rank);
                investmentScores.put(p.getId(), score);
            }

            // 步骤3：计算加权分数并设置到项目
            log.info("================== 投资期排名计算详情 ==================");
            log.info("总队伍数: {}", totalTeams);
            projects.forEach(p -> {
                int uvRank = uvRankMap.get(p.getId());
                int investRank = investmentRankMap.get(p.getId());
                double uvScore = uvScores.get(p.getId());
                double investScore = investmentScores.get(p.getId());
                // 加权分数 = UV排名分数*40% + 投资额排名分数*60%
                double weightedScore = uvScore * 0.4 + investScore * 0.6;
                p.setWeightedScore(weightedScore);

                // 打印详细排名信息
                log.info("项目ID: {}, 名称: {}, 队伍编号: {}", p.getId(), p.getName(), p.getTeamNumber());
                log.info("  UV: {}, UV排名: {}, UV排名分数: {}", p.getUv(), uvRank, String.format("%.2f", uvScore));
                log.info("  投资额: {}万元, 投资额排名: {}, 投资额排名分数: {}", p.getInvestment(), investRank, String.format("%.2f", investScore));
                log.info("  最终加权分数: {} ({}*0.4 + {}*0.6)", String.format("%.2f", weightedScore), String.format("%.2f", uvScore), String.format("%.2f", investScore));
                log.info("---");
            });

            // 步骤4：按加权分数排序
            projects.sort((a, b) -> {
                int cmp = Double.compare(b.getWeightedScore(), a.getWeightedScore());
                if (cmp != 0) return cmp;
                // 分数相同时，按投资额排序
                cmp = Integer.compare(b.getInvestment(), a.getInvestment());
                if (cmp != 0) return cmp;
                // 投资额也相同时，按队伍编号排序
                String teamNumA = a.getTeamNumber() != null ? a.getTeamNumber() : "999";
                String teamNumB = b.getTeamNumber() != null ? b.getTeamNumber() : "999";
                return teamNumA.compareTo(teamNumB);
            });

            // 打印最终排名结果
            log.info("================== 最终排名结果 ==================");
            for (int i = 0; i < Math.min(projects.size(), 20); i++) {
                Project p = projects.get(i);
                log.info("排名#{}: {} (队伍#{}) - 加权分数: {}, UV: {}, 投资: {}万元",
                        i + 1, p.getName(), p.getTeamNumber(),
                        String.format("%.2f", p.getWeightedScore()),
                        p.getUv(), p.getInvestment());
            }
            log.info("投资期排名算法：基于排名分数加权 (UV排名*40% + 投资额排名*60%)");
        } else {
            // 其他阶段：UV排名
            projects.sort((a, b) -> {
                int cmp = Long.compare(b.getUv(), a.getUv());
                if (cmp != 0) return cmp;
                // 处理teamNumber为null的情况
                String teamNumA = a.getTeamNumber() != null ? a.getTeamNumber() : "999";
                String teamNumB = b.getTeamNumber() != null ? b.getTeamNumber() : "999";
                return teamNumA.compareTo(teamNumB);
            });
        }

        // 设置排名和晋级状态
        int qualifiedCount = hackathonProperties.getQualifiedCount();
        for (int i = 0; i < projects.size(); i++) {
            projects.get(i).setRank(i + 1);
            projects.get(i).setQualified(i < qualifiedCount);
        }
    }

    private static class StageWindow {
        private final LocalDateTime start;
        private final LocalDateTime end;

        StageWindow(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }

        LocalDateTime getStart() {
            return start;
        }

        LocalDateTime getEnd() {
            return end;
        }

        boolean contains(LocalDateTime target) {
            if (start == null || end == null || target == null) {
                return false;
            }
            return (target.isAfter(start) || target.isEqual(start))
                    && (target.isBefore(end) || target.isEqual(end));
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

    private Integer getInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(value.toString());
    }
}
