# AI 小说转剧本工具

> 将中文小说文本一键转换为标准 YAML 剧本格式，降低改编门槛，助力作者快速获得可编辑的剧本初稿。

## Demo 视频

> 视频录制完成后链接将更新至此处（Bilibili）

## 功能特性

- **智能改编**：基于 Qwen AI，自动识别场景、人物、对白，转换为标准剧本元素
- **YAML 输出**：结构化、可读性强，便于作者在任意编辑器中打磨修改
- **文件上传**：支持直接上传 `.txt` 文件批量转换
- **一键下载**：转换结果可直接下载为 `.yaml` 文件
- **Web 界面**：无需安装任何客户端，浏览器即用

## 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Java 17 + Spring Boot 3.3 |
| AI   | Qwen（通义千问）OpenAI 兼容 API |
| 序列化 | Jackson（JSON）+ SnakeYAML（YAML） |
| 前端 | 原生 HTML5 + CSS3 + JavaScript（无构建步骤） |

## 本地运行

### 前置条件

- JDK 17+
- Maven 3.8+
- DashScope API Key（[申请地址](https://dashscope.aliyun.com/)）

### 步骤

```bash
# 1. 克隆仓库
git clone https://github.com/gsdf15846-netizen/QianNiu.git
cd QianNiu

# 2. 设置环境变量
export DASHSCOPE_API_KEY=your_api_key_here   # Linux/Mac
# 或 Windows PowerShell:
$env:DASHSCOPE_API_KEY = "your_api_key_here"

# 3. 构建并启动
mvn spring-boot:run

# 4. 打开浏览器
# 访问 http://localhost:8080
```

### Windows 快速启动

```powershell
$env:DASHSCOPE_API_KEY = "your_api_key_here"
mvn spring-boot:run
```

## API 文档

### `GET /api/health`
健康检查。

**响应：**
```json
{ "status": "UP", "service": "novel-to-script" }
```

---

### `POST /api/convert`
将小说文本转换为 YAML 剧本。

**请求体：**
```json
{
  "title": "第一章 初遇",
  "text": "小说正文内容..."
}
```

**响应：**
```json
{
  "success": true,
  "yaml": "script:\n  title: ...",
  "script": { ... }
}
```

---

### `POST /api/upload`
上传 `.txt` 文件进行转换。

**参数：**
- `file`（multipart）：文本文件，最大 10MB
- `title`（可选）：剧本标题

---

## YAML Schema

详见 [docs/yaml-schema.md](docs/yaml-schema.md)，包含完整字段定义和设计说明。

## 项目结构

```
QianNiu/
├── src/
│   ├── main/
│   │   ├── java/com/qianniu/
│   │   │   ├── QianNiuApplication.java
│   │   │   ├── config/AppConfig.java
│   │   │   ├── controller/ConvertController.java
│   │   │   ├── service/
│   │   │   │   ├── QwenService.java        # Qwen API 调用
│   │   │   │   └── NovelConverterService.java  # 转换核心逻辑
│   │   │   └── model/                      # 数据模型
│   │   └── resources/
│   │       ├── application.yml
│   │       └── static/                     # 前端文件
└── docs/
    └── yaml-schema.md                      # YAML Schema 定义
```

## 许可证

MIT
