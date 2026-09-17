# EUROSIM 2026 英文 PPT v4 用词修订对照

基准：英文 ContractRolesFixed_v3。共 11 张幻灯片（封面不编号，正文页码为 1–10）。本次保留原页面顺序、图形、连接关系、位置、尺寸与字号。仅修改文字，并同步备注中的相关术语。

## 用词原则

- 保留正式研究题目和核心建模术语，具体解释其作用。
- 明确动作的主体、对象和条件，区分请求、命令、反馈和状态更新。
- 统一能力模型、状态机和 Adapter 的职责，不把命令契约的解析移给 Adapter。
- 页首引题，图内解释关系，页脚突出结论，避免复述。
- 删除冗余分号，保留表示结构关系所需的箭头与必要分组。
- 优先缩短文字适应原有文本框，保持结构和字号。

以下按实际幻灯片位置列出全部显示文字修改。表中相同术语跨页重复时分别列出，便于逐页核对。

## 第 1 张（封面）

保留正式论文题目、作者与学校名称，仅统一地点英文和分隔符。

| 修改前 | 修改后 | 原因 |
| --- | --- | --- |
| Presenter: Tao Yu    \|    Corresponding author: Chun Zhao | Presenter: Tao Yu    Corresponding author: Chun Zhao | 删除无必要的竖线，保留原有作者身份。 |
| EUROSIM 2026  ·  Genova, Italy | EUROSIM 2026, Genoa, Italy | 统一英文城市名和标点。 |

## 第 2 张（正文第 1 页）

目录与正文用词对应，底部说明改成具体研究问题。

| 修改前 | 修改后 | 原因 |
| --- | --- | --- |
| • Background and problem<br>• Unified modeling framework<br>• Execution mechanism<br>• Implementation and case study<br>• Simulation extension<br>• Conclusions | • Background and motivation<br>• Unified modeling framework<br>• Shared execution path<br>• Implementation and case study<br>• Simulation feedback loop<br>• Conclusions | 目录用词与正文主题对应。 |
| From modeling requirements to a shared device execution path. | How models define and control laboratory task execution. | 用具体研究问题替代抽象的 From ... to ...。 |

## 第 3 张（正文第 2 页）

解释执行边界到底约束什么，去掉指代不清的 it，背景四个问题不再反复重复。

| 修改前 | 修改后 | 原因 |
| --- | --- | --- |
| 2  Background: execution boundaries with LLMs | 2  Background: limits on LLM device control | 标题直接说明边界约束的对象。 |
| Laboratory automation is expanding from predefined workflows<br>to decision-making and control involving large language models (LLMs). | Laboratories already automate task workflows.<br>Large language models (LLMs) now also contribute to planning and device control. | 拆开自动化基础与大模型加入两个意思，便于现场阅读。 |
| Every task requires four aspects to be made explicit: | Every task must specify: | 删除冗长的被动表达。 |
| How they are organized and used | Resource organization and use | 明确讨论资源组织与调用。 |
| How they evolve during execution | State changes during execution | 用直接的状态变化替代 evolve。 |
| How they are generated and passed | Data generation and transfer | passed 缺少对象，改为数据传递。 |
| Whether execution remains allowed | Conditions for allowed execution | 说明边界表现为允许执行的条件。 |
| With LLMs, execution boundaries become more pressing:<br>What may an LLM request, and under what conditions may it execute? | LLM device control requires explicit limits:<br>Which operations may an LLM request, and when are they allowed? | 消除 it 指代 LLM 还是请求的歧义。 |
| Principle: no privileged bypass. All device requests follow the same execution path. | All requests enter the device model. LLMs have no direct path to the Adapter. | 把特权旁路落实为具体入口与禁止跨越的层级。 |
| An executable framework must unify resources, state evolution, data flow, and boundary checks. | We therefore model resources, workflows, and constraints in one executable framework. | 引出下一页三层框架，避免再次重复四个问题。 |
| LLM participation makes shared execution boundaries essential. | The framework defines which requests can execute and under what conditions. | 用具体的执行判断替代对标题的重复。 |

## 第 4 张（正文第 3 页）

页首定义三层，图内解释组成和交互，页脚强调协作，三个区域各有作用。

| 修改前 | 修改后 | 原因 |
| --- | --- | --- |
| Model the execution objects as resources, the task process as a workflow, and operating rules as constraints. | Device resources, task workflows, and execution constraints form the three modeling layers. | 去掉 execution objects、task process 等不自然表达。 |
| Cross-layer protocol Π: control signals, data types, port relations, and execution feedback | Protocol Π defines control signals, data types, port connections, and execution feedback | port connections 更明确地表达端口连接关系。 |
| Execution node | Current node | 与前序、后续节点形成一致的命名，避免暗示其他节点不执行。 |
| Task variables / Attribute links | Variables / Attribute mapping | 明确表示变量与属性映射，具体对象由演讲备注说明。 |
| Execution phase and outcome | Execution stage and result | 用更直接的词解释生命周期。 |
| Signals / Input and output data | Control signals / Data values | 明确接口传递的是控制信号。 |
| Update state / Emit signals | Set values / Send signals | 以 Set values 表达变量或状态更新，统一发送信号的用词。 |
| Attributes and capabilities<br>Operation parameters<br>Interfaces / Adapter contract<br>Intrinsic constraints | Attributes and operations<br>Parameters and interfaces<br>Adapter mapping contract<br>Intrinsic constraints | 把能力落实为可执行操作，并明确契约规定状态机发送给 Adapter 的命令。 |
| Request and state checks<br>Transitions and commands<br>Feedback-driven updates | Check requests and state<br>Run transition actions<br>Update state on feedback | 使用明确动词，说明检查、转移和反馈更新的作用。 |
| Requests / Control signals | Control requests | 去掉同一箭头上语义重叠的说法。 |
| State / Data feedback | State and data feedback | 明确这是状态和数据共同构成的反馈。 |
| Device state, attributes / Task context | Device state / Attributes / Task context | 修正混用逗号与斜线造成的层次不清。 |
| Laboratory baseline / Task rules | Laboratory rules / Task rules | baseline 容易误解为基准值，改为实际适用范围。 |
| Handling actions | Response actions | 明确指约束触发后的响应动作。 |
| Device actions / Warn / Alarm / Abort | Device actions / Alerts / Task abort | 统一为名词并区分设备操作、告警和任务中止。 |
| Actions return through node / state-machine interfaces | Actions use node / state machine interfaces | 避免把动作说成返回值，说明实际作用入口。 |
| Resources define capability and state; workflows organize tasks and data; constraints define boundaries. | The layers interact through control requests, device feedback, and constraint responses. | 删除分号和对页首三层定义的重复，页脚转而说明层间协作。 |

## 第 5 张（正文第 4 页）

突出能力模型提供操作定义与契约、状态机使用契约、Adapter 负责设备命令转换。

| 修改前 | 修改后 | 原因 |
| --- | --- | --- |
| A device object combines its capability model with a state machine to describe what it can do and how it executes. | Each device model combines a capability model with a state machine. | 压缩页首，具体职责由图内和左侧说明承担。 |
| Defines attributes, capabilities,<br>and operation requirements. | Defines operations and the<br>Adapter mapping contract. | 突出能力模型提供操作定义和契约的职责。 |
| Handles requests in the current state,<br>issues commands, and processes feedback. | Checks requests and formats commands<br>using the capability model's contract. | 明确由状态机使用契约生成命令。 |
| People, workflows, and LLMs<br>follow the same state rules. | All device requests enter<br>through the device model. | 使 Shared entry point 的解释真正对应入口。 |
| Attributes and capabilities<br>Operation parameters<br>Interfaces / Adapter contract<br>Intrinsic constraints | Attributes and operations<br>Parameters and interfaces<br>Adapter mapping contract<br>Intrinsic constraints | 把能力落实为可执行操作，并明确契约规定状态机发送给 Adapter 的命令。 |
| Request and state checks<br>Transitions and commands<br>Feedback-driven updates | Check requests and state<br>Run transition actions<br>Update state on feedback | 使用明确动词，说明检查、转移和反馈更新的作用。 |
| Capability definitions and state evolution jointly describe device operation semantics. | The capability model defines available operations. The state machine governs their execution. | 把抽象的语义描述展开为两个清楚的职责。 |

## 第 6 张（正文第 5 页）

页首讲流程，图内讲节点组成，页脚讲触发与完成条件，三者避免重复。

| 修改前 | 修改后 | 原因 |
| --- | --- | --- |
| Workflows organize steps and data dependencies; nodes coordinate through interfaces and triggers. | A workflow defines task steps and data dependencies between nodes. | 页首先讲流程整体，节点机制由图解释，删去分号和重复。 |
| Execution node | Current node | 与前序、后续节点形成一致的命名，避免暗示其他节点不执行。 |
| Task variables / Attribute links | Variables / Attribute mapping | 明确表示变量与属性映射，具体对象由演讲备注说明。 |
| Execution phase and outcome | Execution stage and result | 用更直接的词解释生命周期。 |
| Signals / Input and output data | Control signals / Data values | 明确接口传递的是控制信号。 |
| Update state / Emit signals | Set values / Send signals | 以 Set values 表达变量或状态更新，统一发送信号的用词。 |
| Feedback updates variables; trigger conditions determine requests and progression to the next step. | Node conditions control when to issue requests, complete a step, and activate the next node. | 明确推进条件，避免暗示收到反馈就自动完成节点。 |

## 第 7 张（正文第 6 页）

把抽象的 handling 改为 response，并明确约束适用范围和动作作用接口。

| 修改前 | 修改后 | 原因 |
| --- | --- | --- |
| 6  Constraints: observation and handling actions | 6  Constraints: monitoring and response actions | 用直观的监测与响应表述机制。 |
| Observe resource state and task context together, then return actions through the appropriate interfaces. | Constraint rules monitor device state and task context to determine response actions. | 补足主语，删除 appropriate interfaces 的模糊指代。 |
| Device state, attributes / Task context | Device state / Attributes / Task context | 修正混用逗号与斜线造成的层次不清。 |
| Laboratory baseline / Task rules | Laboratory rules / Task rules | baseline 容易误解为基准值，改为实际适用范围。 |
| Handling actions | Response actions | 明确指约束触发后的响应动作。 |
| Device actions / Warn / Alarm / Abort | Device actions / Alerts / Task abort | 统一为名词并区分设备操作、告警和任务中止。 |
| Actions → Node / State-machine interfaces | Actions use node / state machine interfaces | 明确动作通过模型接口执行。 |
| Handled by the device state machine | State machine checks device limits | 解释 intrinsic constraints 的具体含义和检查方。 |
| Laboratory baseline constraints | Laboratory constraints | 消除 baseline 的歧义。 |
| Continuously supervise the laboratory | Apply during laboratory operation | 规则适用范围比把规则拟人化为 supervise 更准确。 |
| Active during the associated task | Apply during the specified task | 明确任务约束的适用范围。 |

## 第 8 张（正文第 7 页）

区分 Adapter command 与 Device command，说明按契约生成命令的主体，并强调工作流可选而设备模型必经。

| 修改前 | 修改后 | 原因 |
| --- | --- | --- |
| Workflows organize steps; state machines and Adapters execute device requests and return feedback. | Device requests pass through the state machine and Adapter. Feedback returns through the model. | 删去重复的工作流组织说明，突出本页执行与反馈方向。 |
| Exec. node | Task node | 避免不必要缩写。 |
| Exec. node | Task node | 避免不必要缩写。 |
| Exec. node | Task node | 避免不必要缩写。 |
| Run<br>signal | Start<br>signal | 用 Start signal 明确该箭头示意启动执行的信号。 |
| Data<br>status | Data /<br>Status | 修正两个名词缺少关系造成的误读。 |
| Read variables, interface signals, and lifecycle<br>Update variables and state; emit signals | Evaluate variables, signals, and lifecycle state<br>When conditions hold, update values or send signals | 显式补上条件满足才执行动作，去掉分号。 |
| Control request / Parameters | Operation request / Parameters | 明确请求针对设备操作。 |
| Execution state / Data | Execution status / Data | 区分执行状态反馈与状态机内部状态。 |
| Capability model → state machine: operation + Adapter contract | Capability model supplies operations and Adapter contract | 以句子说明契约来源，收短原有符号拼接。 |
| Check request and state | Validate request | 明确检查两者是否相容。 |
| Generate command from contract | Use Adapter contract | 配合 Command output 标题，明确契约的使用方并避免溢出。 |
| Events and telemetry → Update device state and attributes | Device feedback updates model state and attributes | 删除不必要的术语堆叠，保留反馈更新关系。 |
| Standard command | Adapter command | 不再暗示行业标准，指按契约形成的 Adapter 输入命令。 |
| Upper-layer command → Device protocol | Adapter command → Device command | 区分状态机输出与 Adapter 输出，避免将命令与协议类型混为一谈。 |
| Device data → Events / Telemetry | Device data → Model feedback | 用输出反馈的作用解释转换。 |
| Physical device: perform operations and return measurements and status | Physical device: execute commands and return device feedback | 明确物理设备实际执行命令并返回运行状态。 |
| People, workflows, and LLMs share the device-model entry point, state rules, and constraints. | People and LLMs may omit the workflow, but every request must enter the device model. | 补充工作流可选但设备模型必经这一核心区别，避免重复列举。 |

## 第 9 张（正文第 8 页）

减少时序消息的冗长描述，区分反馈消息与状态更新动作，区分停止设备与中止任务。

| 修改前 | 修改后 | 原因 |
| --- | --- | --- |
| 8  Implementation and case study: reactor heating | 8  SmartLab 2.0 case study: reactor heating | 将实现载体直接放进标题，减少空泛词。 |
| SmartLab 2.0: workflow and state-machine engines execute models; an MQTT Adapter handles communication. | Workflow and state machine engines run the models. An MQTT Adapter connects to the device. | 拆开实现职责，删除分号和与标题重复的信息。 |
| 1  Heat request + parameters | 1  Heating request | 图内保留请求名称，参数说明留在演讲备注中。 |
| State check and request handling | Check state and build command | 与状态机检查并按契约生成命令的职责对应。 |
| 2  Standard heat command | 2  Adapter command | 避免 Standard 暗示行业标准。 |
| 3  Device heat command | 3  Device command | 统一 heating 用词。 |
| During execution: continuous feedback and constraint observation | Continuous device feedback and constraint monitoring | 删除 loop 已表达的执行期间信息。 |
| 5  Telemetry / State update | 5  State / Measurements | 说明消息携带的数据，不把状态更新动作当成消息名。 |
| 6  Execution state / Data | 6  Execution status / Data | 统一执行反馈术语。 |
| [Normal] Completion conditions hold and progression is allowed | [Normal] Completion conditions hold and constraints permit the next step | 明确是谁规定允许推进。 |
| Complete node → Emit activation signal → Next step | Mark node complete and activate the next step | 删除不必要的符号链，保留动作关系。 |
| [Exception example] A stop or abort rule is triggered | [Exception] A constraint triggers a stop or abort | 明确异常处理的触发方。 |
| Device action: stop / abort request to the state machine | Request the state machine to stop the operation | 用明确动作替代名词堆叠，并区分设备停止与任务中止。 |
| Task action: abort through the node interface | Abort the task through the node interface | 删除重复的 Task action。 |
| Completion conditions govern progression; temperature violations or device faults trigger constraint handling. | Temperature feedback alone does not complete the node. Its completion conditions must also hold. | 避免复述整个图，强调案例最易误解的完成判定。 |

## 第 10 张（正文第 9 页）

说明 Adapter 为设备创建并复用虚拟实例，两种模式共用上层模型，上位机接受模拟结果后才开始真机执行。

| 修改前 | 修改后 | 原因 |
| --- | --- | --- |
| A virtual device instance inside the Adapter runs the simulation; upper-level models and rules stay the same. | The Adapter simulates each device using a virtual instance with the same command and feedback interface. | 明确模拟对象与接口一致性，删除分号及重复说明。 |
| Propose / Revise plan | Propose a plan | 上框提出方案，返回箭头表示修改方案，避免重复。 |
| Analyze feedback | Evaluate feedback | 用 Evaluate 表达根据反馈评估方案。 |
| Revise steps, parameters, requests | Identify changes to the plan | 说明分析结果，避免与上方重复列举修改动作。 |
| Feedback refines the plan | Revise the plan | 反馈由大模型分析后用于修改，不把反馈拟人化。 |
| Organize steps and data<br>Nodes issue device requests | Define steps and data flow<br>Nodes request operations | 明确模型表示的数据依赖与节点职责。 |
| Handle request / Issue command | Check request / Send command | 用完整动作关系替代斜线堆叠。 |
| State checks / Transition actions | State rules / Adapter contract | 明确请求处理使用状态规则与 Adapter 契约。 |
| Generate workflow | Generate a workflow | 补全英语冠词。 |
| Invoke capability | Request an operation | 更直接地表达运行时操作请求。 |
| State / Data feedback | State and data feedback | 明确这是状态和数据共同构成的反馈。 |
| State, data, constraint results | Device feedback and constraint results | 避免状态与数据再次重复拆分。 |
| Execute in the selected mode | Execution mode set by the host | 明确模式由上位机决定，Adapter 不自行切换。 |
| Create once at simulation start | Create one instance per device | 补全创建对象和对应关系。 |
| One instance for the full run<br>Respond and evolve state<br>Return simulated data / status | Reuse for the full simulation<br>Run commands, update state<br>Return simulated device data | 明确复用虚拟实例、响应命令及模拟输出。 |
| Map commands / Convert feedback | Translate commands and feedback | 清楚说明真机模式双向转换职责。 |
| Execute and return actual data | Execute and return feedback | 补全执行对象，删去 physical device 已表达的 actual。 |
| Simulation feedback uses the same model updates and constraint checks. | Both modes use the same models, interfaces, and constraint checks. | 避免把反馈当成使用模型的主体，说明两种模式共用上层机制。 |
| Refine the plan through simulation; the host then initiates hardware execution after acceptance. | The host starts hardware execution only after accepting the simulation results. | 明确 acceptance 的主体与对象，并保留先模拟后真机的条件。 |

## 第 11 张（正文第 10 页）

用具体的模型行为解释 execution semantics，结论集中于模型定义与执行机制复用。

| 修改前 | 修改后 | 原因 |
| --- | --- | --- |
| Thank you. Questions are welcome. | Thank you. I welcome your questions. | 改为自然的口头结束语。 |
| Resources, workflows, and constraints describe laboratory task execution. | Resource, workflow, and constraint models describe task execution. | 用具体对象解释统一建模。 |
| Variable spaces, interfaces, triggers, and state machines define execution. | Rules define when actions run and how task and device states change. | 说明 execution semantics 的含义，避免再次列举组件名称。 |
| People, workflows, and LLMs use the same path, with a simulation extension. | Every request uses the device model and Adapter in either execution mode. | 明确共同链路及两种执行模式，删除笼统的 extension。 |
| Models describe the laboratory and its tasks; shared semantics drive execution. | Laboratory models supply device and task definitions to a reusable execution mechanism. | 以模型与执行机制的分工收束贡献，去掉分号及重复口号。 |

## 演讲备注同步

- 保留 Adapter mapping contract 这一契约术语，明确它规定模型操作到 Adapter 命令的映射。
- 明确状态机根据契约形成发送给 Adapter 的命令，Adapter 再生成设备指令。
- 与页面统一 laboratory constraints、response actions、port connections。
- 开场使用 Hello, everyone，避免预先假设上午或下午。
- 本次只同步与页面修改相关的措辞，保留原有演讲逻辑与技术内容。
