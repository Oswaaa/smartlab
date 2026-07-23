import json
import codecs

file_path = "src/main/resources/schemas/device-state-machine-model.json"

with codecs.open(file_path, "r", "utf-8") as f:
    schema = json.load(f)

# Update x-executionLifecycleBranches
branches = schema["x-executionLifecycleBranches"]
for b in branches:
    if "IDLE" not in b["sourceStateNames"]:
        b["sourceStateNames"].append("IDLE")

with codecs.open(file_path, "w", "utf-8") as f:
    json.dump(schema, f, indent=2, ensure_ascii=False)
    
print("Updated backend schema again!")
