#!/bin/bash

# 缓存清除辅助工具
# 用于测试时手动修改飞书数据后立即刷新缓存

API_BASE="http://localhost:8080/api/hackathon"

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo ""
echo -e "${BLUE}🧹 缓存清除工具${NC}"
echo ""
echo "选择操作:"
echo "1) 清除所有缓存 (推荐)"
echo "2) 清除指定投资人缓存"
echo "3) 清除指定项目缓存"
echo ""

read -p "请输入选项 (1-3): " option

case $option in
  1)
    echo ""
    echo -e "${YELLOW}正在清除所有缓存...${NC}"
    response=$(curl -s -X POST "$API_BASE/cache/clear")

    if command -v jq &> /dev/null; then
        echo "$response" | jq .
    else
        echo "$response"
    fi

    echo ""
    echo -e "${GREEN}✅ 所有缓存已清除${NC}"
    echo "提示: 下次查询将从飞书重新加载数据"
    ;;

  2)
    echo ""
    read -p "请输入投资人账号 (例如: 1001): " username

    if [ -z "$username" ]; then
        echo -e "${RED}❌ 账号不能为空${NC}"
        exit 1
    fi

    echo ""
    echo -e "${YELLOW}正在清除投资人 $username 的缓存...${NC}"
    response=$(curl -s -X POST "$API_BASE/cache/clear/investor/$username")

    if command -v jq &> /dev/null; then
        echo "$response" | jq .
    else
        echo "$response"
    fi

    echo ""
    echo -e "${GREEN}✅ 投资人 $username 的缓存已清除${NC}"
    ;;

  3)
    echo ""
    read -p "请输入项目ID (例如: 1): " projectId

    if [ -z "$projectId" ]; then
        echo -e "${RED}❌ 项目ID不能为空${NC}"
        exit 1
    fi

    echo ""
    echo -e "${YELLOW}正在清除项目 $projectId 的缓存...${NC}"
    response=$(curl -s -X POST "$API_BASE/cache/clear/project/$projectId")

    if command -v jq &> /dev/null; then
        echo "$response" | jq .
    else
        echo "$response"
    fi

    echo ""
    echo -e "${GREEN}✅ 项目 $projectId 的缓存已清除${NC}"
    ;;

  *)
    echo ""
    echo -e "${RED}❌ 无效选项${NC}"
    exit 1
    ;;
esac

echo ""
echo -e "${BLUE}💡 提示:${NC}"
echo "- 修改飞书数据后建议立即清除缓存"
echo "- 如果不清除,缓存会在5分钟后自动过期"
echo "- 比赛阶段(current_stage)不使用缓存,修改立即生效"
echo ""
