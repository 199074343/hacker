#!/bin/bash

# 飞书配置
APP_ID="cli_a862c494a9431013"
APP_SECRET="tUw7iGj2MKCDVXyGsqJWdh7iLuhqhPc4"
APP_TOKEN="VZ77bJ9MvalDxqscf0Bcfh0Tnjp"

# 表ID
TABLE_PROJECTS="tblGAbkdJKqTOlEw"
TABLE_INVESTORS="tblDp18p0G4xcADk"
TABLE_INVESTMENTS="tblh9DMhfC4dQHMJ"
TABLE_CONFIG="tbl43OV7SBtzFzw1"

echo "===== 获取飞书 Access Token ====="
TOKEN_RESPONSE=$(curl -s -X POST "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal" \
  -H "Content-Type: application/json" \
  -d "{\"app_id\":\"$APP_ID\",\"app_secret\":\"$APP_SECRET\"}")

ACCESS_TOKEN=$(echo $TOKEN_RESPONSE | python3 -c "import sys, json; print(json.load(sys.stdin).get('tenant_access_token', ''))")

if [ -z "$ACCESS_TOKEN" ]; then
    echo "❌ 获取Token失败"
    echo $TOKEN_RESPONSE | python3 -m json.tool
    exit 1
fi

echo "✅ Token获取成功: ${ACCESS_TOKEN:0:20}..."
echo ""

# 检查参赛作品表结构
echo "===== 1. 参赛作品表 (projects) ====="
echo "Table ID: $TABLE_PROJECTS"
echo ""

# 获取表的字段信息
FIELDS_RESPONSE=$(curl -s -X GET \
  "https://open.feishu.cn/open-apis/bitable/v1/apps/$APP_TOKEN/tables/$TABLE_PROJECTS/fields" \
  -H "Authorization: Bearer $ACCESS_TOKEN")

echo "📋 表字段列表："
echo $FIELDS_RESPONSE | python3 -c "
import sys, json
data = json.load(sys.stdin)
if data.get('code') == 0:
    fields = data['data']['items']
    print(f'字段数量: {len(fields)}')
    print()
    for field in fields:
        field_name = field.get('field_name', 'N/A')
        field_type = field.get('type', 'N/A')
        field_id = field.get('field_id', 'N/A')
        print(f'  • {field_name:20s} [{field_type:15s}] (ID: {field_id})')
else:
    print('❌ 获取字段失败')
    print(json.dumps(data, indent=2, ensure_ascii=False))
"
echo ""

# 获取实际数据看看
echo "📊 实际数据示例 (前1条)："
RECORDS_RESPONSE=$(curl -s -X GET \
  "https://open.feishu.cn/open-apis/bitable/v1/apps/$APP_TOKEN/tables/$TABLE_PROJECTS/records?page_size=1" \
  -H "Authorization: Bearer $ACCESS_TOKEN")

echo $RECORDS_RESPONSE | python3 -c "
import sys, json
data = json.load(sys.stdin)
if data.get('code') == 0 and data['data']['items']:
    record = data['data']['items'][0]
    fields = record.get('fields', {})
    print(f'记录ID: {record.get(\"record_id\", \"N/A\")}')
    print('字段值:')
    for key, value in fields.items():
        value_str = str(value)[:50] if value else 'null'
        print(f'  • {key:20s} = {value_str}')
else:
    print('❌ 无数据或获取失败')
    print(json.dumps(data, indent=2, ensure_ascii=False))
"
echo ""
echo "========================================"
echo ""

# 检查投资人表
echo "===== 2. 投资人表 (investors) ====="
echo "Table ID: $TABLE_INVESTORS"
FIELDS_RESPONSE=$(curl -s -X GET \
  "https://open.feishu.cn/open-apis/bitable/v1/apps/$APP_TOKEN/tables/$TABLE_INVESTORS/fields" \
  -H "Authorization: Bearer $ACCESS_TOKEN")

echo "📋 表字段列表:"
echo $FIELDS_RESPONSE | python3 -c "
import sys, json
data = json.load(sys.stdin)
if data.get('code') == 0:
    fields = data['data']['items']
    print(f'字段数量: {len(fields)}')
    print()
    for field in fields:
        field_name = field.get('field_name', 'N/A')
        field_type = field.get('type', 'N/A')
        print(f'  • {field_name:20s} [{field_type}]')
else:
    print('❌ 获取字段失败')
"
echo ""
echo "========================================"
echo ""

# 检查投资记录表
echo "===== 3. 投资记录表 (investments) ====="
echo "Table ID: $TABLE_INVESTMENTS"
FIELDS_RESPONSE=$(curl -s -X GET \
  "https://open.feishu.cn/open-apis/bitable/v1/apps/$APP_TOKEN/tables/$TABLE_INVESTMENTS/fields" \
  -H "Authorization: Bearer $ACCESS_TOKEN")

echo "📋 表字段列表:"
echo $FIELDS_RESPONSE | python3 -c "
import sys, json
data = json.load(sys.stdin)
if data.get('code') == 0:
    fields = data['data']['items']
    print(f'字段数量: {len(fields)}')
    print()
    for field in fields:
        field_name = field.get('field_name', 'N/A')
        field_type = field.get('type', 'N/A')
        print(f'  • {field_name:20s} [{field_type}]')
else:
    print('❌ 获取字段失败')
"
echo ""
echo "========================================"
echo ""

# 检查配置表
echo "===== 4. 系统配置表 (config) ====="
echo "Table ID: $TABLE_CONFIG"
FIELDS_RESPONSE=$(curl -s -X GET \
  "https://open.feishu.cn/open-apis/bitable/v1/apps/$APP_TOKEN/tables/$TABLE_CONFIG/fields" \
  -H "Authorization: Bearer $ACCESS_TOKEN")

echo "📋 表字段列表:"
echo $FIELDS_RESPONSE | python3 -c "
import sys, json
data = json.load(sys.stdin)
if data.get('code') == 0:
    fields = data['data']['items']
    print(f'字段数量: {len(fields)}')
    print()
    for field in fields:
        field_name = field.get('field_name', 'N/A')
        field_type = field.get('type', 'N/A')
        print(f'  • {field_name:20s} [{field_type}]')
else:
    print('❌ 获取字段失败')
"

# 查看配置数据
echo ""
echo "📊 配置数据:"
RECORDS_RESPONSE=$(curl -s -X GET \
  "https://open.feishu.cn/open-apis/bitable/v1/apps/$APP_TOKEN/tables/$TABLE_CONFIG/records" \
  -H "Authorization: Bearer $ACCESS_TOKEN")

echo $RECORDS_RESPONSE | python3 -c "
import sys, json
data = json.load(sys.stdin)
if data.get('code') == 0:
    items = data['data']['items']
    for item in items:
        fields = item.get('fields', {})
        config_key = fields.get('配置项', 'N/A')
        config_value = fields.get('配置值', 'N/A')
        print(f'  • {config_key} = {config_value}')
else:
    print('❌ 获取数据失败')
"

echo ""
echo "✅ 检查完成！"
