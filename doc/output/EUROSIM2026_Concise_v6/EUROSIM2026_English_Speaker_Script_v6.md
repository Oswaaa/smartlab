## Title slide

Hello, everyone.

My name is Tao Yu, and today I will present our work, “A Task-Oriented Executable Modeling Framework for Smart Chemical Laboratories.”

This work examines how a laboratory task moves from a proposed intent to actual execution.

We represent devices, task logic, and operating rules as models and execute them according to explicitly defined semantics.

As LLMs become involved in laboratory decision-making and control, we also need to define how their requests enter device execution.

## 1 Outline

The presentation has six parts, starting with the background and the execution-boundary problem.

I will then introduce the overall modeling framework and explain the resource, workflow, and constraint layers.

Next, I will show how these models work together along a shared execution path.

A reactor-heating task in SmartLab 2.0 illustrates the implementation and a complete execution sequence.

Finally, I will discuss the simulation feedback loop and summarize the work.

## 2 Background and execution boundaries

As large language models, or LLMs, advance, smart chemical laboratories are gradually moving from automated workflow execution toward LLM involvement in decision-making and control.

Regardless of whether an execution intent comes from a person, a workflow, or an LLM, every task execution must make four aspects explicit.

We need to explain how resources are organized and accessed, how device state evolves, how data is generated and transferred, and whether execution stays within the allowed limits.

These questions arise from task execution itself, even without an LLM.

LLM involvement makes execution boundaries a more pressing issue.

What control requests may an LLM make, and under what conditions can those requests be executed?

Our principle is that LLMs must not have a privileged bypass or a separate route directly to a physical device.

All control requests must use the same execution mechanism.

We therefore need an executable framework that models resource organization, state evolution, data transfer, and boundary checks together.

The next question is how to organize these models.

## 3 Unified modeling framework

We start with a laboratory task and identify what needs to be modeled.

A task uses devices, so we need to describe their capabilities, attributes, and current states.

These execution objects are represented by resource models.

A task also contains steps with an order, data dependencies, and conditions for progression.

These task processes are represented by workflows.

Execution must follow operating rules that evaluate boundaries using device state and task context.

These requirements are represented by constraint models.

We therefore organize the framework by modeling responsibility into resources, workflows, and constraints.

The four execution questions do not correspond mechanically to four separate layers.

The resource layer represents device capabilities and state, the workflow layer organizes steps and data relationships, and the constraint layer defines execution boundaries.

State and data span the layers, while the cross-layer protocol, Pi, provides shared definitions for control signals, data types, port connections, and execution feedback.

In the diagram, the workflow is on the left, device resources are on the right, and constraints are at the bottom.

A workflow node binds to a resource capability, sends requests during execution, and receives state and data feedback.

Capability binding is a relationship established in the model, while a control request is a runtime interaction.

Constraints observe both node context and device state and return response actions through model interfaces.

The framework defines both the objects used to represent a laboratory and the interactions between them.

Specific devices and task logic are expressed in models that run under the common execution semantics.

## 4 Resource layer

The resource layer represents each device as a device object model containing a capability model and a state machine.

The stacked outlines indicate that the layer can contain multiple device objects.

The capability model describes what the device can do.

It defines device attributes, available capabilities, operation parameters, interfaces, the Adapter mapping contract, and intrinsic constraints.

For example, a reactor provides a heating capability, temperature is an attribute, and a heating request specifies a target temperature.

The capability model supplies operation definitions and the mapping contract to the state machine.

The state machine uses this contract to generate a command in the format agreed with the Adapter.

The Adapter maps the received command to instructions in the physical device's protocol.

The state machine describes how a request is executed.

It checks the request against capability requirements and the current state, applies the relevant state transitions and actions, and sends commands through its output interface.

Device events and measurements return through the interfaces and update the model's state and attributes.

Intrinsic constraints are handled locally through the device capability definitions and state rules.

The device model therefore describes both device capabilities and the control process.

Requests from people, workflows, and LLMs all enter through this model.

## 5 Workflow layer

A workflow organizes the steps and data dependencies of the entire task.

At the top of the diagram, the preceding node, the current node, and the subsequent node are connected through interfaces that govern progression.

The expanded node can bind to a device capability and send operation requests to the device model.

The components inside the node work together as one unit.

The variable space stores task variables and links the required device data through attribute mappings.

The node lifecycle describes its execution stage and result.

Control interfaces carry activation, execution, and termination signals, while data ports define input and output data relationships.

Triggers read variables, interface signals, and lifecycle state.

When their conditions hold, they update variables or state and send the appropriate signals.

For example, a heating node receives an activation signal and issues a heating request when its preconditions are satisfied.

Temperature feedback updates the variable space, and triggers evaluate the completion conditions.

The node completes and activates the next step only when those conditions hold and continued execution is allowed.

Receiving feedback does not by itself mean that the node has completed.

These model components make task logic explicit, and the execution mechanism runs according to their definitions.

## 6 Constraint layer

The constraint layer determines whether execution stays within the allowed limits.

The workflow provides node lifecycle information, task variables, and execution context.

The resource layer provides device state, attributes, and measurements.

Information from both sides enters the observation space for evaluation by the constraint rules.

A triggered rule may produce a device action, a warning, an alarm, or a task abort.

These actions return through workflow or resource-model interfaces, so they remain within the existing execution mechanism.

Device-related actions are issued by the state machine through the Adapter, while task-related actions are handled by the node mechanism.

We distinguish intrinsic constraints, laboratory-wide constraints, and task constraints.

Intrinsic constraints are handled locally by the device state machine.

Laboratory-wide constraints provide baseline rules that continuously monitor laboratory operation.

Task constraints apply during execution of a specific task.

The diagram emphasizes cross-layer observation and response for the latter two types.

The constraint layer continuously observes execution rather than acting as a serial forwarding stage for requests.

## 7 Shared execution path

This slide shows how the models work together to execute a device operation.

On the left, workflows organize task steps horizontally, while each device operation proceeds vertically through the device model, the Adapter, and the physical device.

Control signals and commands travel downward, and device state and data return upward.

On the right, one workflow node and its corresponding device execution unit are expanded.

The node's triggers evaluate variables, interface signals, and lifecycle state before issuing a control request with operation parameters.

The capability model provides operation definitions and the Adapter mapping contract to the device state machine.

The state machine handles the request using the current state, performs the transition and its actions, and uses the contract to generate an upper-level command in the agreed format.

It then sends that command through its output interface to the Adapter.

The Adapter maps the received command to device instructions, which the physical device executes.

The Adapter also converts returning device data into events or telemetry.

The device model updates its state and attributes, and the node receives execution status and the data it needs.

The node's trigger conditions and actions determine completion and progression to the next step.

Constraints observe resource state and task context and act through node or state-machine interfaces when required.

The workflow route shown here is one possible source of a device request.

A person or an LLM may request a device operation directly without first creating or executing a workflow.

However, every device request must pass through the device model and state machine before reaching the Adapter.

State-transition rules define how the model responds to a request and which actions accompany a transition.

Constraints specify limits and conditions that execution must satisfy.

These concepts are related because constraints can also be implemented as rules, including within the state machine.

All request sources use the same model-based handling, with no privileged bypass.

## 8 Reactor heating case study

In the SmartLab 2.0 prototype, workflow and state machine engines execute the models, and an MQTT Adapter handles device communication.

The reactor model defines its heating capability, attributes, and state machine.

The heating node binds to that capability, stores task parameters, and defines its start and completion conditions.

The constraint model specifies rules that apply during execution.

Reading the sequence from top to bottom, the node first sends a heating request with parameters.

The state machine checks the request and current state and generates a heating command according to the Adapter mapping contract.

The Adapter converts this command into a device instruction and sends it to the reactor.

The loop represents repeated temperature and operating-status feedback during execution.

After conversion by the Adapter, this feedback updates the device model and provides the node with execution status and relevant data.

Resource state and node execution context are also observed by the constraint layer.

Each feedback update can trigger model updates and condition evaluation.

When completion conditions hold and continued execution is allowed, the node completes and sends a signal to activate the next step.

If a temperature violation or device fault triggers a stop or abort rule, device handling uses the state-machine interface and task handling uses the node interface.

Warnings and alarms follow their own rule definitions rather than necessarily aborting the task.

Requests, execution, feedback, completion checks, and exception handling all follow interactions defined in the models.

## 9 Simulation feedback loop

This extension uses simulation feedback to revise experimental plans while preserving the same device execution path.

The LLM can generate a workflow model or directly request an operation on a device.

Both routes enter the device model and reach the Adapter through the state machine.

At the start of a simulation, the Adapter creates a virtual device instance corresponding to the physical device instance.

The same virtual instance is used throughout that simulation run.

It receives commands, evolves its simulated state, and returns simulated measurements and operating status.

This allows successive operations to act on a continuous device state.

The upper-level system processes the returned information as device data.

Device models update normally, workflow nodes evaluate triggers and advance when needed, and constraints continue to evaluate resource state and task context.

The resulting state, data, and constraint outcomes return to the LLM for analysis.

The LLM can revise the steps, parameters, or control requests and simulate again through the same path.

This closes the feedback loop used to optimize the experimental plan.

Once simulation results have been confirmed acceptable, the host system initiates hardware execution and selects the Adapter's hardware mode.

The Adapter does not determine whether the simulation has passed or initiate hardware execution on its own.

Only hardware mode connects to the physical device, mapping commands and converting real feedback.

Both modes share the upper-level models, state-transition rules, feedback interfaces, and constraint checks.

The difference is whether commands act on a virtual instance inside the Adapter or on the physical device.

Simulation therefore supports plan refinement before hardware execution while keeping LLM device control within the shared execution mechanism.

## 10 Conclusions

The work has three main contributions: unified modeling, explicit execution semantics, and a shared execution path.

Resource, workflow, and constraint models describe execution objects, task processes, and operating limits, with a cross-layer protocol connecting their interactions.

Variable spaces, lifecycles, interfaces, triggers, and state machines explain at the model level how tasks progress, data is linked, and devices respond.

Requests from people, workflows, and LLMs use the same device model and Adapter mechanism, which also supports the simulation feedback loop.

The system provides an explicit modeling language and execution semantics.

Specific laboratory devices and task logic are expressed as models, while the common execution mechanism follows the structures, states, and rules defined in those models.

This lets us explain why an operation occurs, how far it has progressed, and what determines its next step.

An LLM may propose plans and control requests, but their execution is always handled by the shared model, state, and constraint mechanisms.

Thank you for your attention, and questions are welcome.