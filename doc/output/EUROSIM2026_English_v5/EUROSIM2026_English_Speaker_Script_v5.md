# EUROSIM 2026 English Speaker Script — Chinese-source translation, v5

## Title slide

Hello, everyone. My name is Tao Yu. Today I will present our work, “A Task-Oriented Executable Modeling Framework for Smart Chemical Laboratories.”

This work concerns the process from proposing a laboratory task to executing it. We ask how to represent devices, task logic, and operating rules as models, and how to run them according to explicitly defined execution semantics. As large language models begin to participate in laboratory decision-making and control, we also need to explain how their requests enter device execution. I will introduce the background, the modeling framework, the execution mechanism, and a concrete example.

## 1  Outline

The presentation has six parts. First, I will explain why execution boundaries become a more pressing issue when large language models are involved. Then, starting from what a task execution needs to describe, I will introduce a framework consisting of resources, workflows, constraints, and a cross-layer protocol.

I will first show the overall framework, then explain the components and responsibilities of each layer. Next, I will connect these layers through the execution path and explain how people, workflows, and LLMs share the device execution mechanism. Finally, I will use a reactor-heating task in the SmartLab 2.0 prototype to illustrate the implementation, discuss the simulation feedback loop as an extension, and summarize the work.

## 2  Background: execution boundaries with LLMs

As large language models, or LLMs, advance, smart chemical laboratories are gradually moving from automated workflow execution toward LLM involvement in decision-making and control.

Regardless of whether the execution intent comes from a person, a workflow, or an LLM, every task execution must clarify four aspects. How are resources organized and accessed? How does device state evolve? How is data generated and transferred? And does execution stay within the allowed limits? These questions arise from task execution itself.

LLM involvement makes execution boundaries a more pressing issue. What control requests may an LLM make, and under what conditions can those requests be executed? Our principle is that LLMs must not have a privileged bypass or a separate route directly to the physical device. All control requests must use the same execution mechanism.

We therefore need to model task execution in a unified way, so that resources, state, data, and execution boundaries can be described and handled within a shared mechanism. The next question is how to organize these models.

## 3  Unified framework: organizing task execution models

We start with a laboratory task and identify what needs to be modeled. First, a task uses devices. We need to describe what each device can do, its attributes, and its current state. These execution objects are represented by resource models. Second, a task contains steps. We need to describe their order, data dependencies, and conditions for progression. These task processes are represented by workflows. Third, execution must follow operating rules. These rules evaluate execution boundaries using device state and task context, and are represented by constraints.

The framework is therefore organized by modeling responsibility into resources, workflows, and constraints. The four execution questions do not correspond mechanically to four separate layers. The resource layer represents device capabilities and state. The workflow layer organizes task steps, node states, and data relationships. The constraint layer defines execution boundaries. State and data run across these layers. The cross-layer protocol, Pi, provides shared definitions for control signals, data types, port connections, and execution feedback.

In the diagram, the workflow is on the left, device resources are on the right, and constraints are at the bottom. A workflow node is first bound to a resource capability. During execution, it sends a request, and the resource model returns state and data. Capability binding and execution requests are shown separately to distinguish a relationship established in the model from an interaction that occurs at runtime. Constraints observe both node context and device state and return response actions through the model interfaces.

The framework defines both the objects used to represent a laboratory and the interactions between those objects. The devices and task logic of a specific laboratory are expressed in models, and the common mechanism executes them according to these semantics. I will now expand the resource layer to show how a device object represents both its capabilities and its operating process.

## 4  Resource layer: capabilities and state machines

The resource layer represents devices as device object models. The stacked outlines indicate that multiple device objects can be organized in this layer. One object is expanded to show its two components: a capability model and a state machine.

The capability model describes what the device can do. It defines device attributes, available capabilities, operation parameters and interfaces, the Adapter mapping contract, and intrinsic constraints. For example, a reactor provides a heating capability. Temperature is one of its attributes, and a heating request specifies a target temperature. The capability model supplies the operation definitions and the mapping contract to the state machine. The state machine uses that contract to generate a command in the format agreed with the Adapter. The Adapter then maps the received command to instructions in the physical device's protocol.

The state machine describes how a request is executed. It receives control signals and checks the capability requirements and current state to determine whether an operation is allowed. It applies the relevant state transitions and actions, then sends a command through its output interface. During execution, device events and measurements return through the interfaces and update the model's state and attributes. Intrinsic constraints are handled locally through the device capability definitions and state rules.

The device model therefore describes both device capabilities and the control process. Requests from people, workflows, and LLMs all enter through the device model and follow the same rules. Next, I will explain how these device operations are organized into a complete task.

## 5  Workflow layer: nodes, interfaces, and triggers

A workflow first organizes the steps and data dependencies of the entire task. At the top of the diagram, the preceding node, the current node, and the subsequent node are connected through interfaces that govern task progression. One execution node is expanded below. It can bind to a device capability and send operation requests to the device model.

The components within a node work together as one unit. The variable space stores task variables and links the required device data through attribute mappings. The node lifecycle describes its current execution stage and result. Control interfaces carry signals such as activation, execution, and termination. Data ports define input and output data relationships. Triggers read variables, interface signals, and lifecycle state. When their conditions hold, they update variables or state and send the appropriate signals.

Together, these components express task logic. For example, a heating node receives an activation signal from its predecessor and issues a heating request when its preconditions are satisfied. As temperature feedback enters the variable space, triggers evaluate the completion conditions. The node completes and sends an activation signal to the next step only when these conditions hold and continued execution is allowed. Receiving feedback does not by itself mean that the node has completed.

Task logic is thus expressed in the model through variable spaces, lifecycles, interfaces, and trigger rules. The execution mechanism runs according to those definitions. The next slide shows how resources and tasks are observed together during execution and how violations are handled.

## 6  Constraint layer: observing execution and responding

The constraint layer concerns whether execution stays within the allowed limits. The workflow provides node lifecycle information, task variables, and execution context. The resource layer provides device state, attributes, and measurements. Information from both sides enters the observation space for evaluation by the constraint rules.

The bottom of the diagram shows the observation space, constraint rules, and response actions. Rules evaluate conditions using the current device state and task stage. When a rule is triggered, the response may be a device action, a warning, an alarm, or a task abort. The return arrows connect to workflow or resource-model interfaces, showing that these actions are executed within the existing model mechanism. Device-related actions are issued by the state machine through the Adapter. Task-related actions are handled by the node mechanism.

We distinguish three types of constraints. Intrinsic constraints belong to device capabilities and state rules and are handled locally by the device state machine. Laboratory-wide constraints provide baseline rules that continuously monitor laboratory operation. Task constraints apply during execution of a specific task. The constraint layer shown here focuses on cross-layer observation and response for the latter two types.

The constraint layer is not a serial forwarding stage for requests. It continuously observes execution and returns response actions when needed. We have now described execution objects, task processes, and boundary rules. Next, I will connect these models through a concrete execution path.

## 7  Key mechanism: the shared execution path

This slide explains how the models work together to execute a device operation. On the left, the workflow organizes task steps horizontally. A device operation proceeds vertically through the device model, the Adapter, and the physical device. Execution signals and control commands travel downward. Device state and data travel upward.

On the right, one workflow node and its corresponding device execution unit are expanded. The node's triggers read variables, interface signals, and lifecycle state. When the conditions hold, the node sends a control request with operation parameters. The capability model provides operation definitions and the Adapter mapping contract to the device state machine. The state machine handles the request using the current state, performs the state transition and its actions, and uses the contract to generate an upper-level control command in the agreed format. It then sends that command to the Adapter through its output interface. The Adapter maps the received upper-level command to instructions in the device protocol, which the physical device executes.

Feedback returns through the corresponding interfaces. The Adapter converts device data into events or telemetry. The device model updates its state and attributes, and the node receives execution status and the data it needs. Whether the node completes or activates the next step is still determined by its trigger conditions and actions. Constraints observe resource state and task context and act through node or state-machine interfaces when required.

This brings us back to the background question. The diagram shows a request initiated by a workflow. A person or an LLM can also request a device capability directly, without first using a workflow node. However, every such request must enter the device model and reach the Adapter through its state machine. Different sources of intent share capability definitions, state rules, feedback interfaces, and constraint responses. They cannot bypass the model to control the physical device directly.

This connects the framework's modeling structure with its execution semantics. Next, I will illustrate a complete execution using a reactor-heating task.

## 8  Implementation and case study: reactor heating

This slide first identifies the implementation and then shows the task sequence. In the SmartLab 2.0 prototype, workflow and state machine engines execute the models, and an MQTT Adapter handles device communication. The reactor resource model defines its heating capability, device attributes, and state machine. The heating node binds to that capability, stores task parameters, and defines its start and completion conditions. The constraint model specifies the rules that apply during execution.

Read the sequence from top to bottom. First, the heating node sends a request with parameters. The device state machine checks the request and its current state. It handles the request according to the model rules and generates a heating command in the format defined by the Adapter mapping contract. The Adapter then maps that command to a device instruction and sends it to the reactor.

The loop represents continuous feedback. The reactor returns temperature and operating status. After conversion by the Adapter, this feedback updates the device model and provides execution status and relevant data to the node. At the same time, resource state and node execution context are observed by the constraint layer. Feedback may arrive repeatedly, and each update can update the model and trigger condition evaluation.

The lower branches distinguish normal progression from an exception example. When completion conditions are met and continued execution is allowed, the node completes and sends a signal to activate the next step. If a temperature violation or device fault triggers a stop or abort rule, the device is handled through the state-machine interface, and the task is handled through the node interface. This exception branch illustrates stop and abort rules. Other actions, including warnings and alarms, follow their respective rule definitions.

Requests, execution, feedback, completion checks, and exception handling all follow interactions defined in the models. We can now consider how the same path supports a simulation feedback loop.

## 9  Extension and outlook: the simulation feedback loop

The focus of this slide is using simulation feedback to revise an experimental plan while keeping the device execution path consistent. On the left, the LLM may generate a workflow model that organizes the steps, or it may directly issue a control request for a device. Both routes enter the device model and reach the Adapter through the state machine. The workflow participates when needed. The device model is the shared entry point.

Simulation takes place inside the Adapter. At the start of a simulation, the Adapter creates a virtual device instance corresponding to the physical device instance. The same virtual instance is used throughout that simulation run. It receives commands, evolves its simulated state, and returns simulated measurements and operating status. It therefore supports successive operations and the state changes that connect them.

The upper-level system processes these returns as device data. The device model updates its state and attributes. If the task includes a workflow, the nodes receive data, evaluate trigger conditions, and advance as usual. Constraints also evaluate resource state and task context in the usual way. State, data, and constraint results form the execution feedback returned to the LLM. The arrow back to the LLM and the loop within it represent the LLM using this feedback to revise steps, parameters, or control requests, then simulating again through the same path. This is the simulation feedback loop for optimizing experimental plans.

After simulation has been completed and the results have been confirmed acceptable, the host system initiates hardware execution and selects the Adapter's hardware mode. The Adapter follows the selected mode. It does not decide whether the simulation has passed, and it does not initiate hardware execution on its own. Only hardware mode connects to the physical device. It maps commands, sends device instructions, and converts real feedback.

Simulation and hardware execution share the upper-level models, state rules, and feedback interfaces. The difference is whether commands act on the virtual instance inside the Adapter or reach the physical device through hardware mode. First optimizing the plan through simulation and then initiating hardware execution keeps LLM device control within the shared execution rules and constraints.

## 10  Conclusions

There are three main points. First, unified modeling: resources, workflows, and constraints describe the laboratory's execution objects, task processes, and operating rules, with interactions connected through a cross-layer protocol. Second, explicit semantics: variable spaces, lifecycles, interfaces, triggers, and state machines explain at the model level how tasks progress, how data is linked, and how devices respond. Third, a shared execution path: device requests from people, workflows, and LLMs use the same device models and Adapter mechanism, which also provides the basis for the simulation feedback loop.

The system has an explicit modeling language and execution semantics. The devices and task logic of a specific laboratory are expressed in models. A common execution mechanism runs according to the structures, states, and rules defined in those models. This explains both which objects make up the system and, through their interactions, why an operation occurs, how far it has progressed, and what determines its next step.

This answers the execution-boundary question raised at the beginning. An LLM may propose plans and control requests, while their execution is always handled by the shared model, state, and constraint mechanisms.

Thank you for your attention. Questions are welcome.
