#!/usr/bin/env python3
"""
飞书多维表格 CRUD 完整测试
测试所有4个表的增删改查操作
"""

import requests
import json
import time
from datetime import datetime

# 飞书配置
APP_ID = "cli_a862c494a9431013"
APP_SECRET = "tUw7iGj2MKCDVXyGsqJWdh7iLuhqhPc4"
APP_TOKEN = "VZ77bJ9MvalDxqscf0Bcfh0Tnjp"

# 表ID
TABLES = {
    "projects": "tblGAbkdJKqTOlEw",
    "investors": "tblDp18p0G4xcADk",
    "investments": "tblh9DMhfC4dQHMJ",
    "config": "tbl43OV7SBtzFzw1"
}

class FeishuTester:
    def __init__(self):
        self.token = None
        self.test_records = {}  # 存储测试创建的记录ID，用于清理

    def get_token(self):
        """获取访问令牌"""
        print("\n" + "="*60)
        print("🔑 步骤1: 获取 Access Token")
        print("="*60)

        resp = requests.post(
            "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal",
            json={"app_id": APP_ID, "app_secret": APP_SECRET}
        )
        data = resp.json()

        if data.get('code') == 0:
            self.token = data['tenant_access_token']
            print(f"✅ Token获取成功: {self.token[:20]}...")
            return True
        else:
            print(f"❌ Token获取失败: {data}")
            return False

    def get_headers(self):
        """获取请求头"""
        return {"Authorization": f"Bearer {self.token}"}

    def test_read(self, table_name, table_id):
        """测试读取操作"""
        print(f"\n📖 读取 {table_name} 表...")

        url = f"https://open.feishu.cn/open-apis/bitable/v1/apps/{APP_TOKEN}/tables/{table_id}/records?page_size=10"
        resp = requests.get(url, headers=self.get_headers())
        data = resp.json()

        if data.get('code') == 0:
            items = data['data']['items']
            print(f"✅ 读取成功，共 {len(items)} 条记录")
            if items:
                print(f"   示例记录ID: {items[0]['record_id']}")
                fields = items[0].get('fields', {})
                if fields:
                    print(f"   字段数量: {len(fields)}")
                    for key, value in list(fields.items())[:3]:
                        print(f"     • {key}: {value}")
                else:
                    print(f"   ⚠️  记录为空（无字段值）")
            return True, items
        else:
            print(f"❌ 读取失败: {data.get('msg')}")
            return False, []

    def test_create(self, table_name, table_id, test_data):
        """测试创建操作"""
        print(f"\n➕ 创建 {table_name} 测试记录...")

        url = f"https://open.feishu.cn/open-apis/bitable/v1/apps/{APP_TOKEN}/tables/{table_id}/records"
        payload = {"fields": test_data}

        resp = requests.post(url, headers=self.get_headers(), json=payload)
        data = resp.json()

        if data.get('code') == 0:
            record_id = data['data']['record']['record_id']
            print(f"✅ 创建成功，记录ID: {record_id}")
            print(f"   字段内容: {json.dumps(test_data, ensure_ascii=False, indent=2)}")

            # 保存记录ID用于后续测试和清理
            if table_name not in self.test_records:
                self.test_records[table_name] = []
            self.test_records[table_name].append(record_id)

            return True, record_id
        else:
            print(f"❌ 创建失败: {data.get('msg')}")
            print(f"   详情: {json.dumps(data, ensure_ascii=False, indent=2)}")
            return False, None

    def test_update(self, table_name, table_id, record_id, update_data):
        """测试更新操作"""
        print(f"\n✏️  更新 {table_name} 记录 {record_id}...")

        url = f"https://open.feishu.cn/open-apis/bitable/v1/apps/{APP_TOKEN}/tables/{table_id}/records/{record_id}"
        payload = {"fields": update_data}

        resp = requests.put(url, headers=self.get_headers(), json=payload)
        data = resp.json()

        if data.get('code') == 0:
            print(f"✅ 更新成功")
            print(f"   更新内容: {json.dumps(update_data, ensure_ascii=False)}")
            return True
        else:
            print(f"❌ 更新失败: {data.get('msg')}")
            return False

    def test_delete(self, table_name, table_id, record_id):
        """测试删除操作"""
        print(f"\n🗑️  删除 {table_name} 记录 {record_id}...")

        url = f"https://open.feishu.cn/open-apis/bitable/v1/apps/{APP_TOKEN}/tables/{table_id}/records/{record_id}"

        resp = requests.delete(url, headers=self.get_headers())
        data = resp.json()

        if data.get('code') == 0:
            print(f"✅ 删除成功")
            return True
        else:
            print(f"❌ 删除失败: {data.get('msg')}")
            return False

    def test_projects_table(self):
        """测试参赛作品表"""
        print("\n" + "="*60)
        print("🎯 测试表1: 参赛作品表 (projects)")
        print("="*60)

        table_id = TABLES["projects"]

        # 1. 读取
        success, items = self.test_read("参赛作品", table_id)
        if not success:
            return False

        # 2. 创建
        test_data = {
            "项目ID": 9999,
            "项目名称": "【测试】AI学习助手",
            "一句话描述": "这是一个测试项目",
            "项目网址": "https://test.example.com",
            "项目配图URL": "https://picsum.photos/200",
            "队伍名称": "测试团队",
            "队伍编号": "999",
            "团队介绍页URL": "https://team.test.com",
            "百度统计SiteID": "12345678",
            "累计UV": 1000,
            "是否启用": True
        }
        success, record_id = self.test_create("参赛作品", table_id, test_data)
        if not success:
            return False

        time.sleep(1)  # 等待写入

        # 3. 更新
        update_data = {
            "项目名称": "【测试-已更新】AI学习助手",
            "累计UV": 2000
        }
        success = self.test_update("参赛作品", table_id, record_id, update_data)
        if not success:
            return False

        time.sleep(1)

        # 4. 验证读取更新后的数据
        print(f"\n🔍 验证更新后的数据...")
        url = f"https://open.feishu.cn/open-apis/bitable/v1/apps/{APP_TOKEN}/tables/{table_id}/records/{record_id}"
        resp = requests.get(url, headers=self.get_headers())
        data = resp.json()
        if data.get('code') == 0:
            fields = data['data']['record']['fields']
            print(f"✅ 验证成功")
            print(f"   项目名称: {fields.get('项目名称')}")
            print(f"   累计UV: {fields.get('累计UV')}")

        return True

    def test_investors_table(self):
        """测试投资人表"""
        print("\n" + "="*60)
        print("👤 测试表2: 投资人表 (investors)")
        print("="*60)

        table_id = TABLES["investors"]

        # 1. 读取
        success, items = self.test_read("投资人", table_id)
        if not success:
            return False

        # 2. 创建
        test_data = {
            "投资人ID": 9999,
            "账号": "9999",
            "初始密码": "test99",
            "姓名": "测试投资人",
            "职务": "测试经理",
            "头像URL": "https://picsum.photos/100",
            "初始额度": 100,
            "是否启用": True
        }
        success, record_id = self.test_create("投资人", table_id, test_data)
        if not success:
            return False

        time.sleep(1)

        # 3. 更新
        update_data = {
            "姓名": "测试投资人-已更新",
            "初始额度": 150
        }
        success = self.test_update("投资人", table_id, record_id, update_data)

        return success

    def test_investments_table(self):
        """测试投资记录表"""
        print("\n" + "="*60)
        print("💰 测试表3: 投资记录表 (investments)")
        print("="*60)

        table_id = TABLES["investments"]

        # 1. 读取
        success, items = self.test_read("投资记录", table_id)
        if not success:
            return False

        # 2. 创建
        test_data = {
            "投资人账号": "9999",
            "项目ID": 9999,
            "投资金额": 50,
            "投资时间": int(time.time() * 1000),  # Unix时间戳（毫秒）
            "投资人姓名": "测试投资人",
            "项目名称": "测试项目"
        }
        success, record_id = self.test_create("投资记录", table_id, test_data)
        if not success:
            return False

        time.sleep(1)

        # 3. 更新（投资记录一般不更新，但测试功能）
        update_data = {
            "投资金额": 60
        }
        success = self.test_update("投资记录", table_id, record_id, update_data)

        return success

    def test_config_table(self):
        """测试系统配置表"""
        print("\n" + "="*60)
        print("⚙️  测试表4: 系统配置表 (config)")
        print("="*60)

        table_id = TABLES["config"]

        # 1. 读取
        success, items = self.test_read("系统配置", table_id)
        if not success:
            return False

        # 2. 创建
        test_data = {
            "配置项": "test_config",
            "配置值": "test_value",
            "说明": "这是一个测试配置"
        }
        success, record_id = self.test_create("系统配置", table_id, test_data)
        if not success:
            return False

        time.sleep(1)

        # 3. 更新
        update_data = {
            "配置值": "updated_value",
            "说明": "配置已更新"
        }
        success = self.test_update("系统配置", table_id, record_id, update_data)

        return success

    def cleanup_test_records(self):
        """清理所有测试记录"""
        print("\n" + "="*60)
        print("🧹 清理测试数据")
        print("="*60)

        for table_name, record_ids in self.test_records.items():
            table_id = TABLES.get(table_name.replace("参赛作品", "projects")
                                         .replace("投资人", "investors")
                                         .replace("投资记录", "investments")
                                         .replace("系统配置", "config"))

            for record_id in record_ids:
                self.test_delete(table_name, table_id, record_id)
                time.sleep(0.5)

        print("\n✅ 清理完成")

    def run_all_tests(self):
        """运行所有测试"""
        print("\n" + "🚀" * 30)
        print("    飞书多维表格 CRUD 完整测试")
        print("🚀" * 30)

        # 获取token
        if not self.get_token():
            print("\n❌ 无法获取Token，测试终止")
            return False

        results = {}

        # 测试所有表
        try:
            results["projects"] = self.test_projects_table()
            results["investors"] = self.test_investors_table()
            results["investments"] = self.test_investments_table()
            results["config"] = self.test_config_table()

            # 清理测试数据
            print("\n⏳ 等待3秒后清理测试数据...")
            time.sleep(3)
            self.cleanup_test_records()

        except Exception as e:
            print(f"\n❌ 测试过程中出现异常: {e}")
            import traceback
            traceback.print_exc()
            return False

        # 打印测试总结
        print("\n" + "="*60)
        print("📊 测试结果汇总")
        print("="*60)

        for table, success in results.items():
            status = "✅ 通过" if success else "❌ 失败"
            print(f"{table:20s} {status}")

        all_passed = all(results.values())

        print("\n" + "="*60)
        if all_passed:
            print("🎉 所有测试通过！")
        else:
            print("⚠️  部分测试失败，请检查上述日志")
        print("="*60)

        return all_passed


if __name__ == "__main__":
    tester = FeishuTester()
    success = tester.run_all_tests()
    exit(0 if success else 1)
