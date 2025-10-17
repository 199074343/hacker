#!/bin/bash

# 给飞书投资人表添加"剩余额度"字段并初始化数据
# 解决剩余额度只存在内存中导致后端重启后数据丢失的问题

# 飞书配置
APP_ID="cli_a862c494a9431013"
APP_SECRET="tUw7iGj2MKCDVXyGsqJWdh7iLuhqhPc4"
APP_TOKEN="VZ77bJ9MvalDxqscf0Bcfh0Tnjp"

TABLE_INVESTORS="tblDp18p0G4xcADk"
TABLE_INVESTMENTS="tblh9DMhfC4dQHMJ"

echo "========================================="
echo "飞书投资人表添加"剩余额度"字段"
echo "========================================="
echo ""

# 1. 获取飞书 Access Token
echo "[1/4] 获取飞书 Access Token..."
TOKEN_RESPONSE=$(curl -s -X POST "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal" \
  -H "Content-Type: application/json" \
  -d "{\"app_id\":\"$APP_ID\",\"app_secret\":\"$APP_SECRET\"}")

ACCESS_TOKEN=$(echo $TOKEN_RESPONSE | python3 -c "import sys, json; print(json.load(sys.stdin).get('tenant_access_token', ''))")

if [ -z "$ACCESS_TOKEN" ]; then
    echo "❌ 获取Token失败"
    echo $TOKEN_RESPONSE | python3 -m json.tool
    exit 1
fi

echo "✅ Token获取成功"
echo ""

# 2. 检查字段是否已存在
echo "[2/4] 检查"剩余额度"字段是否已存在..."
FIELDS_RESPONSE=$(curl -s -X GET \
  "https://open.feishu.cn/open-apis/bitable/v1/apps/$APP_TOKEN/tables/$TABLE_INVESTORS/fields" \
  -H "Authorization: Bearer $ACCESS_TOKEN")

FIELD_EXISTS=$(echo $FIELDS_RESPONSE | python3 -c "
import sys, json
data = json.load(sys.stdin)
if data.get('code') == 0:
    fields = data['data']['items']
    for field in fields:
        if field.get('field_name') == '剩余额度':
            print('yes')
            sys.exit(0)
    print('no')
else:
    print('error')
")

if [ "$FIELD_EXISTS" = "yes" ]; then
    echo "✅ "剩余额度"字段已存在，跳过创建"
    echo ""
elif [ "$FIELD_EXISTS" = "error" ]; then
    echo "❌ 查询字段失败"
    exit 1
else
    echo "⚠️  "剩余额度"字段不存在，开始创建..."

    # 3. 添加"剩余额度"字段
    ADD_FIELD_RESPONSE=$(curl -s -X POST \
      "https://open.feishu.cn/open-apis/bitable/v1/apps/$APP_TOKEN/tables/$TABLE_INVESTORS/fields" \
      -H "Authorization: Bearer $ACCESS_TOKEN" \
      -H "Content-Type: application/json" \
      -d '{
        "field_name": "剩余额度",
        "type": 2,
        "property": {
          "formatter": "0"
        }
      }')

    ADD_FIELD_CODE=$(echo $ADD_FIELD_RESPONSE | python3 -c "import sys, json; print(json.load(sys.stdin).get('code', -1))")

    if [ "$ADD_FIELD_CODE" = "0" ]; then
        echo "✅ "剩余额度"字段创建成功"
    else
        echo "❌ 创建字段失败"
        echo $ADD_FIELD_RESPONSE | python3 -m json.tool
        exit 1
    fi
    echo ""
fi

# 4. 初始化所有投资人的剩余额度
echo "[3/4] 获取所有投资人和投资记录..."

# 获取所有投资人
INVESTORS_RESPONSE=$(curl -s -X GET \
  "https://open.feishu.cn/open-apis/bitable/v1/apps/$APP_TOKEN/tables/$TABLE_INVESTORS/records?page_size=500" \
  -H "Authorization: Bearer $ACCESS_TOKEN")

# 获取所有投资记录
INVESTMENTS_RESPONSE=$(curl -s -X GET \
  "https://open.feishu.cn/open-apis/bitable/v1/apps/$APP_TOKEN/tables/$TABLE_INVESTMENTS/records?page_size=500" \
  -H "Authorization: Bearer $ACCESS_TOKEN")

echo "✅ 数据获取完成"
echo ""

# 5. 计算并更新每个投资人的剩余额度
echo "[4/4] 计算并更新每个投资人的剩余额度..."
echo ""

python3 - <<EOF
import json
import sys
import subprocess

# 解析投资人数据
investors_data = json.loads('''$INVESTORS_RESPONSE''')
investments_data = json.loads('''$INVESTMENTS_RESPONSE''')

if investors_data.get('code') != 0:
    print('❌ 投资人数据解析失败')
    sys.exit(1)

if investments_data.get('code') != 0:
    print('❌ 投资记录数据解析失败')
    sys.exit(1)

investors = investors_data['data']['items']
investments = investments_data['data']['items']

# 统计每个投资人的总投资额
investment_sum = {}
for inv in investments:
    fields = inv.get('fields', {})
    username = fields.get('投资人账号')
    amount = fields.get('投资金额', 0)

    # 转换为整数（处理字符串或None的情况）
    try:
        amount = int(amount) if amount else 0
    except (ValueError, TypeError):
        amount = 0

    if username:
        investment_sum[username] = investment_sum.get(username, 0) + amount

print(f'📊 统计结果: {len(investors)}个投资人, {len(investments)}条投资记录')
print()

# 更新每个投资人的剩余额度
success_count = 0
skip_count = 0
error_count = 0

for investor in investors:
    record_id = investor.get('record_id')
    fields = investor.get('fields', {})
    username = fields.get('账号')
    name = fields.get('姓名', '未知')
    initial_amount = fields.get('初始额度', 0)
    current_remaining = fields.get('剩余额度')  # 可能不存在或为None

    # 转换为整数（处理字符串或None的情况）
    try:
        initial_amount = int(initial_amount) if initial_amount else 0
    except (ValueError, TypeError):
        initial_amount = 0

    try:
        current_remaining = int(current_remaining) if current_remaining else None
    except (ValueError, TypeError):
        current_remaining = None

    # 计算实际剩余额度
    invested = investment_sum.get(username, 0)
    correct_remaining = initial_amount - invested

    # 如果剩余额度已经正确，跳过更新
    if current_remaining == correct_remaining:
        print(f'⏭️  {name} ({username}): 剩余额度已正确 ({correct_remaining}万), 跳过')
        skip_count += 1
        continue

    # 更新飞书记录
    update_data = {
        'fields': {
            '剩余额度': correct_remaining
        }
    }

    curl_cmd = [
        'curl', '-s', '-X', 'PUT',
        f'https://open.feishu.cn/open-apis/bitable/v1/apps/$APP_TOKEN/tables/$TABLE_INVESTORS/records/{record_id}',
        '-H', 'Authorization: Bearer $ACCESS_TOKEN',
        '-H', 'Content-Type: application/json',
        '-d', json.dumps(update_data)
    ]

    try:
        result = subprocess.run(curl_cmd, capture_output=True, text=True, timeout=10)
        response = json.loads(result.stdout)

        if response.get('code') == 0:
            print(f'✅ {name} ({username}): 初始{initial_amount}万 - 已投{invested}万 = 剩余{correct_remaining}万')
            success_count += 1
        else:
            print(f'❌ {name} ({username}): 更新失败 - {response.get("msg", "未知错误")}')
            error_count += 1
    except Exception as e:
        print(f'❌ {name} ({username}): 更新异常 - {str(e)}')
        error_count += 1

print()
print('========================================')
print(f'✅ 迁移完成!')
print(f'   - 更新成功: {success_count}')
print(f'   - 跳过(已正确): {skip_count}')
print(f'   - 失败: {error_count}')
print('========================================')
EOF

echo ""
echo "🎉 脚本执行完成!"
echo ""
echo "⚠️  下一步:"
echo "   1. 验证飞书表中"剩余额度"字段数据是否正确"
echo "   2. 修改后端代码使用飞书的"剩余额度"字段"
echo "   3. 删除代码中的内存缓存 investorRemainingAmount"
echo ""
