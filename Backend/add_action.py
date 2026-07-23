import json
import codecs

file_path = "src/main/resources/schemas/protocol-dict.json"

with codecs.open(file_path, "r", "utf-8") as f:
    schema = json.load(f)

if "definitions" not in schema:
    schema["definitions"] = {}

schema["definitions"]["StateMachineAction"] = {
  "type": "object",
  "properties": {
    "actionName": { "type": "string" },
    "payload": {
      "type": "object",
      "properties": {
        "interfaceName": { "type": "string" },
        "signalName": { "type": "string" },
        "value": { "type": "string" }
      },
      "required": ["interfaceName", "signalName"]
    }
  },
  "required": ["actionName", "payload"]
}

with codecs.open(file_path, "w", "utf-8") as f:
    json.dump(schema, f, indent=2, ensure_ascii=False)
    
print("Added StateMachineAction to protocol-dict.json!")
