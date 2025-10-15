# 火山引擎大模型 API 接入教程

## 目录
- [1. 概述](#1-概述)
- [2. API 认证配置](#2-api-认证配置)
- [3. 文本推理模型接入](#3-文本推理模型接入)
- [4. 视觉理解模型接入](#4-视觉理解模型接入)
- [5. 图片生成模型接入](#5-图片生成模型接入)
- [6. 错误处理](#6-错误处理)
- [7. 最佳实践](#7-最佳实践)

---

## 1. 概述

本文档提供火山引擎（字节跳动）豆包大模型系列的完整接入指南，包括文本推理、视觉理解和图片生成三种模型类型。

### 1.1 可用模型列表

| 模型类型 | 模型名称 | 接入点ID | 功能描述 |
|---------|---------|---------|---------|
| 文本推理 | doubao-seed-1-6-250615 | ep-20251015101857-wc8xz | 支持思维链推理的文本对话模型 |
| 视觉理解 | doubao-seed-1-6-vision-250815 | ep-20251015102018-vn2mf | 支持图片识别和理解的多模态模型 |
| 图片生成 | doubao-seedream-4-0-250828 | ep-20251015102102-x2n2t | 支持4K超高清的图片生成模型 |

### 1.2 API Base URL

```
https://ark.cn-beijing.volces.com/api/v3
```

---

## 2. API 认证配置

### 2.1 API Key

```
API_KEY: bd747896-e89b-46f4-a5ab-0a232d086845
```

### 2.2 认证方式

所有 API 请求需要在 HTTP Header 中包含以下认证信息：

```
Authorization: Bearer bd747896-e89b-46f4-a5ab-0a232d086845
Content-Type: application/json
```

---

## 3. 文本推理模型接入

### 3.1 模型信息

- **模型名称**: doubao-seed-1-6-250615
- **接入点ID**: `ep-20251015101857-wc8xz`
- **API端点**: `/chat/completions`
- **特性**: 支持思维链推理（reasoning_content）

### 3.2 cURL 调用示例

```bash
curl -X POST "https://ark.cn-beijing.volces.com/api/v3/chat/completions" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer bd747896-e89b-46f4-a5ab-0a232d086845" \
  -d '{
    "model": "ep-20251015101857-wc8xz",
    "messages": [
      {
        "role": "system",
        "content": "你是一个有帮助的AI助手"
      },
      {
        "role": "user",
        "content": "你好，请用一句话介绍你自己"
      }
    ]
  }'
```

### 3.3 Python 调用示例

```python
import requests
import json

API_KEY = "bd747896-e89b-46f4-a5ab-0a232d086845"
ENDPOINT_ID = "ep-20251015101857-wc8xz"
API_URL = "https://ark.cn-beijing.volces.com/api/v3/chat/completions"

headers = {
    "Content-Type": "application/json",
    "Authorization": f"Bearer {API_KEY}"
}

data = {
    "model": ENDPOINT_ID,
    "messages": [
        {
            "role": "system",
            "content": "你是一个有帮助的AI助手"
        },
        {
            "role": "user",
            "content": "请解释一下机器学习的基本原理"
        }
    ]
}

response = requests.post(API_URL, headers=headers, json=data)
result = response.json()

# 提取回复内容
if response.status_code == 200:
    message = result['choices'][0]['message']['content']
    reasoning = result['choices'][0]['message'].get('reasoning_content', '')

    print("AI回复:", message)
    print("\n推理过程:", reasoning)
    print("\nToken使用情况:", result['usage'])
else:
    print("错误:", result)
```

### 3.4 JavaScript (Node.js) 调用示例

```javascript
const axios = require('axios');

const API_KEY = 'bd747896-e89b-46f4-a5ab-0a232d086845';
const ENDPOINT_ID = 'ep-20251015101857-wc8xz';
const API_URL = 'https://ark.cn-beijing.volces.com/api/v3/chat/completions';

async function chatWithDoubao(userMessage) {
    try {
        const response = await axios.post(API_URL, {
            model: ENDPOINT_ID,
            messages: [
                {
                    role: 'system',
                    content: '你是一个有帮助的AI助手'
                },
                {
                    role: 'user',
                    content: userMessage
                }
            ]
        }, {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${API_KEY}`
            }
        });

        const message = response.data.choices[0].message.content;
        const reasoning = response.data.choices[0].message.reasoning_content;

        console.log('AI回复:', message);
        console.log('推理过程:', reasoning);
        console.log('Token使用:', response.data.usage);

        return message;
    } catch (error) {
        console.error('调用失败:', error.response?.data || error.message);
    }
}

// 调用示例
chatWithDoubao('请介绍一下人工智能的发展历史');
```

### 3.5 响应格式

```json
{
  "choices": [
    {
      "finish_reason": "stop",
      "index": 0,
      "message": {
        "content": "我是致力于为你提供有帮助的信息和支持的AI助手。",
        "reasoning_content": "用户让我用一句话介绍自己...",
        "role": "assistant"
      }
    }
  ],
  "created": 1760505442,
  "id": "021760505437822710...",
  "model": "doubao-seed-1-6-250615",
  "usage": {
    "completion_tokens": 170,
    "prompt_tokens": 104,
    "total_tokens": 274,
    "completion_tokens_details": {
      "reasoning_tokens": 154
    }
  }
}
```

---

## 4. 视觉理解模型接入

### 4.1 模型信息

- **模型名称**: doubao-seed-1-6-vision-250815
- **接入点ID**: `ep-20251015102018-vn2mf`
- **API端点**: `/chat/completions`
- **特性**: 支持图片识别、多模态输入（文本+图片）

### 4.2 cURL 调用示例

```bash
curl -X POST "https://ark.cn-beijing.volces.com/api/v3/chat/completions" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer bd747896-e89b-46f4-a5ab-0a232d086845" \
  -d '{
    "model": "ep-20251015102018-vn2mf",
    "messages": [
      {
        "role": "user",
        "content": [
          {
            "type": "text",
            "text": "请详细描述这张图片的内容"
          },
          {
            "type": "image_url",
            "image_url": {
              "url": "https://example.com/your-image.jpg"
            }
          }
        ]
      }
    ]
  }'
```

### 4.3 Python 调用示例

```python
import requests
import base64

API_KEY = "bd747896-e89b-46f4-a5ab-0a232d086845"
ENDPOINT_ID = "ep-20251015102018-vn2mf"
API_URL = "https://ark.cn-beijing.volces.com/api/v3/chat/completions"

headers = {
    "Content-Type": "application/json",
    "Authorization": f"Bearer {API_KEY}"
}

# 方式1: 使用图片URL
def analyze_image_from_url(image_url, prompt):
    data = {
        "model": ENDPOINT_ID,
        "messages": [
            {
                "role": "user",
                "content": [
                    {
                        "type": "text",
                        "text": prompt
                    },
                    {
                        "type": "image_url",
                        "image_url": {
                            "url": image_url
                        }
                    }
                ]
            }
        ]
    }

    response = requests.post(API_URL, headers=headers, json=data)
    result = response.json()

    if response.status_code == 200:
        return result['choices'][0]['message']['content']
    else:
        return f"错误: {result}"

# 方式2: 使用本地图片（Base64编码）
def analyze_local_image(image_path, prompt):
    # 读取并编码图片
    with open(image_path, 'rb') as image_file:
        image_data = base64.b64encode(image_file.read()).decode('utf-8')

    # 根据图片扩展名确定MIME类型
    if image_path.lower().endswith('.png'):
        mime_type = 'image/png'
    elif image_path.lower().endswith('.jpg') or image_path.lower().endswith('.jpeg'):
        mime_type = 'image/jpeg'
    else:
        mime_type = 'image/jpeg'

    data = {
        "model": ENDPOINT_ID,
        "messages": [
            {
                "role": "user",
                "content": [
                    {
                        "type": "text",
                        "text": prompt
                    },
                    {
                        "type": "image_url",
                        "image_url": {
                            "url": f"data:{mime_type};base64,{image_data}"
                        }
                    }
                ]
            }
        ]
    }

    response = requests.post(API_URL, headers=headers, json=data)
    result = response.json()

    if response.status_code == 200:
        return result['choices'][0]['message']['content']
    else:
        return f"错误: {result}"

# 使用示例
# 在线图片
description = analyze_image_from_url(
    "https://example.com/image.jpg",
    "请描述这张图片中的主要元素"
)
print(description)

# 本地图片
# description = analyze_local_image(
#     "/path/to/your/image.jpg",
#     "请分析这张图片的构图和色彩"
# )
# print(description)
```

### 4.4 JavaScript 调用示例

```javascript
const axios = require('axios');
const fs = require('fs');

const API_KEY = 'bd747896-e89b-46f4-a5ab-0a232d086845';
const ENDPOINT_ID = 'ep-20251015102018-vn2mf';
const API_URL = 'https://ark.cn-beijing.volces.com/api/v3/chat/completions';

// 使用在线图片URL
async function analyzeImageFromURL(imageUrl, prompt) {
    try {
        const response = await axios.post(API_URL, {
            model: ENDPOINT_ID,
            messages: [
                {
                    role: 'user',
                    content: [
                        {
                            type: 'text',
                            text: prompt
                        },
                        {
                            type: 'image_url',
                            image_url: {
                                url: imageUrl
                            }
                        }
                    ]
                }
            ]
        }, {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${API_KEY}`
            }
        });

        return response.data.choices[0].message.content;
    } catch (error) {
        console.error('调用失败:', error.response?.data || error.message);
    }
}

// 使用本地图片（Base64）
async function analyzeLocalImage(imagePath, prompt) {
    try {
        // 读取图片并转换为Base64
        const imageBuffer = fs.readFileSync(imagePath);
        const base64Image = imageBuffer.toString('base64');
        const mimeType = imagePath.endsWith('.png') ? 'image/png' : 'image/jpeg';

        const response = await axios.post(API_URL, {
            model: ENDPOINT_ID,
            messages: [
                {
                    role: 'user',
                    content: [
                        {
                            type: 'text',
                            text: prompt
                        },
                        {
                            type: 'image_url',
                            image_url: {
                                url: `data:${mimeType};base64,${base64Image}`
                            }
                        }
                    ]
                }
            ]
        }, {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${API_KEY}`
            }
        });

        return response.data.choices[0].message.content;
    } catch (error) {
        console.error('调用失败:', error.response?.data || error.message);
    }
}

// 使用示例
analyzeImageFromURL(
    'https://example.com/image.jpg',
    '请描述这张图片的内容'
).then(result => {
    console.log('分析结果:', result);
});
```

### 4.5 支持的图片格式

- **URL格式**: HTTPS链接（需公网可访问）
- **Base64格式**: `data:image/jpeg;base64,...` 或 `data:image/png;base64,...`
- **文件格式**: JPEG, PNG
- **图片大小**: 建议不超过 20MB

---

## 5. 图片生成模型接入

### 5.1 模型信息

- **模型名称**: doubao-seedream-4-0-250828
- **接入点ID**: `ep-20251015102102-x2n2t`
- **API端点**: `/images/generations` (注意：与对话模型不同)
- **特性**: 支持4K超高清输出、秒级成图

### 5.2 cURL 调用示例

```bash
curl -X POST "https://ark.cn-beijing.volces.com/api/v3/images/generations" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer bd747896-e89b-46f4-a5ab-0a232d086845" \
  -d '{
    "model": "ep-20251015102102-x2n2t",
    "prompt": "一只可爱的小猫在花园里玩耍，阳光明媚，高清摄影",
    "n": 1,
    "size": "1024x1024"
  }'
```

### 5.3 Python 调用示例

```python
import requests
import json
from datetime import datetime

API_KEY = "bd747896-e89b-46f4-a5ab-0a232d086845"
ENDPOINT_ID = "ep-20251015102102-x2n2t"
API_URL = "https://ark.cn-beijing.volces.com/api/v3/images/generations"

headers = {
    "Content-Type": "application/json",
    "Authorization": f"Bearer {API_KEY}"
}

def generate_image(prompt, size="1024x1024", num_images=1):
    """
    生成图片

    参数:
        prompt: 图片描述提示词
        size: 图片尺寸，支持: "512x512", "1024x1024", "2048x2048"
        num_images: 生成图片数量，最多9张
    """
    data = {
        "model": ENDPOINT_ID,
        "prompt": prompt,
        "n": num_images,
        "size": size
    }

    response = requests.post(API_URL, headers=headers, json=data)
    result = response.json()

    if response.status_code == 200:
        images = result['data']
        print(f"成功生成 {len(images)} 张图片")

        for i, img in enumerate(images):
            print(f"\n图片 {i+1}:")
            print(f"  URL: {img['url']}")
            print(f"  尺寸: {img['size']}")

        print(f"\nToken使用: {result['usage']}")
        return images
    else:
        print(f"生成失败: {result}")
        return None

def download_image(image_url, save_path=None):
    """
    下载生成的图片
    """
    if save_path is None:
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        save_path = f"generated_image_{timestamp}.jpg"

    response = requests.get(image_url)
    if response.status_code == 200:
        with open(save_path, 'wb') as f:
            f.write(response.content)
        print(f"图片已保存到: {save_path}")
        return save_path
    else:
        print("下载失败")
        return None

# 使用示例
if __name__ == "__main__":
    # 生成图片
    images = generate_image(
        prompt="一个未来科技感的城市，夜景，霓虹灯，高清",
        size="1024x1024",
        num_images=1
    )

    # 下载第一张图片
    if images:
        download_image(images[0]['url'])
```

### 5.4 JavaScript 调用示例

```javascript
const axios = require('axios');
const fs = require('fs');
const path = require('path');

const API_KEY = 'bd747896-e89b-46f4-a5ab-0a232d086845';
const ENDPOINT_ID = 'ep-20251015102102-x2n2t';
const API_URL = 'https://ark.cn-beijing.volces.com/api/v3/images/generations';

/**
 * 生成图片
 */
async function generateImage(prompt, size = '1024x1024', numImages = 1) {
    try {
        const response = await axios.post(API_URL, {
            model: ENDPOINT_ID,
            prompt: prompt,
            n: numImages,
            size: size
        }, {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${API_KEY}`
            }
        });

        const images = response.data.data;
        console.log(`成功生成 ${images.length} 张图片`);

        images.forEach((img, i) => {
            console.log(`\n图片 ${i+1}:`);
            console.log(`  URL: ${img.url}`);
            console.log(`  尺寸: ${img.size}`);
        });

        console.log(`\nToken使用:`, response.data.usage);
        return images;

    } catch (error) {
        console.error('生成失败:', error.response?.data || error.message);
        return null;
    }
}

/**
 * 下载图片
 */
async function downloadImage(imageUrl, savePath = null) {
    try {
        if (!savePath) {
            const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
            savePath = `generated_image_${timestamp}.jpg`;
        }

        const response = await axios.get(imageUrl, {
            responseType: 'arraybuffer'
        });

        fs.writeFileSync(savePath, response.data);
        console.log(`图片已保存到: ${savePath}`);
        return savePath;

    } catch (error) {
        console.error('下载失败:', error.message);
        return null;
    }
}

// 使用示例
async function main() {
    // 生成图片
    const images = await generateImage(
        '一个宁静的湖泊，倒映着雪山，日出时分，摄影作品',
        '1024x1024',
        1
    );

    // 下载第一张图片
    if (images && images.length > 0) {
        await downloadImage(images[0].url);
    }
}

main();
```

### 5.5 支持的图片尺寸

| 尺寸 | 说明 | 适用场景 |
|-----|------|---------|
| 512x512 | 标准尺寸，生成速度快 | 头像、小图标 |
| 1024x1024 | 高清尺寸（推荐） | 一般用途 |
| 2048x2048 | 2K超高清 | 高质量图片 |
| 4096x4096 | 4K超高清 | 专业级图片 |

### 5.6 提示词优化建议

**好的提示词示例**:
```
一只柴犬在樱花树下玩耍，春天，阳光明媚，高清摄影，细节丰富
```

**提示词要素**:
1. **主体**: 明确描述要生成的对象（一只柴犬）
2. **环境**: 描述场景和背景（樱花树下、春天）
3. **光线**: 说明光照条件（阳光明媚）
4. **风格**: 指定艺术风格（高清摄影）
5. **质量**: 强调画面质量（细节丰富）

### 5.7 响应格式

```json
{
  "model": "doubao-seedream-4-0-250828",
  "created": 1760505748,
  "data": [
    {
      "url": "https://ark-content-generation-v2-cn-beijing.tos-cn-beijing.volces.com/...",
      "size": "1024x1024"
    }
  ],
  "usage": {
    "generated_images": 1,
    "output_tokens": 4096,
    "total_tokens": 4096
  }
}
```

**注意**: 图片URL有效期为24小时，请及时下载保存。

---

## 6. 错误处理

### 6.1 常见错误码

| 错误码 | 说明 | 解决方案 |
|-------|------|---------|
| 401 | 认证失败 | 检查API Key是否正确 |
| 400 | 请求参数错误 | 检查请求格式和参数 |
| 429 | 请求频率超限 | 降低请求频率或联系增加配额 |
| 500 | 服务器错误 | 稍后重试 |

### 6.2 错误处理示例（Python）

```python
import requests
import time

def call_api_with_retry(api_func, max_retries=3, *args, **kwargs):
    """
    带重试机制的API调用
    """
    for attempt in range(max_retries):
        try:
            response = api_func(*args, **kwargs)

            if response.status_code == 200:
                return response.json()
            elif response.status_code == 429:
                # 请求频率超限，等待后重试
                wait_time = 2 ** attempt  # 指数退避
                print(f"请求频率超限，等待 {wait_time} 秒后重试...")
                time.sleep(wait_time)
            elif response.status_code == 500:
                # 服务器错误，短暂等待后重试
                print(f"服务器错误，1秒后重试...")
                time.sleep(1)
            else:
                # 其他错误，直接返回
                print(f"错误: {response.status_code}, {response.text}")
                return None

        except Exception as e:
            print(f"调用异常: {e}")
            if attempt < max_retries - 1:
                time.sleep(1)

    print("达到最大重试次数")
    return None

# 使用示例
def make_request():
    return requests.post(
        "https://ark.cn-beijing.volces.com/api/v3/chat/completions",
        headers={
            "Content-Type": "application/json",
            "Authorization": "Bearer bd747896-e89b-46f4-a5ab-0a232d086845"
        },
        json={
            "model": "ep-20251015101857-wc8xz",
            "messages": [{"role": "user", "content": "Hello"}]
        }
    )

result = call_api_with_retry(make_request)
```

---

## 7. 最佳实践

### 7.1 性能优化

1. **连接复用**: 使用 HTTP Session 复用连接
   ```python
   import requests

   session = requests.Session()
   session.headers.update({
       "Authorization": "Bearer bd747896-e89b-46f4-a5ab-0a232d086845"
   })

   # 后续所有请求使用同一个session
   response = session.post(url, json=data)
   ```

2. **超时设置**: 设置合理的超时时间
   ```python
   response = requests.post(url, json=data, timeout=30)  # 30秒超时
   ```

3. **流式响应**（文本模型）:
   ```python
   data = {
       "model": "ep-20251015101857-wc8xz",
       "messages": [...],
       "stream": True  # 启用流式响应
   }

   response = requests.post(url, json=data, stream=True)
   for line in response.iter_lines():
       if line:
           print(line.decode('utf-8'))
   ```

### 7.2 安全建议

1. **API Key 保护**:
   - 不要在客户端代码中硬编码 API Key
   - 使用环境变量或配置文件存储
   ```python
   import os
   API_KEY = os.environ.get('VOLCENGINE_API_KEY')
   ```

2. **请求频率控制**:
   ```python
   import time
   from functools import wraps

   def rate_limit(calls_per_second=1):
       min_interval = 1.0 / calls_per_second
       last_called = [0.0]

       def decorator(func):
           @wraps(func)
           def wrapper(*args, **kwargs):
               elapsed = time.time() - last_called[0]
               wait_time = min_interval - elapsed
               if wait_time > 0:
                   time.sleep(wait_time)
               result = func(*args, **kwargs)
               last_called[0] = time.time()
               return result
           return wrapper
       return decorator

   @rate_limit(calls_per_second=2)
   def call_api():
       # API调用代码
       pass
   ```

### 7.3 日志记录

```python
import logging
from datetime import datetime

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('api_calls.log'),
        logging.StreamHandler()
    ]
)

logger = logging.getLogger(__name__)

def log_api_call(func):
    def wrapper(*args, **kwargs):
        start_time = datetime.now()
        logger.info(f"API调用开始: {func.__name__}")

        try:
            result = func(*args, **kwargs)
            duration = (datetime.now() - start_time).total_seconds()
            logger.info(f"API调用成功: {func.__name__}, 耗时: {duration}s")
            return result
        except Exception as e:
            logger.error(f"API调用失败: {func.__name__}, 错误: {e}")
            raise

    return wrapper

@log_api_call
def generate_text(prompt):
    # API调用代码
    pass
```

### 7.4 成本优化

1. **Token使用监控**:
   ```python
   def track_token_usage(response_data):
       usage = response_data.get('usage', {})
       total_tokens = usage.get('total_tokens', 0)

       # 记录到数据库或日志
       print(f"本次调用使用Token: {total_tokens}")

       # 累计统计
       with open('token_usage.log', 'a') as f:
           f.write(f"{datetime.now()},{total_tokens}\n")
   ```

2. **提示词优化**:
   - 使用简洁明确的提示词
   - 避免过长的上下文
   - 合理使用系统消息

3. **图片生成优化**:
   - 根据实际需求选择合适的分辨率
   - 批量生成时使用 `n` 参数一次生成多张

---

## 8. 完整示例项目

### 8.1 智能对话机器人（Python）

```python
import requests
import json
from typing import List, Dict

class DoubaoChat:
    def __init__(self):
        self.api_key = "bd747896-e89b-46f4-a5ab-0a232d086845"
        self.endpoint_id = "ep-20251015101857-wc8xz"
        self.api_url = "https://ark.cn-beijing.volces.com/api/v3/chat/completions"
        self.conversation_history: List[Dict] = []

    def send_message(self, user_message: str) -> str:
        """发送消息并获取回复"""
        # 添加用户消息到历史
        self.conversation_history.append({
            "role": "user",
            "content": user_message
        })

        # 调用API
        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {self.api_key}"
        }

        data = {
            "model": self.endpoint_id,
            "messages": self.conversation_history
        }

        response = requests.post(self.api_url, headers=headers, json=data)
        result = response.json()

        if response.status_code == 200:
            assistant_message = result['choices'][0]['message']['content']

            # 添加助手回复到历史
            self.conversation_history.append({
                "role": "assistant",
                "content": assistant_message
            })

            return assistant_message
        else:
            return f"错误: {result}"

    def reset_conversation(self):
        """重置对话历史"""
        self.conversation_history = []

    def set_system_prompt(self, system_prompt: str):
        """设置系统提示"""
        if self.conversation_history and self.conversation_history[0]['role'] == 'system':
            self.conversation_history[0] = {
                "role": "system",
                "content": system_prompt
            }
        else:
            self.conversation_history.insert(0, {
                "role": "system",
                "content": system_prompt
            })

# 使用示例
if __name__ == "__main__":
    bot = DoubaoChat()
    bot.set_system_prompt("你是一个专业的编程助手，擅长Python和JavaScript开发。")

    print("对话机器人已启动！输入 'quit' 退出")

    while True:
        user_input = input("\n你: ")
        if user_input.lower() == 'quit':
            break

        response = bot.send_message(user_input)
        print(f"\n助手: {response}")
```

### 8.2 批量图片生成工具（Python）

```python
import requests
import os
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime

class ImageGenerator:
    def __init__(self):
        self.api_key = "bd747896-e89b-46f4-a5ab-0a232d086845"
        self.endpoint_id = "ep-20251015102102-x2n2t"
        self.api_url = "https://ark.cn-beijing.volces.com/api/v3/images/generations"
        self.output_dir = "generated_images"

        # 创建输出目录
        os.makedirs(self.output_dir, exist_ok=True)

    def generate_single(self, prompt: str, size: str = "1024x1024") -> dict:
        """生成单张图片"""
        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {self.api_key}"
        }

        data = {
            "model": self.endpoint_id,
            "prompt": prompt,
            "n": 1,
            "size": size
        }

        response = requests.post(self.api_url, headers=headers, json=data)
        result = response.json()

        if response.status_code == 200:
            return {
                "success": True,
                "url": result['data'][0]['url'],
                "prompt": prompt
            }
        else:
            return {
                "success": False,
                "error": result,
                "prompt": prompt
            }

    def download_image(self, image_url: str, filename: str) -> str:
        """下载图片"""
        response = requests.get(image_url)
        if response.status_code == 200:
            filepath = os.path.join(self.output_dir, filename)
            with open(filepath, 'wb') as f:
                f.write(response.content)
            return filepath
        return None

    def batch_generate(self, prompts: list, size: str = "1024x1024", max_workers: int = 3):
        """批量生成图片"""
        print(f"开始批量生成 {len(prompts)} 张图片...")

        with ThreadPoolExecutor(max_workers=max_workers) as executor:
            futures = [executor.submit(self.generate_single, prompt, size) for prompt in prompts]

            results = []
            for i, future in enumerate(futures):
                result = future.result()
                results.append(result)

                if result['success']:
                    timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
                    filename = f"image_{i+1}_{timestamp}.jpg"
                    filepath = self.download_image(result['url'], filename)
                    print(f"✓ 图片 {i+1} 生成成功: {filepath}")
                else:
                    print(f"✗ 图片 {i+1} 生成失败: {result['error']}")

        return results

# 使用示例
if __name__ == "__main__":
    generator = ImageGenerator()

    prompts = [
        "一只可爱的小猫，卡通风格",
        "未来城市，科技感，夜景",
        "宁静的海滩，日落，摄影作品",
        "抽象艺术，色彩缤纷"
    ]

    results = generator.batch_generate(prompts, size="1024x1024")

    print(f"\n总结: 成功 {sum(1 for r in results if r['success'])} 张, 失败 {sum(1 for r in results if not r['success'])} 张")
```

---

## 9. 常见问题 FAQ

### Q1: API调用超时怎么办？
**A**:
- 检查网络连接
- 增加超时时间设置（建议30-60秒）
- 对于图片生成，可能需要更长时间（特别是高分辨率）

### Q2: 如何获取更多的API配额？
**A**:
- 联系火山引擎客服
- 访问控制台查看当前配额
- 根据业务需求申请增加

### Q3: 生成的图片URL失效了怎么办？
**A**:
- 图片URL有效期为24小时
- 请及时下载并保存到本地或云存储
- 不要依赖临时URL进行长期存储

### Q4: 可以商用吗？
**A**:
- 请查阅火山引擎服务协议
- 建议联系商务团队确认授权范围

### Q5: 支持哪些编程语言？
**A**:
- 任何支持HTTP请求的语言都可以
- 官方提供Python、Java SDK
- 社区提供Node.js、Go等语言示例

---

## 10. 相关资源

- **官方文档**: https://www.volcengine.com/docs/82379
- **控制台**: https://console.volcengine.com/ark
- **模型广场**: https://console.volcengine.com/ark/region:ark+cn-beijing/model
- **GitHub SDK**: https://github.com/volcengine/volcengine-python-sdk

---

## 附录: API快速参考

### 文本推理模型
```bash
POST https://ark.cn-beijing.volces.com/api/v3/chat/completions
Model: ep-20251015101857-wc8xz
```

### 视觉理解模型
```bash
POST https://ark.cn-beijing.volces.com/api/v3/chat/completions
Model: ep-20251015102018-vn2mf
```

### 图片生成模型
```bash
POST https://ark.cn-beijing.volces.com/api/v3/images/generations
Model: ep-20251015102102-x2n2t
```

### 认证Header
```
Authorization: Bearer bd747896-e89b-46f4-a5ab-0a232d086845
Content-Type: application/json
```

---

*文档版本: 1.0*
*最后更新: 2025-01-15*
*适用于: 火山引擎豆包大模型系列*
