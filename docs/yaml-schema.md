# 剧本 YAML Schema 定义文档

## 概述

本文档定义了"AI 小说转剧本工具"输出的 YAML 文件结构（Schema）及其设计原因。

---

## 完整示例

```yaml
script:
  title: "第一章：相遇"
  novel_source: "斗破苍穹"
  generated_at: "2026-06-05 10:30:00"

  characters:
    - id: "char_001"
      name: "萧炎"
      description: "主角，天才少年，后天废材，意志坚定"
    - id: "char_002"
      name: "药老"
      description: "戒指中的老者，身世神秘，实力深不可测"

  scenes:
    - scene_id: "S001"
      title: "废弃矿洞"
      location: "内 山中废矿洞"
      time_of_day: "DAY"
      elements:
        - type: ACTION
          content: "破旧的矿洞内，阳光从裂缝中斜射而入，尘埃在光束中缓缓飘落。"
        - type: DIALOGUE
          character: "萧炎"
          content: "这里……有人吗？"
          direction: "颤抖地，向黑暗处张望"
        - type: DIALOGUE
          character: "药老"
          content: "小子，你终于来了。"
          direction: "声音从戒指中传出，苍老而深沉"
        - type: TRANSITION
          content: "切入"
    - scene_id: "S002"
      title: "家族广场"
      location: "外 萧家族广场"
      time_of_day: "DAWN"
      elements:
        - type: ACTION
          content: "朝霞映红半边天，族人陆续聚集，低声议论。"
        - type: DIALOGUE
          character: "萧炎"
          content: "测灵台开始了。"
```

---

## Schema 字段说明

### 顶层结构

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `script` | Object | 是 | 剧本根节点，所有内容均在此节点下 |

### `script` 对象

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `title` | String | 是 | 章节/剧本标题 |
| `novel_source` | String | 否 | 原著小说名称 |
| `generated_at` | String | 是 | AI 生成时间，格式 `yyyy-MM-dd HH:mm:ss` |
| `characters` | Array\<Character\> | 是 | 出场人物列表 |
| `scenes` | Array\<Scene\> | 是 | 场景列表，顺序即剧情顺序 |

### `Character` 对象

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | String | 是 | 人物唯一标识，格式 `char_001` |
| `name` | String | 是 | 人物名称 |
| `description` | String | 否 | 人物简介/特征描述 |

### `Scene` 对象

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `scene_id` | String | 是 | 场景唯一标识，格式 `S001` |
| `title` | String | 是 | 场景名称/摘要 |
| `location` | String | 是 | 地点描述，见下方格式规范 |
| `time_of_day` | Enum | 是 | 时间段，见下方枚举值 |
| `elements` | Array\<Element\> | 是 | 场景内容元素列表 |

#### `location` 格式规范

参考好莱坞剧本惯例，本地化为中文：

```
内/外  地点名称
```

- `内`：室内场景（对应英文 INT.）
- `外`：室外场景（对应英文 EXT.）
- 示例：`内 皇宫大殿`、`外 战场废墟`、`内/外 城门处`

#### `time_of_day` 枚举值

| 值 | 含义 |
|----|------|
| `DAY` | 白天 |
| `NIGHT` | 夜晚 |
| `DAWN` | 黎明 |
| `DUSK` | 黄昏 |
| `CONTINUOUS` | 连续（时间未中断，紧接上一场景） |

### `Element` 对象

| 字段 | 类型 | 必填 | 适用类型 | 说明 |
|------|------|------|----------|------|
| `type` | Enum | 是 | 全部 | 元素类型 |
| `content` | String | 是 | 全部 | 元素内容 |
| `character` | String | 条件必填 | DIALOGUE | 发言角色名，必须与 `characters.name` 一致 |
| `direction` | String | 否 | DIALOGUE | 表演指导，括号内的方向性说明 |

#### `type` 枚举值

| 值 | 含义 | 对应小说文本 |
|----|------|------------|
| `ACTION` | 动作/场景描述 | 叙述段落、环境描写、人物行为 |
| `DIALOGUE` | 对白 | 引号内的对话内容 |
| `TRANSITION` | 转场提示 | 场景切换节点 |
| `SOUND` | 音效/声音提示 | 需要特别强调的声音 |

---

## 设计原因

### 1. 为什么以 `script` 为根节点而不是平铺？

YAML 允许多文档合并，用根节点包裹便于将多个章节的剧本合并为一个文件，同时也清晰地区分了"这是一段剧本数据"而不是普通配置。

### 2. 为什么用 `characters` 集中定义人物？

- **可复用**：同一角色在多个场景出现，避免描述重复
- **可校验**：`DIALOGUE` 元素的 `character` 字段可与此列表做一致性校验
- **便于扩展**：未来可为角色增加 `alias`（别名）、`gender`、`age` 等字段

### 3. 为什么场景元素是有序数组（`elements`）而不是分类字段？

剧本的核心是**时序**。动作、对白、转场必须严格按出现顺序排列，分类字段（如 `actions: []`, `dialogues: []`）会丢失这种顺序信息，导致改编后无法还原正确的剧情节奏。

### 4. 为什么 `location` 用自然语言字符串而非结构化对象？

```yaml
# 结构化（过度设计）
location:
  interior: true
  place: "皇宫大殿"

# 本 Schema（简洁）
location: "内 皇宫大殿"
```

剧本工具的用户是作者，`内 皇宫大殿` 的可读性远优于结构化对象，且符合行业惯例，作者可直接理解并修改。

### 5. 为什么 `time_of_day` 用英文枚举？

- 枚举值需要机器严格匹配（后端校验、导出渲染），使用英文避免中文编码和大小写歧义
- 生成的 YAML 给作者阅读，`title`/`content` 等可读字段全部使用中文
- 枚举值数量有限（5个），不存在可读性负担

### 6. 为什么 DIALOGUE 有独立的 `direction` 字段？

舞台指导（如"激动地"、"低声"）在剧本格式里与台词本体有明确区分，分离为独立字段方便：
- 渲染时加括号显示
- 导出为 Final Draft 等专业剧本软件格式
- 作者可选择是否保留 AI 建议的表演指导

### 7. 为什么不直接输出 JSON？

YAML 相比 JSON 的优势：
- 支持多行字符串（对白内容可能很长）
- 无需转义引号，中文台词可读性更好
- 注释支持（作者可手动添加 `# TODO` 备注）
- 与剧本编辑工具（如 Fountain、FDX）的生态更接近

---

## 版本历史

| 版本 | 日期 | 变更 |
|------|------|------|
| v1.0 | 2026-06-05 | 初始 Schema 定义 |
