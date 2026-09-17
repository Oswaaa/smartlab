# EUROSIM 2026 English Speaker Script

Tao Yu — A Task-Oriented Executable Modeling Framework for Smart Chemical Laboratories

This script follows the 11-slide deck. The printed slide numbers run from 1 to 10 after the title slide. Read the paragraphs aloud; use the diagram cues to guide pointing and pauses.

The spoken text contains approximately 1,738 words. At 125–140 words per minute, allow about 12–14 minutes before questions, plus any extended pauses.

## Title slide

Diagram cue: Introduce the title and the modeling problem.

Good morning, everyone. My name is Tao Yu, from Beijing Information Science and Technology University. Today I will present our work, A Task-Oriented Executable Modeling Framework for Smart Chemical Laboratories.

We focus on how a laboratory task moves from an intention to device execution. Our aim is to describe devices, task logic, and operating rules through models, and to make their execution semantics explicit. This becomes especially relevant when large language models participate in laboratory decision-making and control.

## 1  Outline

Diagram cue: Move from background to modeling, execution, the case, and simulation.

I will begin with the background and the execution-boundary problem. I will then introduce the overall modeling framework and explain its resource, workflow, and constraint layers.

Next, I will show how these models cooperate along a shared device execution path. A reactor-heating case in SmartLab 2.0 illustrates the mechanism. Finally, I will discuss a simulation feedback loop as an extension and summarize the main contribution.

## 2  Background: execution boundaries with LLMs

Diagram cue: Point to the four concerns, then the common-boundary principle.

Laboratory automation is expanding from predefined workflows toward decision-making and control involving large language models, or LLMs.

Regardless of whether an intention comes from a person, a workflow, or an LLM, every task requires four aspects to be made explicit. How are resources organized and used? How do device states evolve? How are data generated and passed between steps? And does execution remain within the allowed boundaries?

LLMs make the boundary question more pressing. What requests may an LLM make, and under what conditions may those requests execute? Our principle is that an LLM must not have a privileged path directly to a device. Its requests must enter the same execution mechanism as other requests.

This motivates an executable framework that brings resource organization, state evolution, data flow, and boundary checks together. The next question is how to organize these modeling responsibilities.

## 3  Unified modeling framework

Diagram cue: Read left workflow, right resources, lower constraints; distinguish binding from requests.

We organize the framework by examining what a laboratory task needs to express. The objects used to perform the task become resource models. The order of operations, data dependencies, and progression conditions become a workflow model. The rules that define allowed execution become constraint models.

The diagram therefore has three modeling layers. The workflow layer is on the left, the resource layer is on the right, and the constraint layer observes both from below. The cross-layer protocol, Pi, defines their control signals, data types, port relations, and execution feedback.

The four execution concerns do not map mechanically to four separate layers. Resources represent device capabilities and states. Workflows organize steps, node lifecycles, and data relationships. Constraints define execution boundaries. State and data are shared concerns across these layers.

Notice the distinction between capability binding and runtime interaction. A node is bound to a device capability. During execution, it sends a request and receives state and data feedback. Constraints observe the relevant model values and return handling actions through model interfaces.

This gives the system both an explicit modeling structure and a common basis for execution. Let us first look at the resource layer.

## 4  Resources: capabilities and state machines

Diagram cue: Capability model first, then the state machine and shared entry.

The resource layer represents each device as a device object model. The stacked outlines indicate multiple device objects, while the front object shows the common internal structure: a capability model and a state machine.

The capability model describes what the device can do. It defines attributes, available operations, parameters, interfaces, the Adapter mapping contract, and intrinsic constraints. For a reactor, heating is a capability, temperature is an attribute, and a heating request includes a target temperature. The contract links a model-level operation to the Adapter interface.

The state machine describes how a request is executed. It checks the request against the current state and the capability definition, applies the relevant transition actions, and issues commands through its output interface. Returned events and measurements update the device model.

People, workflows, and LLMs all enter through this device model and follow the same rules. We now need to organize individual operations into a complete task.

## 5  Workflows: nodes, interfaces, and triggers

Diagram cue: Read the node chain first, then the four parts of a node.

The workflow layer organizes task steps and their data dependencies. At the top, a previous node, an execution node, and a next node are connected through their progression relationships. The execution node can bind to a device capability and request an operation.

The lower part expands that node. Its variable space holds task values and links to relevant device attributes. Its lifecycle represents the execution phase and outcome. Control interfaces carry signals, while data ports describe inputs and outputs.

The trigger connects these parts. It reads variables, interface signals, and lifecycle information. When a condition holds, it performs the defined action, such as updating a variable or state, issuing a request, or activating a subsequent node.

For example, a heating node receives an activation signal and requests heating when its preconditions hold. Temperature feedback updates the node's variables. The trigger then evaluates the completion condition. Receiving feedback alone does not mean the task is complete.

These elements form one semantic unit. The task logic is expressed in the model, and the execution mechanism follows that logic.

## 6  Constraints: observation and handling actions

Diagram cue: Follow observations down to rules, then actions back to model interfaces.

The constraint layer observes execution and responds when a rule is triggered. The workflow provides node lifecycle information, task variables, and execution context. The resource layer provides device state, attributes, and measurements.

These values form the observation space. Constraint rules evaluate the observed values in context and produce handling actions. Depending on the rule, an action may operate on a device, issue a warning or alarm, or abort a task. Actions return through node or state-machine interfaces. Device actions therefore continue through the normal Adapter path.

We distinguish three kinds of constraints. Intrinsic constraints belong to the device capability and state rules and are handled locally by the device state machine. Laboratory baseline constraints continuously supervise laboratory operation. Task constraints apply during the associated task.

The constraint layer is not a serial forwarding stage through which every command must pass. It observes execution across layers and returns actions when required. Having defined the objects, task process, and rules, we can now connect them through the execution path.

## 7  Shared device execution path

Diagram cue: Trace commands downward and feedback upward; clarify the optional workflow route.

This diagram shows how the models cooperate during a device operation. On the left, the workflow organizes steps horizontally. An individual operation proceeds through the device model, the Adapter, and the physical device. Requests and commands move downward; state and data feedback move upward.

On the right, the workflow trigger issues a request with operation parameters. The capability model supplies the operation definition and the Adapter mapping contract to the device state machine. The state machine checks the request against the current state, applies the transition actions, and generates the agreed upper-layer command according to that contract. The Adapter then maps the command sent by the upper layer into the device protocol or device instruction.

Feedback returns through the corresponding interfaces. The Adapter converts device data into events and telemetry. The device model updates its state and attributes, and the workflow node receives the relevant execution state and data. Node conditions determine whether execution is complete and whether the next step can begin. Constraints continue to observe and act through the model interfaces.

The workflow route shown here is one example. A person or an LLM may also request a device operation directly, without first using a workflow node. However, the request must still pass through the device model and state machine before reaching the Adapter. This is the shared execution boundary introduced at the beginning.

## 8  Implementation and case study: reactor heating

Diagram cue: Read top to bottom, then loop, normal progression, and exception handling.

We illustrate the mechanism with a reactor-heating task in SmartLab 2.0. Workflow and state-machine engines execute the models, and an MQTT Adapter handles device communication. The heating node is bound to the reactor's heating capability and contains its task parameters and completion conditions.

Read the sequence from top to bottom. First, the node sends a heating request with parameters. The state machine checks the request and current state, then sends a standard heating command to the Adapter. The Adapter sends the device command to the reactor.

The loop represents continuous feedback. Temperature and operating status return through the Adapter, update the device model, and become available to the node. Resource state and node context are also observed by the constraint layer.

The lower part separates normal progression from an exception example. When completion conditions hold and progression is allowed, the node completes and activates the next step. If a stop or abort rule is triggered, the constraint mechanism acts through the state-machine and node interfaces. This connects request handling, feedback, completion, and exception handling in one modeled execution process.

## 9  Extension: simulation feedback loop

Diagram cue: Trace optional workflow or direct request, the virtual instance, and the return loop to the LLM.

This extension uses simulation feedback to refine an experimental plan while preserving the same device execution path.

On the left, an LLM may generate a workflow model or issue a direct device request. Both routes enter the device model and reach the Adapter through its state machine. The workflow is optional; the device model is the common entry point.

At the start of a simulation, the Adapter creates a virtual device instance corresponding to the physical device instance. The same virtual instance is used throughout that simulation run. It responds to commands, evolves its state, and returns simulated measurements and operating status.

The upper layers process this feedback as device data. The device model updates normally. If a workflow is involved, its nodes evaluate triggers and advance normally. Constraint rules also continue to observe the resource state and task context.

The resulting state, data, and constraint outcomes return to the LLM. It can revise the steps, parameters, or device requests and simulate the revised plan again. This is the feedback loop used to improve the experimental plan.

After the host system accepts the simulation result, it initiates hardware execution and selects the Adapter's hardware mode. The Adapter does not decide whether the simulation has passed, and it does not switch to hardware execution on its own. Only hardware mode is connected to the physical device. The upper-level models, interfaces, and execution rules remain the same.

## 10  Conclusions

Diagram cue: Close by connecting explicit semantics to the execution-boundary problem.

To conclude, the framework provides a unified way to model laboratory task execution. Resources describe device capabilities and states, workflows describe task steps and data relationships, and constraints describe operating boundaries.

The system also makes execution semantics explicit through variable spaces, interfaces, triggers, lifecycles, and state machines. Specific laboratory resources and task logic are expressed in models, while a common mechanism executes those models. This gives the framework a clear structure and makes the execution process explainable at the model level.

Finally, people, workflows, and LLMs share the same device execution path. The simulation extension uses this path to obtain feedback and revise a plan before hardware execution. This brings us back to the original question: an LLM may propose what to do, while the shared model and constraint mechanisms govern how the request executes.

Thank you for your attention. I welcome your questions.

## Terminology for rehearsal

| Chinese term | English used in the talk |
| --- | --- |
| 设备对象模型 | device object model |
| 能力模型 | capability model |
| 状态机 | state machine |
| 变量空间 | variable space |
| 节点生命周期 | node lifecycle |
| 控制接口与数据端口 | control interfaces and data ports |
| 触发器 | trigger |
| 内禀约束 | intrinsic constraints |
| 实验室基线约束 | laboratory baseline constraints |
| Adapter 映射契约 | Adapter mapping contract |
| 虚拟机实例（对应设备） | virtual device instance |
| 真机执行 | hardware execution |
| 上位机 | host system |
| 统一执行链路 | shared device execution path |

Read “LLM” as the three letters, “MQTT” as four letters, and Π as “pi.” In this talk, a virtual device instance means the stateful simulated counterpart of a device instance.
