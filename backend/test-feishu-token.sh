#!/bin/bash

APP_ID="cli_a862c494a9431013"
APP_SECRET="tUw7iGj2MKCDVXyGsqJWdh7iLuhqhPc4"

echo "Testing Feishu Token API..."
echo "App ID: $APP_ID"
echo ""

curl -X POST 'https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal' \
  -H 'Content-Type: application/json' \
  -d "{
    \"app_id\": \"$APP_ID\",
    \"app_secret\": \"$APP_SECRET\"
  }" | python3 -m json.tool

echo ""
