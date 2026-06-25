-- 权限字典初始化脚本
-- 数据库：lab
-- 说明：
-- 1. 本脚本只写入 PERMISSION_INFO，不创建表、不修改表结构。
-- 2. 菜单所需权限在后端菜单字典中维护，菜单入口统一引用对应对象的 view 权限。
-- 3. 按钮和接口动作使用 create/edit/delete/control/export/assign 等动作权限。

INSERT INTO PERMISSION_INFO (ID, SCOPE, OBJECT, ACTION, PERMISSION_LEVEL, PERMIS_DESC) VALUES
(1,  'ALL', 'all',              'all',    4, '系统管理员拥有全部权限'),

-- 首页与设备资源层
(2,  'LAB', 'dashboard',        'view',   1, '查看首页总览'),
(3,  'LAB', 'device_category',  'view',   1, '查看设备类别'),
(4,  'LAB', 'device_category',  'edit',   3, '维护设备类别'),
(5,  'LAB', 'device_model',     'view',   1, '查看设备模型'),
(6,  'LAB', 'device_model',     'create', 3, '创建设备模型'),
(7,  'LAB', 'device_model',     'edit',   3, '编辑设备模型'),
(8,  'LAB', 'device_model',     'delete', 3, '删除设备模型'),
(9,  'LAB', 'device_instance',  'view',   1, '查看设备实例'),
(10, 'LAB', 'device_instance',  'create', 3, '创建设备实例'),
(11, 'LAB', 'device_instance',  'edit',   3, '编辑设备实例'),
(12, 'LAB', 'device_instance',  'delete', 3, '删除设备实例'),
(13, 'LAB', 'device_instance',  'control',2, '控制设备实例'),
(14, 'LAB', 'device_component', 'view',   1, '查看设备组件拓扑'),
(15, 'LAB', 'device_component', 'edit',   3, '维护设备组件拓扑'),
(16, 'LAB', 'adapter',          'view',   2, '查看设备执行代理聚合配置'),
(17, 'LAB', 'adapter',          'edit',   3, '编辑设备模型契约和实例代理配置'),

-- 数据资源层
(18, 'LAB', 'data_template',    'view',   1, '查看数据模板'),
(19, 'LAB', 'data_template',    'create', 3, '创建数据模板'),
(20, 'LAB', 'data_template',    'edit',   3, '编辑数据模板'),
(21, 'LAB', 'data_template',    'delete', 3, '删除数据模板'),
(22, 'LAB', 'data_dataset',     'view',   1, '查看数据集和数据点'),
(23, 'LAB', 'data_dataset',     'create', 3, '创建数据集'),
(24, 'LAB', 'data_dataset',     'edit',   3, '编辑数据集'),
(25, 'LAB', 'data_dataset',     'delete', 3, '删除或归档数据集'),
(26, 'LAB', 'data_dataset',     'export', 2, '导出数据集'),
(27, 'LAB', 'property_type',    'view',   1, '查看字段类型'),
(28, 'ALL', 'property_type',    'edit',   4, '维护字段类型'),

-- 工作流层
(29, 'LAB', 'workflow',         'view',   1, '查看流程模型'),
(30, 'OWN', 'workflow',         'create', 2, '创建自己的流程模型'),
(31, 'OWN', 'workflow',         'edit',   2, '编辑自己的流程模型'),
(32, 'OWN', 'workflow',         'delete', 2, '删除自己的流程模型'),
(33, 'LAB', 'workflow',         'create', 3, '创建本实验室流程模型'),
(34, 'LAB', 'workflow',         'edit',   3, '编辑本实验室流程模型'),
(35, 'LAB', 'workflow',         'delete', 3, '删除本实验室流程模型'),
(36, 'LAB', 'workflow',         'export', 2, '导出流程模型'),
(37, 'LAB', 'task',             'view',   1, '查看任务'),
(38, 'OWN', 'task',             'create', 2, '创建自己的任务'),
(39, 'OWN', 'task',             'edit',   2, '编辑自己的任务'),
(40, 'OWN', 'task',             'start',  2, '启动自己的任务'),
(41, 'OWN', 'task',             'stop',   2, '停止自己的任务'),
(42, 'LAB', 'task',             'create', 3, '创建本实验室任务'),
(43, 'LAB', 'task',             'edit',   3, '编辑本实验室任务'),
(44, 'LAB', 'task',             'delete', 3, '删除本实验室任务'),
(45, 'LAB', 'task',             'start',  3, '启动本实验室任务'),
(46, 'LAB', 'task',             'stop',   3, '停止本实验室任务'),

-- 约束层
(47, 'LAB', 'constraint_rule',  'view',   2, '查看约束规则'),
(48, 'LAB', 'constraint_rule',  'create', 3, '创建约束规则'),
(49, 'LAB', 'constraint_rule',  'edit',   3, '编辑约束规则'),
(50, 'LAB', 'constraint_rule',  'delete', 3, '删除约束规则'),
(51, 'LAB', 'violation_log',    'view',   1, '查看违规记录'),
(52, 'LAB', 'violation_log',    'export', 3, '导出违规记录'),

-- 场景与资源结构
(53, 'LAB', 'scene',            'view',   1, '查看场景'),
(54, 'LAB', 'scene',            'create', 3, '创建场景'),
(55, 'LAB', 'scene',            'edit',   3, '编辑场景'),
(56, 'LAB', 'scene',            'delete', 3, '删除场景'),
(57, 'LAB', 'resource',         'view',   1, '查看资源结构'),
(58, 'LAB', 'resource',         'edit',   3, '编辑资源结构'),

-- 用户与权限
(59, 'LAB', 'user',             'view',   3, '查看用户'),
(60, 'LAB', 'user',             'create', 3, '创建用户'),
(61, 'LAB', 'user',             'edit',   3, '编辑用户'),
(62, 'LAB', 'user',             'delete', 3, '删除用户'),
(63, 'LAB', 'permission',       'view',   3, '查看权限'),
(64, 'LAB', 'permission',       'assign', 3, '分配权限'),
(65, 'ALL', 'permission',       'edit',   4, '维护权限字典')
ON CONFLICT (ID) DO UPDATE SET
    SCOPE = EXCLUDED.SCOPE,
    OBJECT = EXCLUDED.OBJECT,
    ACTION = EXCLUDED.ACTION,
    PERMISSION_LEVEL = EXCLUDED.PERMISSION_LEVEL,
    PERMIS_DESC = EXCLUDED.PERMIS_DESC;
