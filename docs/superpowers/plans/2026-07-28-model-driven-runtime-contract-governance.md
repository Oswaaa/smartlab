# SmartLab模型驱动运行时契约治理Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不修改数据库结构和六份定稿模型文件的前提下，将SmartLab当前已经打通的设备模型数据驱动链路收口为“系统契约单一来源、模型写入统一完整校验、实例化后不可变、前端静态表单不重复声明系统骨架”的可生产实现

**Architecture:** 六份JSON文件继续作为设计规范、存储格式和审查契约，不参与运行时动态解析，也不用于前端动态生成表单。固定系统语义由后端类型化契约集中实现；状态机、工作流和约束引擎是通用执行器，运行时读取数据库中的模型实例并解释执行；前端保留人工设计的静态业务表单，仅通过后端元数据接口取得固定选项和系统模板

**Tech Stack:** Java 21、Spring Boot、MyBatis-Plus、Jackson、JUnit 5、Mockito、Vue 3、Element Plus、Vue Flow、Axios、Node.js内置测试运行器、Vite、PostgreSQL JSONB、MQTT

## Global Constraints

- 项目根目录固定为`D:\SmartLab2.0`
- 不修改数据库表结构，不新增字段，不创建数据库迁移
- 不修改`Backend/src/main/resources/schemas`中的六份定稿文件，发现不一致时先报告，不得自行改动
- 六份文件为`设备能力模型.json`、`设备状态机模型.json`、`工作流模型.json`、`约束模型.json`、`协议规范.json`、`系统执行规范.json`
- 不在运行时读取或解释JSON Schema，不增加JSON Schema动态表单渲染
- 前端表单必须保持人工设计的静态业务交互，后端元数据只能提供固定选项、标准接口、生命周期和系统节点模板
- 设备、工作流和约束的具体业务规则必须来自数据库模型，禁止增加任何具体设备名称、能力名、事件名或状态名分支
- 系统统一的状态机算法、协议格式、动作执行器、标准接口和生命周期属于平台语义，可以固化在后端契约模块
- 不兼容已经废弃且前端未调用的旧写入接口，优先删除旁路写入口
- 不改变现有`management`、`global`、`engine`、`adapter`等顶层包结构
- 不使用模拟数据、演示分支、强制类型转换或绕过校验的hack
- 不覆盖、不回退用户现有改动，不运行`git reset --hard`或`git checkout --`
- 修改JSON描述时遵守用户格式偏好：简单对象尽量一行、短枚举一行、中文与英文标识之间不额外加空格、描述末尾不加句号；本计划不授权修改六份JSON
- 后端运行时最终链路必须保持：任务/人工/约束信号→状态机→模型能力解析→Adapter命令映射→MQTT→Adapter→事件/遥测→状态机/设备孪生/数据库

---

# 一、新对话必须继承的上下文记忆

## 1.用户最终确认的设计思想

用户已经确认以下原则，新对话不得重新争论或推翻：

1. 先使用严谨的模型JSON Schema明确系统底层运行逻辑、数据边界和模型保存格式
2. 实际开发与运行时，Schema退居幕后，只作为设计规范、存储规范和验证契约
3. 前端不能把Schema作为动态页面模板，必须由开发者依据模型含义人工设计静态表单
4. 生命周期、标准接口、标准信号、系统动作等固定规则可以在后端代码中集中固化
5. 固化必须位于统一契约模块，不能散落在控制器、引擎条件分支和前端工具文件中
6. 设备模型的核心价值是运行时数据驱动：具体设备的属性、能力、参数映射、Adapter命令、事件、CMD/OP转移和onEntry动作来自数据库模型
7. 状态机引擎只实现通用状态匹配与动作分发算法，不能包含机械臂、加热器、电磁阀等具体设备业务判断
8. 工作流模型中的DEV_NODE只绑定`deviceModelId`，不绑定具体设备实例
9. 创建任务时才按照节点出现路径将DEV_NODE绑定到具体设备实例，绑定写入`TASK.RESOURCE_MAP`
10. 同一个设备实例可以绑定给同一任务中的多个不同节点；并发占用由设备状态机CMD是否为`IDLE`统一仲裁，后来的调用等待设备回到`IDLE`
11. 子流程资源绑定按照节点出现路径展开，例如`root/openLid/armUp`表示主流程根节点下的`openLid`子流程中的`armUp`设备节点，不是节点显示名称拼接出来的任意字符串
12. 约束规则使用表达式模板、`@变量`引用、结构化bindings和结构化violationActions，禁止用户手写JSON
13. 约束终止设备时通过状态机`Interface_constraint_in`发送`CONSTRAINT_ABORT`；约束终止任务时调用工作流任务控制逻辑，由工作流引擎终止节点并向正在执行的设备状态机发送终止信号
14. 数据库已经确定，不允许通过新增表或字段解决本轮问题

## 2.六份文件的职责边界

- `设备能力模型.json`：设备属性、能力、参数映射、Adapter契约、端口、内置约束
- `设备状态机模型.json`：设备状态机接口、CMD生命周期、OP状态空间、Adapter事件驱动转移和状态动作
- `工作流模型.json`：节点、节点接口、触发器、动作、端口、接口连接、端口连接
- `约束模型.json`：表达式、bindings、持续窗口、违规动作
- `协议规范.json`：跨引擎信号、payload、MQTT主题和Adapter报文格式
- `系统执行规范.json`：三个引擎共同遵守的固定接口、系统状态变化和交互过程

运行时不得读取这些文件决定业务行为。数据库中的模型实例才是运行时数据；六份文件用于设计审查、代码实现和一致性测试。

## 3.当前已经实现且不得重做的部分

### 3.1设备状态机数据驱动

- `StateMachineEngine`通过设备实例查询其`DeviceModels`
- `StateMachineModels.Definition`从`DeviceModels.stateTransitions`解析转移
- Adapter事件按照接口名、事件名和当前状态匹配CMD/OP转移
- CMD/OP初始状态来自模型
- 状态和分区的`onEntry`动作来自模型
- 能力执行通过`capabilityName`查找模型能力，再解析`adapterCommandName`
- 终止能力通过模型中的`isAbort=true`查找，不写死终止能力名称
- 参数通过能力`parameterMapping`映射到Adapter命令参数
- Adapter遥测通过模型`attributesMapping`映射设备属性并写入孪生和数据记录
- 命令通过真实MQTT发布，Adapter事件通过真实链路回到状态机

关键文件：

- `Backend/src/main/java/com/smartlab/engine/statemachine/StateMachineEngine.java`
- `Backend/src/main/java/com/smartlab/engine/statemachine/StateMachineModels.java`
- `Backend/src/main/java/com/smartlab/engine/statemachine/action/SendStateMachineActionExecutor.java`
- `Backend/src/main/java/com/smartlab/management/service/protocol/AdapterPayloadMapperService.java`
- `Backend/src/main/java/com/smartlab/adapter/MqttAdapterMessagingService.java`

### 3.2工作流运行时

- 工作流定义保存到`FLOW_MODELS`和`FLOW_NODE`相关JSONB字段
- `WorkflowService.compileDefinition`从数据库重建并编译工作流
- `WorkflowEngine`读取节点接口、bindingTriggers、actions、ports、capability和连接执行
- DEV_NODE通过任务`RESOURCE_MAP`解析具体设备实例
- 子流程按出现路径展开资源绑定
- 节点动作已有注册器和执行器，不允许通过节点类型写临时业务分支

关键文件：

- `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowDefinitionCompiler.java`
- `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowEngine.java`
- `Backend/src/main/java/com/smartlab/engine/workflow/DefaultWorkflowExecutionOperations.java`
- `Backend/src/main/java/com/smartlab/engine/workflow/action`
- `Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowService.java`
- `Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowRuntimeService.java`

### 3.3约束运行时

- `CONSTRAINT_RULE`已经使用`expression`、`bindings`、`window_seconds`、`violation_actions`
- 约束引擎使用结构化绑定取得设备属性、CMD/OP状态、任务状态和节点变量
- 设备OP状态通过状态机状态输出和设备孪生读取
- 系统违规动作与设备能力违规动作均有真实执行路径
- 前端约束编辑器使用表达式模板、`@变量`、表单化绑定和表单化违规动作

关键文件：

- `Backend/src/main/java/com/smartlab/engine/constraint/ConstraintEngine.java`
- `Backend/src/main/java/com/smartlab/engine/constraint/ConstraintExpressionEvaluator.java`
- `Backend/src/main/java/com/smartlab/management/service/db/constraint/ConstraintRuleService.java`
- `Frontend/src/components/constraint/ConstraintRuleEditor.vue`

### 3.4前端静态业务表单

- 设备模型编辑器是人工设计的Element Plus表单，不是Schema渲染器
- 工作流设计器使用Vue Flow画布和人工设计的节点配置抽屉
- 约束编辑器是公式模板和结构化表单
- 设备模型页面会调用`/api/schema-metadata/frontend`取得固定选项，但页面结构仍是静态代码

关键文件：

- `Frontend/src/views/device/components/deviceModel/DeviceModelEditorDrawer.vue`
- `Frontend/src/views/task/WorkflowDesigner.vue`
- `Frontend/src/components/task/workflow/WorkflowNodeInspector.vue`
- `Frontend/src/components/constraint/ConstraintRuleEditor.vue`

## 4.当前代码检查出的未闭合问题

### 4.1系统CMD转移存在两个权威来源

`SystemExecutionContract.stateMachineSystemTransitions()`已经声明九条固定转移，但`StateMachineEngine.systemCommandTransition()`又使用`if`和固定集合重新实现：

- `IDLE→SENT`
- `SENT/RUNNING→ABORTING`
- `CMD_START`
- `CMD_ABORT`

这不是具体设备业务硬编码，但会导致契约与引擎漂移。目标是引擎直接匹配`SystemExecutionContract`。

### 4.2Schema与Java契约没有自动一致性检查

运行时不解析Schema是正确的，但目前没有测试检查：

- 协议枚举是否与`ProtocolContract`一致
- 标准接口是否与`SystemExecutionContract`一致
- 固定CMD系统转移是否一致
-生命周期、操作符和状态枚举是否一致

目标是仅在测试阶段读取六份文件进行契约一致性检查，不把Schema解析带入生产运行时。

### 4.3模型写入没有唯一完整校验门禁

`DeviceModelService.validateFinalDeviceModel()`不是所有模型写入的统一入口。现有旁路包括：

- `PUT /api/device/model/{modelId}/adapter-contract`
- `POST /api/device/model/state-machine/save`

当前前端没有调用这两个写接口。目标是删除旁路写入口，所有设备模型修改只允许通过`POST /api/device/model/save`。

### 4.4实例化后的模型存在旁路修改风险

主保存入口会在已有设备实例时拒绝修改模型，但Adapter契约和状态机独立写入口没有同样保护。状态机运行时会重新读取数据库，因此旁路修改可能改变运行中设备行为。

目标是在不改数据库的情况下采用以下规则：

- 尚无任何设备实例引用的模型视为可编辑模型
- 一旦有设备实例引用，该`modelId`即成为不可变运行版本
- 修改设计必须新建一条设备模型记录，新的`modelId`即新版本
- 不增加`version`或`status`字段
- 创建设备实例前必须对目标模型执行完整运行就绪校验

### 4.5工作流系统骨架在前后端重复

以下定义目前至少存在两份：

- 节点生命周期
- START/END/BRANCH/AGGREGATE接口
- DEV_NODE状态接口
- 系统触发器
- 系统EMIT动作

后端编译器和前端`workflowNodeDefinition.js`各维护一套。目标是后端建立唯一`WorkflowNodeSystemContract`，编译器和元数据接口都使用它；前端只复制后端返回的模板形成新节点，表单仍是静态表单。

## 5.当前工作区状态警告

生成本文件时工作区不是干净状态。新对话开始后必须首先执行`git status --short`并保护这些改动，禁止直接回退。

当前已修改文件包括：

```text
Backend/src/main/java/com/smartlab/engine/constraint/ConstraintExpressionEvaluator.java
Backend/src/main/java/com/smartlab/engine/workflow/DefaultWorkflowExecutionOperations.java
Backend/src/main/java/com/smartlab/engine/workflow/WorkflowEngine.java
Backend/src/main/java/com/smartlab/engine/workflow/WorkflowExecutionOperations.java
Backend/src/main/java/com/smartlab/engine/workflow/action/UpdateWorkflowActionExecutor.java
Backend/src/main/java/com/smartlab/engine/workflow/action/WorkflowValueResolver.java
Backend/src/main/java/com/smartlab/global/contract/SystemExecutionContract.java
Backend/src/main/java/com/smartlab/global/schema/SchemaMetadataService.java
Backend/src/main/java/com/smartlab/management/service/db/workflow/WorkflowRuntimeService.java
Frontend/src/views/device/components/deviceModel/DeviceModelEditorDrawer.vue
Frontend/src/views/device/components/deviceModel/deviceModelConstants.js
Frontend/src/views/device/components/deviceModel/normalizers.js
Frontend/tests/device-model-lifecycle.test.mjs
Frontend/tests/state-machine-contract.test.mjs
```

当前未跟踪内容包括`.agents`、若干工作流后端测试、`Backend/src/test/java/com/smartlab/global/contract`、`Frontend/old_drawer_reference.txt`和`deviceModelLifecycle.js`。这些内容都可能是用户或上一轮实现成果，不得删除。

---

# 二、目标架构

```text
六份定稿JSON规范
    │
    ├──仅供设计审查与测试期一致性校验
    │
    ▼
后端固定契约
    ├──ProtocolContract
    ├──SystemExecutionContract
    └──WorkflowNodeSystemContract
             │
             ├──SchemaMetadataService提供前端固定元数据
             ├──WorkflowDefinitionCompiler校验系统节点骨架
             └──StateMachineEngine匹配系统CMD转移

前端静态表单
    ├──设备模型表单
    ├──工作流画布与节点抽屉
    └──约束公式与绑定表单
             │
             ▼
统一保存API
             │
             ▼
后端完整校验
             │
             ▼
PostgreSQL JSONB模型实例
             │
             ▼
通用状态机/工作流/约束引擎解释执行
             │
             ▼
AdapterPayloadMapper→MQTT→真实Adapter
```

系统固定契约和设备模型数据驱动不矛盾：

- 后端契约决定“状态机怎样匹配转移”“SEND动作怎样执行”“标准接口叫什么”
- 数据库模型决定“这个设备有哪些能力”“事件叫什么”“收到事件后转到什么状态”“参数怎样映射”

---

# 三、文件结构与职责

## 保留并修改

- `Backend/src/main/java/com/smartlab/global/contract/SystemExecutionContract.java`
  - 状态机标准接口、CMD状态、固定CMD系统转移、终态和查询方法的唯一来源
- `Backend/src/main/java/com/smartlab/engine/statemachine/StateMachineEngine.java`
  - 删除重复固定转移判断，改为消费`SystemExecutionContract`
- `Backend/src/main/java/com/smartlab/global/schema/SchemaMetadataService.java`
  - 暴露后端固定契约和工作流系统模板
- `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowDefinitionCompiler.java`
  - 使用统一工作流模板校验系统接口、触发器、动作和生命周期
- `Backend/src/main/java/com/smartlab/management/service/db/resource/device/DeviceModelService.java`
  - 唯一设备模型写入口、完整校验和实例引用锁
- `Backend/src/main/java/com/smartlab/management/service/db/resource/device/DeviceInstanceService.java`
  - 创建设备实例前验证模型运行就绪
- `Backend/src/main/java/com/smartlab/management/controller/resource/device/DeviceModelController.java`
  - 删除旁路写API
- `Frontend/src/utils/workflowNodeDefinition.js`
  - 删除本地系统模板，使用后端模板创建节点
- `Frontend/src/views/task/WorkflowDesigner.vue`
  - 初始化时加载固定契约后再允许添加节点
- `Frontend/src/views/device/components/deviceModel/deviceModelConstants.js`
  - 改用共享元数据加载器，保持设备表单静态结构

## 新建

- `Backend/src/main/java/com/smartlab/global/contract/WorkflowNodeSystemContract.java`
  - 工作流节点系统生命周期、接口、触发器和动作唯一来源
- `Backend/src/test/java/com/smartlab/global/contract/SchemaContractConformanceTest.java`
  - 测试期读取JSON规范并与Java契约比较
- `Backend/src/test/java/com/smartlab/global/contract/WorkflowNodeSystemContractTest.java`
  - 验证六类节点系统模板
- `Frontend/src/services/frontendContractMetadata.js`
  - `/api/schema-metadata/frontend`单例加载器

## 删除

- `Backend/src/main/java/com/smartlab/management/dto/resource/device/DeviceStateMachineSaveDTO.java`
  - 前提是确认只有已废弃状态机独立写接口引用
- `DeviceModelController`中的`updateAdapterContract`和`saveStateMachine`端点
- `DeviceModelService`中的`updateAdapterContract`、`saveStateMachine`和`deleteStateMachine`

不要删除只读的`listStateMachines`，除非仓库搜索证明没有任何调用且删除不会扩大本轮范围。

---

# 四、逐任务实施计划

### Task 1: 建立安全基线并冻结任务范围

**Files:**
- Read: `D:\SmartLab2.0\docs\superpowers\plans\2026-07-28-model-driven-runtime-contract-governance.md`
- Read: `D:\SmartLab2.0\Backend\src\main\resources\schemas\*.json`
- Read: `D:\SmartLab2.0\Backend\src\main\java\com\smartlab\global\contract`
- Read: `D:\SmartLab2.0\Backend\src\main\java\com\smartlab\engine\statemachine`
- Read: `D:\SmartLab2.0\Frontend\src\utils\workflowNodeDefinition.js`

**Interfaces:**
- Consumes: 当前工作区和本交接文件
- Produces: 不覆盖用户改动的任务边界、待修改文件清单和基线测试结果

- [ ] **Step 1: 检查当前分支与工作区**

Run:

```powershell
git status --short
git diff -- Backend/src/main/java/com/smartlab/global/contract/SystemExecutionContract.java
git diff -- Backend/src/main/java/com/smartlab/global/schema/SchemaMetadataService.java
git diff -- Frontend/src/utils/workflowNodeDefinition.js
```

Expected:

- 保留所有用户未提交修改
- 如果本文件记录的状态与实际状态不同，以实际工作区为准
- 不执行任何回退命令

- [ ] **Step 2: 搜索旁路写入口和前端调用**

Run:

```powershell
rg -n "/model/state-machine/save|adapter-contract|saveStateMachine|updateAdapterContract|deleteStateMachine" Backend/src Frontend/src
```

Expected:

- 确认前端只使用`POST /api/device/model/save`
- 如果发现真实调用，先记录调用链，再将调用迁移到统一保存API，不能直接删除导致断链

- [ ] **Step 3: 运行改动前聚焦测试**

Run:

```powershell
Set-Location Backend
mvn "-Dtest=SystemExecutionContractTest,StateMachineEngineTest,DeviceModelServiceTest,DeviceInstanceServiceTest,WorkflowDefinitionCompilerTest" test
Set-Location ../Frontend
node --test tests/workflowNodeDefinition.test.mjs tests/state-machine-contract.test.mjs tests/device-model-lifecycle.test.mjs
```

Expected:

- 记录通过数和失败项
- 基线失败不得伪装成本轮回归，先区分已有失败与新失败

- [ ] **Step 4: 提交边界**

此任务不修改代码，不提交。

---

### Task 2: 让状态机引擎真正消费SystemExecutionContract

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/global/contract/SystemExecutionContract.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/statemachine/StateMachineEngine.java`
- Modify: `Backend/src/test/java/com/smartlab/global/contract/SystemExecutionContractTest.java`
- Modify: `Backend/src/test/java/com/smartlab/engine/statemachine/StateMachineEngineTest.java`

**Interfaces:**
- Consumes: `SystemTransitionDefinition`
- Produces:
  - `Optional<SystemTransitionDefinition> findStateMachineSystemTransition(String stateSpace,String fromStateName,String triggerInterfaceName,String triggerSignalName)`
  - `boolean isCommandStartSignal(String signalName)`
  - `boolean isCommandAbortSignal(String signalName)`
  - `List<String> terminalCommandStateNames()`
  - `String stateOutputInterfaceName()`

- [ ] **Step 1: 为契约查询写失败测试**

在`SystemExecutionContractTest`增加：

```java
@Test
void resolvesSystemTransitionByCurrentStateInterfaceAndSignal() {
    var transition = SystemExecutionContract.findStateMachineSystemTransition(
            "CMD", "IDLE", "Interface_workflow_in", "WF_EXECUTE_START");

    assertTrue(transition.isPresent());
    assertEquals("SENT", transition.orElseThrow().toStateName());
    assertEquals("Interface_adapter_out", transition.orElseThrow().actionInterfaceName());
    assertEquals("CMD_START", transition.orElseThrow().actionSignalName());
    assertTrue(SystemExecutionContract.isCommandStartSignal("WF_EXECUTE_START"));
    assertTrue(SystemExecutionContract.isCommandAbortSignal("CONSTRAINT_ABORT"));
    assertEquals(List.of("COMPLETED", "FAILED", "ABORTED"),
            SystemExecutionContract.terminalCommandStateNames());
}
```

增加反例：

```java
@Test
void rejectsSameSignalOnWrongInterfaceOrWrongCurrentState() {
    assertTrue(SystemExecutionContract.findStateMachineSystemTransition(
            "CMD", "RUNNING", "Interface_workflow_in", "WF_EXECUTE_START").isEmpty());
    assertTrue(SystemExecutionContract.findStateMachineSystemTransition(
            "CMD", "IDLE", "Interface_control_in", "WF_EXECUTE_START").isEmpty());
}
```

- [ ] **Step 2: 验证测试先失败**

Run:

```powershell
Set-Location Backend
mvn -Dtest=SystemExecutionContractTest test
```

Expected: FAIL，提示查询方法不存在

- [ ] **Step 3: 在SystemExecutionContract实现集中查询**

实现必须从`stateMachineSystemTransitions()`派生，不得再复制一份转移列表：

```java
public static Optional<SystemTransitionDefinition> findStateMachineSystemTransition(
        String stateSpace, String fromStateName, String triggerInterfaceName, String triggerSignalName) {
    return stateMachineSystemTransitions().stream()
            .filter(row -> row.stateSpace().equals(stateSpace))
            .filter(row -> row.fromStateName().equals(fromStateName))
            .filter(row -> row.triggerInterfaceName().equals(triggerInterfaceName))
            .filter(row -> row.triggerSignalName().equals(triggerSignalName))
            .findFirst();
}

public static boolean isCommandStartSignal(String signalName) {
    return stateMachineSystemTransitions().stream()
            .anyMatch(row -> row.triggerSignalName().equals(signalName)
                    && AdapterOutboundSignal.CMD_START.name().equals(row.actionSignalName()));
}

public static boolean isCommandAbortSignal(String signalName) {
    return stateMachineSystemTransitions().stream()
            .anyMatch(row -> row.triggerSignalName().equals(signalName)
                    && AdapterOutboundSignal.CMD_ABORT.name().equals(row.actionSignalName()));
}

public static List<String> terminalCommandStateNames() {
    return List.of("COMPLETED", "FAILED", "ABORTED");
}

public static String stateOutputInterfaceName() {
    return "Interface_state_out";
}
```

补充`java.util.Optional`导入。

- [ ] **Step 4: 修改StateMachineEngine使用契约**

删除：

- `START_SIGNALS`
- `ABORT_SIGNALS`
- `TERMINAL_COMMAND_STATES`
- `ADAPTER_OUTPUT_INTERFACE`
- `STATE_OUTPUT_INTERFACE`

将所有判断替换为：

```java
SystemExecutionContract.isCommandStartSignal(signalName)
SystemExecutionContract.isCommandAbortSignal(signalName)
SystemExecutionContract.terminalCommandStateNames().contains(nextState)
```

将`systemCommandTransition`改为同时接收接口名：

```java
private TransitionResult systemCommandTransition(String currentCmdState,
                                                  String interfaceName,
                                                  String signalName) {
    return SystemExecutionContract.findStateMachineSystemTransition(
                    "CMD", currentCmdState, interfaceName, signalName)
            .map(row -> new TransitionResult(
                    row.toStateName(),
                    null,
                    List.of(sendAction(row.actionInterfaceName(), row.actionSignalName())),
                    List.of()))
            .orElse(null);
}
```

`computeNextState`调用必须传入`interfaceName`。状态广播接口使用`SystemExecutionContract.stateOutputInterfaceName()`。

- [ ] **Step 5: 补充状态机回归测试**

在`StateMachineEngineTest`至少覆盖：

```java
@Test
void workflowStartUsesContractTransitionAndEmitsCmdStart() {
    // twin CMD=IDLE
    // dispatch WORKFLOW.WF_EXECUTE_START
    // assert CMD=SENT
    // assert emitted signalName=CMD_START
}

@Test
void constraintAbortUsesContractTransitionOnlyFromSentOrRunning() {
    // SENT接收CONSTRAINT_ABORT后进入ABORTING
    // IDLE接收CONSTRAINT_ABORT不产生转移
}
```

测试模型仍使用模型中声明的接口和能力，不能绕过`requireInputInterface`。

- [ ] **Step 6: 运行测试**

Run:

```powershell
Set-Location Backend
mvn "-Dtest=SystemExecutionContractTest,StateMachineEngineTest" test
```

Expected: PASS

- [ ] **Step 7: 提交**

```powershell
git add Backend/src/main/java/com/smartlab/global/contract/SystemExecutionContract.java
git add Backend/src/main/java/com/smartlab/engine/statemachine/StateMachineEngine.java
git add Backend/src/test/java/com/smartlab/global/contract/SystemExecutionContractTest.java
git add Backend/src/test/java/com/smartlab/engine/statemachine/StateMachineEngineTest.java
git commit -m "refactor: centralize system command transitions"
```

提交前必须确认这些文件中的原有未提交改动已经被正确保留并纳入同一语义修改，不能覆盖。

---

### Task 3: 增加JSON规范与Java契约的一致性测试

**Files:**
- Create: `Backend/src/test/java/com/smartlab/global/contract/SchemaContractConformanceTest.java`
- Read-only: `Backend/src/main/resources/schemas/协议规范.json`
- Read-only: `Backend/src/main/resources/schemas/系统执行规范.json`

**Interfaces:**
- Consumes: `ProtocolContract`、`SystemExecutionContract`、各Java枚举
- Produces: 测试期契约漂移检测，不产生任何生产运行时依赖

- [ ] **Step 1: 创建资源读取辅助方法**

测试类使用Jackson读取classpath资源：

```java
private JsonNode resource(String name) throws IOException {
    try (InputStream input = getClass().getResourceAsStream("/schemas/" + name)) {
        assertNotNull(input, "缺少Schema资源: " + name);
        return JsonNodeSupport.MAPPER.readTree(input);
    }
}

private List<String> enumValues(JsonNode root, String definitionName) {
    return StreamSupport.stream(
                    root.path("definitions").path(definitionName).path("enum").spliterator(), false)
            .map(JsonNode::asText)
            .toList();
}
```

- [ ] **Step 2: 校验协议枚举**

增加测试：

```java
@Test
void protocolEnumsMatchExecutableProtocolContract() throws Exception {
    JsonNode protocol = resource("协议规范.json");
    for (String name : List.of(
            "DataType", "CommunicationProtocol", "WorkflowNodeSignal",
            "WorkflowControlSignal", "ManualControlSignal",
            "ConstraintControlSignal", "AdapterOutboundSignal", "StatusSignal")) {
        assertEquals(ProtocolContract.enumValues(name), enumValues(protocol, name), name);
    }
}
```

- [ ] **Step 3: 校验状态、操作符和标准接口**

增加测试：

```java
@Test
void systemDefinitionsAndInterfacesMatchExecutableContract() throws Exception {
    JsonNode spec = resource("系统执行规范.json");
    assertEquals(
            Arrays.stream(NodeLifecycleState.values()).map(Enum::name).toList(),
            enumValues(spec, "NodeLifecycleState"));
    assertEquals(
            Arrays.stream(TaskLifecycleState.values()).map(Enum::name).toList(),
            enumValues(spec, "TaskLifecycleState"));
    assertEquals(
            Arrays.stream(ConstraintOperator.values()).map(ConstraintOperator::value).toList(),
            enumValues(spec, "ConstraintOperator"));
    assertEquals(
            SystemExecutionContract.commandStateNames(),
            textValues(spec.path("stateMachineEngine").path("commandStateNames")));
}
```

标准接口比较字段：

- `name`
- `direction`
- `interfaceType`

对`allowedSignalsRef`引用的接口，解析引用最后一段并使用`ProtocolContract.enumValues`比较；`Interface_adapter_in.allowedSignals`允许为空。

- [ ] **Step 4: 校验固定系统转移**

从`系统执行规范.json/stateMachineEngine/systemTransitions`中过滤：

- `stateSpace`为`CMD`
- `trigger.signalName`不以`<`开头
- `actions`非空

归一化为：

```text
stateSpace|from|to|triggerInterface|triggerSignal|actionInterface|actionSignal
```

与`SystemExecutionContract.stateMachineSystemTransitions()`逐项比较。设备特有的占位转移不得进入比较。

- [ ] **Step 5: 运行测试并处理真实不一致**

Run:

```powershell
Set-Location Backend
mvn -Dtest=SchemaContractConformanceTest test
```

Expected:

- 一致时PASS
- 不一致时先报告具体字段
- 六份JSON已冻结，未经用户明确授权不得为了让测试通过而修改JSON
- 若Java实现明显偏离已定稿规范，应修改Java契约

- [ ] **Step 6: 提交**

```powershell
git add Backend/src/test/java/com/smartlab/global/contract/SchemaContractConformanceTest.java
git commit -m "test: verify schema and runtime contract consistency"
```

---

### Task 4: 将设备模型保存收口为唯一完整校验入口

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/resource/device/DeviceModelService.java`
- Modify: `Backend/src/main/java/com/smartlab/management/controller/resource/device/DeviceModelController.java`
- Delete: `Backend/src/main/java/com/smartlab/management/dto/resource/device/DeviceStateMachineSaveDTO.java`
- Modify: `Backend/src/test/java/com/smartlab/management/service/db/resource/device/DeviceModelServiceTest.java`

**Interfaces:**
- Consumes: `DeviceModelSaveDTO`
- Produces:
  - `DeviceModels requireRuntimeReady(Long modelId)`
  - `void validateForPersistence(DeviceModels model)`
  - 唯一写入口`POST /api/device/model/save`

- [ ] **Step 1: 写保存完整校验失败测试**

在`DeviceModelServiceTest`增加三个测试：

```java
@Test
void saveRejectsCapabilityWithoutRequiredShapeBeforeInsert() {
    DeviceModelSaveDTO payload = completePayload();
    ((ArrayNode) payload.getCapabilities()).addObject()
            .put("capabilityName", "broken");

    assertThrows(IllegalArgumentException.class, () -> service.savePayload(payload));
    verify(mapper, never()).insert(any());
}

@Test
void saveRejectsStateTransitionReferencingUnknownAdapterEvent() {
    DeviceModelSaveDTO payload = completePayload();
    payload.setStateTransitions(arrayWithTransition(
            "CMD", "SENT", "RUNNING", "UNKNOWN_EVENT"));

    assertThrows(IllegalArgumentException.class, () -> service.savePayload(payload));
    verify(mapper, never()).insert(any());
}

@Test
void runtimeReadyReturnsOnlyFullyValidatedModel() {
    when(mapper.selectById(7L)).thenReturn(completeModel());
    assertSame(model, service.requireRuntimeReady(7L));
}
```

`completePayload()`和`completeModel()`必须构造真实最小模型：

- 非空模型名和类别
- attributes、capabilities、adapterContract、ports、intrinsicConstraints
- 六个标准状态机接口
- 完整CMD状态
- 合法OP状态
- 四条必需Adapter事件CMD转移

- [ ] **Step 2: 验证测试先失败**

Run:

```powershell
Set-Location Backend
mvn -Dtest=DeviceModelServiceTest test
```

Expected: 新测试FAIL

- [ ] **Step 3: 拆清完整校验职责**

将现有私有校验组织为：

```java
public DeviceModels requireRuntimeReady(Long modelId) {
    if (modelId == null) {
        throw new IllegalArgumentException("deviceModelId不能为空");
    }
    DeviceModels model = mapper.selectById(modelId);
    if (model == null) {
        throw new IllegalArgumentException("设备模型不存在: " + modelId);
    }
    validateForPersistence(model);
    return model;
}

void validateForPersistence(DeviceModels model) {
    validateFinalDeviceModel(model);
    validateDeviceStateMachine(model);
}
```

调整校验调用关系，避免`adapterContract==null`时提前`return`导致状态机校验被跳过：

```text
validateFinalDeviceModel
├──模型名和类别
├──validateCapabilityModelShape
├──require adapterContract object
├──validateCapabilityModelIdentifiers
├──validateModelAdapterContract
└──validateDeviceStateMachine
```

`validateModelAdapterContract`只校验Adapter契约、能力映射和遥测映射，不再隐式负责状态机校验。

- [ ] **Step 4: 保存前执行完整校验**

`savePayload`顺序固定为：

```text
解析DTO
→若更新则检查是否已有设备实例
→enrichStateMachine
→validateForPersistence
→insert/update
→保存默认数据模板
```

校验失败时不得写入主模型，也不得写默认数据模板。

- [ ] **Step 5: 删除旁路写接口**

从`DeviceModelController`删除：

```java
@PutMapping("/model/{modelId}/adapter-contract")
@PostMapping("/model/state-machine/save")
```

从`DeviceModelService`删除：

```java
updateAdapterContract
saveStateMachine
deleteStateMachine
```

确认无引用后删除`DeviceStateMachineSaveDTO`。保留：

- `/model/save`
- `/model/preview`
- `/model/{id}/bundle`
- 只读列表接口

- [ ] **Step 6: 运行搜索确认只有一个模型写入口**

Run:

```powershell
rg -n "updateAdapterContract|saveStateMachine|deleteStateMachine|DeviceStateMachineSaveDTO|/model/state-machine/save" Backend/src Frontend/src
```

Expected: 无遗留引用

- [ ] **Step 7: 运行测试**

Run:

```powershell
Set-Location Backend
mvn "-Dtest=DeviceModelServiceTest,AdapterPayloadMapperServiceTest,StateMachineEngineTest" test
```

Expected: PASS

- [ ] **Step 8: 提交**

```powershell
git add Backend/src/main/java/com/smartlab/management/service/db/resource/device/DeviceModelService.java
git add Backend/src/main/java/com/smartlab/management/controller/resource/device/DeviceModelController.java
git add Backend/src/test/java/com/smartlab/management/service/db/resource/device/DeviceModelServiceTest.java
git add -u Backend/src/main/java/com/smartlab/management/dto/resource/device/DeviceStateMachineSaveDTO.java
git commit -m "refactor: enforce one validated device model write path"
```

---

### Task 5: 在设备实例化边界冻结模型

**Files:**
- Modify: `Backend/src/main/java/com/smartlab/management/service/db/resource/device/DeviceInstanceService.java`
- Modify: `Backend/src/test/java/com/smartlab/management/service/db/resource/device/DeviceInstanceServiceTest.java`
- Modify if required by constructor: callers and tests that instantiate`DeviceInstanceService`

**Interfaces:**
- Consumes: `DeviceModelService.requireRuntimeReady(Long)`
- Produces: 只有完整模型才能创建实例；已有实例不能改绑设备模型

- [ ] **Step 1: 写实例创建校验测试**

增加：

```java
@Test
void createInstanceRequiresRuntimeReadyModelBeforeAnyWrite() {
    when(deviceModelService.requireRuntimeReady(7L))
            .thenThrow(new IllegalArgumentException("设备模型不完整"));

    Map<String, Object> payload = Map.of(
            "deviceModelId", 7L,
            "instanceName", "device-1");

    assertThrows(IllegalArgumentException.class, () -> service.savePayload(payload));
    verify(mapper, never()).insert(any());
    verify(twinStatesMapper, never()).insert(any());
}
```

增加：

```java
@Test
void existingInstanceCannotChangeDeviceModel() {
    DeviceInstances existing = instance(10L, 7L);
    when(mapper.selectById(10L)).thenReturn(existing);

    Map<String, Object> payload = new HashMap<>();
    payload.put("id", 10L);
    payload.put("deviceModelId", 8L);
    payload.put("instanceName", "device-1");

    assertThrows(IllegalStateException.class, () -> service.savePayload(payload));
    verify(mapper, never()).updateById(any());
}
```

- [ ] **Step 2: 验证测试先失败**

Run:

```powershell
Set-Location Backend
mvn -Dtest=DeviceInstanceServiceTest test
```

Expected: FAIL

- [ ] **Step 3: 注入DeviceModelService**

在`DeviceInstanceService`构造函数加入：

```java
private final DeviceModelService deviceModelService;
```

禁止在实例服务中复制设备模型校验逻辑。

- [ ] **Step 4: 在任何写操作之前验证**

对新建实例：

```java
DeviceModels runtimeModel = deviceModelService.requireRuntimeReady(instance.getDeviceModelId());
```

必须发生在以下操作之前：

- `buildAdapterBinding`
- `mapper.insert`
- 创建孪生状态
- 创建默认数据集
- 创建组件槽位

对更新实例：

```java
if (!Objects.equals(existing.getDeviceModelId(), instance.getDeviceModelId())) {
    throw new IllegalStateException("设备实例创建后不能更换设备模型");
}
```

更新已有实例时不允许通过遗漏`deviceModelId`把绑定改成`null`；缺省时继承`existing.deviceModelId`。

- [ ] **Step 5: 验证模型不可变规则**

`DeviceModelService.savePayload`更新已有模型时继续统计所有引用该模型的设备实例。只要引用数大于零，无论实例是`IN_USE`还是`RETIRED`都拒绝修改，因为历史实例和日志仍引用该模型语义。

不新增版本字段。用户需要修改模型时，通过现有“新建设备模型”流程创建新的`modelId`。

- [ ] **Step 6: 运行测试**

Run:

```powershell
Set-Location Backend
mvn "-Dtest=DeviceInstanceServiceTest,DeviceModelServiceTest,AdapterPayloadMapperServiceTest" test
```

Expected: PASS

- [ ] **Step 7: 提交**

```powershell
git add Backend/src/main/java/com/smartlab/management/service/db/resource/device/DeviceInstanceService.java
git add Backend/src/test/java/com/smartlab/management/service/db/resource/device/DeviceInstanceServiceTest.java
git commit -m "feat: freeze device models at instance creation"
```

---

### Task 6: 建立工作流节点系统模板的后端唯一来源

**Files:**
- Create: `Backend/src/main/java/com/smartlab/global/contract/WorkflowNodeSystemContract.java`
- Create: `Backend/src/test/java/com/smartlab/global/contract/WorkflowNodeSystemContractTest.java`
- Modify: `Backend/src/main/java/com/smartlab/global/schema/SchemaMetadataService.java`
- Modify: `Backend/src/main/java/com/smartlab/engine/workflow/WorkflowDefinitionCompiler.java`
- Modify: `Backend/src/test/java/com/smartlab/engine/workflow/WorkflowDefinitionCompilerTest.java`

**Interfaces:**
- Produces:
  - `ObjectNode template(String nodeType,String functionType)`
  - `ObjectNode templates()`
  - 模板键`START`、`END`、`BRANCH`、`AGGREGATE`、`DEV_NODE`、`SUBFLOW_NODE`
- Consumes: `NodeLifecycleState`、`WorkflowNodeActionType`、`WorkflowNodeSignal`、`WorkflowControlSignal`、`StatusSignal`

- [ ] **Step 1: 写模板测试**

`WorkflowNodeSystemContractTest`必须明确验证每类节点：

```java
@Test
void deviceTemplateContainsStateMachineInvocationAndCompletionFeedback() {
    JsonNode template = WorkflowNodeSystemContract.template("DEV_NODE", null);
    assertEquals(
            List.of("Interface_workflow_in", "Interface_state_out",
                    "Interface_state_in", "Interface_workflow_out"),
            names(template.path("interfaces")));
    assertEquals(
            List.of("startDevice", "completeNode"),
            actionNames(template.path("actions")));
}

@Test
void branchTemplateContainsMutuallyExclusiveOutputs() {
    JsonNode template = WorkflowNodeSystemContract.template("FUNC_NODE", "BRANCH");
    assertEquals(List.of(true, false), thresholds(
            interfaceByName(template, "Interface_workflow_in").path("bindingTriggers")));
}

@Test
void subflowTemplateContainsNoSystemEmitAction() {
    JsonNode template = WorkflowNodeSystemContract.template("SUBFLOW_NODE", null);
    assertTrue(template.path("actions").isEmpty());
}
```

另外验证：

- START只有工作流OUT和`emitActive`
- END只有工作流IN且没有EMIT
- AGGREGATE接收ACTIVE并输出ACTIVE
- 每个模板生命周期为`PENDING/RUNNING/SUCCEEDED/FAILED/TERMINATING/TERMINATED`

- [ ] **Step 2: 实现WorkflowNodeSystemContract**

模板返回与数据库工作流节点相同的局部结构：

```json
{
  "lifecycle": {},
  "interfaces": [],
  "actions": []
}
```

每个系统项必须包含：

```json
{
  "_system": true,
  "_systemKey": "稳定键"
}
```

稳定键沿用当前前端：

- `lifecycle`
- `start.workflowOut`
- `start.emitActive`
- `end.workflowIn`
- `branch.workflowIn`
- `branch.trueOut`
- `branch.falseOut`
- `branch.true`
- `branch.false`
- `branch.emitTrue`
- `branch.emitFalse`
- `aggregate.workflowIn`
- `aggregate.workflowOut`
- `aggregate.active`
- `aggregate.emitActive`
- `device.workflowIn`
- `device.stateOut`
- `device.stateIn`
- `device.workflowOut`
- `device.workflowStart`
- `device.stateCompleted`
- `device.startDevice`
- `device.completeNode`
- `subflow.workflowIn`
- `subflow.workflowOut`

模板内容必须完全复现当前已经通过测试的业务语义，不增加新动作。

- [ ] **Step 3: 元数据接口返回完整模板**

`SchemaMetadataService.workflowMetadata()`增加：

```java
metadata.set("nodeTemplates", WorkflowNodeSystemContract.templates());
```

保留现有：

- `nodeTypes`
- `functionTypes`
- `actionTypes`
- `standardNodeInterfaces`
- `nodeLifecycleStates`

`nodeTemplates`用于创建和锁定节点；`standardNodeInterfaces`继续用于说明接口类型，不作为动态表单定义。

- [ ] **Step 4: 编译器使用同一模板校验**

保留以下节点业务字段检查：

- DEV_NODE必须有`deviceModelId`和`capability.capabilityName`
- SUBFLOW_NODE必须有`subFlowModelId`
- BRANCH必须有非空`expression`
- functionType必须为已支持枚举

将接口、系统动作、系统触发器和生命周期的期望值改为从：

```java
WorkflowNodeSystemContract.template(nodeType, functionType)
```

取得。编译器必须逐项验证：

- 系统接口存在
- 方向、类型和allowedSignals一致
- 系统动作存在且字段一致
- 系统触发器存在且condition/action一致
- 生命周期初始状态、状态集合和转移集合一致

允许用户增加模型明确允许的自定义变量、端口、触发器和UPDATE动作；不得允许删除或篡改系统项。

- [ ] **Step 5: 增加编译器漂移测试**

```java
@Test
void compilerAcceptsEveryBackendSystemTemplate() {
    for (NodeCase nodeCase : supportedNodeCases()) {
        ObjectNode node = completeNodeFromTemplate(nodeCase);
        assertDoesNotThrow(() -> compiler.compile(requestWith(node)));
    }
}

@Test
void compilerRejectsTamperedBackendSystemItem() {
    ObjectNode node = completeDeviceNodeFromTemplate();
    interfaceByName(node, "Interface_state_out")
            .putArray("allowedSignals").add("UNKNOWN");
    assertThrows(IllegalArgumentException.class, () -> compiler.compile(requestWith(node)));
}
```

- [ ] **Step 6: 运行后端测试**

Run:

```powershell
Set-Location Backend
mvn "-Dtest=WorkflowNodeSystemContractTest,WorkflowDefinitionCompilerTest,SystemExecutionContractTest" test
```

Expected: PASS

- [ ] **Step 7: 提交**

```powershell
git add Backend/src/main/java/com/smartlab/global/contract/WorkflowNodeSystemContract.java
git add Backend/src/main/java/com/smartlab/global/schema/SchemaMetadataService.java
git add Backend/src/main/java/com/smartlab/engine/workflow/WorkflowDefinitionCompiler.java
git add Backend/src/test/java/com/smartlab/global/contract/WorkflowNodeSystemContractTest.java
git add Backend/src/test/java/com/smartlab/engine/workflow/WorkflowDefinitionCompilerTest.java
git commit -m "refactor: centralize workflow node system templates"
```

---

### Task 7: 前端静态表单消费后端系统模板

**Files:**
- Create: `Frontend/src/services/frontendContractMetadata.js`
- Modify: `Frontend/src/views/device/components/deviceModel/deviceModelConstants.js`
- Modify: `Frontend/src/utils/workflowNodeDefinition.js`
- Modify: `Frontend/src/views/task/WorkflowDesigner.vue`
- Modify: `Frontend/tests/workflowNodeDefinition.test.mjs`
- Modify: `Frontend/tests/state-machine-contract.test.mjs`
- Modify: `Frontend/tests/device-model-lifecycle.test.mjs`

**Interfaces:**
- Produces:
  - `loadFrontendContractMetadata(): Promise<object>`
  - `workflowNodeTemplates(): object`
  - `configureWorkflowNodeTemplates(templates): void`
- Consumes: `/api/schema-metadata/frontend`中的`workflow.nodeTemplates`

- [ ] **Step 1: 编写元数据单例加载器**

`frontendContractMetadata.js`：

```js
import axios from 'axios'

let metadataPromise

export function loadFrontendContractMetadata() {
  if (!metadataPromise) {
    metadataPromise = axios.get('/api/schema-metadata/frontend')
      .then(response => response.data?.data ?? response.data)
      .catch(error => {
        metadataPromise = undefined
        throw error
      })
  }
  return metadataPromise
}
```

不得从六份Schema路径请求JSON。

- [ ] **Step 2: 先修改前端测试要求显式配置模板**

测试固定fixture必须模拟后端返回的`workflow.nodeTemplates`。增加：

```js
test.before(() => {
  configureWorkflowNodeTemplates(workflowTemplateFixture)
})
```

增加错误测试：

```js
test('系统模板未加载时拒绝创建节点', () => {
  resetWorkflowNodeTemplatesForTest()
  assert.throws(() => createFunctionNode('START', 'start'), /工作流系统模板尚未加载/)
  configureWorkflowNodeTemplates(workflowTemplateFixture)
})
```

- [ ] **Step 3: 删除workflowNodeDefinition中的本地固定模板**

删除本地实现：

- `lockedLifecycle`
- `systemWorkflowInterface`
- `emitAction`
- `createBranchNode`中的固定接口、触发器和动作
- `createStartNode`中的固定接口和动作
- `createEndNode`中的固定接口
- `createAggregateNode`中的固定接口、触发器和动作
- `createDeviceNode`中的固定接口、触发器和动作
- `createSubflowNode`中的固定接口

保留：

- `isSystemItem`
- 参数类型校验
- 能力切换
- 用户变量、端口和动作编辑
- 节点业务字段组装
- 节点本地快速校验

节点创建过程改为：

```js
function templateCopy(key) {
  const template = configuredTemplates?.[key]
  if (!template) throw new Error(`工作流系统模板尚未加载:${key}`)
  return structuredClone(template)
}

export function createDeviceNode(model, name) {
  const systemTemplate = templateCopy('DEV_NODE')
  return {
    name,
    nodeType: 'DEV_NODE',
    deviceModelId: model.id,
    capability: initialCapability(model),
    internalVariables: [],
    ...systemTemplate,
    ports: []
  }
}
```

模板键映射：

```text
FUNC_NODE+START→START
FUNC_NODE+END→END
FUNC_NODE+BRANCH→BRANCH
FUNC_NODE+AGGREGATE→AGGREGATE
DEV_NODE→DEV_NODE
SUBFLOW_NODE→SUBFLOW_NODE
```

BRANCH的`expression`仍由前端业务构造为空字符串，后端模板不负责用户输入字段。

- [ ] **Step 4: WorkflowDesigner等待元数据**

在资源加载和拖拽启用之前：

```js
const contractReady = ref(false)

onMounted(async () => {
  const metadata = await loadFrontendContractMetadata()
  configureWorkflowNodeTemplates(metadata.workflow?.nodeTemplates)
  contractReady.value = true
  await loadResources()
})
```

要求：

- 元数据未加载时功能节点、设备模型和子流程拖拽入口禁用
- 加载失败显示明确错误，不使用本地后备模板
- 已打开流程仍可显示，但不能在缺少契约时保存
- 节点抽屉仍是静态Vue模板，不能根据元数据字段动态生成表单

- [ ] **Step 5: 设备模型常量复用同一加载器**

`deviceModelConstants.js`不再自己调用Axios，改为：

```js
const metadata = await loadFrontendContractMetadata()
applyMetadata(metadata)
```

保留现有reactive数组和所有表单引用，避免重写设备模型抽屉。

- [ ] **Step 6: 运行前端测试**

Run:

```powershell
Set-Location Frontend
node --test tests/workflowNodeDefinition.test.mjs tests/state-machine-contract.test.mjs tests/device-model-lifecycle.test.mjs
npm run build
```

Expected:

- 节点模板测试PASS
- 设备模型生命周期测试PASS
- Vite生产构建PASS

- [ ] **Step 7: 提交**

```powershell
git add Frontend/src/services/frontendContractMetadata.js
git add Frontend/src/views/device/components/deviceModel/deviceModelConstants.js
git add Frontend/src/utils/workflowNodeDefinition.js
git add Frontend/src/views/task/WorkflowDesigner.vue
git add Frontend/tests/workflowNodeDefinition.test.mjs
git add Frontend/tests/state-machine-contract.test.mjs
git add Frontend/tests/device-model-lifecycle.test.mjs
git commit -m "refactor: load workflow system templates from backend"
```

---

### Task 8: 全链路回归与架构验收

**Files:**
- Test only unless a verified regression requires a scoped fix

**Interfaces:**
- Consumes: Tasks 2–7全部结果
- Produces: 可交付验证记录和剩余风险说明

- [ ] **Step 1: 检查禁止项**

Run:

```powershell
rg -n -i "json.?schema|schemafactory|classpath:.*schemas|resources/schemas" Backend/src/main/java Frontend/src
rg -n -i "机械臂|加热器|电磁阀|heater|robotArm|armUp|armDown" Backend/src/main/java
rg -n "systemCommandTransition|START_SIGNALS|ABORT_SIGNALS|TERMINAL_COMMAND_STATES" Backend/src/main/java/com/smartlab/engine/statemachine
rg -n "lockedLifecycle|systemWorkflowInterface|emitAction" Frontend/src/utils/workflowNodeDefinition.js
rg -n "saveStateMachine|updateAdapterContract|DeviceStateMachineSaveDTO" Backend/src Frontend/src
```

Expected:

- 生产代码没有Schema运行时解析
- 后端没有具体设备业务分支
- 状态机没有重复CMD固定转移集合
- 前端没有本地系统节点模板
- 没有旁路设备模型写入口

- [ ] **Step 2: 运行全部后端测试**

Run:

```powershell
Set-Location Backend
mvn test
```

Expected: 全部PASS

- [ ] **Step 3: 运行全部前端测试**

Run:

```powershell
Set-Location Frontend
node --test tests
```

Expected: 全部PASS

- [ ] **Step 4: 运行前端生产构建**

Run:

```powershell
Set-Location Frontend
npm run build
```

Expected: PASS且无阻断性错误

- [ ] **Step 5: 检查六份模型文件没有被修改**

Run:

```powershell
git diff -- Backend/src/main/resources/schemas
```

Expected: 无输出

- [ ] **Step 6: 检查最终差异**

Run:

```powershell
git status --short
git diff --check
git diff --stat
```

Expected:

- 无空白错误
- 不包含数据库迁移
- 不包含无关格式化或依赖变更
- 用户原有工作区改动均被保留

- [ ] **Step 7: 最终提交**

仅在用户授权提交且仍有未提交的本轮相关文件时执行：

```powershell
git add <仅本轮相关文件>
git commit -m "refactor: complete model-driven contract governance"
```

禁止使用`git add .`。

---

# 五、验收标准

## 1.系统契约

- `SystemExecutionContract`是状态机固定CMD转移的唯一声明位置
- `StateMachineEngine`按照`stateSpace+fromState+interface+signal`查询契约
- 引擎不再手写`IDLE→SENT`或`SENT/RUNNING→ABORTING`
- 固定系统规则仍然不从JSON文件动态加载

## 2.设备模型

- 所有设备模型写入只经过`POST /api/device/model/save`
- 保存前完成能力、Adapter映射、标准接口、CMD/OP状态和转移完整校验
- 创建实例前再次验证模型运行就绪
- 一旦模型被任意设备实例引用，任何修改均被拒绝
- 修改模型通过创建新`modelId`完成，不修改数据库结构

## 3.工作流

- 后端`WorkflowNodeSystemContract`是节点系统骨架唯一来源
- 编译器和前端使用同一份后端模板
- 前端不再本地声明固定生命周期、接口、系统触发器和系统动作
- 前端节点表单仍是人工设计的静态表单
- 用户自定义变量、端口、触发器和UPDATE动作继续工作

## 4.Schema边界

- 六份JSON文件没有被修改
- 生产运行时不读取Schema
- 测试会读取Schema并检查关键契约是否与Java一致
- Schema不用于前端动态渲染

## 5.真实链路

- 能力名、Adapter命令名、参数映射、设备事件和OP状态均来自数据库模型
- 终止命令继续通过`isAbort=true`解析
- 真实MQTT发布和Adapter事件回传链路不被破坏
- 不增加任何具体设备硬编码或演示数据

---

# 六、明确不做的事情

- 不新增数据库字段`version`、`status`或新的模型版本表
- 不实现运行时JSON Schema解释器
- 不实现Schema动态表单
- 不改变工作流模型只绑定`deviceModelId`的原则
- 不将设备实例ID写回工作流模型
- 不重做任务`RESOURCE_MAP`和子流程出现路径方案
- 不重做约束公式编辑器
- 不重写现有三个引擎
- 不修改六份定稿JSON
- 不为了测试方便引入模拟运行分支
- 不进行与本轮无关的目录重构、依赖升级或格式化

---

# 七、新对话启动提示词

将下面内容作为新对话第一条消息发送，并附上本文件路径：

```text
项目根目录是D:\SmartLab2.0

请先完整读取：
D:\SmartLab2.0\docs\superpowers\plans\2026-07-28-model-driven-runtime-contract-governance.md

这份文件是上一段长对话的完整上下文记忆和已确认实施方案。不要重新设计，不要修改六份Schema，不要修改数据库结构，不要回退工作区，不要覆盖用户未提交改动。

先执行git status --short并阅读计划中列出的当前实现文件，核对计划与当前代码是否仍一致。然后使用superpowers:executing-plans或subagent-driven-development按照Task 1到Task 8顺序执行。每个Task先检查现有代码是否已经完成；已经正确完成的部分不要重写，只补缺口。实现过程中必须保持真实设备模型数据驱动链路，禁止具体设备业务硬编码、模拟数据和hack。

开始前先用一段简短的话告诉我：
1.你已理解的目标
2.当前工作区风险
3.准备执行的第一个Task

随后直接开始执行，不需要再次讨论总体方案。
```

---

# 八、新对话最终汇报格式

执行完成后必须按以下格式汇报：

```text
完成结果
- 系统契约单一来源：
- 模型统一校验：
- 模型不可变：
- 工作流模板去重：
- Schema一致性测试：

关键修改
- 文件：修改内容

验证结果
- 后端测试：
- 前端测试：
- 前端构建：
- 六份Schema diff：

未完成或剩余风险
- 没有则明确写“无”

Git状态
- 分支：
- 提交：
- 未提交文件及归属：
```

不得只说“已完成”而不提供测试输出摘要和剩余风险。
