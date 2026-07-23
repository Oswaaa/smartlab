import json
import codecs

file_path = "src/main/resources/schemas/device-state-machine-model.json"

with codecs.open(file_path, "r", "utf-8") as f:
    schema = json.load(f)

# 1. Remove CMD_ABORT rules
new_transitions = []
for t in schema.get("x-systemTransitions", []):
    is_abort = False
    for a in t.get("actions", []):
        if a.get("payload", {}).get("signalName") == "CMD_ABORT":
            is_abort = True
            break
    if not is_abort:
        new_transitions.append(t)
schema["x-systemTransitions"] = new_transitions

# 2. Add TERMINATION branch
branches = schema.get("x-executionLifecycleBranches", [])
has_termination = any(b.get("kind") == "TERMINATION" for b in branches)
if not has_termination:
    branches.append({
        "kind": "TERMINATION",
        "sourceStateNames": ["SENT", "RECEIVED", "RUNNING"],
        "targetStateName": "ABORTED"
    })
schema["x-executionLifecycleBranches"] = branches

# 3. Restore definitions
schema["definitions"] = {
  "StateMachineCommandState": {
    "type": "string",
    "enum": ["IDLE", "SENT", "RECEIVED", "RUNNING", "COMPLETED", "FAILED", "ABORTED"]
  },
  "StateMachineInterfaceType": {
    "type": "string",
    "enum": ["ADAPTER", "WORKFLOW", "CONTROL", "CONSTRAINT", "STAT"]
  }
}

with codecs.open(file_path, "w", "utf-8") as f:
    json.dump(schema, f, indent=2, ensure_ascii=False)

print("Successfully updated device-state-machine-model.json")
