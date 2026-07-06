from pathlib import Path
from datetime import datetime
from collections import defaultdict

ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = Path(__file__).resolve().parent

INCLUDE_EXT = {
    ".java", ".vue", ".ts", ".js", ".json", ".yml", ".yaml",
    ".xml", ".sql", ".md", ".txt", ".properties", ".html", ".css",
    ".ini", ".csv", ".pom"
}

INCLUDE_NAMES = {
    "pom.xml", "package.json", "package-lock.json", "vite.config.js",
    "vite.config.ts", "tsconfig.json", "jsconfig.json"
}

EXCLUDE_DIRS = {
    ".git", ".idea", ".vscode", "node_modules", "target", "dist",
    "build", ".gradle", ".mvn", "__pycache__", ".pytest_cache",
    "chatgpt_context_pack"
}

GROUPS = {
    "smartlab_backend_management.md": ["backend/src/main/java/com/smartlab/management"],
    "smartlab_backend_engine.md": ["backend/src/main/java/com/smartlab/engine"],
    "smartlab_backend_adapter.md": ["backend/src/main/java/com/smartlab/adapter", "adapter/"],
    "smartlab_backend_global.md": ["backend/src/main/java/com/smartlab/global"],
    "smartlab_frontend.md": ["frontend/src", "frontend/package", "frontend/vite", "frontend/index.html"],
    "smartlab_sql_and_config.md": ["backend/src/main/resources", ".sql", "application", "mapper", "schemas"],
}

MAX_FILE_CHARS_SINGLE = 60000
MAX_FILE_CHARS_GROUP = 80000


def rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def should_skip(path: Path) -> bool:
    try:
        relative = path.relative_to(ROOT)
    except ValueError:
        return True
    parts = {part.lower() for part in relative.parts}
    if parts & {d.lower() for d in EXCLUDE_DIRS}:
        return True
    if not path.is_file():
        return True
    name = path.name.lower()
    suffix = path.suffix.lower()
    return suffix not in INCLUDE_EXT and name not in INCLUDE_NAMES


def read_text(path: Path, max_chars: int) -> str:
    try:
        text = path.read_text(encoding="utf-8", errors="ignore")
    except Exception as exc:
        return f"[unable to read: {exc}]"
    if len(text) > max_chars:
        return text[:max_chars] + "\n\n[file truncated because it is too long]\n"
    return text


def collect_files() -> list[Path]:
    files = [p for p in ROOT.rglob("*") if not should_skip(p)]
    return sorted(files, key=lambda p: rel(p).lower())


def write_context(out_path: Path, title: str, files: list[Path], max_chars: int) -> None:
    with out_path.open("w", encoding="utf-8", newline="\n") as out:
        out.write(f"# {title}\n\n")
        out.write(f"Generated at: {datetime.now().isoformat(timespec='seconds')}\n\n")
        out.write("This file is generated from the local SmartLab repository for model-readable project context.\n\n")
        out.write("## File Tree\n\n")
        for file in files:
            out.write(f"- {rel(file)}\n")
        out.write("\n## Files\n")
        for file in files:
            out.write("\n---\n\n")
            out.write(f"## {rel(file)}\n\n")
            out.write("````text\n")
            out.write(read_text(file, max_chars))
            out.write("\n````\n")


def match_group(path: Path, keywords: list[str]) -> bool:
    value = rel(path).lower()
    return any(keyword.lower() in value for keyword in keywords)


def write_code_map(files: list[Path]) -> None:
    by_ext = defaultdict(int)
    by_top = defaultdict(int)
    for file in files:
        by_ext[file.suffix.lower() or file.name.lower()] += 1
        by_top[rel(file).split("/")[0]] += 1

    out_path = OUT_DIR / "smartlab_code_map.md"
    with out_path.open("w", encoding="utf-8", newline="\n") as out:
        out.write("# smartlab_code_map\n\n")
        out.write(f"Generated at: {datetime.now().isoformat(timespec='seconds')}\n\n")
        out.write("## Repository Root\n\n")
        out.write(f"`{ROOT}`\n\n")
        out.write("## File Counts By Top Directory\n\n")
        for key, count in sorted(by_top.items()):
            out.write(f"- {key}: {count}\n")
        out.write("\n## File Counts By Extension\n\n")
        for key, count in sorted(by_ext.items()):
            out.write(f"- {key}: {count}\n")
        out.write("\n## Important Areas\n\n")
        out.write("- Backend adapter: `Backend/src/main/java/com/smartlab/adapter`\n")
        out.write("- Backend engine: `Backend/src/main/java/com/smartlab/engine`\n")
        out.write("- Backend global: `Backend/src/main/java/com/smartlab/global`\n")
        out.write("- Backend management: `Backend/src/main/java/com/smartlab/management`\n")
        out.write("- JSON Schema: `Backend/src/main/resources/schemas`\n")
        out.write("- Frontend views: `Frontend/src/views`\n")
        out.write("- Adapter examples: `adapter`\n")
        out.write("\n## Full File Tree\n\n")
        for file in files:
            out.write(f"- {rel(file)}\n")


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    files = collect_files()
    write_context(OUT_DIR / "smartlab_context.md", "smartlab full code context", files, MAX_FILE_CHARS_SINGLE)
    for out_name, keywords in GROUPS.items():
        matched = [file for file in files if match_group(file, keywords)]
        write_context(OUT_DIR / out_name, out_name.removesuffix(".md"), matched, MAX_FILE_CHARS_GROUP)
    write_code_map(files)
    print(f"output directory: {OUT_DIR}")
    print(f"included files: {len(files)}")
    for out_name in ["smartlab_context.md", *GROUPS.keys(), "smartlab_code_map.md"]:
        path = OUT_DIR / out_name
        print(f"generated: {path.name} ({path.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
