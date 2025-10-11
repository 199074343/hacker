package com.gdtech.hackathon.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 参赛项目
 */
@Data
public class Project {

    /**
     * 项目ID
     */
    private Long id;

    /**
     * 项目名称
     */
    private String name;

    /**
     * 一句话描述
     */
    private String description;

    /**
     * 项目网址
     */
    private String url;

    /**
     * 项目配图URL
     */
    private String image;

    /**
     * 队伍名称
     */
    private String teamName;

    /**
     * 队伍编号
     */
    private String teamNumber;

    /**
     * 团队介绍页URL
     */
    private String teamUrl;

    /**
     * 百度统计 Site ID
     */
    private String baiduSiteId;

    /**
     * 累计UV（从百度统计同步）
     */
    private Long uv = 0L;

    /**
     * 获得投资总额（万元）
     */
    private Integer investment = 0;

    /**
     * 投资记录
     */
    private List<InvestmentRecord> investmentRecords = new ArrayList<>();

    /**
     * 是否启用
     */
    private Boolean enabled = true;

    /**
     * 排名（动态计算）
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer rank;

    /**
     * 是否晋级（动态计算）
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Boolean qualified;

    /**
     * 权重分数（投资期使用）
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Double weightedScore;
}
