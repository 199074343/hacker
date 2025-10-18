package com.gdtech.hackathon.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 投资请求
 */
@Data
public class InvestmentRequest {

    /**
     * 项目ID
     */
    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    /**
     * 投资金额（万元）
     */
    @NotNull(message = "投资金额不能为空")
    @Min(value = 1, message = "投资金额必须大于0")
    private Integer amount;

    /**
     * 投资人账号（从session或token中获取，这里用于临时方案）
     */
    @NotNull(message = "投资人账号不能为空")
    private String investorUsername;

    /**
     * 投资人姓名（前端传递，避免后端重复查询）
     */
    @NotNull(message = "投资人姓名不能为空")
    private String investorName;

    /**
     * 项目名称（前端传递，避免后端重复查询）
     */
    @NotNull(message = "项目名称不能为空")
    private String projectName;
}
