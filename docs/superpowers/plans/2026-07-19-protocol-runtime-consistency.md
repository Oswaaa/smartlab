# Protocol 与运行时一致性 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 protocol/schema、后端校验与执行器、前端设计器保持一致，并修复工作流聚合、Adapter 类型校验和旧约束旁路。

**Architecture:** protocol 和各模型 schema 是格式与可配置项的唯一来源，统一由正式 JSON Schema 服务加载和校验；工作流、状态机通过显式 Java 语义和动作执行器运行。所有写入口先规范化再完整校验，不支持的能力在保存/编译阶段失败。

**Tech Stack:** Java 21、Spring Boot、Jackson、networknt JSON Schema、MyBatis-Plus、Vue 3、Element Plus、Node test runner。

## Global Constraints

- 不修改数据库结构。
- 不兼容已经废弃的约束字段、操作符和 Adapter 配置格式。
- 不回退或覆盖当前工作区中与本任务无关的用户改动。
- 每个行为修复先添加能复现问题的失败测试，再修改生产代码。

---

### Task 1: 修复工作流聚合执行语义

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowEngine.java`
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowRuntimeService.java`
- Test: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowEngineTest.java`

**Produces:** 同一流程实例中一个聚合节点只有一个可推进步骤；全部预期前序到达后只路由一次。

- [ ] 添加两分支仅一条到达时不得推进的失败测试。
- [ ] 添加全部分支完成只推进一次和重复到达不重复推进的失败测试。
- [ ] 让聚合就绪判断基于流程图预期前序与当前流程实例的完成步骤，而不是跳过未激活前序。
- [ ] 复用或幂等创建聚合步骤，并以状态保护后继只路由一次。
- [ ] 运行 `mvn -Dtest=WorkflowEngineTest test`。

### Task 2: 统一 schema 动作与执行器能力

**Files:**
- Modify: `Backend/src/main/resources/schemas/protocol-dict.json`
- Modify: `Backend/src/main/resources/schemas/workflow-model.json`
- Modify: `Backend/src/main/java/com/smartlab/engine/statemachine/StateMachineDictionary.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/statemachine/StateMachineEngine.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowDefinitionCompiler.java`
- Modify: `Frontend/src/views/task/WorkflowDesigner.vue`
- Test: `Backend/src/test/java/com/smartlab/engine/statemachine/StateMachineEngineTest.java`
- Test: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowDefinitionCompilerTest.java`
- Test: `Frontend/tests/state-machine-contract.test.mjs`

**Produces:** schema 中公开的动作都有执行器；未知动作在定义保存或执行时明确失败。

- [ ] 添加未知状态机动作不能静默跳过的失败测试。
- [ ] 添加工作流定义不能携带未实现动作的失败测试。
- [ ] 从 schema 删除当前未实现的 `ASSIGN`、`UPDATE_VARIABLE`、`EXECUTE_LOGIC`，保留真正可运行的动作。
- [ ] 修改状态机引擎对未知动作抛出明确异常。
- [ ] 删除设计器自动生成的无效 `EXECUTE_LOGIC`。
- [ ] 运行对应 Java 和 Node 测试。

### Task 3: 统一工作流 schema、编译器与前端元数据

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/global/schema/SchemaMetadataService.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowDefinitionCompiler.java`
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowService.java`
- Modify: `Frontend/src/views/task/WorkflowDesigner.vue`
- Test: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowDefinitionCompilerTest.java`
- Test: `Backend/src/test/java/com/smartlab/global/schema/SchemaMetadataServiceTest.java`
- Test: `Frontend/tests/device-model-editor-structure.test.mjs`

**Produces:** 接受集合由 schema metadata 提供；保存先做完整 schema 校验，再做图语义编译。

- [ ] 添加修改 schema 枚举后编译器随之变化的失败测试。
- [ ] 添加不满足 workflow schema 的保存请求被拒绝的测试。
- [ ] 删除编译器和设计器中的重复节点/功能类型集合及业务回退值。
- [ ] 保留引擎对四类节点的显式执行语义。
- [ ] 运行工作流编译、metadata 和前端结构测试。

### Task 4: 收紧 Adapter 格式与协议能力

**Files:**
- Modify: `Backend/src/main/resources/schemas/protocol-dict.json`
- Modify: `Backend/src/main/resources/samples/adapter-config.schema.json`
- Modify: `Backend/src/main/java/com/smartlab/adapter/AdapterManifestService.java`
- Modify: `Frontend/src/views/device/components/deviceModel/deviceModelConstants.js`
- Test: `Backend/src/test/java/com/smartlab/adapter/AdapterManifestServiceTest.java`

**Produces:** 当前仅接受 INI/JSON 和 MQTT，解析严格服从格式字段。

- [ ] 添加 `rawConfigFormat=INI` 但内容为 JSON 时必须失败的测试。
- [ ] 添加 YAML/XML 当前明确拒绝的测试。
- [ ] 删除首字符格式探测，按注册格式选择解析器。
- [ ] 将 schema 和前端可选能力收敛到真实实现。
- [ ] 运行 Adapter manifest 测试。

### Task 5: 校验命令、internal 注入和遥测数据类型

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/management/service/protocol/AdapterPayloadMapperService.java`
- Test: `Backend/src/test/java/com/smartlab/management/service/protocol/AdapterPayloadMapperServiceTest.java`

**Produces:** INTEGER、DOUBLE、BOOLEAN、STRING、JSON 按契约校验，错误值不发布、不写入。

- [ ] 分别添加公开参数、internal 注入和遥测类型错误的失败测试。
- [ ] 建立集中 `DataType` 校验/规范化方法，禁止隐式模糊转换。
- [ ] 在绑定路由中保留模型属性的类型元数据。
- [ ] 在发布和写入边界调用类型校验并返回包含字段路径的错误。
- [ ] 运行 payload mapper 测试。

### Task 6: 校验 MQTT Topic 与 payload 身份

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/adapter/MqttAdapterMessagingService.java`
- Test: `Backend/src/test/java/com/smartlab/adapter/MqttAdapterMessagingServiceTest.java`

**Produces:** telemetry/event 的 `adapterName`、`devicePoint` 必须与 Topic 一致。

- [ ] 添加 adapterName 不一致和 devicePoint 不一致时不调用 mapper 的失败测试。
- [ ] 在结构验证后、业务路由前执行身份一致性校验。
- [ ] 使用统一错误日志和拒绝路径，不修改设备状态。
- [ ] 运行 MQTT service 测试。

### Task 7: 使用正式 JSON Schema 验证 protocol 消息

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/global/protocol/ProtocolDictionaryService.java`
- Modify: `Backend/src/main/java/com/smartlab/global/util/JsonSchemaValidationService.java`
- Test: `Backend/src/test/java/com/smartlab/global/protocol/ProtocolDictionaryServiceTest.java`

**Produces:** `$ref`、数组 items、additionalProperties 等标准关键字由 networknt 验证。

- [ ] 添加包含嵌套数组和 `$ref` 的协议定义验证失败测试。
- [ ] 将 definition 包装或解析为可交给 networknt 的 schema，统一引用解析。
- [ ] 删除不完整的递归 `validateNode` 实现。
- [ ] 运行 protocol dictionary 测试。

### Task 8: 清理旧约束旁路并统一设备模型保存

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/resource/device/DeviceModelService.java`
- Modify: `Backend/src/main/java/com/smartlab/management/controller/resource/device/DeviceModelController.java`
- Modify: `Frontend/src/views/device/components/deviceModel/normalizers.js`
- Modify: `Frontend/src/views/dashboard/Dashboard.vue`
- Test: `Backend/src/test/java/com/smartlab/management/service/db/resource/device/DeviceModelServiceTest.java`
- Test: `Frontend/tests/operator-normalization.test.mjs`

**Produces:** 所有设备模型写入口使用当前字段与符号操作符，并经过同一完整校验管线。

- [ ] 添加旧字段和旧操作符被拒绝的失败测试。
- [ ] 将仍需保留的约束 CRUD 改为当前 DTO/字段并调用规范保存管线，或删除无调用的旧入口。
- [ ] 删除前端旧字段、旧操作符归一化别名和 Dashboard 旧结构。
- [ ] 让独立状态机写入口调用与主保存相同的 schema 校验。
- [ ] 运行设备模型和前端操作符测试。

### Task 9: 清理状态机命名推断与重复元数据入口

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/engine/statemachine/StateMachineEngine.java`
- Modify: `Backend/src/main/java/com/smartlab/global/schema/SchemaMetadataService.java`
- Modify: `Backend/src/main/java/com/smartlab/global/protocol/ProtocolDictionaryService.java`
- Test: `Backend/src/test/java/com/smartlab/engine/statemachine/StateMachineEngineTest.java`

**Produces:** payload 要求来自显式信号契约，不以 `_START` 后缀推断；前端元数据只有一个权威入口。

- [ ] 添加名称以 `_START` 结尾但契约不要求 payload 的反例测试。
- [ ] 按信号定义或动作输入契约校验 payload。
- [ ] 删除重复或未使用的 metadata 组装方法。
- [ ] 运行状态机和 schema metadata 测试。

### Task 10: 全量回归与坏代码复审

**Files:**
- Review only: all modified files in this plan

- [ ] 运行后端聚焦测试并记录准确结果。
- [ ] 运行 `mvn test`；如 Mockito agent 仍阻断，修复 Surefire 测试配置后重跑。
- [ ] 运行 `node --test Frontend/tests/*.test.mjs`。
- [ ] 运行 `npm run build`；定位并修复 Windows 下绝对 chunk name 问题后重跑。
- [ ] 使用 `git diff --check`、定向搜索旧字段和重复硬编码。
- [ ] 对照设计规范逐项复审，不声称未被验证的能力已经完成。
