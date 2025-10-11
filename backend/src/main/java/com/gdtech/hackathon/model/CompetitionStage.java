package com.gdtech.hackathon.model;

/**
 * 比赛阶段枚举
 */
public enum CompetitionStage {

    /**
     * 海选期
     */
    SELECTION("selection", "海选期", "10月24日24:00 - 11月7日12:00",
            "本阶段以累计UV排名,如果UV相同,则按队伍序号排名。本阶段结束,前15名晋级,在投资期可以接受投资人投资"),

    /**
     * 锁定期
     */
    LOCK("lock", "锁定期", "11月7日12:00 - 11月14日0:00",
            "本阶段期间,已晋级的15个作品一个队列,按UV排名;其他作品处在非晋级区,单独一个队列,依然按照UV排名"),

    /**
     * 投资期
     */
    INVESTMENT("investment", "投资期", "11月14日0:00 - 18:00",
            "本阶段,投资人可将虚拟投资金投给晋级的15个作品。本阶段排名按照权重值(UV*40%+投资金额*60%)排序,权重相同按投资金额高低排序,投资金额相同按队伍序号排序"),

    /**
     * 活动结束
     */
    ENDED("ended", "活动结束", "11月14日18:00之后",
            "活动结束,所有作品不再更新UV、投资额数据,排名不变");

    private final String code;
    private final String name;
    private final String time;
    private final String rule;

    CompetitionStage(String code, String name, String time, String rule) {
        this.code = code;
        this.name = name;
        this.time = time;
        this.rule = rule;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getTime() {
        return time;
    }

    public String getRule() {
        return rule;
    }

    /**
     * 投资期是否可以投资
     */
    public boolean canInvest() {
        return this == INVESTMENT;
    }

    /**
     * 根据code获取枚举
     */
    public static CompetitionStage fromCode(String code) {
        for (CompetitionStage stage : values()) {
            if (stage.code.equals(code)) {
                return stage;
            }
        }
        return SELECTION; // 默认返回海选期
    }
}
