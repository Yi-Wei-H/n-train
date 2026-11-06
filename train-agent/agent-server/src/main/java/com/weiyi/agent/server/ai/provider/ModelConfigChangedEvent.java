package com.weiyi.agent.server.ai.provider;

/**
 * 模型配置变更事件：保存/启停/删除后发布，LlmProviderRegistry 据此失效对应模型的
 * 配置与客户端缓存——否则改配置后测试/聊天仍用旧客户端（旧 key、旧格式），重启才生效。
 * 进程内事件，仅当前实例可见；当前 agent 单实例部署，多实例时需换广播机制。
 */
public record ModelConfigChangedEvent(Long modelId) {
}
