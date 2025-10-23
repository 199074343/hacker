package com.gdtech.hackathon.controller;

import com.gdtech.hackathon.dto.ApiResponse;
import com.gdtech.hackathon.dto.InvestmentRequest;
import com.gdtech.hackathon.dto.LoginRequest;
import com.gdtech.hackathon.model.CompetitionStage;
import com.gdtech.hackathon.model.Investor;
import com.gdtech.hackathon.model.Project;
import com.gdtech.hackathon.service.HackathonService;
import com.gdtech.hackathon.service.WeChatService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 骇客大赛API控制器
 */
@Slf4j
@RestController
@RequestMapping("/hackathon")
@Validated
public class HackathonController {

    private final HackathonService hackathonService;
    private final WeChatService weChatService;
    private final com.gdtech.hackathon.service.BaiduTongjiService baiduTongjiService;

    public HackathonController(HackathonService hackathonService,
                              WeChatService weChatService,
                              com.gdtech.hackathon.service.BaiduTongjiService baiduTongjiService) {
        this.hackathonService = hackathonService;
        this.weChatService = weChatService;
        this.baiduTongjiService = baiduTongjiService;
    }

    /**
     * 获取当前比赛阶段信息
     */
    @GetMapping("/stage")
    public ApiResponse<Map<String, Object>> getCurrentStage() {
        try {
            CompetitionStage stage = hackathonService.getCurrentStage();

            Map<String, Object> data = new HashMap<>();
            data.put("code", stage.getCode());
            data.put("name", stage.getName());
            data.put("time", stage.getTime());
            data.put("rule", stage.getRule());
            data.put("canInvest", stage.canInvest());

            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("获取比赛阶段失败", e);
            return ApiResponse.error("获取比赛阶段失败");
        }
    }

    /**
     * 获取所有项目列表（带排名）
     */
    @GetMapping("/projects")
    public ApiResponse<List<Project>> getAllProjects() {
        try {
            List<Project> projects = hackathonService.getAllProjects();
            return ApiResponse.success(projects);
        } catch (Exception e) {
            log.error("获取项目列表失败", e);
            return ApiResponse.error("获取项目列表失败");
        }
    }

    /**
     * 根据ID获取项目详情
     */
    @GetMapping("/projects/{id}")
    public ApiResponse<Project> getProjectById(@PathVariable Long id) {
        try {
            Project project = hackathonService.getProjectById(id);
            if (project == null) {
                return ApiResponse.error(404, "项目不存在");
            }
            return ApiResponse.success(project);
        } catch (Exception e) {
            log.error("获取项目详情失败", e);
            return ApiResponse.error("获取项目详情失败");
        }
    }

    /**
     * 投资人登录
     */
    @PostMapping("/login")
    public ApiResponse<Investor> login(@Valid @RequestBody LoginRequest request) {
        try {
            Investor investor = hackathonService.login(request.getUsername(), request.getPassword());
            if (investor == null) {
                return ApiResponse.error(401, "账号或密码错误");
            }
            return ApiResponse.success("登录成功", investor);
        } catch (Exception e) {
            log.error("登录失败", e);
            return ApiResponse.error("登录失败");
        }
    }

    /**
     * 获取投资人信息（含投资历史）
     */
    @GetMapping("/investor/{username}")
    public ApiResponse<Investor> getInvestorInfo(@PathVariable String username) {
        try {
            Investor investor = hackathonService.getInvestorInfo(username);
            if (investor == null) {
                return ApiResponse.error(404, "投资人不存在");
            }
            return ApiResponse.success(investor);
        } catch (Exception e) {
            log.error("获取投资人信息失败", e);
            return ApiResponse.error("获取投资人信息失败");
        }
    }

    /**
     * 执行投资
     */
    @PostMapping("/invest")
    public ApiResponse<String> invest(@Valid @RequestBody InvestmentRequest request) {
        try {
            boolean success = hackathonService.invest(
                    request.getInvestorUsername(),
                    request.getInvestorName(),
                    request.getProjectId(),
                    request.getProjectName(),
                    request.getAmount()
            );

            if (success) {
                return ApiResponse.success("投资成功", "投资 " + request.getAmount() + " 万元");
            } else {
                return ApiResponse.error("投资失败");
            }
        } catch (IllegalStateException e) {
            log.warn("投资失败: {}", e.getMessage());
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("投资失败", e);
            return ApiResponse.error("投资失败: " + e.getMessage());
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("GDTech Hackathon API is running");
    }

    /**
     * 清除所有缓存
     * 用于测试时手动修改飞书数据后立即刷新缓存
     *
     * 使用场景:
     * 1. 手动修改飞书投资人信息后
     * 2. 手动修改飞书项目信息后
     * 3. 手动修改晋级名单后
     *
     * @return 清除结果
     */
    @PostMapping("/cache/clear")
    public ApiResponse<String> clearAllCache() {
        try {
            hackathonService.clearAllCache();
            log.info("手动清除所有缓存成功");
            return ApiResponse.success("缓存已清除,数据将从飞书重新加载");
        } catch (Exception e) {
            log.error("清除缓存失败", e);
            return ApiResponse.error("清除缓存失败: " + e.getMessage());
        }
    }

    /**
     * 清除指定投资人缓存
     *
     * @param username 投资人账号
     * @return 清除结果
     */
    @PostMapping("/cache/clear/investor/{username}")
    public ApiResponse<String> clearInvestorCache(@PathVariable String username) {
        try {
            hackathonService.clearInvestorCache(username);
            log.info("清除投资人 {} 的缓存成功", username);
            return ApiResponse.success("投资人 " + username + " 的缓存已清除");
        } catch (Exception e) {
            log.error("清除投资人缓存失败", e);
            return ApiResponse.error("清除缓存失败: " + e.getMessage());
        }
    }

    /**
     * 清除指定项目缓存
     *
     * @param projectId 项目ID
     * @return 清除结果
     */
    @PostMapping("/cache/clear/project/{projectId}")
    public ApiResponse<String> clearProjectCache(@PathVariable Long projectId) {
        try {
            hackathonService.clearProjectCache(projectId);
            log.info("清除项目 {} 的缓存成功", projectId);
            return ApiResponse.success("项目 " + projectId + " 的缓存已清除");
        } catch (Exception e) {
            log.error("清除项目缓存失败", e);
            return ApiResponse.error("清除缓存失败: " + e.getMessage());
        }
    }

    /**
     * 获取微信 JS-SDK 签名配置
     *
     * @param url 当前页面URL（必填，不包含#及其后面部分）
     * @return 微信签名配置 {appId, timestamp, nonceStr, signature}
     */
    /**
     * 获取服务器出口IP地址（用于配置微信IP白名单）
     * 通过调用微信API并从错误信息中提取IP
     */
    @GetMapping("/server-ip")
    public ApiResponse<Map<String, String>> getServerIp() {
        try {
            log.info("尝试获取服务器出口IP");
            Map<String, String> result = new HashMap<>();

            // 方法1: 尝试调用微信API，从错误信息中提取IP
            try {
                String accessToken = weChatService.getAccessToken();
                result.put("method", "通过微信Access Token推断");
                result.put("accessToken", accessToken.substring(0, Math.min(20, accessToken.length())) + "...");
                result.put("message", "Access Token获取成功，请在Zeabur控制台执行: curl https://ifconfig.me 或查看环境变量获取真实出口IP");
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                log.warn("微信API调用失败: {}", errorMsg);
                // 尝试从错误信息中提取IP（微信会在错误中包含请求IP）
                if (errorMsg != null && errorMsg.contains("ip")) {
                    result.put("error", errorMsg);
                }
            }

            // 方法2: 返回Zeabur环境变量中的IP提示
            String zeaburRegion = System.getenv("ZEABUR_REGION");
            result.put("zeabur_region", zeaburRegion != null ? zeaburRegion : "unknown");
            result.put("instruction", "请在Zeabur控制台Terminal执行以下命令之一获取真实出口IP:");
            result.put("command1", "wget -qO- https://ifconfig.me");
            result.put("command2", "python3 -c \"import urllib.request; print(urllib.request.urlopen('https://ifconfig.me').read().decode())\"");
            result.put("command3", "在Zeabur Dashboard -> Service -> Networking 中查看");

            log.info("返回IP获取提示: {}", result);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取服务器IP失败", e);
            return ApiResponse.error("获取服务器IP失败: " + e.getMessage());
        }
    }

    @GetMapping("/wechat/signature")
    public ApiResponse<Map<String, String>> getWeChatSignature(@RequestParam String url) {
        try {
            log.info("获取微信签名: url={}", url);
            Map<String, String> signature = weChatService.generateSignature(url);
            return ApiResponse.success(signature);
        } catch (Exception e) {
            log.error("获取微信签名失败", e);
            return ApiResponse.error("获取微信签名失败: " + e.getMessage());
        }
    }

    /**
     * 测试百度统计接口
     *
     * @param accountName 账号名称（account1 或 account2）
     * @param siteId 站点ID
     * @return UV统计数据
     */
    @GetMapping("/baidu/test")
    public ApiResponse<Map<String, Object>> testBaiduStats(
            @RequestParam(defaultValue = "account1") String accountName,
            @RequestParam String siteId) {
        try {
            log.info("测试百度统计接口: account={}, siteId={}", accountName, siteId);

            Integer cumulativeUV = baiduTongjiService.getCumulativeUVFromStart(accountName, siteId);
            Integer todayUV = baiduTongjiService.getTodayUV(accountName, siteId);
            Integer realtimeVisitors = baiduTongjiService.getRealtimeVisitors(accountName, siteId);

            Map<String, Object> result = new HashMap<>();
            result.put("accountName", accountName);
            result.put("siteId", siteId);
            result.put("cumulativeUV", cumulativeUV);
            result.put("todayUV", todayUV);
            result.put("realtimeVisitors", realtimeVisitors);
            result.put("message", "活动期间累计UV：" + cumulativeUV + "，当日UV：" + todayUV + "，实时访客：" + realtimeVisitors);

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("测试百度统计接口失败", e);
            return ApiResponse.error("测试失败: " + e.getMessage());
        }
    }
}
