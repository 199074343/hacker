package com.gdtech.hackathon.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 投资历史（投资人维度）
 */
@Data
public class InvestmentHistory {

    /**
     * 投资时间
     */
    private LocalDateTime time;

    /**
     * 项目名称
     */
    private String projectName;

    /**
     * 队伍名称
     */
    private String teamName;

    /**
     * 队伍编号
     */
    private String teamNumber;

    /**
     * 投资金额（万元）
     */
    private Integer amount;
}
