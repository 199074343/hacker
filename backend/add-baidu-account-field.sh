#!/bin/bash

# 飞书配置
APP_ID="cli_a862c494a9431013"
APP_SECRET="tUw7iGj2MKCDVXyGsqJWdh7iLuhqhPc4"
APP_TOKEN="VZ77bJ9MvalDxqscf0Bcfh0Tnjp"

# 参赛作品表ID
TABLE_PROJECTS="tblGAbkdJKqTOlEw"

echo "===== 添加百度统计账号字段 ====="
echo ""

# 获取飞书 Access Token
echo "1️⃣ 获取飞书 Access Token..."
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

# 检查字段是否已存在
echo "2️⃣ 检查字段是否已存在..."
FIELDS_RESPONSE=$(curl -s -X GET \
  "https://open.feishu.cn/open-apis/bitable/v1/apps/$APP_TOKEN/tables/$TABLE_PROJECTS/fields" \
  -H "Authorization: Bearer $ACCESS_TOKEN")

FIELD_EXISTS=$(echo $FIELDS_RESPONSE | python3 -c "
import sys, json
data = json.load(sys.stdin)
if data.get('code') == 0:
    fields = data['data']['items']
    exists = any(f.get('field_name') == '百度统计账号' for f in fields)
    print('yes' if exists else 'no')
else:
    print('error')
")

if [ "$FIELD_EXISTS" = "yes" ]; then
    echo "⚠️  字段'百度统计账号'已存在，无需添加"
    exit 0
elif [ "$FIELD_EXISTS" = "error" ]; then
    echo "❌ 检查字段失败"
    exit 1
fi

echo "✅ 字段不存在，准备添加"
echo ""

# 添加字段
echo "3️⃣ 添加'百度统计账号'字段（文本类型）..."
ADD_FIELD_RESPONSE=$(curl -s -X POST \
  "https://open.feishu.cn/open-apis/bitable/v1/apps/$APP_TOKEN/tables/$TABLE_PROJECTS/fields" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "field_name": "百度统计账号",
    "type": 1
  }')

echo $ADD_FIELD_RESPONSE | python3 -c "
import sys, json
data = json.load(sys.stdin)
if data.get('code') == 0:
    field_id = data['data']['field']['field_id']
    field_name = data['data']['field']['field_name']
    field_type = data['data']['field']['type']
    print(f'✅ 字段添加成功！')
    print(f'   字段名: {field_name}')
    print(f'   字段ID: {field_id}')
    print(f'   字段类型: {field_type} (1=文本)')
else:
    print('❌ 字段添加失败')
    print(json.dumps(data, indent=2, ensure_ascii=False))
"

echo ""
echo "===== 完成 ====="
echo ""
echo "📝 下一步："
echo "   1. 在飞书多维表格中为每个项目填写'百度统计账号'字段"
echo "   2. 值为: account1 或 account2"
echo "   3. 建议前50个项目用account1，后50个用account2"
echo ""
