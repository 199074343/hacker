#!/bin/bash

APP_ID="cli_a862c494a9431013"
APP_SECRET="tUw7iGj2MKCDVXyGsqJWdh7iLuhqhPc4"
APP_TOKEN="VZ77bJ9MvalDxqscf0Bcfh0Tnjp"
TABLE_PROJECTS="tblGAbkdJKqTOlEw"

TOKEN_RESPONSE=$(curl -s -X POST "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal" \
  -H "Content-Type: application/json" \
  -d "{\"app_id\":\"$APP_ID\",\"app_secret\":\"$APP_SECRET\"}")

ACCESS_TOKEN=$(echo $TOKEN_RESPONSE | python3 -c "import sys, json; print(json.load(sys.stdin).get('tenant_access_token', ''))")

curl -s -X GET \
  "https://open.feishu.cn/open-apis/bitable/v1/apps/$APP_TOKEN/tables/$TABLE_PROJECTS/fields" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | python3 -c "
import sys, json
data = json.load(sys.stdin)
if data.get('code') == 0:
    fields = data['data']['items']
    print(f'✅ 参赛作品表字段列表（共{len(fields)}个）:')
    print()
    for field in fields:
        name = field.get('field_name', 'N/A')
        ftype = field.get('type', 'N/A')
        fid = field.get('field_id', 'N/A')
        print(f'  • {name}')
        if name == '百度统计账号':
            print(f'    ✨ [新增字段] ID: {fid}, Type: {ftype}')
else:
    print('❌ 获取失败')
    print(json.dumps(data, indent=2, ensure_ascii=False))
"
