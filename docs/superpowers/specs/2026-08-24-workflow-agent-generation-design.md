# 自然语言生成工作流模型（Agent）设计

日期：2026-08-24  
状态：方案待实现  
范围：实验室用户在产品内用自然语言生成流程模型草稿；不引入 MCP；不发布、不绑实例、不启动任务。

## 1. 目标

把设计器里「选设备模型 → 配节点 → 连线 → 存草稿」变成后端可完成的作者工作。大模型只产出与 `schemas/工作流模型.json` 对齐的 `WorkflowModelDocument`，然后走现有 `WorkflowService.saveDraft`。

不发明第二种流程语言。不把管理端 REST 原样暴露给模型。第一期只有一条 skill：**生成工作流草稿**。

## 2. 角色拆分

| 名称 | 是什么 | 不是什么 |
|---|---|---|
| Tool | 白名单函数，由 Java 在进程内执行 | 现有 `/api/device/**`、`/api/workflow/publish` |
| Skill | 这一条复合任务的操作规程（步骤、准入、完成条件） | 函数本身；也不是 Cursor 的 `SKILL.md` 产品功能 |
| Agent 循环 | 调 LLM ↔ 执行 tool ↔ 强制 skill 门闩 | 工作流引擎、MCP 服务器 |
| 系统提示词 | 角色、禁止项、必须输出模型文件 | 逐步操作细节（细节在 skill 里） |

实验室用户只跟 SmartLab 前端说话。Cursor / MCP 不在本方案内；若以后要让外部代理用同一套 tool，再加 MCP 并与下列函数对齐。

## 3. 总体链路

```
用户自然语言
  → POST /api/agent/workflow/generate
  → AgentLoop（挂上 4 个 tool + 生成 skill + 系统提示词）
  → 厂商 LLM（function calling）
  → Java 执行 tool（DeviceModelService / WorkflowService，不经 HTTP）
  → skill 门闩：未拉目录不准保存；validate 未通过不准 save_draft
  → saveDraft → FLOW_MODELS DRAFT
  → 前端打开设计器审稿
```

LLM 从不直接调 Java 或 REST。它只返回 tool 调用或最终「可以保存」的意图；执行与拦截都在 `com.smartlab.agent`。

## 4. 包与文件

新建与 `adapter` / `engine` / `global` / `management` 同级的 `com.smartlab.agent`。

```
com.smartlab.agent
  api/WorkflowAgentController.java     POST /api/agent/workflow/generate
  loop/AgentLoop.java                  对话轮次、tool 分发、终止条件
  llm/LlmClient.java                   厂商 function-calling 适配
  llm/OpenAiCompatibleLlmClient.java   可换成其它实现
  skill/Skill.java                     skill 元数据（id、何时用、门闩）
  skill/WorkflowGenerationSkill.java   唯一内置 skill
  tool/AgentTool.java                  名称、JSON Schema、执行
  tool/ListDeviceCatalogTool.java
  tool/GetDeviceModelTool.java
  tool/ValidateWorkflowTool.java
  tool/SaveDraftTool.java
  catalog/DeviceCatalogAssembler.java  设备模型 → 摘要 DTO
```

资源文件（给模型读的规程正文，不是给 Cursor 用的）：

- `Backend/src/main/resources/agent/skills/generate-workflow.md`
- `Backend/src/main/resources/agent/prompts/system.md`

配置（`application.yml`）：

```yaml
smartlab:
  agent:
    enabled: true
    provider: openai-compatible
    base-url: ${SMARTLAB_AGENT_BASE_URL:}
    api-key: ${SMARTLAB_AGENT_API_KEY:}
    model: ${SMARTLAB_AGENT_MODEL:}
    max-rounds: 12
    validate-retries: 3
```

密钥只走环境变量。未配置 api-key 时 generate 接口返回明确错误，不调用厂商。

## 5. Tool（白名单）

四个函数，JSON Schema 随请求传给 LLM。实现一律调现有 service，禁止再包一层对外 REST。

### 5.1 `list_device_catalog`

无参数（或可选 `keyword`）。返回数组，每项仅：

- `deviceModelId`、`deviceModelName`、`categoryName`
- `capabilities[]`：`capabilityName`、`displayName`、`isAbort`、`parameters[]`（name、displayName、dataType）
- `attributes[]`：`attributeName`、`displayName`、`dataType`、`unit`
- `ports[]`：`name`、`direction`、`dataType`（列表里带端口，避免为每个设备再打一轮）

剔除：`adapterContract`、`parameterMapping`、`adapterCommandName`、`opState`/`cmdState`、`stateTransitions`、`intrinsicConstraint`、`componentsBom`。

### 5.2 `get_device_model`

参数：`deviceModelId`。在摘要之上增加：

- `stateMachineInterfaces[]`：仅 `name`、`direction`、`interfaceType`（供 NODE_TO_DEVICE / DEVICE_TO_NODE，不要整台状态机）

仍不返回适配器契约和状态机正文。

### 5.3 `validate_workflow`

参数：`document`（`WorkflowModelDocument`）。内部：`WorkflowService.validate`（已有，canonicalize + compile DRAFT）。返回规范化后的 document + `issues`（blocking 与否）。不写库。

### 5.4 `save_draft`

参数：`document`。内部：`WorkflowService.saveDraft`。返回 `flowModelId`、`version`、`status=DRAFT`、`issues`。

**没有** `publish`、`delete`、设备实例、`/control`、MQTT。绑实例与启动仍是任务列表的事后步骤。

第一期不做 `list_subflow_catalog`。自然语言需要子流程时，skill 写明：用 FUNC/DEV 节点表达，或提示用户在设计器里改挂 `SUBFLOW_NODE`。第二期再加 ACTIVE 流程摘要 `{flowModelId, flowModelName, description}`。

## 6. Skill：生成工作流草稿

id：`generate-workflow`。第一期 AgentLoop **固定加载这一条**，不做「先分类再找 skill」。

### 6.1 规程正文（写入 `generate-workflow.md`）

1. 阅读用户实验描述，列出需要的能力关键词（加热、搅拌、测温等）。
2. 调用 `list_device_catalog`；对不准的模型再 `get_device_model`。`deviceModelId` 必须来自返回值，禁止编造。
3. 写出完整 `WorkflowModelDocument`：`metadata`、`nodes`、`interfaceConnections`、`portConnections`。必须有且仅有一个 START、一个 END。设备步骤用 `DEV_NODE`（真实 `deviceModelId` + `capability` 名与参数）。不要写 `_system` / `_systemKey` / `lifecycle` 系统段，由规范化器按节点类型补。
4. 每个 DEV_NODE 补一对 `NODE_TO_DEVICE` / `DEVICE_TO_NODE`，接口名来自 `get_device_model` 的 `stateMachineInterfaces`。节点之间用 `NODE_TO_NODE` 把 START→设备→…→END 串起来。
5. 调用 `validate_workflow`。有 blocking issue 则改 document 再校验，最多 3 次。
6. 无 blocking 后调用 `save_draft`。不要发布。

### 6.2 代码门闩（比提示词更硬）

AgentLoop 为本次会话维护状态：

| 门闩 | 规则 |
|---|---|
| 未成功执行过 `list_device_catalog` | 拒绝 `save_draft` |
| `validate_workflow` 最近一次仍有 blocking | 拒绝 `save_draft` |
| `save_draft` 已成功 | 结束循环，把结果返回 HTTP |
| 轮次 ≥ `max-rounds` 仍未保存 | 失败返回，带上最后 document 与 issues（若有） |
| 模型调用未注册的 tool 名 | 拒绝并回写错误 |

`validate_workflow` 不写库，因此「先校验再保存」不会留下半份垃圾草稿；校验失败的中间稿只留在对话里。

## 7. Agent 循环

伪流程：

1. messages = [system.md + generate-workflow.md, user prompt]
2. tools = 上述四个 schema
3. 循环直到保存成功或超轮次或 LLM 无 tool 且无合法 document：
   - 调 `LlmClient.complete(messages, tools)`
   - 若 tool_calls：按门闩检查 → 执行 → `messages` 追加 tool 结果
   - 若纯文本：尝试解析 JSON 为 document；有则当作「请先 validate」的候选，**不要**把纯文本当已保存
4. 唯一成功出口：`save_draft` 的返回值

厂商差异关在 `LlmClient`。OpenAI 兼容协议（含多数国内网关）即可。温度偏低（0.2 左右），`response` 不强制 JSON mode（因为中间轮是 tool），最终保存以 tool 参数里的 document 为准。

## 8. HTTP 与前端

`POST /api/agent/workflow/generate`

请求：`{ "prompt": "..." }`  
响应成功：与草稿保存一致的准备结果（`definition`、`version`、`status`、`issues`、`flowModelId`），外加 `trace`：本轮调用了哪些 tool（便于调试，不含模型全文）。  
失败：提示词空、未配密钥、超轮次、门闩拦住保存等。

鉴权与现有 JWT 相同。不新增匿名入口。

前端（第一期最小）：流程设计页或任务相关入口一个「用自然语言生成」对话框 → 调 generate → 用返回的 `flowModelId` 走现有 `workflowApi.detail` 打开设计器。不在生成过程中画布逐步渲染。第二期可用 SSE 显示「正在拉取设备目录…」等 trace。

## 9. 系统提示词要点

- 你是 SmartLab 流程作者，不是设备控制器。
- 只使用提供的 tool；不要输出 MQTT、适配器命令、实例 ID。
- 目录里没有的设备不要用。
- 产出必须是工作流模型文件字段，不是散文流程说明（散文只能出现在 `metadata.description`）。
- 存下的是草稿，等人在设计器确认。

## 10. 明确不做

- MCP、Cursor Skill、通用「先分类再找 skill」路由器
- LLM 直接 `publish` / 创建任务 / `resourceMap` / 点动设备
- 把完整 `DeviceModels` JSON 塞进提示词
- 在 engine 里调 LLM
- 用自然语言当运行时指令绕过编译器

## 11. 实现顺序

1. Catalog DTO + `DeviceCatalogAssembler` + 单测（确认剥掉 adapter/状态机正文）
2. 四个 tool 包现有 `validate` / `saveDraft` / 设备查询 + 门闩单测（mock LLM）
3. `LlmClient` + `AgentLoop` + 资源配置
4. `WorkflowAgentController` + 前端入口
5. 用 1～2 个真实设备模型做一次端到端：自然语言 → 草稿 ID → 设计器打开

## 12. 验收

- 未拉目录时 mock 模型直接 `save_draft`，循环拒绝。
- catalog JSON 不含 `adapterContract`、`cmdState`。
- 合法生成结果可被现有 `WorkflowController` 详情/设计器加载，`status=DRAFT`。
- 生成路径与手工保存路径共用 `WorkflowService.saveDraft`，编译器行为一致。
