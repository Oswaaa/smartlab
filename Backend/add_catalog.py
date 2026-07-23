import json
import codecs

file_path = "src/main/resources/schemas/device-state-machine-model.json"

with codecs.open(file_path, "r", "utf-8") as f:
    schema = json.load(f)

schema["x-actionCatalog"] = [
    {
        "actionName": "SEND",
        "displayName": "发送信号",
        "description": "向外发送信号"
    }
]

with codecs.open(file_path, "w", "utf-8") as f:
    json.dump(schema, f, indent=2, ensure_ascii=False)
    
print("Added x-actionCatalog!")
