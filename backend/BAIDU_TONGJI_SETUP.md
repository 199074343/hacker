# 百度统计集成指南（多账号版）

## 功能说明

本项目集成了百度统计API，用于自动同步项目的UV（独立访客）数据。系统会定期从百度统计API获取各项目的UV数据，并更新到飞书多维表格中，用于项目排名计算。

**⚠️ 重要：支持多个百度统计账号**
- 单个百度统计账号最多支持60个站点
- 本系统支持配置多个百度统计账号（account1, account2, ...）
- 每个项目可以指定使用哪个账号进行统计

## 核心功能

1. **自动UV同步**：定时从百度统计API获取UV数据
2. **Token自动刷新**：自动维护百度OAuth2.0 access_token
3. **飞书数据更新**：将UV数据实时更新到飞书表格
4. **定时任务调度**：可配置同步间隔（默认10分钟）

## 架构说明

### 后端组件

```
├── config/
│   └── BaiduConfig.java              # 百度统计配置类
├── service/
│   └── BaiduTongjiService.java       # 百度统计API服务
├── schedule/
│   └── UVSyncScheduler.java          # UV同步定时任务
└── model/
    └── Project.java                   # 项目模型（包含baiduSiteId和uv字段）
```

### 数据流

```
百度统计API
    ↓ (定时拉取UV数据)
BaiduTongjiService
    ↓
UVSyncScheduler
    ↓ (更新UV字段)
飞书多维表格
    ↓ (查询项目列表)
HackathonService (排名计算)
    ↓
前端展示
```

## 配置步骤

### 1. 获取百度统计API凭证

1. **开通百度统计服务**
   - 访问：https://tongji.baidu.com
   - 注册并添加网站

2. **获取Site ID**
   - 进入"管理" → "代码管理"
   - 复制统计代码中的Site ID（如：`hm.js?bc496c6424a2cb836a0512cba7f6d737`）

3. **开通API权限**
   - 进入"管理" → "数据导出服务"
   - 开通API数据导出服务（需要站点主账号权限）

4. **获取API Key (AK/SK)**
   - 访问：https://console.bce.baidu.com/iam/#/iam/accesslist
   - 创建API Key，获取：
     - `client_id`（API Key / AK）
     - `client_secret`（Secret Key / SK）

5. **获取Access Token**
   - 方法1：通过OAuth2.0授权流程获取
   - 方法2：使用百度统计账号密码登录后，从开发者工具中提取
   - 参考：https://tongji.baidu.com/api/manual/Chapter2/openapi.html

### 2. 配置后端

编辑 `backend/src/main/resources/application.yml`：

```yaml
baidu:
  tongji:
    sync-interval: 10  # UV同步间隔（分钟）
    accounts:
      account1:
        client-id: your_api_key_1       # 必填：API Key (AK)
        client-secret: your_secret_key_1 # 必填：Secret Key (SK)
        username: your_username_1        # 可选：用于OAuth授权
        password: your_password_1        # 可选：用于OAuth授权
      account2:
        client-id: your_api_key_2
        client-secret: your_secret_key_2
        username: your_username_2
        password: your_password_2
```

**环境变量方式**（推荐生产环境）：

```bash
# 账号1
export BAIDU_ACCOUNT1_CLIENT_ID=your_api_key_1
export BAIDU_ACCOUNT1_CLIENT_SECRET=your_secret_key_1
export BAIDU_ACCOUNT1_USERNAME=your_username_1
export BAIDU_ACCOUNT1_PASSWORD=your_password_1

# 账号2
export BAIDU_ACCOUNT2_CLIENT_ID=your_api_key_2
export BAIDU_ACCOUNT2_CLIENT_SECRET=your_secret_key_2
export BAIDU_ACCOUNT2_USERNAME=your_username_2
export BAIDU_ACCOUNT2_PASSWORD=your_password_2
```

**重要说明**：
- ⚠️ **只需要配置 client_id 和 client_secret，access_token 和 refresh_token 会自动生成**
- 系统启动时会自动通过Client Credentials方式获取Token
- Token过期后会自动刷新，无需手动维护
- 账号标识（account1, account2）可以自定义，但需要与飞书表格中的"百度统计账号"字段值一致
- 如果有更多账号，继续添加 account3, account4...

### 3. 配置飞书表格

在飞书多维表格的"参赛作品"表中，确保有以下字段：

| 字段名 | 类型 | 说明 |
|--------|------|------|
| 项目ID | 数字 | 项目唯一标识 |
| 项目名称 | 文本 | 项目名称 |
| **百度统计账号** | **文本** | **使用的百度统计账号标识（account1/account2）** |
| 百度统计SiteID | 文本 | 百度统计的站点ID |
| 累计UV | 数字 | UV数据（由系统自动更新） |
| 是否启用 | 复选框 | 是否参与UV同步 |

**重要**：
- "百度统计账号"字段的值必须与 application.yml 中配置的账号标识一致
- 例如：填写 `account1` 表示使用第一个百度统计账号
- 建议分配策略：前50个项目用 account1，后50个项目用 account2

### 4. 配置前端

编辑 `index.html`，替换百度统计ID：

```html
<script>
var _hmt = _hmt || [];
(function() {
  var hm = document.createElement("script");
  hm.src = "https://hm.baidu.com/hm.js?YOUR_BAIDU_SITE_ID";  // 替换为你的Site ID
  var s = document.getElementsByTagName("script")[0];
  s.parentNode.insertBefore(hm, s);
})();
</script>
```

## 使用说明

### 启动服务

```bash
cd backend
mvn spring-boot:run -Dspring.profiles.active=dev
```

### 查看日志

**服务启动时的Token初始化日志**：

```
2025-10-13 16:55:00 - 开始初始化百度统计账号的Token...
2025-10-13 16:55:00 - 初始化账号 account1 的Token...
2025-10-13 16:55:01 - 通过Client Credentials获取账号 account1 的token...
2025-10-13 16:55:02 - 账号 account1 Token获取成功，过期时间: 2592000 秒后
2025-10-13 16:55:02 - 账号 account1 Token初始化成功
2025-10-13 16:55:02 - 账号 account2 未配置，跳过Token初始化
2025-10-13 16:55:02 - 百度统计账号Token初始化完成
```

**UV同步任务日志**：

```
2025-10-13 17:00:00 - 开始同步项目UV数据...
2025-10-13 17:00:05 - 项目 AI学习助手 (ID:1, 账号:account1) UV更新成功: 1523
2025-10-13 17:00:08 - 项目 智能题库 (ID:2, 账号:account2) UV更新成功: 2341
2025-10-13 17:00:10 - UV数据同步完成: 成功 15, 失败 0
```

### 手动触发同步

UV同步任务会在以下时机执行：

1. **定时执行**：每隔N分钟自动执行（配置：`baidu.tongji.sync-interval`）
2. **启动后执行**：服务启动1分钟后首次执行

## API说明

### BaiduTongjiService

```java
// 获取站点今日UV
Integer getTodayUV(String accountName, String siteId)

// 获取站点累计UV（最近N天）
Integer getCumulativeUV(String accountName, String siteId, int days)

// 批量获取所有项目UV
Map<Long, Integer> batchGetUV(Map<Long, ProjectSiteInfo> projectSites)

// 手动刷新指定账号的Token
void refreshAccessToken(String accountName)
```

**多账号支持**：
- 所有方法都需要指定 `accountName` 参数
- 系统会自动使用对应账号的凭证进行API调用
- Token刷新是账号隔离的，互不影响

## 故障排查

### Token过期

**现象**：日志显示 `Token可能无效，尝试刷新token...`

**解决方案**：
1. 系统会自动刷新token
2. 如果自动刷新失败，需要手动更新 `access-token` 和 `refresh-token`

### 获取UV失败

**现象**：日志显示 `获取站点UV失败`

**可能原因**：
1. Site ID 配置错误
2. 网站未开通API权限
3. 百度统计API限流

**解决方案**：
1. 检查Site ID是否正确
2. 确认已开通"数据导出服务"
3. 降低同步频率（增大 `sync-interval`）

### 数据不更新

**现象**：飞书表格中的UV数据没有更新

**检查清单**：
1. 项目的"是否启用"字段是否勾选
2. "百度统计账号"字段是否填写（必须是 account1、account2 等）
3. "百度统计SiteID"字段是否填写
4. 后端配置中是否有对应账号的凭证
5. 后端服务是否正常运行
6. 查看日志中是否有错误信息

### 账号配置错误

**现象**：日志显示"项目 XXX 的百度统计账号 YYY 未配置，跳过"

**原因**：
- 飞书表格中填写的账号标识在 application.yml 中没有对应配置
- 例如：表格中填写了 `account3`，但配置文件只有 account1 和 account2

**解决方案**：
1. 检查飞书表格中的"百度统计账号"字段值
2. 确保 application.yml 中有对应的账号配置
3. 账号标识必须完全一致（区分大小写）

## 数据同步策略

### UV计算方式

- **累计UV**：取最近30天的独立访客数
- **更新频率**：默认每10分钟更新一次
- **数据延迟**：百度统计通常有1-2小时的数据延迟

### 排名计算

1. **选拔期/锁定期**：纯UV排名
2. **投资期/结束期**：权重排名
   - 权重公式：`UV * 0.4 + 投资额 * 0.6`

## 性能优化

1. **合理设置同步间隔**
   - 开发环境：10分钟
   - 生产环境：5-10分钟
   - 注意：过于频繁会触发API限流

2. **批量请求优化**
   - 系统逐个请求各项目UV
   - 如有大量项目，可考虑增加同步间隔

3. **失败重试**
   - Token失败自动重试1次
   - 单个项目失败不影响其他项目

## 参考文档

- [百度统计OpenAPI文档](https://tongji.baidu.com/api/manual/Chapter2/openapi.html)
- [OAuth2.0认证文档](https://tongji.baidu.com/api/manual/Chapter2/openapi.html#oauth2)
- [API接口文档](https://tongji.baidu.com/api/manual/Chapter2/overview.html)

## 注意事项

⚠️ **安全提示**：
- 不要将 `access-token` 和 `refresh-token` 提交到Git仓库
- 生产环境建议使用环境变量配置
- 定期更换API密钥
- 每个账号的凭证独立管理

⚠️ **API限制**：
- 百度统计API有调用频率限制
- 建议同步间隔不低于5分钟
- 超出限制会返回错误码，系统会自动跳过
- 多个账号的API调用是串行的，不会并发请求

⚠️ **数据准确性**：
- 百度统计数据通常有1-2小时延迟
- UV数据可能与实时访问有差异
- 建议在比赛结束后进行最终排名计算

⚠️ **多账号管理**：
- 单个百度统计账号最多60个站点
- 合理分配项目到不同账号（建议每个账号不超过50个）
- 账号标识必须在飞书表格和配置文件中保持一致
- 建议命名：account1, account2, account3...
