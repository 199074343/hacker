package com.gdtech.hackathon.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 投资人
 */
@Data
public class Investor {

    /**
     * 投资人ID
     */
    private Long id;

    /**
     * 飞书记录ID - 用于更新操作，不返回给前端
     */
    @JsonIgnore
    private String recordId;

    /**
     * 账号（4位数字）
     */
    private String username;

    /**
     * 密码（6位数字+小写字母）- 不返回给前端
     */
    @JsonIgnore
    private String password;

    /**
     * 姓名
     */
    private String name;

    /**
     * 职务
     */
    private String title;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 初始额度（万元）
     */
    private Integer initialAmount;

    /**
     * 剩余额度（万元）- 动态计算
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer remainingAmount;

    /**
     * 已投资金额（万元）- 动态计算
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer investedAmount;

    /**
     * 投资历史记录
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private List<InvestmentHistory> investmentHistory = new ArrayList<>();

    /**
     * 是否启用
     */
    @JsonIgnore
    private Boolean enabled = true;
}
