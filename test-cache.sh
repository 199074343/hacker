#!/bin/bash

# 缓存优化测试脚本
# 测试投资接口的缓存和缓存失效机制

API_BASE="http://localhost:8080/api/hackathon"

echo "🧪 开始测试缓存优化..."
echo ""

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# 测试投资接口响应时间
test_invest_performance() {
    local investor=$1
    local project=$2
    local amount=$3
    local test_name=$4

    echo -e "${YELLOW}测试: $test_name${NC}"

    # 使用time命令测量响应时间
    start=$(date +%s%3N)

    response=$(curl -s -X POST "$API_BASE/invest" \
        -H "Content-Type: application/json" \
        -d "{\"investorUsername\":\"$investor\",\"projectId\":$project,\"amount\":$amount}" \
        -w "\n%{time_total}")

    end=$(date +%s%3N)
    duration=$((end - start))

    echo "响应时间: ${duration}ms"
    echo "响应内容: $response"
    echo ""

    return $duration
}

# 测试1: 首次投资 (无缓存,需要查询飞书)
echo "============================================"
echo "测试1: 首次投资 (无缓存)"
echo "============================================"
test_invest_performance "1001" 1 10 "首次投资 - 查询投资人和项目"
FIRST_TIME=$?

sleep 2

# 测试2: 再次查询相同数据 (应该命中缓存)
echo "============================================"
echo "测试2: 再次投资同一投资人和项目 (应该命中缓存)"
echo "============================================"
test_invest_performance "1001" 1 5 "第二次投资 - 应该从缓存读取"
CACHED_TIME=$?

sleep 2

# 测试3: 查询不同的投资人和项目 (部分缓存未命中)
echo "============================================"
echo "测试3: 投资不同项目 (部分缓存)"
echo "============================================"
test_invest_performance "1001" 2 10 "投资人缓存命中,项目缓存未命中"
PARTIAL_CACHE_TIME=$?

echo ""
echo "============================================"
echo "📊 性能对比结果"
echo "============================================"
echo -e "首次投资(无缓存):   ${RED}${FIRST_TIME}ms${NC}"
echo -e "缓存命中后投资:     ${GREEN}${CACHED_TIME}ms${NC}"
echo -e "部分缓存命中投资:   ${YELLOW}${PARTIAL_CACHE_TIME}ms${NC}"

if [ $CACHED_TIME -lt $FIRST_TIME ]; then
    improvement=$(( (FIRST_TIME - CACHED_TIME) * 100 / FIRST_TIME ))
    echo -e "${GREEN}✅ 缓存优化成功! 性能提升 ${improvement}%${NC}"
else
    echo -e "${RED}❌ 缓存可能未生效${NC}"
fi

echo ""
echo "============================================"
echo "🧪 测试缓存失效机制"
echo "============================================"

# 等待5秒,然后再次投资,触发缓存清除
echo "等待5秒后执行投资,观察缓存清除..."
sleep 5

echo "执行投资操作,应该清除缓存..."
curl -s -X POST "$API_BASE/invest" \
    -H "Content-Type: application/json" \
    -d '{"investorUsername":"1002","projectId":3,"amount":20}' | jq .

echo ""
echo "投资完成,缓存应该已被清除"

echo ""
echo "============================================"
echo "💡 优化建议"
echo "============================================"
echo "1. 首次请求较慢是正常的,因为需要查询飞书API"
echo "2. 后续请求应该明显变快,因为数据已缓存"
echo "3. 投资操作会清除相关缓存,确保数据一致性"
echo "4. 可以在日志中查看 '从飞书加载' 和 '将缓存' 等关键字"
echo ""
