# 生成工作流草稿

按下列顺序完成「根据自然语言生成工作流模型草稿」。不要跳步。不要发布。

1. 阅读用户实验描述，列出需要的能力关键词（加热、搅拌、测温等）。
2. 调用 `list_device_catalog`。对不准的设备再调用 `get_device_model`。`deviceModelId` 必须来自工具返回值，禁止编造。
3. 按下方「作者最小结构」写出 document。必须有且仅有一个 `functionType=START` 和一个 `functionType=END` 的 `FUNC_NODE`。设备步骤使用 `DEV_NODE`，填写真实 `deviceModelId`，以及 `capability.capabilityName` 与 `capability.capabilityParameters`（键名来自目录 `parameters[].name`）。不要写 `_system` / `_systemKey` / `lifecycle` / `interfaces` / `actions`。
4. 控制流只走 `interfaceConnections`，端点用 `source`/`target`，不要写 `fromNodeId`/`toNodeId`。
   - 节点之间：`NODE_TO_NODE`，`Interface_workflow_out` → `Interface_workflow_in`，把 START → 设备节点 → … → END 串起来。
   - 每个 DEV_NODE 再补一对设备边：`NODE_TO_DEVICE`（节点 `Interface_state_out` → 设备 IN+WORKFLOW 接口）和 `DEVICE_TO_NODE`（设备 OUT+STATE 接口 → 节点 `Interface_state_in`）。设备接口名来自 `get_device_model` 的 `stateMachineInterfaces`。
5. 没有数据传递时 `ports` 写 `[]`，`portConnections` 写 `[]`。不要为控制流编造 `in`/`out` 端口。
6. 调用 `validate_workflow`。工具只返回 issues 摘要，不要抄它的 definition。若有 blocking issue，修改 document 后再校验，最多 3 次。
7. 没有 blocking issue 之后调用 `save_draft`。不要发布。

## 作者最小结构（照抄字段名）

```json
{
  "metadata": {
    "flowModelId": null,
    "flowModelName": "加热散热流程",
    "description": "将设备在30秒内加热到300℃，然后散热20秒。"
  },
  "nodes": [
    { "name": "start", "nodeType": "FUNC_NODE", "functionType": "START", "ports": [] },
    {
      "name": "heat",
      "nodeType": "DEV_NODE",
      "deviceModelId": 26,
      "capability": {
        "capabilityName": "capability_1",
        "capabilityParameters": { "parameter_1": 300, "parameter_2": 30 }
      },
      "ports": []
    },
    {
      "name": "cool",
      "nodeType": "DEV_NODE",
      "deviceModelId": 26,
      "capability": {
        "capabilityName": "capability_2",
        "capabilityParameters": { "parameter_1": 20 }
      },
      "ports": []
    },
    { "name": "end", "nodeType": "FUNC_NODE", "functionType": "END", "ports": [] }
  ],
  "interfaceConnections": [
    {
      "connectionType": "NODE_TO_NODE",
      "source": { "nodeName": "start", "interfaceName": "Interface_workflow_out" },
      "target": { "nodeName": "heat", "interfaceName": "Interface_workflow_in" }
    },
    {
      "connectionType": "NODE_TO_NODE",
      "source": { "nodeName": "heat", "interfaceName": "Interface_workflow_out" },
      "target": { "nodeName": "cool", "interfaceName": "Interface_workflow_in" }
    },
    {
      "connectionType": "NODE_TO_NODE",
      "source": { "nodeName": "cool", "interfaceName": "Interface_workflow_out" },
      "target": { "nodeName": "end", "interfaceName": "Interface_workflow_in" }
    },
    {
      "connectionType": "NODE_TO_DEVICE",
      "source": { "nodeName": "heat", "interfaceName": "Interface_state_out" },
      "target": { "deviceModelId": 26, "interfaceName": "Interface_workflow_in" }
    },
    {
      "connectionType": "DEVICE_TO_NODE",
      "source": { "deviceModelId": 26, "interfaceName": "Interface_state_out" },
      "target": { "nodeName": "heat", "interfaceName": "Interface_state_in" }
    },
    {
      "connectionType": "NODE_TO_DEVICE",
      "source": { "nodeName": "cool", "interfaceName": "Interface_state_out" },
      "target": { "deviceModelId": 26, "interfaceName": "Interface_workflow_in" }
    },
    {
      "connectionType": "DEVICE_TO_NODE",
      "source": { "deviceModelId": 26, "interfaceName": "Interface_state_out" },
      "target": { "nodeName": "cool", "interfaceName": "Interface_state_in" }
    }
  ],
  "portConnections": []
}
```
