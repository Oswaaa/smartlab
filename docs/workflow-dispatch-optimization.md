# 工作流调度优化执行记录

范围：先批量加载并共享本轮节点定义，再减少连接处理中历史步骤的重复遍历及相同索引的重建。

约束：保持触发器冻结快照语义、终态输出补发、子流程作用域、设备实时属性拉取、命令关联与恢复。保留历史步骤和输出；不运行真实设备任务。

实施：
1. 增加 2/18/100 步骤查询次数测试；端口、接口、执行环节共享本轮定义，独立入口仍可批量加载。沿用已有 WorkflowService 定义缓存和失效机制，不新增长寿命节点缓存。
2. 本轮建立按流程/节点/父步骤/深度索引；连接仅遍历活跃目标，源步骤历史输出仍可读取。相同连接索引不重复替换；新连接、结束、恢复仍按当前状态处理。
3. 执行回归测试和独立代码审查。真实耗时需重启后端后用同一任务实测。

决策：沿用当前工作目录未提交的改动并创建专用分支，避免隔离检出遗漏当前会话已修改的执行语义；不提交用户已有改动。

第一步完成：查询次数测试先失败（逐条查询），改为批量共享后通过。WorkflowRoundQueriesTest、WorkflowEngine*Test、InterfaceConnection*Test、PortConnectionIndexTest 共 61 项通过。

第二步完成：相同连接刷新保持原索引实例，接口侧同时保持命令占用。内容变化或清空时仍按当前状态替换。端口、接口索引测试通过。

第三步完成：回归 66 项，失败 0。WorkflowRoundQueriesTest、WorkflowRoundNodesTest、WorkflowEngineExecutionTest、WorkflowEngineExpressionHistoryTest、WorkflowEngineSchedulingTest、InterfaceConnectionCatalogTest、InterfaceConnectionForwarderTest、InterfaceConnectionIndexTest、PortConnectionIndexTest。

审查：触发器仍用本轮开始时的冻结快照。终态未观察完的步骤仍会推进并补发输出。已完成步骤不再作为连接目标；同一流程、父步骤和深度上的历史输出仍能被活跃下游读到，设备实时属性仍会拉取。子流程按父步骤和深度隔离。连接列表未变时不替换索引，命令占用保留；新连接、结束和进程内恢复仍按当前步骤重建。未新增长寿命节点缓存。未运行真实设备任务，因此没有实测耗时。
