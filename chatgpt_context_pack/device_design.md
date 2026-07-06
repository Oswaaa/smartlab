# 设备域业务设计方案

## 一、Adapter 注册域

### 1.1 业务路线

```
[用户在系统中注册 Adapter]
    └─ 前端跳出注册悬浮窗
    └─ 系统监听 MQTT topic smartlab/adapter/register 注册消息

[Adapter 进程启动]
    └─ 自动向 MQTT topic smartlab/adapter/register 发布注册消息
       {
         adapterName: "adapter1",
         rawConfigFormat: "INI",
         rawConfigContent: "... ini 文件全文 ...",
         timestamp: 1719892800
       }

[系统后端订阅该 topic]
    └─ 接收消息 → 调用解析服务
    └─ 解析成功 → 将解析结果暂存在内存/Redis
    └─ 通过 WebSocket/SSE 推送给前端：
       { event: "adapter_register_request", data: { parsedConfig, rawMeta } }

[前端 Adapter 悬浮窗]
    └─ 展示 parsedConfig 预览：
       - 模板列表 / 每个模板的属性与命令 / 设备点位列表
    └─ 用户可修改显示名称等附加元信息
    └─ 用户点击"完成注册"→ 后端将数据写入 ADAPTER_INDEX（ PARSED_CONFIG 存的是解析后且用户审阅修改后（应该也就是修改了一下description，方便前端显示）的配置文件内容）
```

### 1.2 PARSED_CONFIG 格式定义（最终确认版）

```json
{
  "adapterName": "adapter1",
  "adapterDescription": "对该adapter的描述",
  "registerMeta": {
    "rawConfigFormat": "INI",
    "registeredAt": 1719892800,
    "specVersion": "1.0"
  },
  "deviceCategories": [
    {
      "categoryName": "Reactor",
      "categoryDescription": "反应釜设备",
      "deviceTemplate": {
        "templateName": "Reactor_Basic",
        "description": "基础反应釜通信模板",
        "attributes": [
          { "name": "temperature", "dataType": "DOUBLE", "description": "釜内温度" },
          { "name": "pressure",    "dataType": "DOUBLE", "description": "釜内压力" }
        ],
        "commands": [
          {
            "name": "startHeating",
            "description": "开始加热",
            "parameters": [
              { "name": "targetTemp", "dataType": "DOUBLE", "hidden": false, "description": "目标温度" },
              { "name": "heatTime", "dataType": "INTEGER", "hidden": false, "description": "加热时间（秒）" },
              { "name": "_slaveId",   "dataType": "INTEGER", "hidden": true, "sourceField": "index", "description": "底层从机ID，对用户不可见【hidden=true则不向用户展示，为adapter内部使用，映射至devicePoints的某字段】" }
            ]
          }
        ],
        "events": {
          "cmdEvents": [
            { "name": "SENT",      "description": "命令已发送" },
            { "name": "EXECUTING", "description": "命令执行中" },
            { "name": "DONE",      "description": "命令完成" },
            { "name": "FAILED",    "description": "命令失败" }
          ],
          "opEvents": [
            { "name": "IDLE",    "description": "空闲" },
            { "name": "RUNNING", "description": "运行中" },
            { "name": "FAULT",   "description": "故障" }
          ]
        }
      },
      "devicePoints": [
        {
          "devicePoint": "Reactor_01",
          "description": "1号反应釜",
          "index": 1,
          "attributeMapping": { "temperature": "ch1_temp", "pressure": "ch1_pres" }
        },
        {
          "devicePoint": "Reactor_02",
          "description": "2号反应釜",
          "index": 2,
          "attributeMapping": { "temperature": "ch2_temp", "pressure": "ch2_pres" }
        }
      ]
    },
    {
      "categoryName": "StirrerMotor",
      "categoryDescription": "搅拌电机设备",
      "deviceTemplate":{
        "templateName": "StirrerMotor",
        "description": "搅拌电机通信模板",
        "attributes": [
          { "name": "rpm", "dataType": "INTEGER", "description": "转速" }
        ],
        "commands": [
          { 
            "name": "setRpm", 
            "description": "设置转速", 
            "parameters": [
              { "name": "targetRpm", "dataType": "INTEGER", "hidden": false }
            ]
          }
        ],
        "events": {
          "cmdEvents": [
            { "name": "SENT",      "description": "命令已发送" },
            { "name": "DONE",      "description": "命令完成" },
            { "name": "FAILED",    "description": "命令失败" }
          ],
          "opEvents": [
            { "name": "IDLE",    "description": "空闲" },
            { "name": "RUNNING", "description": "运行中" }
          ]
        }
      },
      "devicePoints": [
        { 
          "devicePoint": "Stirrer_A1", 
          "description": "A区1号搅拌电机", 
          "index": 1,
          "attributeMapping": { "rpm": "motor_a1_rpm" } }
      ]
    }
  ]
}
```

**设计原则**：
- `devicePoints` 与 `deviceTemplates` 放在同一个类别下，自然实现同一类别的模板 + 点位的分组
- `registerMeta` 保存注册时刻的元信息
- Adapter 不存储与模型/类别的关联关系（那是上层业务的职责）

---

## 二、设备类别域

### 2.1 数据模型（DEVICE_CATEGORY）

```
DEVICE_CATEGORY 是一棵递归的树（parent_category_id 自引用），只有叶子节点可以挂载设备模型【假设叶子节点“泵类”本身挂载了模型A，之后要在其下新增电子泵类别，则在泵类下新增一个子类别（电子泵类别），本来的模型A如果属于电子泵则挂载在电子泵类别下，否则需要为模型A创建一个他的类别例如气动泵然后将模型A挂载在该类别下面。建议业务提醒语句：系统在用户尝试给一个已有模型的类别添加子类别时，弹出警告："该类别下已有设备模型，添加子类别后该类别将变为中间节点，其下的模型需要迁移至具体的叶子类别。是否继续？"，并提供引导式迁移操作。】

例：
  流体设备（id=1, parent=null）
  ├─ 化工容器（id=2, parent=1）
  │   ├─ 反应釜（id=5, parent=2）  ← 最细粒度类别，可挂载设备模型
  │   └─ 储液罐（id=6, parent=2）
  └─ 泵类（id=3, parent=1）
  电气设备（id=4, parent=null）
  └─ 电机类（id=7, parent=4）
```

### 2.2 页面布局与操作

类别管理**不单独弹窗**，直接融合在设备模型页面的左侧边栏中，采用类似 IDE 文件树的交互方式：
- 点击类别节点 → 展开/收起子类别
- 点击叶子类别节点 → 展示/隐藏模型列表
- 类别节点右侧提供快捷操作按钮，包括新增子类别、重命名类别、删除类别
- 类别节点下无任何子类别且无模型时，才允许删除

---

## 三、设备模型域

### 3.1 页面布局

```
┌─────────────────┬─────────────────────────────────────────────────────────────┐
│ 左侧模型树      │ 右侧详情区                                                   │
│ ─────────────   │ ┌────────────┬───────────────────────────────────────────┐  │
│ 📁 流体设备     │ │ 基础信息   │                                            │  │
│   📁 化工容器   │ │ 属性功能   │   [右侧为滚动编辑区]                        │  │
│     📄 反应釜   │ │ 端口        │   基础信息                                │  │
│     📄 储液罐   │ │ 状态机      │   属性功能                                │  │
│ 📁 电气设备     │ │ BOM        │   端口配置                                 │  │
│   📄 电机类     │ │ 数据模板    │   状态机                                  │  │
│                 │ └────────────┴───────────────────────────────────────────┘   │
└─────────────────┴──────────────────────────────────────────────────────────────┘
```

- 中间节点（类别）：灰蓝色，文件夹图标
- 叶子节点（模型）：主题色，芯片图标
- 叶子类别节点快捷操作按钮：新增子类别 / 新增模型

### 3.2 创建设备模型的表单（分步引导）

#### Step 1：基础信息
- 模型名称（必填）
- 所属类别（从类别树中选择，必须选到叶子级类别）
- 描述

#### Step 2：属性功能
- 编辑该模型的业务属性、业务能力、端口配置

#### Step 3：Adapter 契约配置
此步骤决定该类型设备如何与物理世界通信。

**操作流程**：
1. 下拉选择已注册的 Adapter（展示 Adapter 名称以及描述信息）
2. 系统读取该 Adapter 的 `PARSED_CONFIG.deviceCategories`，以下拉列表展示所有类别的 `categoryName`以及`categoryDescription`
3. 用户选择对应的 `categoryName`
4. 系统自动展开该类别下的所有属性列表（attributes）、命令列表（commands）、事件列表（events）
5. 用户可以**审阅并编辑**需要的属性、命令和事件

【PARSED_CONFIG 中的 deviceCategories[].categoryName（如 "Reactor"）是 Adapter 自定义的名称，和数据库 DEVICE_CATEGORY.category_name（如 "反应釜"）是两个完全独立的命名空间。两者完全分开，互不干扰。】

#### Step 4：映射关系
- 将模型的属性、命令与 Adapter 契约中的属性、命令、事件进行映射，确保模型的属性、命令能够正确地与 Adapter 交互。

#### Step 5：状态机定义

#### Step 6：内置约束定义

#### Step 7：组件结构清单（COMPONENTS_BOM）
```json
[
  {
    "slotName": "搅拌电机",
    "categoryId": 7,
    "categoryName": "电机类",
    "quantity": 1,
    "description": "负责物料搅拌"
  },
  {
    "slotName": "温度传感器",
    "categoryId": 8,
    "categoryName": "传感器类",
    "quantity": 2,
    "description": "双点测温"
  }
]
```
- 用户在此步骤通过类别树选择器选类别并填写 slotName、quantity、description。

#### Step 8：默认数据模板
- 展示该模型所有 attributes
- 用户勾选需要记录历史数据的属性
- 用于生成 `DATA_TEMPLATE_MAIN`（`is_default=true`）和对应的 `DATA_TEMPLATE_DETAIL`

#### Step 9：模型文件
- 预览模型文件内容

---

## 四、设备实例域

### 4.1 INSTANCE_CONFIG 设计分析

**核心问题**：设备实例化配置的作用是什么？

模型定义“这类设备是什么”，实例定义“这台设备怎么用”。

`DEVICE_INSTANCES` 记录设备实例的配置；`DEVICE_TWIN_STATES` 记录设备实例的运行状态。前者是模型静态能力的实例化，后者是模型动态状态机的实例化。两者由模型衍生，共同实例化一个模型。

**`INSTANCE_CONFIG` 格式（最终定义）**：
```json
{
  "assetInfo": {
    "serialNumber": "SN-202401-0031",
    "purchaseDate": "2024-01-15",
    "instalDate": "2024-02-01",
    "location": "实验室A，3号工位",
    "notes": "2024年采购，额定容量50L"
  },

   "intrinsicConstraints": [
    {
      "objectAttributeName": "temperature",
      "operator": "LTE",
      "boundaryValue": 300.0,
      "unit": "°C",
      "violationStateName": "温度异常",
      "description": "该设备实例的最高允许温度为300°C"
    }
  ]
}
```

**说明**：
- `assetInfo`：资产管理信息，与业务逻辑无关，纯描述性
- `intrinsicConstraints`：设备实例内置约束定义，默认具有模型的 `INTRINSIC_CONSTRAINTS`，并支持用户新增对不同实例化设备的自定义约束。

### 4.2 实例创建的业务路线（四步引导式）

```
[Step 1：选择模型]
  用户从类别树中选择设备类别 → 显示该类别下的模型列表
  用户选择具体模型
  → 系统读取模型 ADAPTER_CONTRACT，获取绑定的 adapterName + categoryName

[Step 2：绑定物理设备点]
  系统从 ADAPTER_INDEX 读取对应 Adapter 的 PARSED_CONFIG
  → 定位到对应 categoryName 下的 devicePoints 列表
  → 以下拉列表展示（每一项格式："Reactor_01 - 1号反应釜"，即 devicePoint + description）
  → 用户选择该实例对应的物理点位
  → 后续提交数据库时系统自动填入【这里没有categoryName是因为默认adapter内设备点位是唯一的】：
      bound_adapter_name = "adapter1"
      bound_device_point = "Reactor_01"

[Step 3：填写实例信息]
  - 实例名称（必填）
  - assetInfo：资产信息（序列号、位置等）（可选）
  预览区域展示（只读）：
    - 该实例将拥有的属性列表（继承自模型）
    - 该实例将拥有的操作能力（继承自模型）
    - 该实例将拥有的功能状态空间（继承自模型）
    注意：不显示底层 attributeMapping 等通信细节

[Step 4：确认并提交]
  系统执行以下操作（事务内）：
  1. 写入 DEVICE_INSTANCES 记录
  2. 写入 DEVICE_TWIN_STATES 初始记录：
     { instance_id: xxx, current_op_state: 设备状态机模型的 opStateSpace.initialStateName, online_status: "OFFLINE", CURRENT_CMD_STATE: 设备状态机模型的 cmdLifecycleSpace.initialStateName, current_attr: {} }
  3. 如果模型有默认数据模板（DATA_TEMPLATE_MAIN.is_default=true）：
     自动创建 DATA_INDEX（分配新物理数据表名 DATA_RECORD_XXXX）
     ↑ 注：物理表的实际 CREATE TABLE 语句由后端在此时执行
  4. 如果模型的 COMPONENTS_BOM 不为空，那么系统自动在 DEVICE_COMPONENTS 表中创建对应的记录【 COMPONENT_NAME 为 slotName, PARENT_INSTANCE_ID 为该设备实例的id，安装时间默认为生成时间，SPECIFICATION 留空, SELF_INSTANCE_ID 均为空, status 为 "未配置" 】
     如果模型的 COMPONENTS_BOM 为空，代表该模型没有组件，不进行对 DEVICE_COMPONENTS 表的写入
     系统在实例详情页的"组件拓扑"区域，自动生成空的组件槽（状态：未配置），等待用户后续填写
```

### 4.3 设备实例详情页布局

```
┌───────────────────────────────────────────────────────────────────────┐
│  设备实例：1号反应釜（Reactor_01）        状态：●在线 / RUNNING         │
│  模型：基础反应釜 | Adapter：adapter1                                   │
├──────────┬────────────────────────────────────────────────────────────┤
│  基础信息 │ 手动控制 │ 实时状态 │ 组件拓扑 │ 数据记录 │ 约束配置          │
├──────────┴────────────────────────────────────────────────────────────┤
│  [选项卡内容区]                                                         │
└───────────────────────────────────────────────────────────────────────┘
```

---

## 五、设备组件拓扑域（资产管理）

### 5.1 业务定位

组件拓扑是**设备资产管理**的核心功能，解决以下问题：
- 一台复杂设备（如反应釜）内部有哪些子组件？
- 这些子组件是否有对应的数字孪生？
- 某个组件损坏更换后，历史记录如何保留？

### 5.2 组件拓扑配置的业务路线

**入口**：设备实例详情页的"组件拓扑"选项卡。

```
[页面载入时]
  系统查询 DEVICE_COMPONENTS 表中 parent_instance_id = 该实例ID 的所有记录【初始时，所有槽位都未绑定实例】
  → 将找到的 DEVICE_COMPONENTS 记录显示：
    ┌────────────────────────────────────────────────────────────────┐
    │  组件槽位      类别        已绑定实例        状态      操作      │
    │  ──────────   ──────     ─────────────    ──────    ──────     │
    │  搅拌电机-1    电机类      Stirrer_A1       使用中   [管理]      │
    │  温度传感器-1  传感器类    （未配置）         -        [配置]    │
    │  温度传感器-2  传感器类    （未配置）         -        [配置]    │
    └────────────────────────────────────────────────────────────────┘
```

**点击"配置"（未绑定的槽位）**：
```
系统按照槽位的 categoryId 查找：
  → 该类别下有设备模型？
      是 → 展示该类别下的所有模型，用户选定模型后展示该模型下的设备实例列表，用户从中选择（可以搜索）
      否 → 提示"该类别暂无数字化设备模型，仅记录规格信息"
            → 用户填写 specification（型号、品牌、规格参数等 JSONB）
  → 用户确认后系统更新 DEVICE_COMPONENTS 的对应记录【在设备实例化时该记录就已经自动生成，理由为既然数据库中有设备组件拓扑表那么就应该存储设备的结构信息，并且如果用户一直不配置那么该组件结构信息就一直不存在，这是错误的】：
      self_instance_id = 绑定的设备实例ID（如果有的话，否则留空）
      status = "使用中"
      install_time = now()
```

### 5.3 组件状态变更与更换的业务路线

**点击"管理"（已配置的槽位）**：

用户可以执行以下操作：
1. **查看详情**：规格信息、安装时间、历史更换记录（通过 predecessor_id 链追溯）
2. **标记废弃**：`status` → "已废弃"，记录废弃时间
3. **标记更换**（最常用）：

```
[用户点击"更换组件"]

情况 A：更换为同类别的另一个已有实例
  → 弹出选择框，从该类别的模型的设备实例中选择新实例
  → 填写规格信息（用户输入，可选）
  → 系统执行：
      1. 将旧 DEVICE_COMPONENTS 记录的 status 改为 "已更换"
      2. 新建一条 DEVICE_COMPONENTS 记录：
           parent_instance_id = 同父
           category_id = 同类别
           component_name = 新名称（可与旧的相同）
           self_instance_id = 新实例ID
           status = "使用中"
           predecessor_id = 旧记录ID  ← 关键：形成更换链
           install_time = now()

情况 B：更换为未数字化的物理组件
  → 用户填写新组件规格（specification）
  → 系统执行同上，但 self_instance_id 留空，specification 填写用户输入
```

### 5.4 DEVICE_COMPONENTS 数据示意

```
id=1: 反应釜1号的搅拌电机槽
  parent_instance_id=10（反应釜1号实例）
  category_id=7（电机类）
  self_instance_id=20（搅拌电机A实例）
  status="已更换"
  predecessor_id=null
  install_time=2024-01-15

id=2: 反应釜1号的搅拌电机槽（更换后的新电机）
  parent_instance_id=10
  category_id=7
  self_instance_id=21（搅拌电机B实例，新的）
  status="使用中"
  predecessor_id=1  ← 指向旧记录
  install_time=2024-08-20
```

通过 `predecessor_id` 链，可以追溯任何组件的完整更换历史。

---

## 六、完整数据链路图

```
                    ┌─────────────────────────────────────────┐
                    │            DEVICE_CATEGORY              │
                    │  树形类别（反应釜、电机类、传感器类...）    │
                    └────────────────┬────────────────────────┘
                                     │ category_id（归类）
                    ┌────────────────▼────────────────────────┐
                    │              DEVICE_MODELS              │
                    │  模型（抽象蓝图）                         │
                    │  attributes / capabilities / op_state   │
                    │  components_bom / adapter_contract      │
                    │  intrinsic_constraints                  │
                    └────┬───────────────────────┬────────────┘
                         │ device_model_id        │ device_model_id
            ┌────────────▼────────┐   ┌──────────▼───────────────────┐
            │   ADAPTER_INDEX     │   │      DATA_TEMPLATE_MAIN       │
            │  adapter1           │   │  默认数据模板（is_default=T）   │
            │  PARSED_CONFIG:     │   │  + DATA_TEMPLATE_DETAIL       │
            │  categoryName+points│   │    （字段定义，关联属性 key）    │
            └────────────┬────────┘   └───────────────┬──────────────┘
                         │ bound_adapter_name          │ data_template_id
                         │ bound_device_point          │
            ┌────────────▼────────────────────────────▼──────────────┐
            │                   DEVICE_INSTANCES                      │
            │  实例（一台具体物理设备）                                  │
            │  instance_config:                                       │
            │    assetInfo（序列号、位置）                              │
            │    constraintOverrides（覆盖模型约束的更严限制）           │
            │                                                         │
            └────────┬──────────────────┬──────────────────┬─────────┘
                     │                  │                  │
           ┌─────────▼──────┐  ┌────────▼────────┐  ┌────▼──────────────┐
           │DEVICE_TWIN_    │  │DEVICE_COMPONENTS│  │   DATA_INDEX      │
           │STATES          │  │（组件拓扑树）    │  │  → DATA_RECORD_   │
           │（数字孪生状态）  │  │predecessor_id   │  │     XXXX          │
           │实时属性/状态/   │  │形成更换链       │  │  （历史时序数据）   │
           │心跳             │  │self_instance_id │  │                   │
           └────────────────┘  │可递归→子实例    │  └───────────────────┘
                                └─────────────────┘
```

---

## 七、约束体系数据链路【暂定】

```
                DEVICE_MODELS.intrinsic_constraints（模型固有约束）
                         ↕ 并集
                INSTANCE_CONFIG.constraintOverrides（实例内部约束）
                         ↕ 运行时读取
                DEVICE_TWIN_STATES.current_attr（实时属性值）
                         ↕ 对比检查（约束引擎，后续实现）
                CONSTRAINT_RULE（全局约束规则）
                TASK.task_constraints（任务级约束）
                         ↓ 违规
                VIOLATION_LOG（违规记录）
```

约束优先级（从低到高）：全局约束规则 < 任务级约束 < 模型固有约束 < 实例覆盖约束【暂定】

---

## 八、各页面职责总结

| 页面 | 核心职责 |
|---|---|
| Adapter 管理页 | Adapter 注册、在线状态监控、PARSED_CONFIG 查看 |
| 设备模型页 | 类别树管理、模型创建（8步引导）、模型版本管理 |
| 设备实例页 | 实例创建（4步引导）、实例详情查看、组件拓扑配置与更换历史 |
| 数据中心页 | 数据模板管理、历史数据查询、数据表创建/绑定 |
