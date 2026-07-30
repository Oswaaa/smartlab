---
name: codex-multi-agent-v1
description: Use when Windows Codex Desktop forces MultiAgent V2 through GPT-5.6 model metadata, spawn_agent omits model or reasoning_effort, or a full local model_catalog_json override is needed.
---

# Codex Multi-Agent V1

## Overview

Create a host-level, full-catalog override that selects MultiAgent V1 for the GPT-5.6 family. Preserve every catalog field and make only the requested metadata changes.

## Required workflow

1. Resolve the active Codex home: explicit path, then `CODEX_HOME`, then `%USERPROFILE%\.codex`.
2. Inspect `config.toml`, its top-level `model_catalog_json`, and the selected source catalog before writing.
3. Run `scripts/Enable-CodexMultiAgentV1.ps1`. Use `-DryRun` first when the environment is unfamiliar.
4. Verify the JSON diff and effective catalog with `codex debug models`.
5. Fully restart Codex Desktop and create a new task. Existing tasks retain their original runtime/schema.

```powershell
pwsh -File scripts/Enable-CodexMultiAgentV1.ps1 -DryRun
pwsh -File scripts/Enable-CodexMultiAgentV1.ps1
codex debug models
```

## Change contract

| Surface | Required result |
|---|---|
| Source | Current configured full catalog, otherwise `models_cache.json` |
| Target | Host-level `$CODEX_HOME\models-v1.json` |
| Models | Sol and Terra must exist; Luna is changed only when present |
| Catalog diff | Only each present target's `multi_agent_version` becomes `"v1"` |
| `config.toml` diff | Only top-level `model_catalog_json` is inserted or replaced |

Do not edit `models_cache.json`, construct minimal model records, add feature flags, change the selected model, or place the active override only in a project `.codex` directory. Windows Desktop has had project-local catalog loading bugs; keep the active catalog and pointer in the host Codex home. A project may contain this skill without containing the active catalog.

## Stop conditions

Stop before writing if JSON parsing fails, `models` is missing, a target slug is duplicated, Sol or Terra is absent, the config contains duplicate top-level `model_catalog_json` keys, or a source file changes during the operation. Preserve backups and report their paths if a later write fails.

## Verification

Require all of the following:

- target and source have identical root fields, model count, order, and non-target models;
- each target model is otherwise deeply equal to its source entry;
- Sol and Terra are `v1`; Luna is `v1` when present;
- `config.toml` has exactly one top-level `model_catalog_json` pointing to the target;
- `codex debug models` parses the override and reports the expected versions.

After restart, inspect the new task's `spawn_agent` schema. Success means `model` and `reasoning_effort` are exposed; test them with a harmless child task.

## Common mistakes

- A feature toggle alone does not override model-selected V2 metadata.
- A small handcrafted catalog replaces the full catalog and loses required model fields.
- Editing `models_cache.json` is temporary because Codex refreshes it.
- Validating in the current task does not prove the startup-only override was loaded.
