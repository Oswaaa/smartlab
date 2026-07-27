# 工作流节点完整配置与执行链路设计

## 1.目标

补齐工作流节点从前端建模、模型保存、后端编译、运行时执行到任务步骤快照的完整链路，使`internalVariables`、`ports`、`bindingTriggers`、`actions`、`capabilityParameters`和`portConnections`都能通过结构化界面配置并被引擎真实执行

本次不修改六个最终模型文件，不改变现有数据库表结构，不允许用户通过手写JSON绕过业务校验

## 2.方案选择

### 方案A：受保护骨架＋开放业务配置

系统按节点类型生成引擎必需的生命周期、接口、触发器和动作，并在界面中标记为系统项，不允许删除或修改关键字段。用户可以配置设备能力参数、功能表达式、内部变量、数据端口，并添加自定义触发器、EMIT动作和UPDATE动作

优点是模型仍然具有扩展能力，同时不会产生引擎无法执行的节点结构

### 方案B：完全开放底层模型

所有接口、生命周期、触发器和动作都允许用户编辑

该方案灵活性最高，但普通用户可以删除DEV_NODE状态接口、让START没有输出动作、创建多个同时命中的EMIT动作或破坏终止收敛规则，后端只能在保存时拒绝，业务体验不可接受

### 方案C：完全固定节点模板

用户只能选择设备能力和填写表达式，其他结构全部由系统生成且不能扩展

该方案最安全，但`internalVariables`、`ports`和UPDATE动作失去设计意义，无法满足数据在工作流节点间流动和节点内计算的需求

最终采用方案A

## 3.连接的业务含义

工作流画布同时显示两种连接：

- WORKFLOW接口连接：蓝色实线，从WORKFLOW类型OUT接口连接到WORKFLOW类型IN接口，写入`interfaceConnections`，传递`ACTIVE`信号
- 数据端口连接：紫色虚线，从OUT端口连接到IN端口，写入`portConnections`，把源端口绑定的内部变量值写入目标端口绑定的内部变量

接口连接和端口连接使用不同的连接点和不同的边类型，禁止跨类型连接

DEV_NODE与设备状态机之间的`NODE_TO_DEVICE`和`DEVICE_TO_NODE`连接仍由系统根据设备模型自动创建，不让用户手工连线

## 4.系统节点模板

### 4.1 START

系统项：

- `Interface_workflow_out`：WORKFLOW/OUT，允许`ACTIVE`
- `emitActive`：EMIT到`Interface_workflow_out`，发送`ACTIVE`

START没有输入接口和触发器。任务启动是START唯一的系统输入。START允许增加内部变量、端口和UPDATE动作，但始终只能保留一个系统EMIT出口

### 4.2 END

系统项：

- `Interface_workflow_in`：WORKFLOW/IN，允许`ACTIVE`

END不声明输出动作。收到合法ACTIVE后由工作流引擎结束当前流程或返回父级子流程节点

### 4.3 BRANCH

系统项：

- `Interface_workflow_in`：WORKFLOW/IN，允许`ACTIVE`
- `Interface_true_out`：WORKFLOW/OUT，允许`ACTIVE`
- `Interface_false_out`：WORKFLOW/OUT，允许`ACTIVE`
- `emitTrue`：EMIT到`Interface_true_out`
- `emitFalse`：EMIT到`Interface_false_out`
- 真分支触发器：`expression = true`时执行`emitTrue`
- 假分支触发器：`expression = false`时执行`emitFalse`

用户配置`expression`，表达式只引用当前节点内部变量、端口写入值、任务变量和当前输入上下文。两个系统分支动作和触发器不可删除

### 4.4 AGGREGATE

系统项：

- `Interface_workflow_in`：WORKFLOW/IN，允许`ACTIVE`
- `Interface_workflow_out`：WORKFLOW/OUT，允许`ACTIVE`
- `emitActive`：EMIT到`Interface_workflow_out`
- ACTIVE触发器：上游全部完成并收到ACTIVE后执行`emitActive`

是否满足全部前驱节点完成由工作流引擎的`aggregateReady`判断，前端不把它伪造为用户条件

### 4.5 DEV_NODE

系统项：

- `Interface_workflow_in`：WORKFLOW/IN，允许`ACTIVE`
- `Interface_state_out`：STATE/OUT，允许`WF_EXECUTE_START`
- `Interface_state_in`：STATE/IN，允许`CMD_STATE`和`OP_STATE`
- `Interface_workflow_out`：WORKFLOW/OUT，允许`ACTIVE`
- `startDevice`：EMIT到`Interface_state_out`
- `completeNode`：EMIT到`Interface_workflow_out`
- 工作流输入触发器：收到ACTIVE时执行`startDevice`
- 状态输入触发器：收到`CMD_STATE`且`inputPayload.stateName=COMPLETED`时执行`completeNode`
- 固定节点生命周期

用户配置设备模型、能力、能力参数、内部变量、属性映射、数据端口以及附加UPDATE动作和触发器

任务终止不依赖用户动作。任务服务和工作流引擎直接通过该节点的STATE输出路径发送`WF_EXECUTE_ABORT`

### 4.6 SUBFLOW_NODE

系统项：

- `Interface_workflow_in`：WORKFLOW/IN，允许`ACTIVE`
- `Interface_workflow_out`：WORKFLOW/OUT，允许`ACTIVE`
- 固定节点生命周期

收到ACTIVE后由工作流引擎创建子流程步骤，子流程完成后由引擎从输出接口发送ACTIVE。用户可以配置内部变量、端口和输入阶段的UPDATE动作，但不能添加绕过子流程执行的EMIT动作

## 5.节点配置界面

节点配置抽屉拆分为四个页签

### 5.1基础配置

- 节点名称
- DEV_NODE：只读设备模型、能力选择、按能力参数定义生成的参数表单
- FUNC_NODE：功能类型只读、表达式编辑
- SUBFLOW_NODE：引用流程只读、子流程说明

能力参数根据`parameters[].dataType`生成控件：

- INTEGER、DOUBLE：数字输入
- STRING：文本输入
- BOOLEAN：开关
- JSON：键值列表编辑器，不要求用户手写JSON

切换设备能力时保留名称和类型都兼容的参数，其余参数清除

### 5.2变量与端口

内部变量使用表格编辑：

- `name`
- `dataType`
- `attributesMapping`

`attributesMapping`只对DEV_NODE开放，选项来自所属设备模型`attributes`，选择属性后自动同步`dataType`

端口使用表格编辑：

- `name`
- `direction`
- `internalVariableName`

只能选择当前节点已声明的内部变量。删除被端口引用的变量前必须先删除对应端口；删除存在`portConnections`的端口前必须确认并同步删除连接

### 5.3触发器与动作

动作使用结构化表单：

- 公共字段：`actionName`、`actionType`
- EMIT：`targetInterfaceName`、`signalName`
- UPDATE：`internalVariableName`、`valueExpression`

`actionName`是节点内唯一业务名称，`actionType`只允许EMIT或UPDATE。最终模型中`WorkflowNodeAction.actionName`的枚举与描述存在冲突，本次不修改模型文件，前后端按已确定的实际语义处理

触发器按输入接口分组：

- `condition.object`
- `condition.operator`
- `condition.threshold`
- `action`

`action`只能从当前节点已有动作中选择。系统触发器和系统动作显示锁定标识，不允许删除或改变关键字段

条件目标提供引导选项：

- `inputSignalName`
- `inputPayload.stateName`
- `nodeLifecycleState`
- `expression`
- 当前节点内部变量名

同时允许输入`inputPayload.<字段路径>`，用于设备状态payload中的扩展字段

### 5.4接口与生命周期

展示接口名称、方向、类型、允许信号、系统/自定义状态和生命周期转移

本轮不开放新增接口和修改生命周期。工作流节点的扩展数据通道通过ports实现，扩展信号行为通过既有输入接口的触发器和动作实现

## 6.触发器和动作运行规则

一个输入接口收到信号时：

1. 工作流引擎构造变量上下文
2. 按触发器声明顺序求值所有condition
3. 收集所有命中的动作
4. 先按触发器顺序执行全部UPDATE动作
5. 命中的EMIT动作必须为零个或一个
6. 如果命中多个EMIT，节点失败并记录“同一输入命中多个EMIT动作”
7. 最后执行唯一EMIT动作

该规则保证变量更新不会因为前面的EMIT提前返回而被跳过，也保证BRANCH不会同时激活真假两条路径

START没有输入触发器，任务启动时采用相同规则执行其actions：先UPDATE，最后执行唯一EMIT

## 7.内部变量与设备属性

任务步骤变量空间初始包含任务变量

DEV_NODE执行期间，工作流引擎根据任务`resourceMap`解析设备实例，读取`DEVICE_TWIN_STATES.current_attr`，再按`internalVariables[].attributesMapping`把设备属性写入节点变量空间

同步发生在节点每次处理输入或继续执行前，因此长时间等待设备状态的节点能够使用最新属性

写入和端口传递都必须符合内部变量声明的`dataType`：

- INTEGER只接受整数
- DOUBLE接受整数和小数
- STRING只接受字符串
- BOOLEAN只接受布尔值
- JSON接受对象或数组

类型不兼容时节点失败，不进行隐式字符串转数字等类型修补

## 8.端口数据流

源节点通过WORKFLOW接口完成并路由到目标节点时，工作流引擎查找两节点间的`portConnections`

对每条端口连接：

1. 读取源OUT端口绑定的内部变量
2. 校验源变量与目标变量`dataType`一致
3. 将值写入目标IN端口绑定的内部变量
4. 保存到目标`TASK_STEP.variable_space`
5. 发布节点内部变量观测事件供约束引擎使用

端口连接不会单独激活节点，节点激活仍由WORKFLOW接口的ACTIVE信号决定

## 9.后端校验

前端保存前和后端编译时都执行同一组业务规则：

- 节点名、变量名、端口名、接口名和动作名在各自作用域内唯一
- 内部变量数据类型合法
- DEV_NODE属性映射引用真实设备模型属性且数据类型相同
- 能力名称引用真实设备能力
- `capabilityParameters`覆盖能力要求的全部参数，不允许多余参数，值类型正确
- 端口引用真实内部变量
- 端口连接必须OUT到IN，源目标变量数据类型一致
- EMIT目标是当前节点真实OUT接口，信号在allowedSignals中
- UPDATE目标是当前节点真实变量，表达式可以被解析
- 触发器只声明在IN接口，动作引用存在
- 系统项存在且关键字段未被改变
- BRANCH具有真、假两个出口和对应系统动作
- 同一输入事件不能产生多个EMIT结果

后端仍是最终校验权威，前端校验用于即时反馈

## 10.持久化修复

工作流保存和加载必须完整往返以下字段：

- `capability.capabilityParameters`
- `internalVariables`
- `lifecycle`
- `interfaces.bindingTriggers`
- `ports`
- `actions`
- `subFlowModelDescription`
- `interfaceConnections`
- `portConnections`

数据库不增加字段。`subFlowModelDescription`随SUBFLOW_NODE存入现有`FLOW_NODE.capability`JSONB，并在读取工作流定义时还原到节点顶层

画布坐标继续只保存在浏览器布局存储中，不进入工作流模型和数据库

## 11.错误处理

- 前端配置错误在对应页签和表格行显示，不提交请求
- 后端编译错误返回带节点名和字段路径的错误信息
- 运行时类型错误、表达式错误、多EMIT冲突写入执行日志并将当前节点和任务置为FAILED
- 设备孪生属性不存在时，不伪造默认值；引用该变量的条件为无法求值，UPDATE表达式引用时明确失败
- 端口源变量尚无值时不写入目标变量，并在步骤日志中记录跳过原因

## 12.测试与验收

### 前端单元测试

- 每类节点模板包含正确系统项
- 系统项不可删除
- 能力参数表单值序列化正确
- 变量删除引用保护
- 端口连接创建、类型校验和删除同步
- 动作和触发器引用校验
- BRANCH真、假出口生成
- 保存payload不包含编辑器字段

### 后端单元测试

- 编译器接受完整合法节点配置
- 编译器拒绝能力参数、属性映射、端口类型和动作引用错误
- UPDATE在EMIT之前执行
- 同一输入命中多个EMIT时失败
- DEV_NODE属性映射从设备孪生状态写入变量空间
- 端口连接把源变量写入目标变量
- 子流程说明保存后重新读取不丢失

### 浏览器验收

1. 创建START、BRANCH、两个DEV_NODE和END
2. 配置BRANCH表达式与内部变量
3. 为DEV_NODE选择带参数能力并填写参数
4. 创建OUT/IN数据端口并用紫色虚线连接
5. 查看系统触发器和动作锁定状态
6. 添加UPDATE动作和自定义触发器
7. 保存并重新打开流程，全部配置保持一致
8. 创建任务、绑定设备实例并执行，检查步骤变量、分支路由、设备指令和端口数据传递
