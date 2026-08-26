# 自然语言生成工作流模型（Agent）设计

日期：2026-08-24（2026-08-26 按讨论更正；第一期代码已落地）  
状态：第一期已实现  
范围：实验室用户在产品内用自然语言生成流程模型草稿。第一期不引入 MCP，不发布，不绑设备实例，不启动任务。

## 1. 目标

把设计器里「选设备模型 → 配节点 → 连线 → 存草稿」变成产品内可完成的作者工作。

**模型文件的内容由大模型写**，后端不代替它编流程。后端负责：把提示词和工具清单发给大模型 API、按返回的工具调用去查库/校验/存草稿、用技能门闩拦住跳步。

产出必须是与 `schemas/工作流模型.json` 对齐的 `WorkflowModelDocument`，保存必须走现有 `WorkflowService.saveDraft`。不发明第二种流程语言。不把管理端 REST 原样暴露给大模型。

## 2. 概念（中文名）

本方案只立这些词，不要再叠插件、MCP、检索库。

| 中文 | 英文（代码里可沿用） | 含义 |
|---|---|---|
| 工具 | tool | 大模型能请求、由 Java 真正执行的白名单函数 |
| 技能 | skill | 一类复合任务的操作规程：步骤、用哪些工具、怎样算完成、什么不许做。与「操作规范」是同一件事 |
| 系统提示词 | system prompt | 固定角色和禁区；逐步怎么干写在技能里 |
| 用户提示词 | user prompt | 实验室人员输入的自然语言 |
| 代理循环 | agent loop | 后端与大模型 API 多轮对话：发请求 → 看返回 → 执行工具 → 把结果再发回去 |
| 模型文件 | document | 最终产物 `WorkflowModelDocument` |

关系：工具是「能做什么」；技能是「生成工作流这件事按什么顺序做」；代理循环是这些决策如何接到真实系统上。大模型负责读技能、决定调哪个工具、写出模型文件；它不能自己连数据库或执行 Java。

本系统与 Cursor 无关。技能就是上述操作规程，不是 IDE 功能。第一期不做 MCP：MCP 是给外部客户端暴露工具的协议，不是大模型和本后端之间的必经层。

## 3. 大模型与后端如何交互

不是写文件，后端也不轮询本地文件。就是后端调厂商的 HTTP API。

每一轮：

1. 后端 `POST` 到配置的 `base-url`。请求体带上：系统提示词、技能正文、用户提示词、至此的对话历史、**四个工具的名称与参数 Schema**。
2. 大模型在**同一次 HTTP 响应**里给出本轮输出，通常是：
   - **工具调用**：结构化字段（OpenAI 兼容的 `tool_calls`），例如「请执行 `list_device_catalog`，参数为 `{}`」；
   - **普通文本**：模型说的话（第一期不把纯文本当作已保存）。
3. 后端解析 JSON：若有 `tool_calls`，按 `name` 找对应 Java 方法，在**本进程内**调 `DeviceModelService` / `WorkflowService`（不再绕 HTTP 调自己的 REST）。
4. 把工具返回值作为下一条消息，再发下一轮 HTTP。大模型看到目录或校验结果后，再决定写模型文件或继续调工具。

技能的 markdown 存在工程里（`resources/agent/skills/generate-workflow.md`），由后端启动时读入，**作为请求体的一部分发给大模型**。大模型并不自己打开该文件。

因此「大模型读技能、调用工具、生成模型」说的是它的决策；「后端发 API、执行工具、把结果塞回去」说的是决策如何落地。内容仍是大模型写的。

## 4. 总体链路

```
用户自然语言（用户提示词）
  → POST /api/agent/workflow/generate（需登录）
  → 代理循环：请求体 = 系统提示词 + 技能正文 + 用户提示词 + 四个工具 Schema
  → 厂商大模型 HTTP API（须支持 tool / function calling）
  → 响应里的 tool_calls → Java 执行工具 → 结果进入下一轮请求
  → 技能门闩：未拉目录不准保存；validate 有 blocking 不准 save_draft
  → save_draft → 现有 saveDraft → FLOW_MODELS 且 status=DRAFT
  → 前端用 flowModelId 打开设计器审稿
```

## 5. 开工前要准备的（不只 API Key）

只准备 Key 不够。循环必须知道往哪发、用哪个模型、该模型会不会返回工具调用。

| 项 | 说明 |
|---|---|
| 厂商 | 须提供 OpenAI 兼容的 chat + tool calling（OpenAI、DeepSeek、通义兼容模式、Azure、本地网关等） |
| API Key | 环境变量 `SMARTLAB_AGENT_API_KEY`，禁止写入仓库 |
| Base URL | `SMARTLAB_AGENT_BASE_URL`，例如 `https://api.openai.com/v1` |
| 模型名 | `SMARTLAB_AGENT_MODEL`，**必须支持 tool calling**；只会聊天的模型跑不了本方案 |
| 网络 | 跑 Spring 的那台机器能访问该 Base URL |
| 设备数据 | 库中至少有若干已配好能力、参数、状态机接口的设备模型，否则目录为空，模型只能编造 ID |

未配齐 Key / URL / 模型名时，generate 接口返回明确错误，不调厂商。没有真实 Key 时，仍可用 mock 大模型把循环、工具、门闩先写完测通。

## 6. 包与文件

新建与 `adapter` / `engine` / `global` / `management` 同级的 `com.smartlab.agent`。

```
com.smartlab.agent
  api/WorkflowAgentController.java     POST /api/agent/workflow/generate
  loop/AgentLoop.java                  代理循环：发 API、分发工具、技能门闩、终止
  llm/LlmClient.java                   厂商 HTTP + tool_calls 解析
  llm/OpenAiCompatibleLlmClient.java   OpenAI 兼容实现
  llm/MockLlmClient.java               单测与无 Key 时的脚本化回复
  skill/Skill.java                     技能元数据（id、正文、门闩）
  skill/WorkflowGenerationSkill.java   第一期唯一技能
  tool/AgentTool.java                  名称、JSON Schema、执行
  tool/ListDeviceCatalogTool.java
  tool/GetDeviceModelTool.java
  tool/ValidateWorkflowTool.java
  tool/SaveDraftTool.java
  catalog/DeviceCatalogAssembler.java  设备模型 → 给大模型看的摘要
```

资源：

- `Backend/src/main/resources/agent/skills/generate-workflow.md` 技能正文
- `Backend/src/main/resources/agent/prompts/system.md` 系统提示词

配置：

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

## 7. 工具（白名单）

四个函数的 Schema **每一轮请求都带给大模型**。实现只调现有 service，禁止再包一层对外 REST，也禁止暴露 `publish`、删除、设备实例、点动、MQTT。

### 7.1 `list_device_catalog`

可选 `keyword`。返回数组，每项仅：

- `deviceModelId`、`deviceModelName`、`categoryName`
- `capabilities[]`：`capabilityName`、`displayName`、`isAbort`、`parameters[]`（name、displayName、dataType）
- `attributes[]`：`attributeName`、`displayName`、`dataType`、`unit`
- `ports[]`：`name`、`direction`、`dataType`

剔除：`adapterContract`、`parameterMapping`、`adapterCommandName`、`opState`/`cmdState`、`stateTransitions`、`intrinsicConstraint`、`componentsBom`。

### 7.2 `get_device_model`

参数：`deviceModelId`。在摘要之上增加 `stateMachineInterfaces[]`：仅 `name`、`direction`、`interfaceType`（供 NODE_TO_DEVICE / DEVICE_TO_NODE）。仍不要整台状态机和适配器契约。

### 7.3 `validate_workflow`

参数：完整 `document`。内部：`WorkflowService.validate`。返回规范化后的 document + `issues`。不写库。

### 7.4 `save_draft`

参数：完整 `document`。内部：`WorkflowService.saveDraft`。返回 `flowModelId`、`version`、`status=DRAFT`、`issues`。

第一期不做子流程目录工具。需要子流程时，技能写明用 DEV/FUNC 表达，或生成后再在设计器挂 `SUBFLOW_NODE`。

## 8. 技能：生成工作流草稿

id：`generate-workflow`。第一期代理循环**固定加载这一条**，不做「先分类再检索技能」。

### 8.1 规程（`generate-workflow.md`，由后端读入后放进请求）

1. 阅读用户实验描述，列出需要的能力关键词。
2. 调用 `list_device_catalog`；对不准再 `get_device_model`。`deviceModelId` 必须来自工具返回，禁止编造。
3. 写出完整模型文件：`metadata`、`nodes`、`interfaceConnections`、`portConnections`。必须有且仅有一个 START、一个 END。设备步骤用 `DEV_NODE`（真实 id + capability 名与参数）。不要写 `_system` / `_systemKey` / 系统 lifecycle，由规范化器补。
4. 每个 DEV_NODE 补一对 `NODE_TO_DEVICE` / `DEVICE_TO_NODE`，接口名来自 `stateMachineInterfaces`。节点之间用 `NODE_TO_NODE` 串联 START→…→END。
5. 调用 `validate_workflow`。有 blocking 则改 document 再校验，最多 3 次。
6. 无 blocking 后调用 `save_draft`。不要发布。

### 8.2 门闩（Java 强制，不只靠提示词）

| 条件 | 行为 |
|---|---|
| 尚未成功执行 `list_device_catalog` | 拒绝 `save_draft` |
| 最近一次 `validate_workflow` 仍有 blocking | 拒绝 `save_draft` |
| `save_draft` 已成功 | 结束循环，把草稿结果返回给前端 |
| 轮次 ≥ `max-rounds` 仍未保存 | 失败，附带最后一次 document/issues（若有） |
| 未注册的工具名 | 拒绝，并把错误作为 tool 结果写回对话 |

校验不写库，失败的中间稿只留在本轮对话里。

## 9. 代理循环（实现要点）

1. `messages = [系统提示词 + 技能正文, 用户提示词]`
2. 每轮 `LlmClient.complete(messages, 四个工具 Schema)`
3. 若响应含 `tool_calls`：过门闩 → 执行 → `messages` 追加工具结果
4. 若仅文本：可尝试解析 JSON 当作「请先 validate」的候选，**不得**视为已保存
5. **唯一成功出口**：`save_draft` 的返回值

温度偏低（约 0.2）。不强制整段 JSON mode（中间轮是工具调用）。最终入库以 `save_draft` 参数里的 document 为准。

## 10. HTTP 与前端

`POST /api/agent/workflow/generate`  
请求：`{ "prompt": "..." }`  
成功：与现有草稿准备结果相同的字段（definition、version、status、issues、flowModelId），外加 `trace`（调用了哪些工具，不含提示词全文与 Key）。  
失败：提示词空、未配齐厂商三项、超轮次、门闩拦住保存。

鉴权与现有 JWT 相同。

前端第一期：流程设计页「用自然语言生成」→ 本接口 → 用 `flowModelId` 走现有 `workflowApi.detail` 打开设计器。生成过程不逐步画布。第二期可用 SSE 展示 trace。

## 11. 系统提示词要点

- 你是 SmartLab 流程作者，不是设备控制器。
- 只使用本次请求提供的工具。
- 不要输出 MQTT、适配器命令、设备实例 ID。
- 目录里没有的设备不要用。
- 产出必须是模型文件字段；散文只能放在 `metadata.description`。
- 保存的是草稿，由人在设计器确认。

## 12. 修改边界

新代码几乎都在 `com.smartlab.agent` 以及 `resources/agent/`、`application.yml` 的 `smartlab.agent` 段。`@SpringBootApplication` 已扫描 `com.smartlab`，新包会自动成为 Bean，不必改启动类。

**调用、但不改行为的现有后端：**

- `management`：`DeviceModelService.list/getById`、`DeviceCategoryService`（类别名）、`WorkflowService.validate`、`WorkflowService.saveDraft`。工具在进程内调这些方法，不改编译、拆表、版本规则。
- `engine`：不直接依赖。保存/校验仍由 `WorkflowService` 进现有规范化器和编译器。
- `adapter` / `global.contract`：不改。JWT 下 `/api/agent/**` 已走 `anyRequest().authenticated()`，不必为了生成接口改 `SecurityConfig`。

**允许的旁路改动（尽量小）：**

- `application.yml` 增加 agent 配置（Key/URL/模型名走环境变量）。
- 单测放在 `src/test/java/com/smartlab/agent/`。
- 前端「用自然语言生成」入口属于后续 UI，不是本包；它只多调一个 POST，设计器加载草稿仍用现有 detail。

**不要改：** 工作流 schema、规范化器模板、设备能力模型全文、`publish`、任务/实例/MQTT。若现有 `validate` 对草稿偏严，第一期沿用（与人手点校验一致），不要为 agent 另做一套编译。

## 13. 明确不做

- MCP；「先分类再找技能」的通用路由器
- 大模型直接 publish、建任务、写 resourceMap、点动设备
- 把完整 `DeviceModels` JSON 塞进提示词
- 在 `engine` 里调大模型
- 用自然语言当运行时指令绕过编译器
- 把 API Key 写入 git

## 14. 实现顺序

1. 摘要 DTO + `DeviceCatalogAssembler` + 单测（确认剥掉 adapter/状态机正文）
2. 四个工具接到现有查询/`validate`/`saveDraft`；用 `MockLlmClient` 测门闩（无 Key 可做）
3. `OpenAiCompatibleLlmClient` + 代理循环 + 资源配置
4. `WorkflowAgentController` + 前端入口
5. 配齐 Key / URL / 模型名后，用库中真实设备做：自然语言 → 草稿 ID → 设计器打开

## 15. 验收

- mock 大模型未拉目录就 `save_draft`，循环拒绝。
- 目录 JSON 不含 `adapterContract`、`cmdState`。
- 合法生成结果可被现有详情/设计器加载，且 `status=DRAFT`。
- 生成保存与手工保存共用 `WorkflowService.saveDraft`，编译行为一致。
- 未配置 Key/URL/模型名时接口失败信息明确，不误打外网。
