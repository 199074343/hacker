package com.gdtech.hackathon.model;

import lombok.Data;

/**
 * 投资记录（项目维度）
 */
@Data
public class InvestmentRecord {

    /**
     * 投资人姓名
     */
    private String name;

    /**
     * 投资人职务
     */
    private String title;

    /**
     * 投资人头像
     */
    private String avatar;

    /**
     * 投资金额（万元）
     */
    private Integer amount;

    /**
     * 投资人初始额度（万元）
     */
    private Integer initialAmount;
}
