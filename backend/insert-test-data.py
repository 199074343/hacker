#!/usr/bin/env python3
"""
插入测试数据到飞书多维表格
包含：20个项目、3个投资人、若干投资记录
"""

import requests
import json
import time

# 飞书配置
APP_ID = "cli_a862c494a9431013"
APP_SECRET = "tUw7iGj2MKCDVXyGsqJWdh7iLuhqhPc4"
APP_TOKEN = "VZ77bJ9MvalDxqscf0Bcfh0Tnjp"

# 表ID
TABLES = {
    "projects": "tblGAbkdJKqTOlEw",
    "investors": "tblDp18p0G4xcADk",
    "investments": "tblh9DMhfC4dQHMJ",
    "config": "tbl43OV7SBtzFzw1"
}

def get_token():
    """获取访问令牌"""
    resp = requests.post(
        "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal",
        json={"app_id": APP_ID, "app_secret": APP_SECRET}
    )
    data = resp.json()
    if data.get('code') == 0:
        return data['tenant_access_token']
    else:
        raise Exception(f"获取Token失败: {data}")

def create_record(token, table_id, fields):
    """创建记录"""
    url = f"https://open.feishu.cn/open-apis/bitable/v1/apps/{APP_TOKEN}/tables/{table_id}/records"
    headers = {"Authorization": f"Bearer {token}"}
    payload = {"fields": fields}

    resp = requests.post(url, headers=headers, json=payload)
    data = resp.json()

    if data.get('code') == 0:
        return data['data']['record']['record_id']
    else:
        raise Exception(f"创建失败: {data}")

# ==================== 测试数据 ====================

# 20个参赛项目
PROJECTS_DATA = [
    {
        "项目ID": 1,
        "项目名称": "AI智能作业批改助手",
        "一句话描述": "基于GPT-4的智能作业批改系统，支持主观题自动评分",
        "项目网址": "https://project1.example.com",
        "项目配图URL": "https://picsum.photos/400/300?random=1",
        "队伍名称": "智能教育队",
        "队伍编号": "001",
        "团队介绍页URL": "https://team1.example.com",
        "百度统计SiteID": "10001",
        "累计UV": 12500,
        "是否启用": True
    },
    {
        "项目ID": 2,
        "项目名称": "虚拟实验室平台",
        "一句话描述": "在线化学实验模拟系统，让学生安全体验实验过程",
        "项目网址": "https://project2.example.com",
        "项目配图URL": "https://picsum.photos/400/300?random=2",
        "队伍名称": "虚拟科技队",
        "队伍编号": "002",
        "团队介绍页URL": "https://team2.example.com",
        "百度统计SiteID": "10002",
        "累计UV": 9800,
        "是否启用": True
    },
    {
        "项目ID": 3,
        "项目名称": "个性化学习路径规划器",
        "一句话描述": "AI分析学生学习数据，生成定制化学习计划",
        "项目网址": "https://project3.example.com",
        "项目配图URL": "https://picsum.photos/400/300?random=3",
        "队伍名称": "智慧学习队",
        "队伍编号": "003",
        "团队介绍页URL": "https://team3.example.com",
        "百度统计SiteID": "10003",
        "累计UV": 15200,
        "是否启用": True
    },
    {
        "项目ID": 4,
        "项目名称": "互动式编程教学平台",
        "一句话描述": "实时代码协作和智能提示的编程学习环境",
        "项目网址": "https://project4.example.com",
        "项目配图URL": "https://picsum.photos/400/300?random=4",
        "队伍名称": "代码先锋队",
        "队伍编号": "004",
        "团队介绍页URL": "https://team4.example.com",
        "百度统计SiteID": "10004",
        "累计UV": 18600,
        "是否启用": True
    },
    {
        "项目ID": 5,
        "项目名称": "AR增强现实历史课堂",
        "一句话描述": "通过AR技术让历史场景重现，沉浸式学习体验",
        "项目网址": "https://project5.example.com",
        "项目配图URL": "https://picsum.photos/400/300?random=5",
        "队伍名称": "时空穿越队",
        "队伍编号": "005",
        "团队介绍页URL": "https://team5.example.com",
        "百度统计SiteID": "10005",
        "累计UV": 11200,
        "是否启用": True
    },
    {
        "项目ID": 6,
        "项目名称": "智能错题本",
        "一句话描述": "自动识别错题，生成相似题型练习和知识点讲解",
        "项目网址": "https://project6.example.com",
        "项目配图URL": "https://picsum.photos/400/300?random=6",
        "队伍名称": "学习助手队",
        "队伍编号": "006",
        "团队介绍页URL": "https://team6.example.com",
        "百度统计SiteID": "10006",
        "累计UV": 14500,
        "是否启用": True
    },
    {
        "项目ID": 7,
        "项目名称": "在线口语陪练AI",
        "一句话描述": "AI虚拟外教，支持多语言对话练习和发音纠正",
        "项目网址": "https://project7.example.com",
        "项目配图URL": "https://picsum.photos/400/300?random=7",
        "队伍名称": "语言大师队",
        "队伍编号": "007",
        "团队介绍页URL": "https://team7.example.com",
        "百度统计SiteID": "10007",
        "累计UV": 16800,
        "是否启用": True
    },
    {
        "项目ID": 8,
        "项目名称": "数学可视化工具箱",
        "一句话描述": "抽象数学概念3D可视化，帮助学生理解复杂公式",
        "项目网址": "https://project8.example.com",
        "项目配图URL": "https://picsum.photos/400/300?random=8",
        "队伍名称": "数学之美队",
        "队伍编号": "008",
        "团队介绍页URL": "https://team8.example.com",
        "百度统计SiteID": "10008",
        "累计UV": 13400,
        "是否启用": True
    },
    {
        "项目ID": 9,
        "项目名称": "职业规划导师系统",
        "一句话描述": "基于大数据的职业发展建议和课程推荐平台",
        "项目网址": "https://project9.example.com",
        "项目配图URL": "https://picsum.photos/400/300?random=9",
        "队伍名称": "未来规划队",
        "队伍编号": "009",
        "团队介绍页URL": "https://team9.example.com",
        "百度统计SiteID": "10009",
        "累计UV": 10500,
        "是否启用": True
    },
    {
        "项目ID": 10,
        "项目名称": "游戏化学习激励平台",
        "一句话描述": "将学习过程游戏化，通过任务和排行榜提升学习动力",
        "项目网址": "https://project10.example.com",
        "项目配图URL": "https://picsum.photos/400/300?random=10",
        "队伍名称": "游戏学习队",
        "队伍编号": "010",
        "团队介绍页URL": "https://team10.example.com",
        "百度统计SiteID": "10010",
        "累计UV": 19200,
        "是否启用": True
    },
    {
        "项目ID": 11,
        "项目名称": "智能课程表优化器",
        "一句话描述": "根据学生生物钟和学习效率自动安排最优课程表",
        "项目网址": "https://project11.example.com",
        "项目配图URL": "https://picsum.photos/400/300?random=11",
        "队伍名称": "时间管理队",
        "队伍编号": "011",
        "团队介绍页URL": "https://team11.example.com",
        "百度统计SiteID": "10011",
        "累计UV": 8900,
        "是否启用": True
    },
    {
        "项目ID": 12,
        "项目名称": "论文写作AI助手",
        "一句话描述": "智能文献检索、论点提炼和格式校验工具",
        "项目网址": "https://project12.example.com",
        "项目配图URL": "https://picsum.photos/400/300?random=12",
        "队伍名称": "学术助手队",
        "队伍编号": "012",
        "团队介绍页URL": "https://team12.example.com",
        "百度统计SiteID": "10012",
        "累计UV": 17300,
        "是否启用": True
    },
    {
        "项目ID": 13,
        "项目名称": "音乐理论学习游戏",
        "一句话描述": "通过互动游戏学习乐理知识和音乐创作",
        "项目网址": "https://project13.example.com",
        "项目配图URL": "https://picsum.photos/400/300?random=13",
        "队伍名称": "音乐教育队",
        "队伍编号": "013",
        "团队介绍页URL": "https://team13.example.com",
        "百度统计SiteID": "10013",
        "累计UV": 11800,
        "是否启用": True
    },
    {
        "项目ID": 14,
        "项目名称": "在线实验报告生成器",
        "一句话描述": "自动分析实验数据，生成标准格式实验报告",
        "项目网址": "https://project14.example.com",
        "项目配图URL": "https://picsum.photos/400/300?random=14",
        "队伍名称": "实验助手队",
        "队伍编号": "014",
        "团队介绍页URL": "https://team14.example.com",
        "百度统计SiteID": "10014",
        "累计UV": 13900,
        "是否启用": True
    },
    {
        "项目ID": 15,
        "项目名称": "思维导图协作平台",
        "一句话描述": "实时多人协作思维导图，支持AI自动扩展和整理",
        "项目网址": "https://project15.example.com",
        "项目配图URL": "https://picsum.photos/400/300?random=15",
        "队伍名称": "思维协作队",
        "队伍编号": "015",
        "团队介绍页URL": "https://team15.example.com",
        "百度统计SiteID": "10015",
        "累计UV": 20100,
        "是否启用": True
    },
    {
        "项目ID": 16,
        "项目名称": "知识图谱学习系统",
        "一句话描述": "用知识图谱展示学科知识点关系，清晰学习路径",
        "项目网址": "https://project16.example.com",
        "项目配图URL": "https://picsum.photos/400/300?random=16",
        "队伍名称": "知识图谱队",
        "队伍编号": "016",
        "团队介绍页URL": "https://team16.example.com",
        "百度统计SiteID": "10016",
        "累计UV": 9200,
        "是否启用": True
    },
    {
        "项目ID": 17,
        "项目名称": "智能考试防作弊系统",
        "一句话描述": "AI监控考试过程，识别异常行为保证考试公平",
        "项目网址": "https://project17.example.com",
        "项目配图URL": "https://picsum.photos/400/300?random=17",
        "队伍名称": "诚信考试队",
        "队伍编号": "017",
        "团队介绍页URL": "https://team17.example.com",
        "百度统计SiteID": "10017",
        "累计UV": 14700,
        "是否启用": True
    },
    {
        "项目ID": 18,
        "项目名称": "编程竞赛训练营",
        "一句话描述": "在线编程题库和实时对战，提升算法能力",
        "项目网址": "https://project18.example.com",
        "项目配图URL": "https://picsum.photos/400/300?random=18",
        "队伍名称": "算法竞技队",
        "队伍编号": "018",
        "团队介绍页URL": "https://team18.example.com",
        "百度统计SiteID": "10018",
        "累计UV": 16200,
        "是否启用": True
    },
    {
        "项目ID": 19,
        "项目名称": "家校沟通桥梁平台",
        "一句话描述": "连接家长、老师和学生的智能沟通和反馈系统",
        "项目网址": "https://project19.example.com",
        "项目配图URL": "https://picsum.photos/400/300?random=19",
        "队伍名称": "家校共育队",
        "队伍编号": "019",
        "团队介绍页URL": "https://team19.example.com",
        "百度统计SiteID": "10019",
        "累计UV": 12100,
        "是否启用": True
    },
    {
        "项目ID": 20,
        "项目名称": "注意力训练游戏",
        "一句话描述": "通过科学游戏提升学生专注力和记忆力",
        "项目网址": "https://project20.example.com",
        "项目配图URL": "https://picsum.photos/400/300?random=20",
        "队伍名称": "专注力队",
        "队伍编号": "020",
        "团队介绍页URL": "https://team20.example.com",
        "百度统计SiteID": "10020",
        "累计UV": 10800,
        "是否启用": True
    }
]

# 3个投资人
INVESTORS_DATA = [
    {
        "投资人ID": 1001,
        "账号": "1001",
        "初始密码": "inv001",
        "姓名": "张明",
        "职务": "高级产品总监",
        "头像URL": "https://ui-avatars.com/api/?name=Zhang+Ming&size=200&background=4F46E5&color=fff",
        "初始额度": 100,
        "是否启用": True
    },
    {
        "投资人ID": 1002,
        "账号": "1002",
        "初始密码": "inv002",
        "姓名": "李华",
        "职务": "技术VP",
        "头像URL": "https://ui-avatars.com/api/?name=Li+Hua&size=200&background=10B981&color=fff",
        "初始额度": 100,
        "是否启用": True
    },
    {
        "投资人ID": 1003,
        "账号": "1003",
        "初始密码": "inv003",
        "姓名": "王芳",
        "职务": "教育事业部总经理",
        "头像URL": "https://ui-avatars.com/api/?name=Wang+Fang&size=200&background=F59E0B&color=fff",
        "初始额度": 100,
        "是否启用": True
    }
]

# ==================== 主函数 ====================

def main():
    print("=" * 60)
    print("🚀 开始插入测试数据到飞书多维表格")
    print("=" * 60)

    # 获取token
    print("\n🔑 获取访问令牌...")
    token = get_token()
    print(f"✅ Token获取成功: {token[:20]}...")

    # 1. 插入项目数据
    print("\n" + "=" * 60)
    print(f"📊 插入 {len(PROJECTS_DATA)} 个项目...")
    print("=" * 60)

    for i, project in enumerate(PROJECTS_DATA, 1):
        try:
            record_id = create_record(token, TABLES["projects"], project)
            print(f"✅ [{i:2d}/20] {project['项目名称'][:30]:30s} (ID: {record_id})")
            time.sleep(0.3)  # 避免请求过快
        except Exception as e:
            print(f"❌ [{i:2d}/20] {project['项目名称'][:30]:30s} 失败: {e}")

    # 2. 插入投资人数据
    print("\n" + "=" * 60)
    print(f"👥 插入 {len(INVESTORS_DATA)} 个投资人...")
    print("=" * 60)

    for i, investor in enumerate(INVESTORS_DATA, 1):
        try:
            record_id = create_record(token, TABLES["investors"], investor)
            print(f"✅ [{i}/3] {investor['姓名']:10s} - {investor['账号']} / {investor['初始密码']} (ID: {record_id})")
            time.sleep(0.3)
        except Exception as e:
            print(f"❌ [{i}/3] {investor['姓名']:10s} 失败: {e}")

    # 3. 插入一些投资记录（模拟前期投资）
    print("\n" + "=" * 60)
    print("💰 插入模拟投资记录...")
    print("=" * 60)

    # 张明投资3个项目
    investments = [
        {"投资人账号": "1001", "项目ID": 4, "投资金额": 30, "投资人姓名": "张明", "项目名称": "互动式编程教学平台"},
        {"投资人账号": "1001", "项目ID": 10, "投资金额": 25, "投资人姓名": "张明", "项目名称": "游戏化学习激励平台"},
        {"投资人账号": "1001", "项目ID": 15, "投资金额": 20, "投资人姓名": "张明", "项目名称": "思维导图协作平台"},

        # 李华投资3个项目
        {"投资人账号": "1002", "项目ID": 3, "投资金额": 35, "投资人姓名": "李华", "项目名称": "个性化学习路径规划器"},
        {"投资人账号": "1002", "项目ID": 7, "投资金额": 30, "投资人姓名": "李华", "项目名称": "在线口语陪练AI"},
        {"投资人账号": "1002", "项目ID": 12, "投资金额": 15, "投资人姓名": "李华", "项目名称": "论文写作AI助手"},

        # 王芳投资4个项目
        {"投资人账号": "1003", "项目ID": 1, "投资金额": 25, "投资人姓名": "王芳", "项目名称": "AI智能作业批改助手"},
        {"投资人账号": "1003", "项目ID": 6, "投资金额": 20, "投资人姓名": "王芳", "项目名称": "智能错题本"},
        {"投资人账号": "1003", "项目ID": 15, "投资金额": 25, "投资人姓名": "王芳", "项目名称": "思维导图协作平台"},
        {"投资人账号": "1003", "项目ID": 18, "投资金额": 15, "投资人姓名": "王芳", "项目名称": "编程竞赛训练营"},
    ]

    for i, inv in enumerate(investments, 1):
        try:
            inv["投资时间"] = int(time.time() * 1000) - (len(investments) - i) * 3600000  # 模拟不同时间
            record_id = create_record(token, TABLES["investments"], inv)
            print(f"✅ [{i:2d}/{len(investments)}] {inv['投资人姓名']} 投资 {inv['投资金额']}万 给 {inv['项目名称'][:20]:20s}")
            time.sleep(0.3)
        except Exception as e:
            print(f"❌ [{i:2d}/{len(investments)}] 投资记录创建失败: {e}")

    # 完成
    print("\n" + "=" * 60)
    print("🎉 测试数据插入完成！")
    print("=" * 60)
    print("\n📊 数据摘要:")
    print(f"  • 项目数量: 20个")
    print(f"  • 投资人数量: 3个")
    print(f"  • 投资记录: {len(investments)}条")
    print("\n🔑 投资人登录信息:")
    print("  • 账号: 1001 密码: inv001 (张明 - 已投资75万，剩余25万)")
    print("  • 账号: 1002 密码: inv002 (李华 - 已投资80万，剩余20万)")
    print("  • 账号: 1003 密码: inv003 (王芳 - 已投资85万，剩余15万)")
    print("\n✨ 可以开始测试前端页面了！")

if __name__ == "__main__":
    main()
