package com.gdtech.hackathon.service;

import com.gdtech.hackathon.config.FeishuConfig;
import com.gdtech.hackathon.config.HackathonProperties;
import com.gdtech.hackathon.model.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private final ApplicationContext applicationContext;
    private final CacheManager cacheManager;
    private HackathonService self;

    // 用于并发API调用的线程池（固定4个线程）
    private final ExecutorService apiExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r);
        t.setName("feishu-api-" + t.getId());
        t.setDaemon(true);
        return t;
    });

    public HackathonService(FeishuService feishuService,
                            FeishuConfig feishuConfig,
                            HackathonProperties hackathonProperties,
                            ApplicationContext applicationContext,
                            CacheManager cacheManager) {
        this.feishuService = feishuService;
        this.feishuConfig = feishuConfig;
        this.hackathonProperties = hackathonProperties;
        this.applicationContext = applicationContext;
        this.cacheManager = cacheManager;
    }

    @PostConstruct
    private void initSelfProxy() {
        try {
            this.self = applicationContext.getBean(HackathonService.class);
        } catch (Exception ex) {
            log.warn("初始化HackathonService代理失败，将回退到直接调用: {}", ex.getMessage());
            this.self = this;
        }
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
     * 优化：使用CompletableFuture并发调用多个飞书API，减少总响应时间
     */
    public List<Project> getAllProjects() {
        try {
            long startTime = System.currentTimeMillis();

            // 并发获取所有需要的飞书表数据
            CompletableFuture<List<Map<String, Object>>> projectsFuture =
                CompletableFuture.supplyAsync(() ->
                    feishuService.listRecords(feishuConfig.getProjectsTableId()), apiExecutor);

            CompletableFuture<List<Map<String, Object>>> investmentsFuture =
                CompletableFuture.supplyAsync(() ->
                    feishuService.listRecords(feishuConfig.getInvestmentsTableId()), apiExecutor);

            CompletableFuture<List<Map<String, Object>>> investorsFuture =
                CompletableFuture.supplyAsync(() ->
                    feishuService.listRecords(feishuConfig.getInvestorsTableId()), apiExecutor);

            CompletableFuture<List<Map<String, Object>>> configFuture =
                CompletableFuture.supplyAsync(() ->
                    feishuService.listRecords(feishuConfig.getConfigTableId()), apiExecutor);

            // 等待所有API调用完成
            CompletableFuture.allOf(projectsFuture, investmentsFuture, investorsFuture, configFuture).join();

            // 获取结果
            List<Map<String, Object>> projectRecords = projectsFuture.get();
            List<Map<String, Object>> investmentRecords = investmentsFuture.get();
            List<Map<String, Object>> investorRecords = investorsFuture.get();
            List<Map<String, Object>> configRecords = configFuture.get();

            long apiTime = System.currentTimeMillis() - startTime;
            log.debug("并发获取4个飞书表数据耗时: {}ms", apiTime);

            if (projectRecords == null || projectRecords.isEmpty()) {
                log.warn("从飞书查询项目列表为空");
                return Collections.emptyList();
            }

            // 转换项目数据
            List<Project> projects = projectRecords.stream()
                    .map(this::convertToProject)
                    .filter(Objects::nonNull)
                    .filter(p -> p.getEnabled() != null && p.getEnabled())
                    .collect(Collectors.toList());

            if (projects.isEmpty()) {
                log.warn("没有启用的项目");
                return Collections.emptyList();
            }

            // 使用已获取的数据填充投资记录（无需再次调用API）
            enrichProjectsWithInvestmentsFromData(projects, investmentRecords, investorRecords);

            // 计算排名（传入已获取的配置数据）
            calculateRankingsFromData(projects, configRecords);

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("getAllProjects总耗时: {}ms (API并发耗时: {}ms)", totalTime, apiTime);

            return projects;
        } catch (Exception e) {
            log.error("获取项目列表失败", e);
            return Collections.emptyList();
        }
    }


    /**
     * 根据ID获取项目（轻量版，投资时使用）
     * 只查询项目基本信息，不加载晋级配置（减少1次API调用）
     * 使用缓存,5分钟过期
     * 投资操作或UV同步后会清除缓存
     */
    @Cacheable(value = "project", key = "#projectId", unless = "#result == null")
    public Project getProjectById(Long projectId) {
        if (projectId == null || projectId <= 0) {
            log.warn("无效的项目ID: {}", projectId);
            return null;
        }

        try {
            // 使用全表扫描查询
            String tableId = feishuConfig.getProjectsTableId();
            Map<String, Object> record = findRecordByField(tableId, "项目ID", projectId);

            if (record == null) {
                log.debug("未找到项目: {}", projectId);
                return null;
            }

            Project project = convertToProject(record);
            log.debug("从飞书加载项目: {}, 将缓存5分钟", projectId);
            return project;
        } catch (Exception e) {
            log.error("获取项目{}失败", projectId, e);
            return null;
        }
    }

    /**
     * 投资人登录
     * 优化：并发查询投资人信息和投资历史，减少API调用耗时
     */
    public Investor login(String username, String password) {
        try {
            long startTime = System.currentTimeMillis();

            String investorsTableId = feishuConfig.getInvestorsTableId();
            String investmentsTableId = feishuConfig.getInvestmentsTableId();

            // 优化：并发获取投资人信息和投资历史
            CompletableFuture<List<Map<String, Object>>> investorsFuture =
                CompletableFuture.supplyAsync(() ->
                    feishuService.listRecords(investorsTableId), apiExecutor);

            CompletableFuture<List<Map<String, Object>>> investmentsFuture =
                CompletableFuture.supplyAsync(() ->
                    feishuService.listRecords(investmentsTableId), apiExecutor);

            // 等待两个API调用完成
            CompletableFuture.allOf(investorsFuture, investmentsFuture).join();

            // 获取结果
            List<Map<String, Object>> investorRecords = investorsFuture.get();
            List<Map<String, Object>> investmentRecords = investmentsFuture.get();

            long fetchTime = System.currentTimeMillis() - startTime;
            log.debug("并发获取投资人和投资记录耗时: {}ms", fetchTime);

            // 查找匹配的投资人记录
            for (Map<String, Object> record : investorRecords) {
                if (fieldValueEquals(record.get("账号"), username)) {
                    String recordPassword = (String) record.get("初始密码");
                    Boolean enabled = (Boolean) record.getOrDefault("是否启用", true);

                    if (password.equals(recordPassword) && Boolean.TRUE.equals(enabled)) {
                        Investor investor = convertToInvestor(record);
                        // 使用已获取的投资记录数据填充历史
                        enrichInvestorWithHistoryFromData(investor, investmentRecords);

                        long totalTime = System.currentTimeMillis() - startTime;
                        log.info("登录成功: {}, 总耗时: {}ms (并发查询耗时: {}ms)",
                                username, totalTime, fetchTime);
                        return investor;
                    }
                    return null; // 密码错误或账号未启用
                }
            }

            return null; // 账号不存在
        } catch (Exception e) {
            log.error("投资人登录失败", e);
            return null;
        }
    }

    /**
     * 执行投资
     * 方案5优化：前端传递姓名，完全消除查询投资人和项目的步骤1
     * 投资成功后清除相关缓存 (afterInvocation=true确保在方法执行后清除)
     */
    @Caching(evict = {
            @CacheEvict(value = "investor", key = "#investorUsername", beforeInvocation = false),
            @CacheEvict(value = "project", key = "#projectId", beforeInvocation = false),
            @CacheEvict(value = "projects", allEntries = true, beforeInvocation = false)
    })
    public synchronized boolean invest(String investorUsername, String investorName,
                                       Long projectId, String projectName, Integer amount) {
        try {
            long startTime = System.currentTimeMillis();
            long stepStart;

            // 优化：跳过阶段检查以减少飞书API调用（已在前端控制）
            // CompetitionStage stage = getCurrentStage();
            // if (!stage.canInvest()) {
            //     throw new IllegalStateException("当前阶段不可投资，请见大赛规则");
            // }

            // 【方案5优化】步骤1已完全消除：前端传递姓名，无需查询投资人和项目
            log.info("[投资性能] 方案5生效 - 跳过步骤1查询，直接使用前端传递的姓名");

            // 步骤1.5：仅查询投资人的recordId和剩余额度（轻量查询）
            stepStart = System.currentTimeMillis();
            Investor investor = getInvestorByUsername(investorUsername);
            long step1Time = System.currentTimeMillis() - stepStart;
            log.info("[投资性能] 步骤1-轻量查询投资人额度和recordId耗时: {}ms", step1Time);

            if (investor == null) {
                throw new IllegalStateException("投资人不存在");
            }

            // 检查剩余额度（从飞书表读取）
            Integer remaining = investor.getRemainingAmount();
            if (remaining == null) {
                remaining = investor.getInitialAmount(); // 兼容旧数据
            }
            if (amount > remaining) {
                throw new IllegalStateException("投资金额超过剩余额度");
            }

            // 计算新的剩余额度
            final Integer newRemaining = remaining - amount;

            // 使用步骤1获取的recordId
            final String investorRecordId = investor.getRecordId();
            if (investorRecordId == null || investorRecordId.isEmpty()) {
                log.warn("投资人{}的recordId为空，投资可能失败", investorUsername);
                throw new IllegalStateException("投资人数据异常");
            }

            // 【方案2+3优化】：并发 + 异步执行步骤2和步骤4
            stepStart = System.currentTimeMillis();

            // 步骤2：创建投资记录（同步执行，确保成功）
            // 【方案5优化】：使用前端传递的姓名，无需从对象中获取
            Map<String, Object> investmentFields = new HashMap<>();
            investmentFields.put("投资人账号", investorUsername);
            investmentFields.put("项目ID", projectId);
            investmentFields.put("投资金额", amount);
            investmentFields.put("投资时间", System.currentTimeMillis());  // 使用Unix时间戳（毫秒）
            investmentFields.put("投资人姓名", investorName);  // 使用前端传递的姓名
            investmentFields.put("项目名称", projectName);      // 使用前端传递的名称

            CompletableFuture<String> createRecordFuture = CompletableFuture.supplyAsync(
                    () -> feishuService.createRecord(feishuConfig.getInvestmentsTableId(), investmentFields),
                    apiExecutor);

            // 步骤4：更新剩余额度（异步执行，不阻塞返回）
            final String investorsTableId = feishuConfig.getInvestorsTableId();
            final Integer oldRemaining = remaining;  // 用于日志
            CompletableFuture<Void> updateAmountFuture = CompletableFuture.runAsync(() -> {
                try {
                    long updateStart = System.currentTimeMillis();
                    Map<String, Object> updateFields = new HashMap<>();
                    updateFields.put("剩余额度", newRemaining);
                    feishuService.updateRecord(investorsTableId, investorRecordId, updateFields);
                    long updateTime = System.currentTimeMillis() - updateStart;
                    log.info("[投资性能] 步骤4-异步更新剩余额度耗时: {}ms ({}->{})",
                            updateTime, oldRemaining, newRemaining);
                } catch (Exception e) {
                    log.error("异步更新投资人{}剩余额度失败", investorUsername, e);
                }
            }, apiExecutor);

            // 等待创建投资记录完成（必须成功）
            String recordId = createRecordFuture.get();
            long step2Time = System.currentTimeMillis() - stepStart;
            log.info("[投资性能] 步骤2-创建投资记录耗时: {}ms", step2Time);

            // 不等待更新剩余额度，异步执行（方案3）
            // 注：前端需要在点击投资按钮时重新校验额度

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("[投资性能] 总耗时: {}ms (步骤1:{}ms + 步骤2:{}ms, 步骤4异步执行中)",
                    totalTime, step1Time, step2Time);
            log.info("投资成功: {} 投资 {} 万元给项目 {}, recordId: {}",
                    investorUsername, amount, projectId, recordId);
            return true;
        } catch (Exception e) {
            log.error("投资失败", e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * 获取投资人信息（含投资历史）
     * 优化：并发查询投资人信息和投资历史，减少API调用耗时
     */
    public Investor getInvestorInfo(String username) {
        try {
            long startTime = System.currentTimeMillis();

            String investorsTableId = feishuConfig.getInvestorsTableId();
            String investmentsTableId = feishuConfig.getInvestmentsTableId();

            // 优化：并发获取投资人信息和投资历史
            CompletableFuture<List<Map<String, Object>>> investorsFuture =
                CompletableFuture.supplyAsync(() ->
                    feishuService.listRecords(investorsTableId), apiExecutor);

            CompletableFuture<List<Map<String, Object>>> investmentsFuture =
                CompletableFuture.supplyAsync(() ->
                    feishuService.listRecords(investmentsTableId), apiExecutor);

            // 等待两个API调用完成
            CompletableFuture.allOf(investorsFuture, investmentsFuture).join();

            // 获取结果
            List<Map<String, Object>> investorRecords = investorsFuture.get();
            List<Map<String, Object>> investmentRecords = investmentsFuture.get();

            long fetchTime = System.currentTimeMillis() - startTime;
            log.debug("并发获取投资人和投资记录耗时: {}ms", fetchTime);

            // 查找匹配的投资人记录
            for (Map<String, Object> record : investorRecords) {
                if (fieldValueEquals(record.get("账号"), username)) {
                    Investor investor = convertToInvestor(record);
                    // 使用已获取的投资记录数据填充历史
                    enrichInvestorWithHistoryFromData(investor, investmentRecords);

                    long totalTime = System.currentTimeMillis() - startTime;
                    log.info("获取投资人信息成功: {}, 总耗时: {}ms (并发查询耗时: {}ms)",
                            username, totalTime, fetchTime);
                    return investor;
                }
            }

            return null; // 投资人不存在
        } catch (Exception e) {
            log.error("获取投资人信息失败", e);
            return null;
        }
    }

    // ==================== 私有方法 ====================

    private Project convertToProject(Map<String, Object> record) {
        if (record == null || record.isEmpty()) {
            log.warn("尝试转换空记录为Project");
            return null;
        }

        Project project = new Project();
        project.setId(getLong(record, "项目ID"));
        project.setName(getStringValue(record, "项目名称"));
        project.setDescription(getStringValue(record, "一句话描述"));
        project.setUrl(getStringValue(record, "项目网址"));
        project.setImage(getStringValue(record, "项目配图URL"));
        project.setTeamName(getStringValue(record, "队伍名称"));
        project.setTeamNumber(getStringValue(record, "队伍编号"));
        project.setTeamUrl(getStringValue(record, "团队介绍页URL"));
        project.setBaiduAccount(getStringValue(record, "百度统计账号"));
        project.setBaiduSiteId(getStringValue(record, "百度统计SiteID"));
        project.setUv(getLong(record, "累计UV"));
        project.setEnabled(getBooleanValue(record, "是否启用", true));
        return project;
    }

    private Investor convertToInvestor(Map<String, Object> record) {
        if (record == null || record.isEmpty()) {
            log.warn("尝试转换空记录为Investor");
            return null;
        }

        Investor investor = new Investor();
        investor.setRecordId(getStringValue(record, "record_id"));  // 保存record_id用于后续更新
        investor.setId(getLong(record, "投资人ID"));
        investor.setUsername(getStringValue(record, "账号"));
        investor.setPassword(getStringValue(record, "初始密码"));
        investor.setName(getStringValue(record, "姓名"));
        investor.setTitle(getStringValue(record, "职务"));
        investor.setAvatar(getStringValue(record, "头像URL"));
        investor.setInitialAmount(getInteger(record, "初始额度"));
        investor.setRemainingAmount(getInteger(record, "剩余额度"));  // 从飞书读取剩余额度
        investor.setEnabled(getBooleanValue(record, "是否启用", true));
        return investor;
    }

    private String getStringValue(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        return value.toString().trim();
    }

    private Boolean getBooleanValue(Map<String, Object> map, String key, Boolean defaultValue) {
        if (map == null || key == null) {
            return defaultValue;
        }
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String strValue = value.toString().trim().toLowerCase();
        if ("true".equals(strValue) || "1".equals(strValue)) {
            return true;
        }
        if ("false".equals(strValue) || "0".equals(strValue)) {
            return false;
        }
        return defaultValue;
    }

    /**
     * 根据用户名获取投资人信息
     * 使用缓存,5分钟过期
     * 投资操作后会清除缓存
     */
    @Cacheable(value = "investor", key = "#username", unless = "#result == null")
    private Investor getInvestorByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            log.warn("无效的投资人账号: {}", username);
            return null;
        }

        try {
            // 使用全表扫描查询
            String tableId = feishuConfig.getInvestorsTableId();
            Map<String, Object> record = findRecordByField(tableId, "账号", username);

            if (record == null) {
                log.debug("未找到投资人: {}", username);
                return null;
            }

            Investor investor = convertToInvestor(record);
            log.debug("从飞书加载投资人: {}, 将缓存5分钟", username);
            return investor;
        } catch (Exception e) {
            log.error("获取投资人{}失败", username, e);
            return null;
        }
    }

    /**
     * 使用已获取的数据填充投资记录（新方法，避免重复API调用）
     */
    private void enrichProjectsWithInvestmentsFromData(List<Project> projects,
                                                        List<Map<String, Object>> investmentRecords,
                                                        List<Map<String, Object>> investorRecords) {
        try {
            // 构建投资人Map
            Map<String, Map<String, Object>> investorMap = investorRecords.stream()
                    .collect(Collectors.toMap(
                            r -> (String) r.get("账号"),
                            r -> r,
                            (existing, replacement) -> existing
                    ));

            // 按项目ID分组投资记录
            Map<Long, List<Map<String, Object>>> investmentsByProject = investmentRecords.stream()
                    .collect(Collectors.groupingBy(r -> getLong(r, "项目ID")));

            // 填充每个项目的投资数据
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

    /**
     * 旧方法：保留用于其他调用点
     */
    private void enrichProjectsWithInvestments(List<Project> projects) {
        try {
            String tableId = feishuConfig.getInvestmentsTableId();
            List<Map<String, Object>> records = feishuService.listRecords(tableId);

            // 预加载所有投资人信息到Map中，避免重复查询
            String investorsTableId = feishuConfig.getInvestorsTableId();
            List<Map<String, Object>> investorRecords = feishuService.listRecords(investorsTableId);

            // 复用新方法
            enrichProjectsWithInvestmentsFromData(projects, records, investorRecords);
        } catch (Exception e) {
            log.warn("加载投资记录失败", e);
        }
    }

    /**
     * 使用已获取的投资记录数据填充投资历史（新方法，避免重复API调用）
     */
    private void enrichInvestorWithHistoryFromData(Investor investor, List<Map<String, Object>> investmentRecords) {
        try {
            List<InvestmentHistory> history = investmentRecords.stream()
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

            // 如果飞书表没有剩余额度数据，使用计算值
            if (investor.getRemainingAmount() == null) {
                investor.setRemainingAmount(investor.getInitialAmount() - invested);
            }
        } catch (Exception e) {
            log.warn("从数据填充投资历史失败", e);
        }
    }

    /**
     * 旧方法：保留用于其他调用点（如果有）
     */
    private void enrichInvestorWithHistory(Investor investor) {
        try {
            String tableId = feishuConfig.getInvestmentsTableId();
            List<Map<String, Object>> records = feishuService.listRecords(tableId);
            enrichInvestorWithHistoryFromData(investor, records);
        } catch (Exception e) {
            log.warn("加载投资历史失败", e);
        }
    }

    /**
     * 使用已获取的配置数据计算排名（新方法，避免重复API调用）
     */
    private void calculateRankingsFromData(List<Project> projects, List<Map<String, Object>> configRecords) {
        CompetitionStage stage = getCurrentStageFromData(configRecords);
        calculateRankingsWithStage(projects, stage, configRecords);
    }

    /**
     * 从已获取的配置数据中解析当前阶段
     */
    private CompetitionStage getCurrentStageFromData(List<Map<String, Object>> configRecords) {
        try {
            for (Map<String, Object> record : configRecords) {
                if ("current_stage".equals(record.get("配置项"))) {
                    String stageCode = (String) record.get("配置值");

                    if (stageCode != null && !stageCode.trim().isEmpty()) {
                        log.debug("使用飞书配置的阶段: {}", stageCode);
                        return CompetitionStage.fromCode(stageCode);
                    }

                    log.debug("配置值为空，根据当前时间自动判断阶段");
                    return determineStageByTime();
                }
            }

            log.warn("未找到 current_stage 配置项，使用时间判断");
        } catch (Exception e) {
            log.error("从配置数据解析比赛阶段失败，根据时间判断", e);
        }

        return determineStageByTime();
    }

    /**
     * 旧方法：保留用于其他调用点
     */
    private void calculateRankings(List<Project> projects) {
        CompetitionStage stage = getCurrentStage();
        calculateRankingsWithStage(projects, stage, null);
    }

    /**
     * 核心排名计算逻辑（重构后被两个方法共用）
     */
    private void calculateRankingsWithStage(List<Project> projects, CompetitionStage stage, List<Map<String, Object>> configRecords) {
        int totalTeams = projects.size();
        int qualifiedCount = hackathonProperties.getQualifiedCount();

        if (stage == CompetitionStage.SELECTION) {
            if (configRecords != null) {
                clearQualifiedProjectsConfigFromData(configRecords);
            } else {
                clearQualifiedProjectsConfig();
            }
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
            List<Long> qualifiedProjectIds = (configRecords != null)
                    ? resolveQualifiedProjectIdsFromData(projects, qualifiedCount, configRecords)
                    : resolveQualifiedProjectIds(projects, qualifiedCount);

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
            // 结束期：UV数据不再更新（由UVSyncScheduler控制），但使用相同的排序逻辑保持投资期的排名

            // 步骤1：获取晋级项目ID列表
            List<Long> qualifiedProjectIds = (configRecords != null)
                    ? resolveQualifiedProjectIdsFromData(projects, qualifiedCount, configRecords)
                    : resolveQualifiedProjectIds(projects, qualifiedCount);

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

            // 步骤3：只在晋级区的15个队伍内按UV排序，计算UV排名分数
            List<Project> qualifiedProjectsForRanking = projects.stream()
                    .filter(Project::getQualified)
                    .collect(Collectors.toList());

            List<Project> uvSorted = new ArrayList<>(qualifiedProjectsForRanking);
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
                int rank = i + 1; // UV排名（1-15）
                Project p = uvSorted.get(i);
                // 新公式：UV分数 = (16 - UV排名) / 15，但UV为0时分数为0
                double score;
                if (p.getUv() != 0) {
                    score = (double)(16 - rank) / 15;
                } else {
                    score = 0.0;
                }
                uvRankMap.put(p.getId(), rank);
                uvScores.put(p.getId(), score);
            }

            // 步骤4：只在晋级区的15个队伍内按投资额排序，计算投资额排名分数
            List<Project> investmentSorted = new ArrayList<>(qualifiedProjectsForRanking);
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
                int rank = i + 1; // 投资额排名（1-15）
                Project p = investmentSorted.get(i);
                // 新公式：投资额分数 = (16 - 投资额排名) / 15，但投资额为0时分数为0
                double score;
                if (p.getInvestment() != 0) {
                    score = (double)(16 - rank) / 15;
                } else {
                    score = 0.0;
                }
                investmentRankMap.put(p.getId(), rank);
                investmentScores.put(p.getId(), score);
            }

            // 步骤5：只对晋级项目计算加权分数
            qualifiedProjectsForRanking.forEach(p -> {
                int uvRank = uvRankMap.get(p.getId());
                int investRank = investmentRankMap.get(p.getId());
                double uvScore = uvScores.get(p.getId());
                double investScore = investmentScores.get(p.getId());
                // 新加权公式：加权分数 = UV分数*20% + 投资额分数*80%
                double weightedScore = uvScore * 0.2 + investScore * 0.8;
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

            String stageName = stage == CompetitionStage.ENDED ? "结束期" : "投资期";
            log.debug("{}排名计算完成，使用加权算法：(16-UV排名)/15*0.2 + (16-投资额排名)/15*0.8（仅晋级区15队）", stageName);
        }
    }

    /**
     * 从已获取的配置数据中解析晋级项目配置（新方法，避免重复API调用）
     */
    private QualifiedProjectsConfig loadQualifiedProjectsConfigFromData(List<Map<String, Object>> configRecords) {
        try {
            for (Map<String, Object> record : configRecords) {
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
            log.warn("从数据解析晋级项目配置失败", e);
        }
        return new QualifiedProjectsConfig(new LinkedHashSet<>(), null);
    }

    /**
     * 旧方法：保留用于其他调用点
     */
    private QualifiedProjectsConfig loadQualifiedProjectsConfig() {
        try {
            String tableId = feishuConfig.getConfigTableId();
            List<Map<String, Object>> records = feishuService.listRecords(tableId);
            return loadQualifiedProjectsConfigFromData(records);
        } catch (Exception e) {
            log.warn("获取晋级项目配置失败", e);
        }
        return new QualifiedProjectsConfig(new LinkedHashSet<>(), null);
    }

    /**
     * 从已获取的配置数据中解析晋级项目ID列表（新方法，避免重复API调用）
     */
    private List<Long> resolveQualifiedProjectIdsFromData(List<Project> projects, int qualifiedCount, List<Map<String, Object>> configRecords) {
        QualifiedProjectsConfig config = loadQualifiedProjectsConfigFromData(configRecords);
        if (!config.getProjectIds().isEmpty()) {
            // 限制晋级项目数量，最多取qualifiedCount个
            List<Long> projectIds = new ArrayList<>(config.getProjectIds());
            if (projectIds.size() > qualifiedCount) {
                log.warn("配置的晋级项目数量{}超过限制{}，截取前{}个", projectIds.size(), qualifiedCount, qualifiedCount);
                projectIds = projectIds.subList(0, qualifiedCount);
            }
            return projectIds;
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

    /**
     * 旧方法：保留用于其他调用点
     */
    private List<Long> resolveQualifiedProjectIds(List<Project> projects, int qualifiedCount) {
        QualifiedProjectsConfig config = loadQualifiedProjectsConfig();
        if (!config.getProjectIds().isEmpty()) {
            // 限制晋级项目数量，最多取qualifiedCount个
            List<Long> projectIds = new ArrayList<>(config.getProjectIds());
            if (projectIds.size() > qualifiedCount) {
                log.warn("配置的晋级项目数量{}超过限制{}，截取前{}个", projectIds.size(), qualifiedCount, qualifiedCount);
                projectIds = projectIds.subList(0, qualifiedCount);
            }
            return projectIds;
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

    /**
     * 从已获取的配置数据中清除晋级项目配置（新方法，避免重复API调用）
     */
    private void clearQualifiedProjectsConfigFromData(List<Map<String, Object>> configRecords) {
        try {
            QualifiedProjectsConfig config = loadQualifiedProjectsConfigFromData(configRecords);
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

    /**
     * 旧方法：保留用于其他调用点
     */
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

    private Map<String, Object> findRecordByField(String tableId, String fieldName, Object value) {
        try {
            // 直接使用全表扫描，飞书filter语法复杂且不稳定
            // 投资人表和项目表数据量小（<100条），全表扫描性能可接受
            List<Map<String, Object>> records = feishuService.listRecords(tableId);
            return records.stream()
                    .filter(record -> fieldValueEquals(record.get(fieldName), value))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.error("查询字段 {}={} 失败", fieldName, value, e);
            return null;
        }
    }

    private String buildFilterExpression(String fieldName, Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number) {
            return String.format("CurrentValue.[%s] = %s", fieldName, value);
        }

        String escaped = escapeFilterValue(value.toString());
        return String.format("CurrentValue.[%s] = \"%s\"", fieldName, escaped);
    }

    private String escapeFilterValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean fieldValueEquals(Object recordValue, Object targetValue) {
        // 两者都为null，相等
        if (recordValue == null && targetValue == null) {
            return true;
        }
        // 只有一个为null，不相等
        if (recordValue == null || targetValue == null) {
            return false;
        }

        // 都是数字类型，按数值比较
        if (recordValue instanceof Number && targetValue instanceof Number) {
            return Double.compare(
                    ((Number) recordValue).doubleValue(),
                    ((Number) targetValue).doubleValue()
            ) == 0;
        }

        // 一个是数字一个是字符串，转字符串比较
        if (recordValue instanceof Number || targetValue instanceof Number) {
            String str1 = recordValue.toString().trim();
            String str2 = targetValue.toString().trim();
            return str1.equals(str2);
        }

        // 都是字符串或其他类型，转字符串比较（忽略前后空格）
        String str1 = recordValue.toString().trim();
        String str2 = targetValue.toString().trim();
        return str1.equals(str2);
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
        if (map == null || key == null) {
            return 0L;
        }
        Object value = map.get(key);
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            String strValue = value.toString().trim();
            if (strValue.isEmpty()) {
                return 0L;
            }
            return Long.parseLong(strValue);
        } catch (NumberFormatException e) {
            log.warn("字段{}的值{}无法转换为Long，返回默认值0", key, value);
            return 0L;
        }
    }

    private Integer getInteger(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return 0;
        }
        Object value = map.get(key);
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            String strValue = value.toString().trim();
            if (strValue.isEmpty()) {
                return 0;
            }
            return Integer.parseInt(strValue);
        } catch (NumberFormatException e) {
            log.warn("字段{}的值{}无法转换为Integer，返回默认值0", key, value);
            return 0;
        }
    }

    // ==================== 缓存管理方法 ====================

    /**
     * 清除所有缓存
     * 用于测试时手动修改飞书数据后立即刷新
     */
    public void clearAllCache() {
        cacheManager.getCacheNames().forEach(cacheName -> {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
                log.info("清除缓存: {}", cacheName);
            }
        });
    }

    /**
     * 清除指定投资人缓存
     *
     * @param username 投资人账号
     */
    @CacheEvict(value = "investor", key = "#username")
    public void clearInvestorCache(String username) {
        log.info("清除投资人缓存: {}", username);
    }

    /**
     * 清除指定项目缓存
     *
     * @param projectId 项目ID
     */
    @CacheEvict(value = "project", key = "#projectId")
    public void clearProjectCache(Long projectId) {
        log.info("清除项目缓存: {}", projectId);
    }
}
