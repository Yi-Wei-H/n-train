-- =====================================================================
-- 超管账号不在 SQL 里（密码不能进仓库）：data-server 首次启动
-- 由 SuperAdminInitRunner 用环境变量 SUPER_ADMIN_PASSWORD 创建
-- =====================================================================
use ntrain_admin;
-- ---------- 部门（根部门 id=1，超管初始化挂这里） ----------
INSERT INTO sys_dept (id, parent_id, ancestors, dept_name, order_num, status)
VALUES (1, 0, '0', 'ntrain 总部', 1, 1);

-- ---------- 菜单权限树（perms 与 admin 服务 @RequiresPermissions 一致） ----------
-- 一级目录
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, path, component, icon, order_num, visible, status) VALUES
(100, 0, '系统管理', 'M', NULL, '/system',  NULL, 'setting', 1, 1, 1),
(200, 0, '业务管理', 'M', NULL, '/business', NULL, 'train',   2, 1, 1);

-- 系统管理子菜单（C 节点 perms 即列表权限）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, path, component, icon, order_num, visible, status) VALUES
(110, 100, '管理员', 'C', 'sys:user:list', 'user', 'system/user', 'user',      1, 1, 1),
(120, 100, '角色',   'C', 'sys:role:list', 'role', 'system/role', 'peoples',   2, 1, 1),
(130, 100, '菜单',   'C', 'sys:menu:list', 'menu', 'system/menu', 'tree-table',3, 1, 1),
(140, 100, '部门',   'C', 'sys:dept:list', 'dept', 'system/dept', 'tree',      4, 1, 1),
(150, 100, '日志',   'C', 'sys:log:list',  'log',  'system/log',  'log',       5, 1, 1);

-- 管理员按钮
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, order_num, status) VALUES
(111, 110, '新增管理员', 'F', 'sys:user:add',      1, 1),
(112, 110, '编辑管理员', 'F', 'sys:user:edit',     2, 1),
(113, 110, '删除管理员', 'F', 'sys:user:delete',   3, 1),
(114, 110, '重置密码',   'F', 'sys:user:resetPwd', 4, 1);
-- 角色按钮
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, order_num, status) VALUES
(121, 120, '新增角色', 'F', 'sys:role:add',    1, 1),
(122, 120, '编辑角色', 'F', 'sys:role:edit',   2, 1),
(123, 120, '删除角色', 'F', 'sys:role:delete', 3, 1);
-- 菜单按钮
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, order_num, status) VALUES
(131, 130, '新增菜单', 'F', 'sys:menu:add',    1, 1),
(132, 130, '编辑菜单', 'F', 'sys:menu:edit',   2, 1),
(133, 130, '删除菜单', 'F', 'sys:menu:delete', 3, 1);
-- 部门按钮
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, order_num, status) VALUES
(141, 140, '新增部门', 'F', 'sys:dept:add',    1, 1),
(142, 140, '编辑部门', 'F', 'sys:dept:edit',   2, 1),
(143, 140, '删除部门', 'F', 'sys:dept:delete', 3, 1);

-- 业务管理子菜单
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, path, component, icon, order_num, visible, status) VALUES
(210, 200, '车次管理',   'C', 'b:train:list',  'train',      'business/train',      'train',   1, 1, 1),
(220, 200, '每日车次',   'C', 'b:daily:list',  'daily-train','business/dailyTrain', 'date',    2, 1, 1),
(230, 200, '订单管理',   'C', 'b:order:list',  'order',      'business/order',      'form',    3, 1, 1),
(240, 200, '会员管理',   'C', 'm:member:list', 'member',     'business/member',     'people',  4, 1, 1);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, order_num, status) VALUES
(211, 210, '新增车次', 'F', 'b:train:add',    1, 1),
(212, 210, '编辑车次', 'F', 'b:train:edit',   2, 1),
(213, 210, '删除车次', 'F', 'b:train:delete', 3, 1),
(214, 210, '车站维护', 'F', 'b:station:edit', 4, 1),
(221, 220, '生成每日车次', 'F', 'b:daily:gen',    1, 1),
(231, 230, '订单操作（发车/完成）', 'F', 'b:order:travel', 1, 1),
(241, 240, '编辑会员', 'F', 'm:member:edit',  1, 1);

-- 定时任务管理台（train-task 服务，仅超管可见，不授给普管角色）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, path, component, icon, order_num, visible, status) VALUES
(160, 100, '定时任务', 'C', 'sys:job:list', 'job', 'system/job', 'time', 6, 1, 1);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, order_num, status) VALUES
(161, 160, '新增/编辑任务', 'F', 'sys:job:edit',   1, 1),
(162, 160, '手动执行任务', 'F', 'sys:job:run',    2, 1),
(163, 160, '删除任务',     'F', 'sys:job:delete', 3, 1);

-- 会员管理"重置密码"按钮 + 座位/余票查询菜单
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, order_num, status) VALUES
(242, 240, '重置密码', 'F', 'm:member:resetPwd', 2, 1);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, path, component, icon, order_num, visible, status) VALUES
(250, 200, '座位查询', 'C', 'b:daily:seat',   'daily-seat',   'business/dailySeat',   'seat',   5, 1, 1),
(251, 200, '余票查询', 'C', 'b:daily:ticket', 'daily-ticket', 'business/dailyTicket', 'ticket', 6, 1, 1);

-- ---------- AI 管理（train-agent 服务经 admin RPC 透出） ----------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, path, component, icon, order_num, visible, status) VALUES
(300, 0,   'AI 管理', 'M', NULL,           '/agent',     NULL,          'ai',    3, 1, 1);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, path, component, icon, order_num, visible, status) VALUES
(310, 300, '模型配置', 'C', 'a:model:list',        'model',        'agent/model',        'model',   1, 1, 1),
(320, 300, '模型预设', 'C', 'a:preset:list',       'preset',       'agent/preset',       'preset',  2, 1, 1),
(330, 300, '用户授权', 'C', 'a:user-model:list',   'user-model',   'agent/userModel',    'people',  3, 1, 1),
(340, 300, '会话管理', 'C', 'a:conversation:list', 'conversation', 'agent/conversation', 'chat',    4, 1, 1),
(350, 300, '便捷卡片', 'C', 'a:card:list',         'card',         'agent/card',         'card',    5, 1, 1),
(360, 300, '用量统计', 'C', 'a:usage:list',        'usage',        'agent/usage',        'chart',   6, 1, 1);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, order_num, status) VALUES
(311, 310, '新增/编辑模型', 'F', 'a:model:save',                    1, 1),
(312, 310, '删除模型',      'F', 'a:model:delete',                  2, 1),
(313, 310, '连通性测试',    'F', 'a:model:test',                    3, 1),
(321, 320, '新增/编辑预设', 'F', 'a:preset:save',                   1, 1),
(322, 320, '删除预设',      'F', 'a:preset:delete',                 2, 1),
(331, 330, '编辑授权',      'F', 'a:user-model:save',               1, 1),
(332, 330, '删除授权行',    'F', 'a:user-model:delete',             2, 1),
(341, 340, '查看会话消息',  'F', 'a:message:list',                  1, 1),
(342, 340, '重新生成标题',  'F', 'a:conversation:regenerate-title', 2, 1),
(343, 340, '删除会话',      'F', 'a:conversation:delete',           3, 1),
(351, 350, '新增/编辑卡片', 'F', 'a:card:save',                     1, 1),
(352, 350, '删除卡片',      'F', 'a:card:delete',                   2, 1);

-- ---------- 车站（电报码为业务主标识） ----------
