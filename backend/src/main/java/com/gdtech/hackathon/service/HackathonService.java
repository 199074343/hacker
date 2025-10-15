package com.gdtech.hackathon.service;

import com.gdtech.hackathon.config.FeishuConfig;
import com.gdtech.hackathon.config.HackathonProperties;
import com.gdtech.hackathon.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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
     * 缓存1小时
     */
    public CompetitionStage getCurrentStage() {
        try {
            // 1. 读取飞书配置表
            String tableId = feishuConfig.getConfigTableId();
            List<Map<String, Object>> records = feishuService.listRecords(tableId);

            for (Map<String, Object> record : records) {
                if ("current_stage".equals(record.get("配置项"))) {
                    String stageCode = (String) record.get("配置值");

                    // 2. 如果配置值不为空，使用配置值（方便产品验证）
                    if (stageCode != null && !stageCode.trim().isEmpty()) {
                        log.debug("使用飞书配置的阶段: {}", stageCode);
                        return CompetitionStage.fromCode(stageCode);
                    }

                    // 3. 配置值为空，根据当前时间自动判断
                    log.debug("配置值为空，根据当前时间自动判断阶段");
                    return determineStageByTime();
                }
            }

            log.warn("未找到 current_stage 配置项，使用时间判断");
        } catch (Exception e) {
            log.error("从飞书获取比赛阶段失败，根据时间判断", e);
        }

        // 4. 读取失败，根据时间判断
        return determineStageByTime();
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
            return CompetitionStage.SELECTION;
        }

        if (selectionWindow != null && selectionWindow.contains(now)) {
            return CompetitionStage.SELECTION;
        }

        if (lockWindow != null && lockWindow.contains(now)) {
            return CompetitionStage.LOCK;
        }

        if (investmentWindow != null && investmentWindow.contains(now)) {
            return CompetitionStage.INVESTMENT;
        }

        if (investmentWindow != null && investmentWindow.getEnd() != null
                && now.isAfter(investmentWindow.getEnd())) {
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
     * 缓存1小时
     */
    @Cacheable(value = "projects", unless = "#result == null || #result.isEmpty()")
    public List<Project> getAllProjects() {
        try {
            String tableId = feishuConfig.getProjectsTableId();
            List<Map<String, Object>> records = feishuService.listRecords(tableId);

            List<Project> projects = records.stream()
                    .map(this::convertToProject)
                    .filter(Project::getEnabled)
                    .collect(Collectors.toList());

            // 获取投资记录并汇总
            enrichProjectsWithInvestments(projects);

            // 计算排名
            calculateRankings(projects);

            return projects;
        } catch (Exception e) {
            log.error("获取项目列表失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 清除项目列表缓存
     */
    @CacheEvict(value = "projects", allEntries = true)
    public void evictProjectsCache() {
        log.info("清除项目列表缓存");
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
     * 投资成功后清除项目列表缓存
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

            // 清除项目列表缓存，下次查询会重新计算排名
            evictProjectsCache();

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
        int qualifiedCount = hackathonProperties.getQualifiedCount();

        if (stage == CompetitionStage.SELECTION) {
            clearQualifiedProjectsConfig();
            // 海选期：按UV排名，前15名晋级
            projects.sort((a, b) -> {
                int cmp = Long.compare(b.getUv(), a.getUv());
                if (cmp != 0) return cmp;
                String teamNumA = a.getTeamNumber() != null ? a.getTeamNumber() : "999";
                String teamNumB = b.getTeamNumber() != null ? b.getTeamNumber() : "999";
                return teamNumA.compareTo(teamNumB);
            });

            // 设置排名和晋级状态
            for (int i = 0; i < projects.size(); i++) {
                projects.get(i).setRank(i + 1);
                projects.get(i).setQualified(i < qualifiedCount);
            }

            log.debug("海选期排名计算完成，按UV排名");

        } else if (stage == CompetitionStage.LOCK) {
            // 锁定期：晋级名单固定（从飞书配置表读取），晋级区和非晋级区分别按UV排序

            // 步骤1：获取晋级项目ID列表（从飞书配置表）
            List<Long> qualifiedProjectIds = resolveQualifiedProjectIds(projects, qualifiedCount);

            // 如果配置表为空，回退到按海选期规则计算
            if (qualifiedProjectIds.isEmpty()) {
                log.warn("锁定期未找到晋级项目配置，回退到按UV排名前{}名", qualifiedCount);
                // 按UV排序
                projects.sort((a, b) -> {
                    int cmp = Long.compare(b.getUv(), a.getUv());
                    if (cmp != 0) return cmp;
                    String teamNumA = a.getTeamNumber() != null ? a.getTeamNumber() : "999";
                    String teamNumB = b.getTeamNumber() != null ? b.getTeamNumber() : "999";
                    return teamNumA.compareTo(teamNumB);
                });
                for (int i = 0; i < projects.size(); i++) {
                    projects.get(i).setRank(i + 1);
                    projects.get(i).setQualified(i < qualifiedCount);
                }
            } else {
                // 步骤2：标记晋级状态
                final Set<Long> finalQualifiedIds = new LinkedHashSet<>(qualifiedProjectIds);
                projects.forEach(p -> p.setQualified(finalQualifiedIds.contains(p.getId())));

                // 步骤3：分组并分别排序
                List<Project> qualifiedProjects = projects.stream()
                        .filter(Project::getQualified)
                        .collect(Collectors.toList());
                List<Project> nonQualifiedProjects = projects.stream()
                        .filter(p -> !p.getQualified())
                        .collect(Collectors.toList());

                // 晋级区按UV排序
                qualifiedProjects.sort((a, b) -> {
                    int cmp = Long.compare(b.getUv(), a.getUv());
                    if (cmp != 0) return cmp;
                    String teamNumA = a.getTeamNumber() != null ? a.getTeamNumber() : "999";
                    String teamNumB = b.getTeamNumber() != null ? b.getTeamNumber() : "999";
                    return teamNumA.compareTo(teamNumB);
                });

                // 非晋级区按UV排序
                nonQualifiedProjects.sort((a, b) -> {
                    int cmp = Long.compare(b.getUv(), a.getUv());
                    if (cmp != 0) return cmp;
                    String teamNumA = a.getTeamNumber() != null ? a.getTeamNumber() : "999";
                    String teamNumB = b.getTeamNumber() != null ? b.getTeamNumber() : "999";
                    return teamNumA.compareTo(teamNumB);
                });

                // 步骤4：设置排名（晋级区和非晋级区分别计算）
                for (int i = 0; i < qualifiedProjects.size(); i++) {
                    qualifiedProjects.get(i).setRank(i + 1);
                }
                for (int i = 0; i < nonQualifiedProjects.size(); i++) {
                    nonQualifiedProjects.get(i).setRank(i + 1);
                }

                // 步骤5：合并列表（晋级区在前）
                projects.clear();
                projects.addAll(qualifiedProjects);
                projects.addAll(nonQualifiedProjects);

                log.debug("锁定期排名计算完成，晋级项目{}个，非晋级项目{}个", qualifiedProjects.size(), nonQualifiedProjects.size());
            }

        } else if (stage == CompetitionStage.INVESTMENT || stage == CompetitionStage.ENDED) {
            // 投资期/结束期：晋级名单固定，使用加权排名算法

            // 步骤1：获取晋级项目ID列表
            List<Long> qualifiedProjectIds = resolveQualifiedProjectIds(projects, qualifiedCount);

            // 如果配置表为空，回退到按UV排名前15名
            if (qualifiedProjectIds.isEmpty()) {
                log.warn("投资期未找到晋级项目配置，回退到按UV排名前{}名", qualifiedCount);
                projects.sort((a, b) -> {
                    int cmp = Long.compare(b.getUv(), a.getUv());
                    if (cmp != 0) return cmp;
                    String teamNumA = a.getTeamNumber() != null ? a.getTeamNumber() : "999";
                    String teamNumB = b.getTeamNumber() != null ? b.getTeamNumber() : "999";
                    return teamNumA.compareTo(teamNumB);
                });
                for (int i = 0; i < projects.size(); i++) {
                    projects.get(i).setRank(i + 1);
                    projects.get(i).setQualified(i < qualifiedCount);
                }
                return;
            }

            // 步骤2：标记晋级状态
            final Set<Long> finalQualifiedIds = new LinkedHashSet<>(qualifiedProjectIds);
            projects.forEach(p -> p.setQualified(finalQualifiedIds.contains(p.getId())));

            // 步骤3：按UV排序，计算UV排名分数
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

            // 步骤4：按投资额排序，计算投资额排名分数
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

            // 步骤5：计算加权分数并设置到项目
            projects.forEach(p -> {
                int uvRank = uvRankMap.get(p.getId());
                int investRank = investmentRankMap.get(p.getId());
                double uvScore = uvScores.get(p.getId());
                double investScore = investmentScores.get(p.getId());
                // 加权分数 = UV排名分数*40% + 投资额排名分数*60%
                double weightedScore = uvScore * 0.4 + investScore * 0.6;
                p.setWeightedScore(weightedScore);
            });

            // 步骤6：分组
            List<Project> qualifiedProjects = projects.stream()
                    .filter(Project::getQualified)
                    .collect(Collectors.toList());
            List<Project> nonQualifiedProjects = projects.stream()
                    .filter(p -> !p.getQualified())
                    .collect(Collectors.toList());

            // 步骤7：晋级区按加权分数排序
            qualifiedProjects.sort((a, b) -> {
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

            // 步骤8：非晋级区按UV排序
            nonQualifiedProjects.sort((a, b) -> {
                int cmp = Long.compare(b.getUv(), a.getUv());
                if (cmp != 0) return cmp;
                String teamNumA = a.getTeamNumber() != null ? a.getTeamNumber() : "999";
                String teamNumB = b.getTeamNumber() != null ? b.getTeamNumber() : "999";
                return teamNumA.compareTo(teamNumB);
            });

            // 步骤9：设置排名
            for (int i = 0; i < qualifiedProjects.size(); i++) {
                qualifiedProjects.get(i).setRank(i + 1);
            }
            for (int i = 0; i < nonQualifiedProjects.size(); i++) {
                nonQualifiedProjects.get(i).setRank(i + 1);
            }

            // 步骤10：合并列表
            projects.clear();
            projects.addAll(qualifiedProjects);
            projects.addAll(nonQualifiedProjects);

            log.debug("投资期排名计算完成，使用加权算法：UV排名分数*40% + 投资额排名分数*60%");
        }
    }

    private QualifiedProjectsConfig loadQualifiedProjectsConfig() {
        try {
            String tableId = feishuConfig.getConfigTableId();
            List<Map<String, Object>> records = feishuService.listRecords(tableId);

            for (Map<String, Object> record : records) {
                Object key = record.get("配置项");
                if (key != null && "qualified_project_ids".equals(key.toString())) {
                    String recordId = (String) record.get("record_id");
                    Object valueObj = record.get("配置值");
                    String idsStr = valueObj != null ? valueObj.toString() : null;

                    if (idsStr != null && !idsStr.trim().isEmpty()) {
                        Set<Long> ids = Arrays.stream(idsStr.split(","))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .map(Long::parseLong)
                                .collect(Collectors.toCollection(LinkedHashSet::new));
                        return new QualifiedProjectsConfig(ids, recordId);
                    }

                    return new QualifiedProjectsConfig(new LinkedHashSet<>(), recordId);
                }
            }
        } catch (Exception e) {
            log.warn("获取晋级项目配置失败", e);
        }
        return new QualifiedProjectsConfig(new LinkedHashSet<>(), null);
    }

    private List<Long> resolveQualifiedProjectIds(List<Project> projects, int qualifiedCount) {
        QualifiedProjectsConfig config = loadQualifiedProjectsConfig();
        if (!config.getProjectIds().isEmpty()) {
            return new ArrayList<>(config.getProjectIds());
        }

        List<Long> computed = selectTopProjectsByUv(projects, qualifiedCount).stream()
                .map(Project::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (!computed.isEmpty()) {
            persistQualifiedProjects(computed, config.getRecordId());
            log.info("未配置晋级名单，按UV锁定前{}名并写入配置", computed.size());
        }

        return computed;
    }

    private List<Project> selectTopProjectsByUv(List<Project> projects, int count) {
        if (projects == null || projects.isEmpty() || count <= 0) {
            return Collections.emptyList();
        }

        return projects.stream()
                .sorted((a, b) -> {
                    int cmp = Long.compare(
                            Optional.ofNullable(b.getUv()).orElse(0L),
                            Optional.ofNullable(a.getUv()).orElse(0L)
                    );
                    if (cmp != 0) {
                        return cmp;
                    }
                    String teamNumA = a.getTeamNumber() != null ? a.getTeamNumber() : "999";
                    String teamNumB = b.getTeamNumber() != null ? b.getTeamNumber() : "999";
                    return teamNumA.compareTo(teamNumB);
                })
                .limit(count)
                .collect(Collectors.toList());
    }

    private void persistQualifiedProjects(List<Long> qualifiedIds, String existingRecordId) {
        if (qualifiedIds == null || qualifiedIds.isEmpty()) {
            return;
        }

        try {
            String idsStr = qualifiedIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));

            if (idsStr.isEmpty()) {
                return;
            }

            String tableId = feishuConfig.getConfigTableId();
            Map<String, Object> fields = new HashMap<>();
            fields.put("配置值", idsStr);

            if (existingRecordId != null && !existingRecordId.isEmpty()) {
                feishuService.updateRecord(tableId, existingRecordId, fields);
            } else {
                fields.put("配置项", "qualified_project_ids");
                feishuService.createRecord(tableId, fields);
            }
        } catch (Exception e) {
            log.warn("写入晋级项目配置失败", e);
        }
    }

    private void clearQualifiedProjectsConfig() {
        try {
            QualifiedProjectsConfig config = loadQualifiedProjectsConfig();
            if (config.getRecordId() == null || config.getProjectIds().isEmpty()) {
                return;
            }

            String tableId = feishuConfig.getConfigTableId();
            Map<String, Object> fields = new HashMap<>();
            fields.put("配置值", "");
            feishuService.updateRecord(tableId, config.getRecordId(), fields);
        } catch (Exception e) {
            log.warn("清空晋级项目配置失败", e);
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

    private static class QualifiedProjectsConfig {
        private final Set<Long> projectIds;
        private final String recordId;

        QualifiedProjectsConfig(Set<Long> projectIds, String recordId) {
            this.projectIds = projectIds;
            this.recordId = recordId;
        }

        Set<Long> getProjectIds() {
            return projectIds;
        }

        String getRecordId() {
            return recordId;
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
