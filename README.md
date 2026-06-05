# AI 小说转剧本工具

将中文小说文本自动转换为结构化 YAML 剧本，助力作者快速获得可编辑的剧本初稿。

## 功能特性

- 支持 3 个章节以上的小说文本输入（粘贴或上传 .txt）
- 自动识别人物、场景、对白和动作描述
- 输出符合标准 Schema 的 YAML 格式剧本
- 支持下载 `.yaml` 文件，可直接二次编辑打磨
- 内置人物表自动提取

## 快速开始

### 环境要求

- Python 3.10+
- 通义千问 API Key（[免费申请](https://dashscope.console.aliyun.com/apiKey)，注册阿里云账号即可）

### 安装与运行

```bash
# 1. 克隆仓库
git clone https://github.com/gsdf15846-netizen/QianNiu.git
cd QianNiu

# 2. 安装后端依赖
cd backend
pip install -r requirements.txt

# 3. 配置 API Key
cp .env.example .env
# 编辑 .env，填入你的 DASHSCOPE_API_KEY

# 4. 启动后端服务
python main.py
# 服务运行在 http://127.0.0.1:8000

# 5. 打开前端
# 直接用浏览器打开 frontend/index.html
```

## 项目结构

```
QianNiu/
├── backend/              # Python FastAPI 后端
│   ├── main.py           # 服务入口
│   ├── converter.py      # 小说→剧本转换核心逻辑
│   ├── schema.py         # YAML Schema Pydantic 模型
│   ├── requirements.txt
│   └── .env.example
├── frontend/             # 纯 HTML/CSS/JS 前端
│   ├── index.html
│   ├── style.css
│   └── app.js
├── docs/
│   └── yaml-schema.md    # YAML Schema 定义文档
└── README.md
```

## YAML 剧本格式

详见 [docs/yaml-schema.md](docs/yaml-schema.md)

示例输出片段：

```yaml
screenplay:
  metadata:
    title: "改编自《示例小说》"
    total_chapters: 3
  characters:
    - id: "char_001"
      name: "林晓"
  chapters:
    - chapter_number: 1
      title: "初遇"
      scenes:
        - scene_number: 1
          heading:
            location_type: "INT"
            place: "咖啡馆"
            time: "DAY"
          elements:
            - type: "action"
              content: "林晓推开玻璃门，阳光洒在她脸上。"
            - type: "dialogue"
              character: "林晓"
              content: "还好，没迟到。"
```

## Demo 视频

> 视频链接：（待发布后更新）

## 依赖说明

| 依赖 | 版本 | 用途 |
|------|------|------|
| fastapi | ≥0.110 | Web 框架 |
| uvicorn | ≥0.29 | ASGI 服务器 |
| openai | ≥1.30 | 通义千问 API SDK（OpenAI 兼容格式）|
| python-dotenv | ≥1.0 | 环境变量管理 |
| pyyaml | ≥6.0 | YAML 序列化 |

## 原创声明

本项目为个人参赛作品，所有代码为比赛期间原创编写，未复用个人历史代码片段。
