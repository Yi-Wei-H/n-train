-- =====================================================================
-- ntrain_agent 初始数据（在 01_schema.sql 之后执行）
-- 预置 ID 固定（预设 1001+，卡片 1+），请勿复用已删行 ID
-- 管理端接入流程：模型预设列表 → 选一条「接入」→ 填 API KEY → 生成 a_model
-- =====================================================================

-- 模型预设：baseURL + 上游格式 + 认证方式三要素
-- （api_format: 1 OpenAI兼容-chat 2 OpenAI兼容-Responses 3 Anthropic 4 Gemini；auth_type: 1 Bearer头 2 Key头直传 3 查询参数）
INSERT INTO a_model_preset (id, vendor, preset_name, model_type, base_url, api_format, auth_type, auth_key_name, api_model_id, default_params, description, sort_no) VALUES
(1001, 'deepseek',    'DeepSeek-Chat',     1, 'https://api.deepseek.com',                          1, 1, NULL,       'deepseek-chat',             '{"temperature":0.7}', 'DeepSeek 通用对话模型，性价比高', 1),
(1002, 'deepseek',    'DeepSeek-Reasoner', 1, 'https://api.deepseek.com',                          1, 1, NULL,       'deepseek-reasoner',         '{"temperature":0.7}', 'DeepSeek 推理模型，深度思考场景', 2),
(1003, 'qwen',        '通义千问-Plus',      1, 'https://dashscope.aliyuncs.com/compatible-mode/v1', 1, 1, NULL,       'qwen-plus',                 '{"temperature":0.7}', '阿里通义旗舰对话模型', 3),
(1004, 'qwen',        '通义文本向量-V3',    2, 'https://dashscope.aliyuncs.com/compatible-mode/v1', 1, 1, NULL,       'text-embedding-v3',         '{}',                  '通义向量模型（1024 维，接入需配 pgvector 维度）', 4),
(1005, 'openai',      'GPT-4o-mini',       1, 'https://api.openai.com',                            1, 1, NULL,       'gpt-4o-mini',               '{"temperature":0.7}', 'OpenAI 轻量对话模型', 5),
(1006, 'openai',      'text-embedding-3-small', 2, 'https://api.openai.com',                       1, 1, NULL,       'text-embedding-3-small',    '{}',                  'OpenAI 向量模型（1536 维，pgvector 默认维度）', 6),
(1007, 'zhipu',       'GLM-4-Flash',       1, 'https://open.bigmodel.cn/api/paas/v4',              1, 1, NULL,       'glm-4-flash',               '{"temperature":0.7}', '智谱免费对话模型，适合开发联调', 7),
(1008, 'siliconflow', 'Qwen2.5-7B-Instruct', 1, 'https://api.siliconflow.cn/v1',                   1, 1, NULL,       'Qwen/Qwen2.5-7B-Instruct',  '{"temperature":0.7}', '硅基流动聚合平台的便宜型号', 8),
(1009, 'anthropic',   'Claude Sonnet',     1, 'https://api.anthropic.com',                         3, 2, 'x-api-key', 'claude-sonnet-4-5',         '{"temperature":0.7}', 'Anthropic 官方 messages 格式，x-api-key 认证', 9);

-- 便捷卡片：聊天输入框上方快捷提问
INSERT INTO a_quick_card (id, card_name, prompt_text, icon, sort_no) VALUES
(1, '查询我的车票', '请帮我查询我最近购买的车票',            'ticket',   1),
(2, '查询余票',     '请帮我查询北京到上海未来三天的高铁余票', 'search',   2),
(3, '退改签政策',   '请介绍一下火车票退票和改签的规则',       'policy',   3),
(4, '车次经停',     '请帮我介绍 G1 次列车的经停站信息',       'train',    4);
