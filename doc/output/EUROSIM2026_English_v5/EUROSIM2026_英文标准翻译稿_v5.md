# EUROSIM 2026 中文原稿对应的英文定稿（v5）

语义基准：中文《图文与机制修订版_职责修正版_v3》。保留原页面顺序与图形结构，逐项恢复中文表达的论证关系、动作主体与执行条件。下面的英文与 PPT 显示内容一致，省略重复页脚与重复标签，取消仅为排版插入的换行。

## 翻译问题及处理

英文语法通顺并不等于忠实传达中文原意。此前部分改写属于信息压缩，改变了发展关系、模型职责或条件，而不是英语表达习惯所必需的变化。本版允许英语语序变化，但保留原文信息。

| 中文原意 | 本版处理 |
| --- | --- |
| 从流程自动执行逐步走向大模型参与决策与控制 | 用 gradually moving from … toward … 保留演进关系，用 decision-making 保留“决策”，不用 planning 替代。 |
| 四个问题始终存在，大模型加入使边界问题更突出 | 分别保留 every task execution 与 more pressing，避免把执行边界讲成大模型独有问题。 |
| 从执行对象、任务过程、运行规则分析三层组织 | 总览页保留三组对应关系，讲稿解释抽象过程，不直接跳到三层结论。 |
| 变量空间、接口、触发器与状态机定义执行语义 | 恢复具体建模构件和动作，不仅用泛化的 rules 替代。 |
| 能力模型提供契约，状态机使用契约，Adapter 映射设备指令 | 三个主体的职责在链路页和讲稿中分别表述，Adapter 不承担解析模型契约的职责。 |
| 内禀约束、实验室基线约束、任务约束 | 统一为 intrinsic constraints、laboratory-wide constraints、task constraints。讲稿明确 laboratory-wide 指持续有效的实验室基线规则。 |
| 仿真创建对应设备的虚拟实例，整次执行复用 | 保留创建时机、设备对应关系、状态演化和模拟测量/运行状态，不译为操作系统意义上的 virtual machine。 |
| 先优化方案，确认仿真结果无问题后，再由上位机发起真机执行 | 明确保留 Once results are confirmed acceptable 这一条件和 host system 这一主体，不由 Adapter 判断或自动切换。 |

## 核心术语

| 中文 | 英文 |
| --- | --- |
| 决策 / 控制 | decision-making / control |
| 设备对象模型 / 能力模型 | device object model / capability model |
| 状态机 / 状态演化 | state machine / state evolution |
| 执行语义 / 执行链路 | execution semantics / execution path |
| 控制请求 / 发给 Adapter 的约定命令 / 设备指令 | control request / command for the Adapter / device command |
| Adapter 映射契约 | Adapter mapping contract |
| 仿真模式 / 真机模式 | simulation mode / hardware mode |
| 对应设备的虚拟实例 / 上位机 | virtual device instance / host system |

## 封面 — A Task-Oriented Executable Modeling Framework for Smart Chemical Laboratories

- A Task-Oriented Executable Modeling Framework for Smart Chemical Laboratories

- Beijing Information Science and Technology University

- Presenter: Tao Yu    Corresponding author: Chun Zhao

- EUROSIM 2026, Genoa, Italy

## 正文第 1 页 — 1  Outline

- • Background and problem • Unified modeling framework • Key execution mechanism • Implementation and case study • Extension and outlook • Conclusions

- Why unified modeling is needed, and how the models run along a shared execution path.

## 正文第 2 页 — 2  Background: execution boundaries with LLMs

- As large language models (LLMs) advance, smart chemical laboratories are gradually moving from automated workflow execution toward LLM involvement in decision-making and control.

- Four aspects must be explicit in every task execution:

- Resources

- How resources are organized and accessed

- State

- How state evolves during execution

- Data

- How data is generated and transferred

- Boundaries

- Whether execution stays within allowed limits

- LLM involvement makes execution boundaries a more pressing issue: What may an LLM request, and under what conditions can those requests be executed?

- No privileged bypass for LLMs: all control requests follow the same execution path.

- We therefore need one executable framework to model resource organization, state evolution, data transfer, and boundary checks.

- LLM involvement increases the need for shared execution boundaries.

## 正文第 3 页 — 3  Unified framework: organizing task execution models

- Execution objects → resource models. Task processes → workflows. Operating rules → constraints.

- Cross-layer protocol Π defines shared control signals, data types, port connections, and execution feedback

- Workflow layer

- Previous node

- Current node

- Next node

- Workflow node

- Variable space

- Task variables / Attribute mapping

- Node lifecycle

- Execution stage and result

- Control interfaces / Data ports

- Control signals / I/O data

- Trigger: condition → action

- Update variables/state · Send signals

- Resource layer

- Device object model

- Capability model

- Attributes and capabilities Operation parameters and interfaces Adapter mapping contract Intrinsic constraints

- State machine

- Request handling and state checks Transitions and command output Feedback-based state updates

- Capability binding

- Execution requests / Control signals

- State and data feedback

- Constraint layer

- Observation space

- Device state and attributes / Workflow context

- Constraint rules

- Laboratory-wide / Task constraints

- Response actions

- Device actions / Warnings / Alarms / Abort

- Node variables Execution context

- Device state / Measurements

- Actions return via node / state machine interfaces

- Resources: capabilities and state. Workflows: steps and data. Constraints: execution boundaries.

## 正文第 4 页 — 4  Resource layer: capabilities and state machines

- A capability model and a state machine together describe what a device can do and how its operations are executed.

- Capability model

- Defines device capabilities, attributes, and operation requirements.

- State machine

- Handles requests using the current state, sends commands, and receives feedback.

- Shared entry point

- People, workflows, and LLMs follow the same state rules.

- Resource layer

- Device object model

- Attributes and capabilities Operation parameters and interfaces Adapter mapping contract Intrinsic constraints

- Request handling and state checks Transitions and command output Feedback-based state updates

- Capability definitions and state evolution together define the semantics of device operations.

## 正文第 5 页 — 5  Workflow layer: nodes, interfaces, and triggers

- Workflows organize task steps and data dependencies. Nodes coordinate through interfaces and triggers.

- Workflow layer

- Previous node

- Current node

- Next node

- Workflow node

- Variable space

- Task variables / Attribute mapping

- Node lifecycle

- Execution stage and result

- Control interfaces / Data ports

- Control signals / I/O data

- Trigger: condition → action

- Update variables/state · Send signals

- Feedback updates variables. Triggers check conditions to issue requests or advance to subsequent steps.

## 正文第 6 页 — 6  Constraint layer: observing execution and responding

- Observe resource state and task context together, and return response actions through the relevant interfaces.

- Workflow layer

- Node lifecycle and task variables Task execution context

- Resource layer

- Device state and attributes Measurements

- Constraint layer

- Observation space

- Device state and attributes / Workflow context

- Constraint rules

- Laboratory-wide / Task constraints

- Response actions

- Device actions / Warnings / Alarms / Abort

- Workflow observations

- Resource observations

- Actions return via node / state machine interfaces

- Intrinsic constraints

- Handled locally by the device state machine

- Laboratory-wide constraints

- Continuously monitor laboratory operation

- Task constraints

- Apply during task execution

## 正文第 7 页 — 7  Key mechanism: the shared execution path

- Workflows organize steps. Device requests run through the state machine and Adapter. Feedback returns through the models.

- Workflow layer

- Device task node

- Device model layer

- Device model

- Adapter layer

- Adapter

- Physical device

- Run signal

- State feedback

- Control command

- Data feedback

- Device command

- Data / Status

- Workflow node

- Control interfaces / Data ports

- Variable space

- Node lifecycle

- Trigger: condition → action

- Read variables, interface signals, and lifecycle state Update variables and state, and send execution signals

- Control request / Operation parameters

- Execution status / Data feedback

- Device state machine

- Capability model supplies operations + Adapter mapping contract

- Request handling

- Check request and state

- State transition

- Apply transition actions

- Command output

- Form command per contract

- Events and telemetry → update device model state and attributes

- Command for the Adapter

- Device events / Measurements

- Command mapping

- Upper-level command → Device protocol

- Feedback conversion

- Device data → Events / Telemetry

- Measurements / Operating status

- Physical device: execute operations and return measurements and operating status

- People, workflows, and LLMs share the device model entry point, state rules, and constraints.

## 正文第 8 页 — 8  Implementation and case study: reactor heating

- SmartLab 2.0: workflow and state machine engines execute models. An MQTT Adapter handles device communication.

- Heating node

- Device state machine

- Adapter

- Reactor

- Constraint layer

- 1  Heating request (parameters)

- State checks and request handling

- 2  Heating command for Adapter

- 3  Device heating command

- loop

- During execution: continuous data feedback and constraint monitoring

- 4  Temperature / Operating status

- 5  Telemetry / State update

- 6  Execution status / Data

- Device state and measurements

- Node execution context

- alt

- [Normal] Completion conditions are met and continued execution is allowed

- Node completes → Activation signal → Subsequent step

- [Exception example] A stop or abort rule is triggered

- Device response: send a stop / abort request to the state machine

- Task response: abort task execution through the node interface

- Completion conditions govern node progression. Temperature violations or device faults trigger constraint responses.

## 正文第 9 页 — 9  Extension and outlook: the simulation feedback loop

- Virtual instances inside the Adapter run simulations. Upper-level models and execution rules remain the same.

- LLM

- Generate or revise a plan

- Workflow model / Device request

- Analyze feedback

- Revise steps, parameters, requests

- Feedback improves the plan

- Workflow model (optional)

- Organize steps and data dependencies Workflow nodes issue requests

- Device model

- Capability model + State machine

- Handle requests and issue commands

- State checks / Transition actions

- Process feedback

- Update device state and attributes

- Generate workflow model

- Invoke device capability

- Direct device control request

- State and data feedback

- Simulation feedback

- State, data, and constraint results

- Adapter

- Receive commands in the selected mode

- Simulation mode

- Create instance per device at start

- Virtual device instance

- Same instance for the full run Respond to commands Evolve state Return simulated measurements and operating status

- Hardware mode

- Map commands / Convert real feedback

- Command

- State / Data

- Physical device

- Execute operations and return actual data

- Device command

- Data / Status

- Treat simulated feedback as device data. Update models and check constraints as usual.

- Optimize the plan in simulation. Once results are confirmed acceptable, the host initiates hardware execution.

## 正文第 10 页 — 10  Conclusions

- Thank you. Questions are welcome.

- Unified modeling

- Resource, workflow, and constraint models jointly describe laboratory task execution.

- Explicit semantics

- Variable spaces, interfaces, triggers, and state machines define execution semantics.

- Shared execution

- People, workflows, and LLMs share the execution mechanism, with support for simulation.

- Models represent specific laboratories and tasks. Shared rules drive execution.
