# 测试时手动修改飞书数据的缓存处理指南

## 🎯 问题背景

在测试时,你可能需要手动修改飞书多维表格中的数据,例如:
- 修改配置表的 `current_stage` 来切换比赛阶段
- 修改投资人的初始额度或其他信息
- 修改项目的名称、描述、UV等信息
- 修改晋级配置

**问题:** 由于我们添加了缓存优化,手动修改飞书数据后,缓存中的旧数据可能不会立即更新,导致测试结果不符合预期。

## ✅ 哪些数据会被缓存?

| 数据类型 | 缓存名称 | 过期时间 | 是否实时 | 说明 |
|---------|---------|---------|---------|------|
| **比赛阶段** | ❌ 无缓存 | - | ✅ 实时 | 每次都查飞书,手动修改立即生效 |
| **投资人信息** | `investor` | 5分钟 | ❌ 延迟 | 需要手动清除缓存 |
| **单个项目** | `project` | 5分钟 | ❌ 延迟 | 需要手动清除缓存 |
| **项目列表** | `projects` | 5分钟 | ❌ 延迟 | 需要手动清除缓存 |
| **晋级配置** | ❌ 无缓存 | - | ✅ 实时 | 每次都查飞书,手动修改立即生效 |

## 🔧 解决方案: 手动清除缓存API

我们提供了3个缓存清除接口,方便测试时使用:

### 1. 清除所有缓存 (推荐)

**使用场景:** 修改多个数据或不确定修改了哪些数据时

```bash
curl -X POST http://localhost:8080/api/hackathon/cache/clear
```

**响应:**
```json
{
  "code": 200,
  "message": "success",
  "data": "缓存已清除,数据将从飞书重新加载"
}
```

**效果:** 清除所有缓存 (investor, project, projects, feishuToken等)

### 2. 清除指定投资人缓存

**使用场景:** 修改了某个投资人的信息后

```bash
curl -X POST http://localhost:8080/api/hackathon/cache/clear/investor/1001
```

**响应:**
```json
{
  "code": 200,
  "message": "success",
  "data": "投资人 1001 的缓存已清除"
}
```

### 3. 清除指定项目缓存

**使用场景:** 修改了某个项目的信息后

```bash
curl -X POST http://localhost:8080/api/hackathon/cache/clear/project/1
```

**响应:**
```json
{
  "code": 200,
  "message": "success",
  "data": "项目 1 的缓存已清除"
}
```

## 📋 常见测试场景处理

### 场景1: 手动切换比赛阶段

**操作步骤:**
1. 打开飞书多维表格 → 系统配置表
2. 修改 `current_stage` 配置项的值:
   - `selection` - 海选期
   - `lock` - 锁定期
   - `investment` - 投资期
   - `ended` - 结束期
3. ✅ **无需清除缓存** - 阶段查询不使用缓存,修改立即生效

**验证:**
```bash
curl http://localhost:8080/api/hackathon/stage | jq .
```

### 场景2: 修改投资人初始额度

**操作步骤:**
1. 打开飞书多维表格 → 投资人表
2. 修改某个投资人的 `初始额度` 字段
3. ⚠️ **需要清除缓存**:
```bash
# 方式1: 清除指定投资人
curl -X POST http://localhost:8080/api/hackathon/cache/clear/investor/1001

# 方式2: 清除所有缓存
curl -X POST http://localhost:8080/api/hackathon/cache/clear
```

**验证:**
```bash
curl http://localhost:8080/api/hackathon/investor/1001 | jq .
```

### 场景3: 修改项目UV数据

**操作步骤:**
1. 打开飞书多维表格 → 参赛作品表
2. 修改某个项目的 `累计UV` 字段
3. ⚠️ **需要清除缓存**:
```bash
# 方式1: 清除指定项目
curl -X POST http://localhost:8080/api/hackathon/cache/clear/project/1

# 方式2: 清除所有缓存 (推荐,因为项目列表也需要更新)
curl -X POST http://localhost:8080/api/hackathon/cache/clear
```

**验证:**
```bash
curl http://localhost:8080/api/hackathon/projects | jq '.data[] | select(.id==1)'
```

### 场景4: 修改晋级名单

**操作步骤:**
1. 打开飞书多维表格 → 系统配置表
2. 修改 `qualified_project_ids` 配置项的值 (逗号分隔的项目ID)
   例如: `1,2,3,4,5,6,7,8,9,10,11,12,13,14,15`
3. ⚠️ **需要清除缓存**:
```bash
curl -X POST http://localhost:8080/api/hackathon/cache/clear
```

**验证:**
```bash
# 查看项目列表,检查qualified字段
curl http://localhost:8080/api/hackathon/projects | jq '.data[] | {id, name, qualified}'
```

### 场景5: 完整的测试流程

**示例: 测试投资功能**

```bash
# 1. 切换到投资期
# 手动修改飞书: current_stage = "investment"

# 2. 验证阶段已切换 (无需清缓存)
curl http://localhost:8080/api/hackathon/stage | jq '.data.code'
# 输出: "investment"

# 3. 修改投资人1001的初始额度为200万
# 手动修改飞书: 初始额度 = 200

# 4. 清除投资人缓存
curl -X POST http://localhost:8080/api/hackathon/cache/clear/investor/1001

# 5. 验证投资人额度已更新
curl http://localhost:8080/api/hackathon/investor/1001 | jq '.data.initialAmount'
# 输出: 200

# 6. 执行投资
curl -X POST http://localhost:8080/api/hackathon/invest \
  -H "Content-Type: application/json" \
  -d '{"investorUsername":"1001","projectId":1,"amount":50}'

# 7. 再次查看投资人信息 (投资后自动清除缓存)
curl http://localhost:8080/api/hackathon/investor/1001 | jq '.data.remainingAmount'
# 输出: 150
```

## ⚙️ 自动缓存失效机制

即使不手动清除缓存,以下操作也会自动清除相关缓存:

| 操作 | 自动清除的缓存 |
|------|--------------|
| **投资操作** | ✅ 投资人缓存 + 项目缓存 + 项目列表缓存 |
| **UV同步** (每10分钟) | ✅ 项目缓存 + 项目列表缓存 |
| **自动过期** | ✅ 所有缓存5分钟后自动失效 |

**这意味着:**
- 手动修改数据后,最多等待5分钟缓存就会自动失效
- 但为了测试效率,建议手动清除缓存立即生效

## 🧪 测试脚本

创建一个测试辅助脚本 `clear-cache.sh`:

```bash
#!/bin/bash

API_BASE="http://localhost:8080/api/hackathon"

echo "🧹 清除缓存工具"
echo ""
echo "选择操作:"
echo "1) 清除所有缓存"
echo "2) 清除指定投资人缓存"
echo "3) 清除指定项目缓存"
echo ""

read -p "请输入选项 (1-3): " option

case $option in
  1)
    echo "正在清除所有缓存..."
    curl -X POST "$API_BASE/cache/clear" | jq .
    ;;
  2)
    read -p "请输入投资人账号: " username
    echo "正在清除投资人 $username 的缓存..."
    curl -X POST "$API_BASE/cache/clear/investor/$username" | jq .
    ;;
  3)
    read -p "请输入项目ID: " projectId
    echo "正在清除项目 $projectId 的缓存..."
    curl -X POST "$API_BASE/cache/clear/project/$projectId" | jq .
    ;;
  *)
    echo "无效选项"
    exit 1
    ;;
esac

echo ""
echo "✅ 完成"
```

使用:
```bash
chmod +x clear-cache.sh
./clear-cache.sh
```

## 📝 最佳实践

### ✅ 推荐做法

1. **测试前清缓存**
   ```bash
   curl -X POST http://localhost:8080/api/hackathon/cache/clear
   ```

2. **修改数据后立即清缓存**
   - 修改投资人 → 清除投资人缓存
   - 修改项目 → 清除项目缓存
   - 修改多个数据 → 清除所有缓存

3. **使用脚本批量操作**
   ```bash
   # 修改数据 + 清缓存一气呵成
   echo "修改飞书数据..." && sleep 2 && \
   curl -X POST http://localhost:8080/api/hackathon/cache/clear
   ```

### ❌ 避免的做法

1. **忘记清缓存** - 会导致测试看到旧数据
2. **频繁清缓存** - 失去缓存优化的意义
3. **在生产环境手动清缓存** - 应该让自动失效机制处理

## 🔍 故障排查

### 问题1: 修改数据后仍然是旧数据

**检查清单:**
- ✅ 是否调用了清除缓存API?
- ✅ 清除缓存API是否返回成功?
- ✅ 后端日志是否显示 "清除缓存: xxx"?
- ✅ 是否刷新了前端页面?

**解决:**
```bash
# 强制清除所有缓存
curl -X POST http://localhost:8080/api/hackathon/cache/clear

# 查看后端日志
tail -f backend/logs/spring.log | grep "清除缓存"
```

### 问题2: 比赛阶段没有立即切换

**检查:**
- ✅ 比赛阶段不使用缓存,应该立即生效
- ✅ 检查飞书配置表的 `current_stage` 是否正确修改
- ✅ 检查配置值是否有空格或特殊字符

**验证:**
```bash
curl http://localhost:8080/api/hackathon/stage | jq '.data.code'
```

### 问题3: 清除缓存后API仍然很慢

**原因:** 清除缓存后,下次查询需要重新从飞书加载,会比较慢 (~900ms)

**这是正常的!** 缓存清除后:
- 首次查询: ~900ms (查飞书 + 写入缓存)
- 后续查询: ~300ms (从缓存读取)

## 📈 监控缓存状态

查看后端日志确认缓存行为:

```bash
# 查看缓存相关日志
tail -f backend/logs/spring.log | grep -E "缓存|从飞书加载"
```

**预期日志:**
```
INFO - 清除缓存: investor
INFO - 清除缓存: project
INFO - 清除缓存: projects
DEBUG - 从飞书加载投资人: 1001, 将缓存5分钟
DEBUG - 从飞书加载项目: 1, 将缓存5分钟
```

## 🎉 总结

- ✅ **比赛阶段** - 不缓存,手动修改立即生效
- ⚠️ **投资人/项目** - 有缓存,需要手动清除或等待5分钟
- 🔧 **清除缓存API** - 测试必备工具,修改数据后记得调用
- ⏰ **自动过期** - 5分钟兜底,无需过度担心缓存一致性
