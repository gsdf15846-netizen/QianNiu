import re
import json
import yaml
from datetime import datetime
from anthropic import Anthropic
from schema import ScreenplayWrapper

SYSTEM_PROMPT = """你是一位专业的影视剧本改编专家，擅长将中文小说转换为标准剧本格式。
你的任务是将用户提供的小说文本转换为结构化的 YAML 格式剧本。

输出规则：
1. 严格按照给定的 JSON Schema 输出，不要输出任何额外文字
2. 只输出一个合法的 JSON 对象（后续会转为 YAML）
3. 所有字段必须使用给定的枚举值
4. action 类型描述用客观第三人称，简洁有画面感
5. dialogue 必须填写 character 字段
6. 每个场景至少包含 2 个 elements
7. characters 列表必须包含所有在对话中出现的人物
8. total_chapters 不得少于 3"""

JSON_SCHEMA = """{
  "screenplay": {
    "metadata": {
      "title": "string",
      "source_novel": "string",
      "author": "AI辅助生成",
      "created_at": "ISO8601 string",
      "total_chapters": "integer >= 3"
    },
    "characters": [
      {"id": "char_001", "name": "string", "aliases": ["string"], "description": "string"}
    ],
    "chapters": [
      {
        "chapter_number": "integer",
        "title": "string",
        "scenes": [
          {
            "scene_number": "integer",
            "heading": {
              "location_type": "INT|EXT|INT/EXT",
              "place": "string",
              "time": "DAY|NIGHT|DUSK|DAWN|CONTINUOUS|LATER|MOMENTS LATER"
            },
            "synopsis": "string",
            "elements": [
              {
                "type": "action|dialogue|transition|note",
                "character": "string (仅dialogue必填)",
                "parenthetical": "string (可选)",
                "content": "string"
              }
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

    patterns = [
        r'第[一二三四五六七八九十百千\d]+章[^\n]*',
        r'Chapter\s+\d+[^\n]*',
        r'\n\s*\d+\s*\n',
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

    # Fallback: split by double newlines into roughly equal chunks
    paragraphs = [p.strip() for p in text.split('\n\n') if p.strip()]
    if len(paragraphs) < 3:
        return [text]
    chunk_size = max(1, len(paragraphs) // 3)
    chunks = []
    for i in range(0, len(paragraphs), chunk_size):
        chunk = '\n\n'.join(paragraphs[i:i + chunk_size])
        if chunk:
            chunks.append(chunk)
    return chunks[:max(3, len(chunks))]


def convert_novel_to_screenplay(
    novel_text: str,
    novel_title: str,
    chapter_separator: str,
    client: Anthropic,
) -> dict:
    """Call Claude API to convert novel text to screenplay YAML structure."""
    chapters = split_chapters(novel_text, chapter_separator)
    chapters_to_process = chapters[:min(len(chapters), 5)]
    chapters_text = "\n\n---CHAPTER_BREAK---\n\n".join(
        f"[第{i+1}章]\n{ch}" for i, ch in enumerate(chapters_to_process)
    )

    user_prompt = f"""请将以下小说文本改编为 YAML 剧本。

小说标题：{novel_title or '未知'}
共检测到 {len(chapters_to_process)} 个章节（需要至少3个）

小说内容：
{chapters_text}

请严格按照以下 JSON Schema 格式输出，只输出 JSON，不要有任何多余文字：
{JSON_SCHEMA}

注意：
- created_at 填写当前时间 {datetime.now().isoformat()}
- 每章至少生成 2 个场景
- 人物 id 格式为 char_001, char_002...
- 确保 total_chapters >= 3"""

    message = client.messages.create(
        model="claude-sonnet-4-6",
        max_tokens=8192,
        system=SYSTEM_PROMPT,
        messages=[{"role": "user", "content": user_prompt}],
    )

    response_text = message.content[0].text.strip()

    # Extract JSON from response (handle markdown code blocks)
    if "```json" in response_text:
        response_text = response_text.split("```json")[1].split("```")[0].strip()
    elif "```" in response_text:
        response_text = response_text.split("```")[1].split("```")[0].strip()

    data = json.loads(response_text)
    return data


def validate_and_serialize(data: dict) -> tuple[str, int, int]:
    """Validate against Pydantic schema and serialize to YAML string."""
    wrapper = ScreenplayWrapper(**data)
    screenplay_dict = wrapper.model_dump(mode="python", exclude_none=True)

    # Convert enums to values for YAML output
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
