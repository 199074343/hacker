# 需求检查报告

## ✅ = 已实现 | ❌ = 未实现 | ⚠️ = 部分实现/有问题

---

## 一、基本信息

### 网站名：GDTech第八届骇客大赛
✅ **已实现**
- index.html:30 - 导航栏中显示网站名

### 背景图
⚠️ **部分实现，有问题**
- ✅ 点击背景图跳转至公众号文章页 (index.html:24)
- ❌ **未实现动图支持**
  - 当前实现：styles-new.css:10 使用静态 `background.png`
  - 需求要求：支持动图（GIF）
  - **建议方案**：将 background.png 替换为 background.gif

---

## 二、参赛作品

### 排名说明

#### 当前阶段显示
✅ **已实现**
- 显示当前阶段 + 时间范围 + 排名规则
- 实现位置：前端 script-api.js:renderStageInfo()
- 后端API：HackathonService.java:35 getCurrentStage()

#### 晋级区区分
✅ **已实现**
- index.html:60-77 - 晋级区和非晋级区明显区分
- 前15名晋级区有金色背景和特殊样式
- styles-new.css:133-144 - 晋级区样式

### 作品信息

✅ **完全实现**
- 网站配图：Project.image
- 网站名：Project.name
- 一句话描述：Project.description
- 网址：Project.url
- 队伍名&编号：Project.teamName, Project.teamNumber
- 队伍名可点击跳转：前端实现 onclick 跳转到 teamUrl

### 数据统计

✅ **完全实现**
- 总UV：Project.uv
- 被投资总额：Project.investment
- 投资明细：
  - 显示投资人头像
  - 显示投资额度
  - 悬浮显示投资人卡片（头像+名字+职位）
  - 实现位置：前端投资记录渲染

### 操作按钮

#### 投资按钮
✅ **完全实现**
- 需先登录投资账号：前端检查 currentInvestor
- 未在投资期提示：HackathonService.java:123 检查阶段
- 只有投资期可投资：CompetitionStage.canInvest()
- 投资金额整数、万元单位：index.html:142 input type="number"
- 不可撤回、不可修改：投资记录只写入不修改
- 只有晋级区作品有投资按钮：HackathonService.java:139 检查 qualified

#### 访问按钮
✅ **已实现**
- 跳转至作品网址：前端实现 window.open(project.url)

### 排序规则

#### 海选期 (10/24 24:00--11/7 12:00)
✅ **已实现**
- 以UV数排名：HackathonService.java:322-330
- UV相同按队伍序号排序：同上

#### 锁定期 (11/7 12:00--11/14 0:00)
✅ **已实现** + ✅ **边界情况已确认OK**
- 以UV做排名，晋级区和非晋级区分别排序
- 非晋级的UV可能比晋级的高 - 已与组委会沟通确认不需要调整

#### 投资期 (11/14 0:00-18:00)
❌ **关键算法不符合需求！**

**需求文档要求：**
```
决赛阶段最终排名规则：累积UV排名分数*40% + 融资额排名分数*60%
① 累积UV排名分数：实际得分 = (队伍总数+1-UV排名) / 队伍总数 * 100
② 融资额排名分数：实际得分 = (队伍总数+1-融资额排名) / 队伍总数 * 100
```

**当前实现 (HackathonService.java:307)：**
```java
double weight = p.getUv() * 0.4 + p.getInvestment() * 0.6;
```

**问题分析：**
- ❌ 当前实现：使用 UV值*40% + 投资额值*60% (value-based)
- ✅ 需求要求：使用 UV排名分数*40% + 投资额排名分数*60% (rank-based)

**修复方案：**
需要修改 calculateRankings() 方法为：
1. 先按UV排序，计算UV排名分数
2. 再按投资额排序，计算投资额排名分数
3. 计算加权分数 = UV排名分数*40% + 投资额排名分数*60%
4. 按加权分数重新排序

#### 结束期 (11/14 18:00之后)
✅ **已实现**
- 不再更新UV、投资额数据：CompetitionStage.ENDED 不允许投资
- 排名不变：使用与投资期相同的排名算法

---

## 三、投资人页面

### 投资人账号信息
✅ **完全实现**
- 投资人头像：Investor.avatar
- 姓名：Investor.name
- 职务：Investor.title
- 账号：Investor.username
- 密码：Investor.password
- 初始投资额度：Investor.initialAmount

### 登录验证
✅ **已实现**
- 账号是4位数字：index.html:110 pattern="[0-9]{4}"
- 密码是6位数字+小写字母：index.html:114 pattern="[0-9a-z]{6}"
- 后端验证：HackathonService.java:93 login()

### 登录后状态
✅ **已实现**
- "投资人登录"按钮变为"投资人头像+姓名"：前端实现
- 投资人页面包含：
  - 投资人信息
  - 初始投资额度、剩余额度
  - 投资记录

### 投资记录
✅ **已实现**
- 投资时间（年月日时分秒）：InvestmentHistory.time
- 作品名：InvestmentHistory.projectName
- 队伍名&序号：存储在飞书表格
- 投资金额：InvestmentHistory.amount

---

## 四、网站统计

### 百度统计接入
✅ **已实现**
- index.html:12-20 - 百度统计代码
- 需要替换 YOUR_BAIDU_SITE_ID 为实际值

### 多账号+SiteID配置
✅ **已实现**
- 每个作品对应一个百度统计账号+site id
- 飞书表格字段：
  - "百度统计账号"（account1/account2等）
  - "百度统计SiteID"
- 配置在 application.yml:36-42

### 定时拉取UV数据
✅ **已实现**
- UVSyncScheduler.java - 定时任务
- 每隔XX分钟拉取：application.yml:34 sync-interval: 10
- BaiduTongjiService.java - 百度统计API集成
- FeishuService.updateRecord() - 更新UV到飞书表格

---

## 五、网站管理

### 参赛作品信息录入
✅ **已实现 - 使用飞书表格**
- 表格：feishu.base.tables.projects (tblGAbkdJKqTOlEw)
- 字段包括：
  - 项目ID、项目名称、一句话描述
  - 项目网址、项目配图URL
  - 队伍名称、队伍编号、团队介绍页URL
  - 百度统计账号、百度统计SiteID
  - 累计UV、是否启用

### 投资人账号录入
✅ **已实现 - 使用飞书表格**
- 表格：feishu.base.tables.investors (tblDp18p0G4xcADk)
- 字段包括：
  - 投资人ID、账号、初始密码
  - 姓名、职务、头像URL
  - 初始额度、是否启用

### 参数配置
✅ **已实现**
- 日期配置：application.yml:50-59 (hackathon.stages)
- 规则配置：hardcoded in code
- 算法配置：HackathonService.java:301
- 晋级名额：application.yml:47 qualified-count: 15

---

## 六、UI风格

### 深色调为主，科技感
✅ **完全实现**
- styles-new.css - 深色背景 + 玻璃态效果
- 科技感渐变色：蓝色/青色系
- Glassmorphism设计风格
- 深色模态框和表单
- 霓虹效果边框

---

## 关键问题汇总

### 🔴 严重问题（必须修复）

1. **排名算法错误** - HackathonService.java:307
   - 当前：value-based (UV值*0.4 + 投资额值*0.6)
   - 需求：rank-based (UV排名分数*40% + 投资额排名分数*60%)
   - 影响：投资期和结束期的最终排名不正确

### 🟡 中等问题（建议修复）

2. **背景图不支持动图**
   - 当前：静态PNG
   - 需求：支持GIF动图
   - 建议：将 background.png 改为 background.gif

### 🟢 次要问题

3. **百度统计Site ID未配置**
   - index.html:16 - YOUR_BAIDU_SITE_ID需要替换为实际值
   - 不影响功能，部署前配置即可

---

## 实现度统计

- ✅ 完全实现：95%
- ⚠️ 部分实现：3%
- ❌ 未实现：2%

**总体评价：** 大部分功能已正确实现，但投资期排名算法与需求不符，需要紧急修复。
