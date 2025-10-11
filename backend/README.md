# GDTech第八届骇客大赛 - 后端服务

基于 Spring Boot 3.2 + 飞书多维表格的骇客大赛官网后端服务。

## 技术栈

- **Java 17**
- **Spring Boot 3.2.0**
- **飞书开放平台 SDK 2.0.28**
- **Caffeine Cache**（本地缓存）
- **WebFlux**（HTTP客户端）

## 项目结构

```
backend/
├── src/main/java/com/gdtech/hackathon/
│   ├── HackathonApplication.java          # 主应用类
│   ├── config/                            # 配置类
│   │   ├── FeishuConfig.java             # 飞书配置
│   │   ├── WebConfig.java                # Web配置（CORS）
│   │   └── CacheConfig.java              # 缓存配置
│   ├── controller/                        # 控制器
│   │   └── HackathonController.java      # 主控制器
│   ├── service/                          # 服务层
│   │   ├── FeishuService.java           # 飞书API服务
│   │   └── HackathonService.java        # 业务服务
│   ├── model/                            # 模型类
│   │   ├── Project.java                 # 项目模型
│   │   ├── Investor.java                # 投资人模型
│   │   ├── InvestmentRecord.java        # 投资记录
│   │   ├── InvestmentHistory.java       # 投资历史
│   │   └── CompetitionStage.java        # 比赛阶段枚举
│   └── dto/                              # 数据传输对象
│       ├── ApiResponse.java             # 统一响应
│       ├── LoginRequest.java            # 登录请求
│       └── InvestmentRequest.java       # 投资请求
└── src/main/resources/
    ├── application.yml                   # 主配置文件
    └── application-dev.yml              # 开发环境配置
```

## 快速开始

### 1. 前置准备

#### 飞书开放平台配置

1. 登录 [飞书开放平台](https://open.feishu.cn/)
2. 创建企业自建应用
3. 获取凭证：
   - `App ID`
   - `App Secret`
4. 开通权限：
   - `bitable:app` - 获取多维表格元数据
   - `bitable:app:readonly` - 查看多维表格
   - `bitable:app:write` - 编辑多维表格

#### 创建飞书多维表格

创建4个数据表（参考CLAUDE.md中的表结构设计）：

1. **参赛作品表**（projects）
2. **投资人表**（investors）
3. **投资记录表**（investments）
4. **系统配置表**（config）

获取以下信息：
- **App Token**：多维表格的唯一标识
- **Table ID**：每个数据表的ID

### 2. 配置环境变量

复制 `application-dev.yml` 并填入你的配置：

```yaml
feishu:
  app-id: cli_xxxxxxxxxx          # 你的App ID
  app-secret: xxxxxxxxxxxx        # 你的App Secret
  base:
    app-token: bascnxxxxxxxxxx    # 多维表格App Token
    tables:
      projects: tblxxxxxxxxxx     # 项目表Table ID
      investors: tblxxxxxxxxxx    # 投资人表Table ID
      investments: tblxxxxxxxxxx  # 投资记录表Table ID
      config: tblxxxxxxxxxx       # 配置表Table ID
```

### 3. 编译运行

```bash
# 进入backend目录
cd backend

# 使用Maven编译
mvn clean package

# 运行（开发环境）
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 或直接运行jar
java -jar target/hackathon-1.0.0.jar --spring.profiles.active=dev
```

服务启动后访问：`http://localhost:8080/api/hackathon/health`

## API文档

### 基础路径

```
http://localhost:8080/api/hackathon
```

### 接口列表

#### 1. 获取当前比赛阶段

```http
GET /stage
```

**响应示例：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "code": "investment",
    "name": "投资期",
    "time": "11月14日 0:00 - 18:00",
    "rule": "本阶段,投资人可将虚拟投资金投给晋级的15个作品...",
    "canInvest": true
  }
}
```

#### 2. 获取所有项目列表

```http
GET /projects
```

**响应示例：**

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "name": "AI智能学习助手",
      "description": "基于大语言模型的个性化学习辅导平台",
      "url": "https://ai-tutor.example.com",
      "image": "https://xxx.com/img.png",
      "teamName": "智慧教育团队",
      "teamNumber": "001",
      "teamUrl": "https://team.example.com",
      "uv": 15420,
      "investment": 50,
      "rank": 1,
      "qualified": true,
      "weightedScore": 36168.0,
      "investmentRecords": [...]
    }
  ]
}
```

#### 3. 投资人登录

```http
POST /login
Content-Type: application/json

{
  "username": "1001",
  "password": "123abc"
}
```

**响应示例：**

```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "id": 1,
    "username": "1001",
    "name": "张投资",
    "title": "高级投资经理",
    "avatar": "https://xxx.com/avatar.png",
    "initialAmount": 100,
    "remainingAmount": 50,
    "investedAmount": 50,
    "investmentHistory": [...]
  }
}
```

#### 4. 执行投资

```http
POST /invest
Content-Type: application/json

{
  "investorUsername": "1001",
  "projectId": 1,
  "amount": 50
}
```

**响应示例：**

```json
{
  "code": 200,
  "message": "投资成功",
  "data": "投资 50 万元"
}
```

#### 5. 获取投资人信息

```http
GET /investor/{username}
```

## 核心功能说明

### 1. 飞书API集成

`FeishuService.java` 封装了飞书多维表格的操作：

- `getTenantAccessToken()` - 获取访问令牌（缓存5分钟）
- `listRecords(tableId)` - 查询表格记录（支持分页）
- `createRecord(tableId, fields)` - 新增记录

### 2. 缓存策略

使用 Caffeine 实现本地缓存：

- **feishuToken** - 飞书token缓存5分钟
- **currentStage** - 当前阶段缓存5分钟
- **projects** - 项目列表缓存5分钟

### 3. 并发控制

投资操作使用 `synchronized` + 内存Map实现简单的并发控制。

⚠️ **生产环境建议**：使用Redis分布式锁 + Redis计数器

### 4. 排名算法

根据比赛阶段动态计算：

- **海选期/锁定期**：UV排名，UV相同按队伍编号
- **投资期/结束期**：权重分 = UV*0.4 + 投资额*0.6

## 注意事项

### 1. 飞书API限频

- 默认每小时几千次调用限制
- 已通过缓存优化，实际调用频率较低
- 如遇限频，调整缓存时间或使用Redis

### 2. 数据一致性

当前方案：
- 项目基础信息从飞书读取（缓存5分钟）
- 投资记录写入飞书（实时）
- 剩余额度用内存Map管理（重启丢失）

生产环境改进：
- 使用Redis存储剩余额度
- 使用Redis分布式锁保证并发安全
- 定期同步数据到飞书

### 3. 错误处理

所有API调用都有异常捕获，失败时返回友好提示。

查看日志：
```bash
tail -f logs/spring.log
```

## 下一步开发

1. **百度统计集成**
   - 实现 `BaiduTongjiService`
   - 定时任务同步UV数据

2. **Redis集成**
   - 分布式缓存
   - 投资并发控制
   - Session管理

3. **定时任务**
   - UV数据同步
   - 比赛阶段自动切换

4. **监控告警**
   - Spring Boot Actuator
   - 飞书API调用监控

## 故障排查

### 问题1：无法获取飞书token

检查：
- App ID和App Secret是否正确
- 应用是否已发布
- 网络是否能访问 `open.feishu.cn`

### 问题2：查询表格记录失败

检查：
- App Token和Table ID是否正确
- 权限是否已开通
- 表格是否存在数据

### 问题3：投资失败

检查：
- 当前是否为投资期
- 项目是否晋级
- 剩余额度是否足够

## 联系方式

项目相关问题请联系 GDTech 开发团队。
