from pydantic import BaseModel, Field
from typing import Optional
from enum import Enum


class LocationType(str, Enum):
    INT = "INT"
    EXT = "EXT"
    INT_EXT = "INT/EXT"


class TimeOfDay(str, Enum):
    DAY = "DAY"
    NIGHT = "NIGHT"
    DUSK = "DUSK"
    DAWN = "DAWN"
    MORNING = "MORNING"
    AFTERNOON = "AFTERNOON"
    EVENING = "EVENING"
    CONTINUOUS = "CONTINUOUS"
    LATER = "LATER"
    MOMENTS_LATER = "MOMENTS LATER"


class ElementType(str, Enum):
    ACTION = "action"
    DIALOGUE = "dialogue"
    TRANSITION = "transition"
    NOTE = "note"


class Character(BaseModel):
    id: str
    name: str
    aliases: list[str] = []
    description: str = ""


class SceneHeading(BaseModel):
    location_type: LocationType
    place: str
    time: TimeOfDay


class SceneElement(BaseModel):
    type: ElementType
    character: Optional[str] = None
    parenthetical: Optional[str] = None
    content: str


class Scene(BaseModel):
    scene_number: int
    heading: SceneHeading
    synopsis: str = ""
    elements: list[SceneElement]


class Chapter(BaseModel):
    chapter_number: int
    title: str
    scenes: list[Scene]


class ScreenplayMetadata(BaseModel):
    title: str
    source_novel: str
    author: str = "AI辅助生成"
    created_at: str
    total_chapters: int = Field(ge=3)


class Screenplay(BaseModel):
    metadata: ScreenplayMetadata
    characters: list[Character]
    chapters: list[Chapter]


class ScreenplayWrapper(BaseModel):
    screenplay: Screenplay


class ConvertRequest(BaseModel):
    novel_text: str = Field(min_length=100)
    novel_title: str = ""
    chapter_separator: str = ""


class ConvertResponse(BaseModel):
    success: bool
    yaml_content: str = ""
    error: str = ""
    chapters_detected: int = 0
    characters_detected: int = 0
