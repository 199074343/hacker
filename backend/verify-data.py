#!/usr/bin/env python3
"""
验证飞书表格中的数据
"""

import requests
import json

# 飞书配置
APP_ID = "cli_a862c494a9431013"
APP_SECRET = "tUw7iGj2MKCDVXyGsqJWdh7iLuhqhPc4"
APP_TOKEN = "VZ77bJ9MvalDxqscf0Bcfh0Tnjp"

TABLES = {
    "projects": "tblGAbkdJKqTOlEw",
    "investors": "tblDp18p0G4xcADk",
    "investments": "tblh9DMhfC4dQHMJ",
    "config": "tbl43OV7SBtzFzw1"
}

def get_token():
    resp = requests.post(
        "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal",
        json={"app_id": APP_ID, "app_secret": APP_SECRET}
    )
    data = resp.json()
    return data['tenant_access_token'] if data.get('code') == 0 else None

def list_records(token, table_id):
    url = f"https://open.feishu.cn/open-apis/bitable/v1/apps/{APP_TOKEN}/tables/{table_id}/records?page_size=100"
    headers = {"Authorization": f"Bearer {token}"}
    resp = requests.get(url, headers=headers)
    data = resp.json()
    return data['data']['items'] if data.get('code') == 0 else []

print("=" * 70)
print("📊 验证飞书多维表格数据")
print("=" * 70)

token = get_token()
print(f"✅ Token: {token[:20]}...\n")

# 验证项目
print("=" * 70)
print("1️⃣  参赛作品表")
print("=" * 70)
projects = list_records(token, TABLES["projects"])
print(f"总数: {len(projects)} 条记录\n")

valid_projects = [p for p in projects if p.get('fields')]
print(f"有效记录: {len(valid_projects)} 条\n")

if valid_projects:
    print("前5个项目:")
    for i, proj in enumerate(valid_projects[:5], 1):
        fields = proj['fields']
        name = str(fields.get('项目名称', 'N/A'))[:35]
        pid = str(fields.get('项目ID', 'N/A'))
        uv = int(fields.get('累计UV', 0))
        print(f"  {i}. [{pid}] {name:<35} UV: {uv:,}")

# 验证投资人
print("\n" + "=" * 70)
print("2️⃣  投资人表")
print("=" * 70)
investors = list_records(token, TABLES["investors"])
print(f"总数: {len(investors)} 条记录\n")

valid_investors = [inv for inv in investors if inv.get('fields')]
print(f"有效记录: {len(valid_investors)} 条\n")

if valid_investors:
    print("投资人列表:")
    for i, inv in enumerate(valid_investors, 1):
        fields = inv['fields']
        name = fields.get('姓名', 'N/A')
        account = fields.get('账号', 'N/A')
        password = fields.get('初始密码', 'N/A')
        amount = fields.get('初始额度', 0)
        title = fields.get('职务', 'N/A')
        print(f"  {i}. {name:10s} [{account}/{password}] {amount}万 - {title}")

# 验证投资记录
print("\n" + "=" * 70)
print("3️⃣  投资记录表")
print("=" * 70)
investments = list_records(token, TABLES["investments"])
print(f"总数: {len(investments)} 条记录\n")

valid_investments = [inv for inv in investments if inv.get('fields') and len(inv['fields']) > 1]
print(f"有效记录: {len(valid_investments)} 条\n")

if valid_investments:
    print("投资记录:")
    for i, inv in enumerate(valid_investments, 1):
        fields = inv['fields']
        investor = fields.get('投资人姓名', 'N/A')
        project = fields.get('项目名称', 'N/A')
        amount = fields.get('投资金额', 0)
        print(f"  {i:2d}. {investor:10s} -> {project[:30]:30s} {amount}万")

# 统计投资情况
print("\n" + "=" * 70)
print("📈 投资统计")
print("=" * 70)

from collections import defaultdict
investor_totals = defaultdict(int)
project_totals = defaultdict(int)

for inv in valid_investments:
    fields = inv['fields']
    investor = fields.get('投资人账号', '')
    project_id = int(fields.get('项目ID', 0))
    amount = int(fields.get('投资金额', 0))

    investor_totals[investor] += amount
    project_totals[project_id] += amount

print("\n投资人已投金额:")
for account, total in sorted(investor_totals.items()):
    remaining = 100 - total
    print(f"  • 账号 {account}: 已投 {total}万，剩余 {remaining}万")

print("\n获投最多的项目 (Top 5):")
top_projects = sorted(project_totals.items(), key=lambda x: x[1], reverse=True)[:5]
for project_id, total in top_projects:
    # 查找项目名称
    project_name = "未知项目"
    for proj in valid_projects:
        if proj['fields'].get('项目ID') == project_id:
            project_name = proj['fields'].get('项目名称', '未知项目')
            break
    print(f"  • 项目 {project_id:2}: {project_name[:30]:30s} {total}万")

print("\n" + "=" * 70)
print("✅ 验证完成！")
print("=" * 70)
