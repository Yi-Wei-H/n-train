package com.weiyi.agent.server.service.model;

import com.weiyi.agent.server.ai.provider.ErrorClassifier;
import com.weiyi.agent.server.ai.provider.LlmProviderRegistry;
import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.agent.api.dto.ModelTestDTO;
import com.weiyi.agent.server.entity.AModel;
import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.util.BizAssert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelTestService {

    private final AModelService modelService;
    private final LlmProviderRegistry llmProviderRegistry;

    public ModelTestDTO testModel(Long id) {
        log.info("正在测试模型连通性,模型ID:{}", id);
        BizAssert.notNull(id, ErrorCode.PARAM_ERROR);
        AModel model = modelService.getModelById(id);
        BizAssert.notNull(model, ErrorCode.MODEL_NOT_EXISTS);
        return switch (model.getModelType()) {
            case AgentConstants.MODEL_TYPE_CHAT -> testChat(llmProviderRegistry.getChatClient(model.getId()), model);
            case AgentConstants.MODEL_TYPE_EMBEDDING -> testEmbedding(llmProviderRegistry.getEmbeddingModel(model.getId()), model);
            default -> throw new BusinessException(ErrorCode.PARAM_ERROR);
        };
    }

    private ModelTestDTO testEmbedding(EmbeddingModel embeddingModel, AModel model) {
        ModelTestDTO dto = new ModelTestDTO();
        long start = System.currentTimeMillis();
        try {
            float[] vector = embeddingModel.embed("ping");
            dto.setLatencyMs((int) (System.currentTimeMillis() - start));
            dto.setSuccess(true);
            dto.setDimension(vector.length);
            log.info("模型连通性测试成功: modelId={}, apiModelId={}, dimension={}, latencyMs={}",
                    model.getId(), model.getApiModelId(), dto.getDimension(), dto.getLatencyMs());
        } catch (Exception e) {
            dto.setSuccess(false);
            dto.setLatencyMs((int) (System.currentTimeMillis() - start));
            dto.setErrMsg(ErrorClassifier.classify(e));
            log.warn("模型连通性测试失败: modelId={}, apiModelId={}, error={}",
                    model.getId(), model.getApiModelId(), e.getMessage(), e);
        }
        return dto;
    }

    private ModelTestDTO testChat(ChatClient chatClient, AModel model) {
        ModelTestDTO dto = new ModelTestDTO();
        long start = System.currentTimeMillis();
        try {
            // Spring AI 2.0 的 call() 是惰性的，必须再调终止操作 content() 才真正发出请求
            chatClient.prompt().user("Reply with OK only.").call().content();
            dto.setSuccess(true);
            dto.setLatencyMs((int) (System.currentTimeMillis() - start));
            log.info("模型连通性测试成功: modelId={}, apiModelId={}, latencyMs={}",
                    model.getId(), model.getApiModelId(), dto.getLatencyMs());
        } catch (Exception e) {
            dto.setSuccess(false);
            dto.setLatencyMs((int) (System.currentTimeMillis() - start));
            dto.setErrMsg(ErrorClassifier.classify(e));
            log.warn("模型连通性测试失败: modelId={}, apiModelId={}, error={}",
                    model.getId(), model.getApiModelId(), e.getMessage(), e);
        }
        return dto;
    }
}
