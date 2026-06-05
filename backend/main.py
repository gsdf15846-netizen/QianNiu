import os
from pathlib import Path
from dotenv import load_dotenv
from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from openai import OpenAI, AuthenticationError, APIError

from schema import ConvertRequest, ConvertResponse
from converter import convert_novel_to_screenplay, validate_and_serialize

load_dotenv()

app = FastAPI(
    title="AI小说转剧本工具",
    description="将中文小说文本转换为结构化 YAML 剧本",
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

QWEN_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"


def get_client() -> tuple[OpenAI, str]:
    api_key = os.getenv("DASHSCOPE_API_KEY", "")
    if not api_key or api_key.startswith("sk-xxx"):
        raise HTTPException(
            status_code=500,
            detail="未配置 DASHSCOPE_API_KEY，请在 backend/.env 中填入通义千问 API Key"
        )
    model = os.getenv("QWEN_MODEL", "qwen-plus")
    client = OpenAI(api_key=api_key, base_url=QWEN_BASE_URL)
    return client, model


@app.get("/health")
def health_check():
    return {"status": "ok", "version": "1.0.0", "provider": "通义千问"}


@app.post("/convert", response_model=ConvertResponse)
async def convert_novel(request: ConvertRequest):
    client, model = get_client()
    try:
        data = convert_novel_to_screenplay(
            novel_text=request.novel_text,
            novel_title=request.novel_title,
            chapter_separator=request.chapter_separator,
            client=client,
            model=model,
        )
        yaml_content, chapters_count, chars_count = validate_and_serialize(data)
        return ConvertResponse(
            success=True,
            yaml_content=yaml_content,
            chapters_detected=chapters_count,
            characters_detected=chars_count,
        )
    except AuthenticationError:
        raise HTTPException(status_code=401, detail="API Key 无效，请检查 backend/.env 中的 DASHSCOPE_API_KEY")
    except APIError as e:
        raise HTTPException(status_code=502, detail=f"通义千问 API 错误: {str(e)}")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"转换失败: {str(e)}")


@app.post("/upload", response_model=ConvertResponse)
async def upload_and_convert(
    file: UploadFile = File(...),
    novel_title: str = "",
    chapter_separator: str = "",
):
    if not file.filename.endswith(".txt"):
        raise HTTPException(status_code=400, detail="仅支持 .txt 格式文件")

    content = await file.read()
    try:
        novel_text = content.decode("utf-8")
    except UnicodeDecodeError:
        novel_text = content.decode("gbk", errors="replace")

    if len(novel_text) < 100:
        raise HTTPException(status_code=400, detail="文件内容太少，请上传至少包含 3 个章节的小说文件")

    request = ConvertRequest(
        novel_text=novel_text,
        novel_title=novel_title or Path(file.filename).stem,
        chapter_separator=chapter_separator,
    )
    return await convert_novel(request)


if __name__ == "__main__":
    import uvicorn
    host = os.getenv("HOST", "127.0.0.1")
    port = int(os.getenv("PORT", "8000"))
    uvicorn.run("main:app", host=host, port=port, reload=True)
