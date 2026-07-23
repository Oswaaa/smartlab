import json

with open("src/main/resources/schemas/device-state-machine-model.json", "r", encoding="utf-8") as f:
    schema = json.load(f)

required_keys = [
    "x-executionLifecycleMainPath",
    "x-executionLifecycleBranches",
    "x-systemTransitions",
    "x-automaticTransitionStateSpaces",
    "x-commandTerminalStates",
    "x-actionCatalog",
    "x-standardInterfaces"
]

missing = []
for k in required_keys:
    if k not in schema:
        missing.append(k)

if missing:
    print("Missing keys:", missing)
else:
    print("All keys present!")
