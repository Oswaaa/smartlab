# EUROSIM 2026现场问答准备

下面的问题按“先给结论，再补机制”的方式组织。现场回答建议控制在 30–60 秒；如果评委继续追问，再展开第二段。

## 1. What is the main contribution of this work?

**Answer:** The main contribution is an executable modeling framework with explicit execution semantics for smart chemical laboratories. Resources, workflows, and constraints are modeled together, and device requests from people, workflows, or LLMs follow the same path through the device model, state machine, and Adapter. This makes the control boundary explicit at the model level.

中文提示：贡献不是单独提出一个 LLM 控制器，而是把资源、流程、状态和约束放到一套可执行语义中，并让不同意图共用同一设备执行链路。

## 2. Why do you need a device object model instead of controlling the device directly?

**Answer:** Direct control would make the workflow bypass the modeled device state. The device object model provides the capability definition and the state machine that checks requests, performs state transitions, and updates state from feedback. The Adapter then handles protocol translation. This keeps the modeled state and the physical execution on one traceable path.

中文提示：流程不能绕过设备模型直接连物理设备，否则资源、状态、数据和约束无法同路检查。

## 3. What is the role of the capability model, and how is it different from the Adapter?

**Answer:** The capability model defines what an operation means and provides the operation definition and Adapter mapping contract to the state machine. The state machine uses that contract to produce the agreed upper-layer command. The Adapter then maps the command sent by the upper layer into the device protocol or device instruction, and converts device feedback back into model-level data.

## 4. What does the state machine add to the device model?

**Answer:** It gives device operations an explicit lifecycle. It checks a request against the current state, applies the allowed transition, emits the command, and updates the state from returned events and measurements. Therefore, a request is not treated as an isolated function call; it is part of a modeled state evolution.

## 5. Is a workflow always required when an LLM controls a device?

**Answer:** No. An LLM may generate a workflow model, or it may issue a direct request for a device capability. The workflow is optional for that request, but the device model and state machine are mandatory. Both routes converge before the Adapter, so there is no privileged direct path to the device.

## 6. How are constraints different from the state machine?

**Answer:** Intrinsic device constraints are handled locally by the device state machine. The constraint layer handles broader observations that combine resource state with task context, such as laboratory baseline rules and task-specific rules. It observes execution and returns handling actions through the workflow or device-model interfaces; it is not a serial command-forwarding stage.

## 7. Does receiving feedback mean that a workflow node is complete?

**Answer:** No. Feedback updates the device model and the node variables. The trigger then evaluates the node's completion condition and context. Only when the condition is satisfied and progression is allowed does the node complete and activate the next step.

## 8. Where does simulation take place?

**Answer:** Simulation takes place inside the Adapter. At the beginning of a simulation run, the Adapter creates a virtual device instance corresponding to the physical device instance. The complete simulated run uses that same virtual instance, which receives commands, evolves its state, and returns simulated measurements and status.

## 9. Does the Adapter decide when simulation has passed and switch to hardware?

**Answer:** No. The host system decides the mode and the run sequence. The Adapter only executes according to the selected mode. The system first uses simulation feedback to revise and evaluate the plan. After the host accepts the result, it starts a hardware run with the same upper-level models and interfaces, while only the hardware mode connects to the physical device.

## 10. Is the simulation path different from the hardware path?

**Answer:** The upper-level path is the same: device model and state machine to Adapter, with the same feedback interfaces and constraints. The difference is where the Adapter applies the command. In simulation mode it acts on the virtual device instance; in hardware mode it maps the command to the physical device.

## 11. What does the cross-layer protocol Π specify?

**Answer:** Π specifies the common vocabulary and interaction rules across the layers: control signals, data types, port relationships, and execution feedback. It allows the workflow, resource, and constraint models to interact without relying on ad hoc connections.

## 12. What is new about the workflow node mechanism?

**Answer:** A node is defined as a semantic unit rather than only a code callback. Its variable space, lifecycle, control and data ports, and triggers are modeled together. The trigger reads these elements and turns conditions into actions, so both node-to-device interaction and node-to-node progression have explicit semantics.

## 13. How do you claim reusability across laboratories?

**Answer:** The execution mechanism is shared, while laboratory-specific devices and task logic are expressed as models. A new laboratory can therefore be represented by its workflow and device models within the same semantic structure. The framework does not require rewriting the overall execution mechanism for each task.

中文提示：强调“模型是蓝图，执行机制是机器”，但现场不要绝对化为任何新设备都完全不需要扩展。

## 14. How do you validate the framework?

**Answer:** We instantiate the models in SmartLab 2.0 with a reactor-heating task. The sequence shows request handling, state transitions, Adapter communication, continuous feedback, completion checks, and constraint-triggered handling. The simulation extension then reuses the same path for plan refinement before hardware execution.

## 15. What happens if a device returns an unexpected or abnormal state?

**Answer:** The feedback is converted by the Adapter and used to update the device model. The state machine and constraint rules then evaluate the new state. Depending on the rule, the system may warn, alarm, stop a device operation, or abort the task through the corresponding model interface.

## 16. Can an LLM bypass constraints by issuing a low-level command?

**Answer:** No. The LLM does not receive a privileged Adapter or physical-device interface. Its request must enter the device model and state machine, where capability, state, and constraint semantics apply. This is the boundary enforced by the framework.

## 17. What is the difference between the workflow model and the device model?

**Answer:** The workflow model describes the task process: steps, data dependencies, node lifecycle, and progression triggers. The device model describes an execution resource: capabilities, attributes, state transitions, and feedback. The workflow requests operations from device models; it does not directly operate physical devices.

## 18. How does the LLM use simulation feedback?

**Answer:** The LLM receives state, data, and constraint outcomes from the simulated run. It analyzes them and revises the experimental steps, parameters, or device requests. The revised plan is executed again through the same model and Adapter path until the host accepts the plan for hardware execution.

## 19. What is the limitation of the current presentation?

**Answer:** The presentation focuses on the modeling language and execution semantics. The case study demonstrates the mechanism for reactor heating, while broader empirical evaluation across more devices and tasks is a natural next step.

中文提示：如果被追问实验规模，可以诚实说本次汇报重点是框架和机制，案例用于展示链路如何落地。

## 20. If you had to summarize the work in one sentence, what would you say?

**Answer:** We model laboratory resources, task workflows, and execution constraints with explicit semantics, then make people, workflows, and LLMs use the same stateful device execution path from request to feedback.
