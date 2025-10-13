# 百度统计集成指南

## 功能说明

本项目集成了百度统计API，用于自动同步项目的UV（独立访客）数据。系统会定期从百度统计API获取各项目的UV数据，并更新到飞书多维表格中，用于项目排名计算。

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

编辑 `backend/src/main/resources/application-dev.yml`（或生产环境配置）：

```yaml
baidu:
  tongji:
    client-id: your_api_key_here              # 百度API Key (AK)
    client-secret: your_secret_key_here        # 百度Secret Key (SK)
    access-token: your_access_token_here       # Access Token
    refresh-token: your_refresh_token_here     # Refresh Token（用于自动刷新）
    sync-interval: 10                          # UV同步间隔（分钟）
```

**环境变量方式**（推荐生产环境）：

```bash
export BAIDU_CLIENT_ID=your_api_key
export BAIDU_CLIENT_SECRET=your_secret_key
export BAIDU_ACCESS_TOKEN=your_access_token
export BAIDU_REFRESH_TOKEN=your_refresh_token
```

### 3. 配置飞书表格

在飞书多维表格的"参赛作品"表中，确保有以下字段：

| 字段名 | 类型 | 说明 |
|--------|------|------|
| 项目ID | 数字 | 项目唯一标识 |
| 项目名称 | 文本 | 项目名称 |
| 百度统计SiteID | 文本 | 百度统计的站点ID |
| 累计UV | 数字 | UV数据（由系统自动更新） |
| 是否启用 | 复选框 | 是否参与UV同步 |

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

UV同步任务会输出以下日志：

```
2025-10-13 17:00:00 - 开始同步项目UV数据...
2025-10-13 17:00:05 - 项目 AI学习助手 (ID:1) UV更新成功: 1523
2025-10-13 17:00:08 - 项目 智能题库 (ID:2) UV更新成功: 2341
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
Integer getTodayUV(String siteId)

// 获取站点累计UV（最近N天）
Integer getCumulativeUV(String siteId, int days)

// 批量获取所有项目UV
Map<Long, Integer> batchGetUV(Map<Long, String> siteIds)

// 手动刷新Token
void refreshAccessToken()
```

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
2. "百度统计SiteID"字段是否填写
3. 后端服务是否正常运行
4. 查看日志中是否有错误信息

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

⚠️ **API限制**：
- 百度统计API有调用频率限制
- 建议同步间隔不低于5分钟
- 超出限制会返回错误码，系统会自动跳过

⚠️ **数据准确性**：
- 百度统计数据通常有1-2小时延迟
- UV数据可能与实时访问有差异
- 建议在比赛结束后进行最终排名计算
