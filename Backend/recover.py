import json
import re

transcript_path = r"C:\Users\22960\.gemini\antigravity\brain\5a6da88e-9ff9-489a-b5d9-f637407a0ad6\.system_generated\logs\transcript_full.jsonl"

file_lines = {}

with open(transcript_path, "r", encoding="utf-8") as f:
    for line in f:
        try:
            data = json.loads(line)
            if data.get("type") == "TOOL_RESPONSE":
                content = data.get("content", "")
                if "File Path: `file:///d:/SmartLab2.0/Backend/src/main/resources/schemas/device-state-machine-model.json`" in content:
                    # Parse the lines
                    for rline in content.split("\n"):
                        m = re.match(r"^(\d+): (.*)$", rline)
                        if m:
                            line_num = int(m.group(1))
                            line_content = m.group(2)
                            file_lines[line_num] = line_content
        except Exception as e:
            pass

if file_lines:
    max_line = max(file_lines.keys())
    with open("recovered_schema.json", "w", encoding="utf-8") as out:
        for i in range(1, max_line + 1):
            out.write(file_lines.get(i, f"// MISSING LINE {i}") + "\n")
    print(f"Recovered {len(file_lines)} lines out of {max_line}")
else:
    print("Nothing found.")
