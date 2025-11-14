#!/usr/bin/env python3
"""
阶段切换脚本 - 用于手动切换比赛阶段
使用方法：
  python3 switch_stage.py investment  # 切换到投资期
  python3 switch_stage.py ended       # 切换到结束期
"""
import requests
import sys
import time

# 配置信息
APP_ID = "cli_a862c494a9431013"
APP_SECRET = "tUw7iGj2MKCDVXyGsqJWdh7iLuhqhPc4"
APP_TOKEN = "VZ77bJ9MvalDxqscf0Bcfh0Tnjp"
CONFIG_TABLE_ID = "tbl43OV7SBtzFzw1"
API_BASE = "https://hackathon-backend.gaodun.com/api"

STAGE_INFO = {
    "lock": {"name": "锁定期", "time": "11月7日12:00 - 11月14日0:00"},
    "investment": {"name": "投资期", "time": "11月14日0:00 - 18:00"},
    "ended": {"name": "活动结束", "time": "11月14日18:00之后"}
}

def get_feishu_token():
    """获取飞书token"""
    resp = requests.post(
        "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal",
        json={"app_id": APP_ID, "app_secret": APP_SECRET}
    )
    return resp.json()["tenant_access_token"]

def update_stage_config(stage_code):
    """更新飞书配置表中的阶段"""
    print(f"🔄 正在切换到 {STAGE_INFO[stage_code]['name']} ({stage_code})...")
    
    # 1. 获取token
    token = get_feishu_token()
    
    # 2. 查找current_stage配置的record_id
    resp = requests.get(
        f"https://open.feishu.cn/open-apis/bitable/v1/apps/{APP_TOKEN}/tables/{CONFIG_TABLE_ID}/records?page_size=100",
        headers={"Authorization": f"Bearer {token}"}
    )
    data = resp.json()
    
    record_id = None
    for item in data["data"]["items"]:
        if item["fields"].get("配置项") == "current_stage":
            record_id = item["record_id"]
            break
    
    if not record_id:
        print("❌ 错误：未找到current_stage配置项")
        return False
    
    # 3. 更新配置值
    resp = requests.put(
        f"https://open.feishu.cn/open-apis/bitable/v1/apps/{APP_TOKEN}/tables/{CONFIG_TABLE_ID}/records/{record_id}",
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
        json={"fields": {"配置值": stage_code}}
    )
    
    if resp.json().get("code") != 0:
        print(f"❌ 更新飞书配置失败: {resp.json()}")
        return False
    
    print("✅ 飞书配置更新成功")
    return True

def clear_cache():
    """清除后端缓存"""
    print("🔄 清除后端缓存...")
    resp = requests.post(f"{API_BASE}/hackathon/cache/clear")
    if resp.json().get("code") == 200:
        print("✅ 缓存清除成功")
        return True
    else:
        print(f"❌ 缓存清除失败: {resp.json()}")
        return False

def verify_stage(expected_stage):
    """验证阶段切换结果"""
    print("🔄 验证阶段切换...")
    time.sleep(2)  # 等待2秒确保缓存刷新
    
    resp = requests.get(f"{API_BASE}/hackathon/stage")
    data = resp.json()
    
    if data.get("code") == 200:
        stage = data["data"]
        current_code = stage["code"]
        current_name = stage["name"]
        
        if current_code == expected_stage:
            print(f"✅ 验证成功！当前阶段: {current_name} ({current_code})")
            print(f"   时间: {stage['time']}")
            print(f"   可投资: {'是' if stage['canInvest'] else '否'}")
            return True
        else:
            print(f"❌ 验证失败！预期: {expected_stage}, 实际: {current_code}")
            return False
    else:
        print(f"❌ 验证失败: {data}")
        return False

def main():
    if len(sys.argv) != 2:
        print("使用方法:")
        print("  python3 switch_stage.py investment  # 切换到投资期")
        print("  python3 switch_stage.py ended       # 切换到结束期")
        sys.exit(1)
    
    stage_code = sys.argv[1].lower()
    
    if stage_code not in STAGE_INFO:
        print(f"❌ 错误：无效的阶段 '{stage_code}'")
        print(f"   有效值: {', '.join(STAGE_INFO.keys())}")
        sys.exit(1)
    
    print("=" * 60)
    print(f"  比赛阶段切换工具")
    print("=" * 60)
    print(f"目标阶段: {STAGE_INFO[stage_code]['name']} ({stage_code})")
    print(f"时间范围: {STAGE_INFO[stage_code]['time']}")
    print("=" * 60)
    print()
    
    # 执行切换
    if not update_stage_config(stage_code):
        sys.exit(1)
    
    if not clear_cache():
        sys.exit(1)
    
    if not verify_stage(stage_code):
        sys.exit(1)
    
    print()
    print("=" * 60)
    print("✅ 阶段切换完成！")
    print("=" * 60)
    print()
    
    # 显示下一步操作提醒
    if stage_code == "investment":
        print("📋 注意事项:")
        print("  - 投资人现在可以进行投资")
        print("  - UV数据继续同步")
        print("  - 排名已切换为加权排名（UV 40% + 投资额 60%）")
        print()
        print("⏰ 下次操作提醒:")
        print("  - 11月14日 18:00 执行: python3 switch_stage.py ended")
    elif stage_code == "ended":
        print("📋 注意事项:")
        print("  - 投资功能已关闭")
        print("  - UV数据同步已停止")
        print("  - 排名已固化，不再变化")
        print()
        print("🎉 比赛已结束！")

if __name__ == "__main__":
    main()
