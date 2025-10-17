#!/bin/bash

# 修复投资人数据 - 处理超额投资问题
# 目标: 将所有投资人的剩余额度修正为正数

# 飞书配置
APP_ID="cli_a862c494a9431013"
APP_SECRET="tUw7iGj2MKCDVXyGsqJWdh7iLuhqhPc4"
APP_TOKEN="VZ77bJ9MvalDxqscf0Bcfh0Tnjp"

TABLE_INVESTORS="tblDp18p0G4xcADk"
TABLE_INVESTMENTS="tblh9DMhfC4dQHMJ"

echo "========================================="
echo "投资人数据修复工具"
echo "========================================="
echo ""

# 1. 获取飞书 Access Token
echo "[1/5] 获取飞书 Access Token..."
TOKEN_RESPONSE=$(curl -s -X POST "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal" \
  -H "Content-Type: application/json" \
  -d "{\"app_id\":\"$APP_ID\",\"app_secret\":\"$APP_SECRET\"}")

ACCESS_TOKEN=$(echo $TOKEN_RESPONSE | python3 -c "import sys, json; print(json.load(sys.stdin).get('tenant_access_token', ''))")

if [ -z "$ACCESS_TOKEN" ]; then
    echo "❌ 获取Token失败"
    exit 1
fi

echo "✅ Token获取成功"
echo ""

# 2. 获取所有数据
echo "[2/5] 获取投资人和投资记录数据..."

INVESTORS_RESPONSE=$(curl -s -X GET \
  "https://open.feishu.cn/open-apis/bitable/v1/apps/$APP_TOKEN/tables/$TABLE_INVESTORS/records?page_size=500" \
  -H "Authorization: Bearer $ACCESS_TOKEN")

INVESTMENTS_RESPONSE=$(curl -s -X GET \
  "https://open.feishu.cn/open-apis/bitable/v1/apps/$APP_TOKEN/tables/$TABLE_INVESTMENTS/records?page_size=500" \
  -H "Authorization: Bearer $ACCESS_TOKEN")

echo "✅ 数据获取完成"
echo ""

# 3. 分析问题
echo "[3/5] 分析投资数据..."
echo ""

# 保存数据到临时文件
echo "$INVESTORS_RESPONSE" > /tmp/investors.json
echo "$INVESTMENTS_RESPONSE" > /tmp/investments.json

python3 - <<'ANALYZE_SCRIPT'
import json
import sys

with open('/tmp/investors.json', 'r') as f:
    investors_data = json.load(f)
with open('/tmp/investments.json', 'r') as f:
    investments_data = json.load(f)

if investors_data.get('code') != 0 or investments_data.get('code') != 0:
    print('❌ 数据解析失败')
    sys.exit(1)

investors = investors_data['data']['items']
investments = investments_data['data']['items']

# 统计每个投资人的投资记录
investment_details = {}
for inv in investments:
    fields = inv.get('fields', {})
    username = fields.get('投资人账号')
    amount = fields.get('投资金额', 0)
    project_name = fields.get('项目名称', '未知项目')
    record_id = inv.get('record_id')

    try:
        amount = int(amount) if amount else 0
    except:
        amount = 0

    if username:
        if username not in investment_details:
            investment_details[username] = []
        investment_details[username].append({
            'record_id': record_id,
            'project': project_name,
            'amount': amount
        })

# 显示每个投资人的详细情况
print("📊 投资人详细情况:")
print()

for investor in investors:
    fields = investor.get('fields', {})
    username = fields.get('账号')
    name = fields.get('姓名', '未知')

    try:
        initial_amount = int(fields.get('初始额度', 0))
    except:
        initial_amount = 0

    records = investment_details.get(username, [])
    total_invested = sum(r['amount'] for r in records)
    remaining = initial_amount - total_invested

    status = "✅ 正常" if remaining >= 0 else "❌ 超额"

    print(f"{status} {name} ({username}):")
    print(f"   初始额度: {initial_amount}万")
    print(f"   已投资: {total_invested}万 ({len(records)}笔)")
    print(f"   剩余: {remaining}万")

    if remaining < 0:
        print(f"   ⚠️ 超额 {-remaining}万")
        print(f"   投资明细:")
        # 按金额降序排列
        sorted_records = sorted(records, key=lambda x: x['amount'], reverse=True)
        for i, record in enumerate(sorted_records, 1):
            print(f"      {i}. {record['project']}: {record['amount']}万 (ID: {record['record_id'][:8]}...)")
    print()

ANALYZE_SCRIPT

# 4. 提供修复方案选择
echo ""
echo "[4/5] 修复方案选择:"
echo ""
echo "请选择修复方案:"
echo ""
echo "1) 自动修复 - 增加所有超额投资人的初始额度"
echo "   (将初始额度调整为: 已投资金额,剩余额度设为0)"
echo ""
echo "2) 手动修复 - 删除指定投资人的最大金额投资记录"
echo "   (需要手动选择要删除的记录)"
echo ""
echo "3) 仅查看,不修复"
echo ""

read -p "请输入选项 (1-3): " choice

case $choice in
  1)
    echo ""
    echo "[5/5] 执行自动修复..."
    echo ""

    export ACCESS_TOKEN
    export APP_TOKEN
    export TABLE_INVESTORS

    python3 - <<'EOF'
import json
import sys
import subprocess
import os

with open('/tmp/investors.json', 'r') as f:
    investors_data = json.load(f)
with open('/tmp/investments.json', 'r') as f:
    investments_data = json.load(f)

ACCESS_TOKEN = os.environ.get('ACCESS_TOKEN', '')
APP_TOKEN = os.environ.get('APP_TOKEN', '')
TABLE_INVESTORS = os.environ.get('TABLE_INVESTORS', '')

investors = investors_data['data']['items']
investments = investments_data['data']['items']

# 统计每个投资人的总投资
investment_sum = {}
for inv in investments:
    fields = inv.get('fields', {})
    username = fields.get('投资人账号')
    amount = fields.get('投资金额', 0)

    try:
        amount = int(amount) if amount else 0
    except:
        amount = 0

    if username:
        investment_sum[username] = investment_sum.get(username, 0) + amount

# 修复超额投资人
success_count = 0
skip_count = 0

for investor in investors:
    record_id = investor.get('record_id')
    fields = investor.get('fields', {})
    username = fields.get('账号')
    name = fields.get('姓名', '未知')

    try:
        initial_amount = int(fields.get('初始额度', 0))
    except:
        initial_amount = 0

    invested = investment_sum.get(username, 0)
    remaining = initial_amount - invested

    # 只处理超额的投资人
    if remaining >= 0:
        skip_count += 1
        continue

    # 方案: 将初始额度调整为已投资金额,剩余额度设为0
    new_initial = invested
    new_remaining = 0

    print(f"🔧 修复 {name} ({username}):")
    print(f"   原初始额度: {initial_amount}万 → 新初始额度: {new_initial}万")
    print(f"   原剩余额度: {remaining}万 → 新剩余额度: {new_remaining}万")

    update_data = {
        'fields': {
            '初始额度': new_initial,
            '剩余额度': new_remaining
        }
    }

    curl_cmd = [
        'curl', '-s', '-X', 'PUT',
        f'https://open.feishu.cn/open-apis/bitable/v1/apps/{APP_TOKEN}/tables/{TABLE_INVESTORS}/records/{record_id}',
        '-H', f'Authorization: Bearer {ACCESS_TOKEN}',
        '-H', 'Content-Type: application/json',
        '-d', json.dumps(update_data)
    ]

    try:
        result = subprocess.run(curl_cmd, capture_output=True, text=True, timeout=10)
        response = json.loads(result.stdout)

        if response.get('code') == 0:
            print(f"   ✅ 修复成功")
            success_count += 1
        else:
            print(f"   ❌ 修复失败: {response.get('msg')}")
    except Exception as e:
        print(f"   ❌ 修复异常: {str(e)}")
    print()

print("========================================")
print(f"✅ 修复完成!")
print(f"   - 修复成功: {success_count}")
print(f"   - 跳过(正常): {skip_count}")
print("========================================")
EOF
    ;;

  2)
    echo ""
    echo "⚠️ 手动修复功能开发中..."
    echo "请手动登录飞书多维表格删除对应的投资记录"
    ;;

  3)
    echo ""
    echo "✅ 仅查看,不执行修复"
    ;;

  *)
    echo ""
    echo "❌ 无效选项"
    exit 1
    ;;
esac

echo ""
echo "🎉 脚本执行完成!"
echo ""
