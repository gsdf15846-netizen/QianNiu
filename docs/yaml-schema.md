# 剧本 YAML Schema 定义文档

## 概述

本文档定义了"AI 小说转剧本工具"所使用的 YAML 剧本格式规范（Schema）。该格式用于描述由小说文本改编而来的结构化剧本，兼顾标准影视剧本格式（Final Draft / 好莱坞格式）与中文编剧实践，同时保留足够的结构化信息以支持程序化处理和二次编辑。

---

## 完整 Schema 定义

```yaml
screenplay:
  metadata:                     # 元信息
    title: string               # 剧本标题
    source_novel: string        # 原著小说名称
    author: string              # 改编者/编剧
    created_at: string          # 生成时间（ISO 8601 格式）
    total_chapters: integer     # 本次改编的章节数（≥3）

  characters:                   # 人物表（全剧人物汇总）
    - id: string                # 唯一标识符，格式 char_001
      name: string              # 人物主要姓名
      aliases:                  # 别名/外号/称谓列表
        - string
      description: string       # 人物简要描述

  chapters:                     # 章节列表（按原著章节顺序）
    - chapter_number: integer   # 章节序号（从 1 开始）
      title: string             # 章节标题（来自原著或 AI 生成）
      scenes:                   # 场景列表
        - scene_number: integer # 场景序号（章节内从 1 开始）
          heading:              # 场景标题行（对应标准剧本 Slug Line）
            location_type:      # 室内/室外/混合
              enum: [INT, EXT, INT/EXT]
            place: string       # 具体地点，如"咖啡馆·靠窗位置"
            time:               # 时间标识
              enum: [DAY, NIGHT, DUSK, DAWN, CONTINUOUS, LATER, MOMENTS LATER]
          synopsis: string      # 本场景一句话概要（可选，供编剧快速浏览）
          elements:             # 场景元素列表（剧本内容主体）
            - type:             # 元素类型
                enum: [action, dialogue, transition, note]
              character: string # 仅 dialogue 类型必填，对应 characters[].id 或 name
              parenthetical:    # 括号说明，仅用于 dialogue（可选）
                string          # 如 "(低声)" "(转身)"
              content: string   # 元素正文内容
```

---

## 字段说明

### `screenplay.metadata`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `title` | string | 是 | 改编剧本标题，默认为"《原著名》剧本改编" |
| `source_novel` | string | 是 | 原著名称，用于版权追踪和说明来源 |
| `author` | string | 否 | 改编者姓名，AI 生成时填写"AI辅助生成" |
| `created_at` | string | 是 | ISO 8601 时间戳，如 `2026-06-05T10:30:00+08:00` |
| `total_chapters` | integer | 是 | 本次改编覆盖的章节数，最小值为 3 |

### `screenplay.characters`

存放全剧人物表，方便编剧一览所有登场人物。

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | string | 是 | 唯一 ID，格式为 `char_001`，便于在 elements 中引用 |
| `name` | string | 是 | 人物主名（最常用的称呼）|
| `aliases` | list[string] | 否 | 人物其他称谓，如外号、姓名缩写等 |
| `description` | string | 否 | 一句话人物描述，辅助编剧理解角色定位 |

### `screenplay.chapters[].scenes[].heading`

对应标准剧本的"场景标题行"（Slug Line / Scene Heading），格式为：

```
{location_type}. {place} - {time}
```

例如：`INT. 咖啡馆·靠窗位置 - DAY`

| 字段 | 取值 | 说明 |
|------|------|------|
| `location_type` | `INT` | 室内（Interior） |
| | `EXT` | 室外（Exterior） |
| | `INT/EXT` | 室内外切换（如车内/车外交替）|
| `time` | `DAY` | 白天 |
| | `NIGHT` | 夜晚 |
| | `DUSK` | 黄昏 |
| | `DAWN` | 黎明 |
| | `CONTINUOUS` | 紧接上一场景，时间连续 |
| | `LATER` | 稍后 |
| | `MOMENTS LATER` | 片刻后 |

### `screenplay.chapters[].scenes[].elements[].type`

场景内容由有序的元素列表构成，共四种类型：

| 类型 | 说明 | 必填字段 |
|------|------|---------|
| `action` | 动作描述行，描述人物行为、场景氛围（客观第三人称）| `content` |
| `dialogue` | 对话行，包含说话人和台词 | `character`, `content` |
| `transition` | 转场指令，如"淡出"、"切入"、"叠化" | `content` |
| `note` | 编剧备注，不出现在最终剧本，供改稿参考 | `content` |

---

## Schema 设计原因

### 1. 为何选择 YAML 而非 JSON 或 Fountain

**YAML 的优势**：
- **可读性强**：YAML 缩进结构接近自然语言，编剧无需编程背景即可直接阅读和修改
- **注释支持**：YAML 原生支持 `#` 注释，便于编剧在文件中记录修改意图
- **工具友好**：可直接被 Python、Node.js 等主流语言解析，也可轻松导入 Excel/表格工具

**对比 Fountain**（专业剧本标记语言）：
- Fountain 偏向最终排版输出，结构化信息弱，不适合程序化处理
- YAML 更适合作为"中间格式"，可以再导出为 Fountain、PDF 等

**对比 JSON**：
- JSON 不支持注释，对非技术用户编辑不友好
- YAML 是 JSON 的超集，两者可互相转换

### 2. 为何在 chapters 下嵌套 scenes（两级结构）

小说改编往往保留章节概念，但一章可能含多个场景（如日夜切换、地点切换）。两级结构（chapter → scene）：
- 保留了原著章节的逻辑单元，便于编剧对照修改
- 允许一章内有多个独立场景，符合影视剧本实际需求
- 场景序号在章节内重置（从 1 开始），与行业惯例一致

### 3. 为何设计独立的 `characters` 人物表

传统剧本格式中，人物信息分散在各场景的对白提示中。独立人物表的设计原因：
- 编剧可在动笔前一览全部人物，快速了解小说人物关系
- `aliases` 字段解决了中文小说中同一人物多种称谓问题（如"林晓"/"晓晓"/"林同学"）
- 为后续"人物关系图"等扩展功能预留结构

### 4. 为何 `elements` 使用有序列表而非分类字段

剧本内容天然是线性有序的——对白和动作描述的顺序直接影响剧情表达。将所有内容放在统一的有序 `elements` 列表中：
- 保留了剧本元素的时序关系
- 比"dialogue_list + action_list"分离设计更直观
- 与 Final Draft、Celtx 等专业软件的内部数据模型逻辑一致

### 5. `synopsis` 字段的必要性

每个场景增加可选的 `synopsis` 字段（一句话概要），原因：
- AI 生成的剧本可能较长，编剧需要快速浏览场景结构
- 作为"大纲层"视图的数据基础，未来可生成场景卡片（index card）视图
- 不影响剧本主体内容，可选填，不增加使用负担

---

## 完整示例

```yaml
screenplay:
  metadata:
    title: "改编自《青山不老》第一至三章"
    source_novel: "青山不老"
    author: "AI辅助生成"
    created_at: "2026-06-05T10:30:00+08:00"
    total_chapters: 3

  characters:
    - id: "char_001"
      name: "林晓"
      aliases: ["晓晓", "林同学"]
      description: "女主角，大学生，性格开朗"
    - id: "char_002"
      name: "陈远"
      aliases: ["陈哥"]
      description: "男主角，山村教师，沉稳内敛"

  chapters:
    - chapter_number: 1
      title: "初入山村"
      scenes:
        - scene_number: 1
          heading:
            location_type: "EXT"
            place: "山村入口·土路"
            time: "DAY"
          synopsis: "林晓第一次抵达山村，被眼前的景色震撼。"
          elements:
            - type: "action"
              content: "破旧的中巴车停在黄土路尽头。林晓提着行李跳下车，望着连绵的青山发呆。"
            - type: "dialogue"
              character: "林晓"
              parenthetical: "喃喃自语"
              content: "这里……比照片里还偏远。"
            - type: "action"
              content: "远处，一个高挑的男人骑着自行车缓缓驶来，车篓里放着一摞课本。"

        - scene_number: 2
          heading:
            location_type: "EXT"
            place: "山村入口·土路"
            time: "CONTINUOUS"
          synopsis: "林晓与陈远初次相遇，互报姓名。"
          elements:
            - type: "action"
              content: "陈远停下自行车，打量着背包沉重的林晓。"
            - type: "dialogue"
              character: "陈远"
              content: "你是来支教的志愿者？"
            - type: "dialogue"
              character: "林晓"
              content: "对，我叫林晓。请问学校怎么走？"
            - type: "dialogue"
              character: "陈远"
              parenthetical: "指了指山路"
              content: "跟我来吧，正好同路。我是陈远，村小的老师。"
            - type: "transition"
              content: "切入——"

    - chapter_number: 2
      title: "山村小学"
      scenes:
        - scene_number: 1
          heading:
            location_type: "INT"
            place: "山村小学·教室"
            time: "DAY"
          synopsis: "林晓第一次走进简陋的教室，面对十几双好奇的眼睛。"
          elements:
            - type: "action"
              content: "教室墙壁斑驳，黑板上用粉笔写着算术题。十几个孩子齐刷刷地盯着走进来的林晓。"
            - type: "dialogue"
              character: "陈远"
              content: "同学们，这是林老师，从城里来的，以后教你们语文。"
            - type: "action"
              content: "孩子们爆发出一阵欢呼。林晓的眼眶微微泛红。"
```
