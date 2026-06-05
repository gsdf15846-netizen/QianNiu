import re
import json
import yaml
from datetime import datetime
from openai import OpenAI
from schema import ScreenplayWrapper

SYSTEM_PROMPT = """你是一位专业的影视剧本改编专家，擅长将中文小说转换为标准剧本格式。
你的任务是将用户提供的小说文本转换为结构化的 JSON 格式（后续会转为 YAML 剧本）。

输出规则：
1. 严格按照给定的 JSON Schema 输出，不要输出任何额外文字或解释
2. 只输出一个合法的 JSON 对象，不要用 markdown 代码块包裹
3. 所有字段必须使用给定的枚举值
4. action 类型描述用客观第三人称，简洁有画面感
5. dialogue 必须填写 character 字段（填写人物姓名，不是 id）
6. 每个场景至少包含 2 个 elements
7. characters 列表必须包含所有在对话中出现的人物
8. total_chapters 不得少于 3
9. 场景标题中的地点要具体，如"咖啡馆·靠窗位置"而不是"室内"
10. parenthetical 仅在有明确语气/动作说明时才填写，否则省略"""

JSON_SCHEMA_EXAMPLE = """{
  "screenplay": {
    "metadata": {
      "title": "改编自《小说名》",
      "source_novel": "小说名",
      "author": "AI辅助生成",
      "created_at": "2026-06-05T10:00:00+08:00",
      "total_chapters": 3
    },
    "characters": [
      {"id": "char_001", "name": "人物姓名", "aliases": ["别名"], "description": "简要描述"}
    ],
    "chapters": [
      {
        "chapter_number": 1,
        "title": "章节标题",
        "scenes": [
          {
            "scene_number": 1,
            "heading": {
              "location_type": "INT",
              "place": "具体地点",
              "time": "DAY"
            },
            "synopsis": "本场景一句话概要",
            "elements": [
              {"type": "action", "content": "动作描述"},
              {"type": "dialogue", "character": "人物姓名", "parenthetical": "低声（可省略）", "content": "台词内容"},
              {"type": "transition", "content": "切入——"}
            ]
          }
        ]
      }
    ]
  }
}"""


def split_chapters(text: str, separator: str = "") -> list[str]:
    """Split novel text into chapters."""
    if separator:
        parts = text.split(separator)
        return [p.strip() for p in parts if p.strip()]

    # Try common Chinese chapter heading patterns
    patterns = [
        r'第[一二三四五六七八九十百千\d]+[章节回][^\n]*',
        r'Chapter\s+\d+[^\n]*',
    ]
    for pattern in patterns:
        matches = list(re.finditer(pattern, text))
        if len(matches) >= 3:
            chapters = []
            for i, match in enumerate(matches):
                start = match.start()
                end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
                chapters.append(text[start:end].strip())
            return chapters

    # Fallback: split by blank lines into roughly equal chunks of at least 3
    paragraphs = [p.strip() for p in text.split('\n\n') if p.strip()]
    if len(paragraphs) < 3:
        return [text]
    chunk_size = max(1, len(paragraphs) // 3)
    chunks = []
    for i in range(0, len(paragraphs), chunk_size):
        chunk = '\n\n'.join(paragraphs[i:i + chunk_size])
        if chunk:
            chunks.append(chunk)
    return chunks


def convert_novel_to_screenplay(
    novel_text: str,
    novel_title: str,
    chapter_separator: str,
    client: OpenAI,
    model: str,
) -> dict:
    """Call Qwen API to convert novel text to screenplay JSON structure."""
    chapters = split_chapters(novel_text, chapter_separator)
    chapters_to_process = chapters[:min(len(chapters), 5)]
    chapters_text = "\n\n---CHAPTER_BREAK---\n\n".join(
        f"[第{i+1}章]\n{ch}" for i, ch in enumerate(chapters_to_process)
    )
    now = datetime.now().strftime("%Y-%m-%dT%H:%M:%S+08:00")

    user_prompt = f"""请将以下小说文本改编为剧本，输出 JSON 格式。

小说标题：{novel_title or '未命名'}
共 {len(chapters_to_process)} 个章节

小说内容：
{chapters_text}

请严格按照以下 JSON 结构输出（直接输出 JSON，不要 markdown 格式）：
{JSON_SCHEMA_EXAMPLE}

要求：
- created_at 填写：{now}
- 每章至少 2 个场景
- 人物 id 格式为 char_001, char_002...
- total_chapters = {len(chapters_to_process)}（需要 >= 3）
- synopsis 用一句话概括本场景"""

    response = client.chat.completions.create(
        model=model,
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_prompt},
        ],
        temperature=0.3,
    )

    response_text = response.choices[0].message.content.strip()

    # Strip markdown code blocks if present
    if "```json" in response_text:
        response_text = response_text.split("```json")[1].split("```")[0].strip()
    elif "```" in response_text:
        response_text = response_text.split("```")[1].split("```")[0].strip()

    return json.loads(response_text)


def validate_and_serialize(data: dict) -> tuple[str, int, int]:
    """Validate against Pydantic schema and serialize to YAML string."""
    wrapper = ScreenplayWrapper(**data)
    screenplay_dict = wrapper.model_dump(mode="python", exclude_none=True)

    yaml_str = yaml.dump(
        screenplay_dict,
        allow_unicode=True,
        default_flow_style=False,
        sort_keys=False,
        indent=2,
        width=120,
    )

    chapters_count = len(wrapper.screenplay.chapters)
    chars_count = len(wrapper.screenplay.characters)
    return yaml_str, chapters_count, chars_count
