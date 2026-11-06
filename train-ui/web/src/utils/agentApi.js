import { createRequest } from './request'

// agent 域接口：走网关 /api/agent/**，鉴权与 Result 解包同全仓工厂口径
const agentApi = createRequest('/api/agent')

export default agentApi
