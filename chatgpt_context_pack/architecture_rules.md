# SmartLab Architecture Rules

## 不可修改的定稿资产

1. 数据库结构设计不得随意修改。
2. `Backend/src/main/resources/schemas` 下的 JSON Schema 不得随意修改。
3. Adapter 配置文件格式不得随意修改。
4. 如果代码与上述结构不一致，应修改代码去适配它们，而不是反向修改定稿结构。

## 后端模块边界

当前后端按职责分为：

- `adapter`：Adapter 通信、MQTT 连接、注册监听、消息解析等底层适配逻辑。
- `engine`：状态机、工作流、执行引擎等运行时逻辑。
- `global`：公共配置、异常、工具、通用基础设施。
- `management`：业务管理逻辑，包含 controller、dto、entity、mapper、service，负责数据库对接与前端交互。

约束：

1. 设备模型、设备实例、Adapter 管理、数据中心、组件拓扑等业务管理逻辑必须放在 `management`。
2. 与数据库表直接对接的 entity、mapper、service 必须放在 `management`。
3. 与前端交互的 controller 和 dto 必须放在 `management`。
4. `adapter`、`engine`、`global` 不应直接操作设备模型、设备实例、数据中心相关业务表。
5. `adapter` 或 `engine` 如需访问业务对象，应通过 `management` 暴露的 service/facade/DTO。
6. 不要新增重复的 Entity/DTO/Service 体系，避免多套业务模型。
7. 不允许为局部功能破坏模块边界。

## 前端 UI 原则

1. 工业管理系统优先使用密集、清晰、可扫描的布局。
2. 少用装饰性卡片，优先使用树、表格、分区标题、紧凑表单。
3. 字体层级要稳定，标题、字段名、值、说明应有明确区分。
4. 不该展示给用户的内部标识不要直接显示，优先显示 displayName 或业务名称。
5. 设备模型页面采用左侧类别/模型树、右侧详情区，详情区内部使用纵向导航和滚动编辑区。
6. Adapter 页面用于 Adapter 注册、审阅、连接状态、配置查看、设备点与实例绑定管理。
7. 数据中心用于模板、数据实例、物理表、历史数据查询与导出管理。

## Codex / ChatGPT 使用建议

处理任务时应先说明读取到的关键文件，再说明要修改的文件，最后给出修改结果与验证结果。
每次较大代码改动后，可以重新运行 `chatgpt_context_pack/generate_context_pack.py` 刷新上下文包。
