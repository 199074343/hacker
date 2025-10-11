package com.gdtech.hackathon.controller;

import com.gdtech.hackathon.dto.ApiResponse;
import com.gdtech.hackathon.dto.InvestmentRequest;
import com.gdtech.hackathon.dto.LoginRequest;
import com.gdtech.hackathon.model.CompetitionStage;
import com.gdtech.hackathon.model.Investor;
import com.gdtech.hackathon.model.Project;
import com.gdtech.hackathon.service.HackathonService;
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

    public HackathonController(HackathonService hackathonService) {
        this.hackathonService = hackathonService;
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
                    request.getProjectId(),
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
}
