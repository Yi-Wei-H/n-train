package com.weiyi.agent.server.service.memory;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weiyi.agent.server.config.AgentProperties;
import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.agent.server.dto.AUserMemoryDTO;
import com.weiyi.agent.server.dto.MemoryExtractionResult;
import com.weiyi.agent.server.entity.AUserMemory;
import com.weiyi.agent.server.entity.AUserMemoryAudit;
import com.weiyi.agent.server.entity.AUserMemorySetting;
import com.weiyi.agent.server.mapper.AUserMemoryAuditMapper;
import com.weiyi.agent.server.mapper.AUserMemoryMapper;
import com.weiyi.agent.server.mapper.AUserMemorySettingMapper;
import com.weiyi.agent.server.req.AUserMemoryQuery;
import com.weiyi.agent.server.req.AUserMemoryReq;
import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.util.BeanCopyUtils;
import com.weiyi.common.core.util.BizAssert;
import com.weiyi.common.mybatis.util.PageUtils;
import com.weiyi.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 账户长期记忆：设置（总开关/纪元）、CRUD（校验与去重共用）、自动提炼采纳、上下文注入读取。
 * 真相源 PostgreSQL；删除为物理删除（唯一键释放）；删除/清空/关闭重置递增纪元防异步任务复活。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryService {

    /** 规范键格式：小写字母数字下划线 */
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");

    private final AUserMemoryMapper memoryMapper;
    private final AUserMemorySettingMapper settingMapper;
    private final AUserMemoryAuditMapper auditMapper;
    private final MemoryCommandValidator validator;
    private final AgentProperties agentProperties;

    // ---------------- 设置 ----------------

    /** 取设置行，无则初始化（默认开启，纪元 0）；并发初始化冲突时读回已有行 */
    public AUserMemorySetting settingOf(Long userId) {
        AUserMemorySetting setting = settingMapper.selectById(userId);
        if (setting != null) {
            return setting;
        }
        AUserMemorySetting created = new AUserMemorySetting();
        created.setUserId(userId);
        created.setEnabled(AgentConstants.STATUS_ON);
        created.setVersion(0);
        created.setMemoryEpoch(0);
        try {
            settingMapper.insert(created);
            return created;
        } catch (Exception e) {
            AUserMemorySetting existing = settingMapper.selectById(userId);
            if (existing != null) {
                return existing;
            }
            throw new IllegalStateException("记忆设置初始化失败：userId=" + userId, e);
        }
    }

    public boolean enabled(Long userId) {
        return settingOf(userId).getEnabled() == AgentConstants.STATUS_ON;
    }

    /** 更新总开关：enabled 必填且只允许 1/2；version 冲突提示刷新；关闭/重置递增纪元（使在途提炼任务失效），开启不递增 */
    @Transactional(rollbackFor = Exception.class)
    public AUserMemorySetting updateSetting(Long userId, Integer enabled, Integer version) {
        BizAssert.isTrue(enabled != null
                && (enabled == AgentConstants.STATUS_ON || enabled == AgentConstants.STATUS_OFF),
                ErrorCode.PARAM_ERROR, "开关取值只允许 1开启 2关闭");
        AUserMemorySetting setting = settingOf(userId);
        BizAssert.isTrue(version != null && version.equals(setting.getVersion()),
                ErrorCode.DATA_CONFLICT, "设置已变更，请刷新后重试");
        setting.setEnabled(enabled);
        setting.setVersion(setting.getVersion() + 1);
        if (enabled != AgentConstants.STATUS_ON) {
            setting.setMemoryEpoch(setting.getMemoryEpoch() + 1);
        }
        settingMapper.updateById(setting);
        audit(userId, "SETTING", null, null, "USER", "enabled=" + enabled, null);
        return setting;
    }

    /** 递增记忆纪元（删除/清空时调用，旧异步提炼任务比对后放弃）；无设置行先初始化 */
    @Transactional(rollbackFor = Exception.class)
    public int bumpEpoch(Long userId) {
        settingOf(userId);
        settingMapper.update(null, Wrappers.<AUserMemorySetting>lambdaUpdate()
                .eq(AUserMemorySetting::getUserId, userId)
                .setSql("memory_epoch = memory_epoch + 1, version = version + 1"));
        return settingMapper.selectById(userId).getMemoryEpoch();
    }

    // ---------------- 管理页 CRUD ----------------

    public PageResult<AUserMemoryDTO> list(Long userId, AUserMemoryQuery query) {
        Page<AUserMemory> page = memoryMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()),
                Wrappers.<AUserMemory>lambdaQuery()
                        .eq(AUserMemory::getUserId, userId)
                        .eq(query.getCategory() != null, AUserMemory::getCategory, query.getCategory())
                        .eq(query.getStatus() != null, AUserMemory::getStatus, query.getStatus())
                        .orderByDesc(AUserMemory::getUpdatedAt));
        return PageUtils.convert(page, memory -> BeanCopyUtils.copy(memory, AUserMemoryDTO.class));
    }

    /** 单条查询（id + userId 归属校验，越权与不存在统一返回不存在） */
    public AUserMemoryDTO getOwned(Long userId, Long id) {
        return BeanCopyUtils.copy(ownedMemory(userId, id), AUserMemoryDTO.class);
    }

    /** 手动新增（与自动提炼共用校验与去重合并），来源固定 USER_MANUAL */
    @Transactional(rollbackFor = Exception.class)
    public AUserMemory createManual(Long userId, AUserMemoryReq req) {
        validator.validateCategory(req.getCategory());
        validator.validateContent(req.getContent());
        validateKey(req.getMemoryKey());
        AUserMemory memory = upsert(userId, req.getMemoryKey(), normalizeMode(req.getValueMode()),
                req.getCategory(), req.getContent(), AgentConstants.MEMORY_SOURCE_MANUAL,
                null, java.math.BigDecimal.ONE, "manual-create");
        return memory;
    }

    /**
     * 对话内显式保存（"记住……"）：来源 USER_EXPLICIT、置信度 1；
     * 约束类关键词用 MULTI 键按内容指纹去重，其余单值键最新覆盖。
     */
    @Transactional(rollbackFor = Exception.class)
    public void createExplicit(Long userId, String memoryKey, String category, String content) {
        int mode = "custom_constraint".equals(memoryKey)
                ? AgentConstants.MEMORY_MODE_MULTI : AgentConstants.MEMORY_MODE_SINGLE;
        upsert(userId, memoryKey, mode, category, content, AgentConstants.MEMORY_SOURCE_EXPLICIT,
                null, java.math.BigDecimal.ONE, "explicit-record");
    }

    /**
     * 修改：content 非空时走完整校验与去重（修改不得绕过新增校验）；status 可单独提交。
     * 按 id+userId+version 乐观锁更新。
     */
    @Transactional(rollbackFor = Exception.class)
    public AUserMemory update(Long userId, Long id, AUserMemoryReq req) {
        AUserMemory memory = ownedMemory(userId, id);
        BizAssert.isTrue(req.getVersion() != null && req.getVersion().equals(memory.getVersion()),
                ErrorCode.DATA_CONFLICT, "记忆已变更，请刷新后重试");
        if (req.getContent() != null && !req.getContent().isBlank()) {
            validator.validateCategory(req.getCategory() != null ? req.getCategory() : memory.getCategory());
            validator.validateContent(req.getContent());
            String category = req.getCategory() != null ? req.getCategory() : memory.getCategory();
            // 内容变化重建去重键：与其它行冲突则拒绝（提示已存在），不静默合并他人行
            String dedupeKey = memory.getValueMode() == AgentConstants.MEMORY_MODE_MULTI
                    ? fingerprint(req.getContent()) : memory.getMemoryKey();
            Long conflict = memoryMapper.selectCount(Wrappers.<AUserMemory>lambdaQuery()
                    .eq(AUserMemory::getUserId, userId)
                    .eq(AUserMemory::getMemoryKey, memory.getMemoryKey())
                    .eq(AUserMemory::getDedupeKey, dedupeKey)
                    .ne(AUserMemory::getId, id));
            BizAssert.isTrue(conflict == 0, ErrorCode.DATA_CONFLICT, "已存在相同内容的记忆");
            memory.setContent(req.getContent().strip());
            memory.setCategory(category);
            memory.setDedupeKey(dedupeKey);
            memory.setLastConfirmedAt(LocalDateTime.now());
        }
        if (req.getStatus() != null) {
            BizAssert.isTrue(AgentConstants.STATUS_ON == req.getStatus()
                    || AgentConstants.STATUS_OFF == req.getStatus(), ErrorCode.PARAM_ERROR, "状态只允许 1启用 2停用");
            memory.setStatus(req.getStatus());
        }
        memory.setVersion(memory.getVersion() + 1);
        memoryMapper.updateById(memory);
        audit(userId, "UPDATE", id, memory.getCategory(), "USER", null);
        return memory;
    }

    /** 删除单条：物理删除（唯一键释放），同事务递增纪元 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        AUserMemory memory = ownedMemory(userId, id);
        memoryMapper.deleteById(id);
        bumpEpoch(userId);
        audit(userId, "DELETE", id, memory.getCategory(), "USER", null);
    }

    /** 清空全部：物理删除 + 纪元递增（撤销在途提炼），审计不保留正文 */
    @Transactional(rollbackFor = Exception.class)
    public void clear(Long userId) {
        memoryMapper.delete(Wrappers.<AUserMemory>lambdaQuery().eq(AUserMemory::getUserId, userId));
        bumpEpoch(userId);
        audit(userId, "CLEAR", null, null, "USER", null);
    }

    // ---------------- 提炼采纳 ----------------

    /**
     * 自动提炼候选采纳：逐条二次校验（枚举/敏感/注入/置信度/临时表述），
     * SINGLE 键显式来源优先（AUTO 不覆盖 USER_*，冲突跳过并记拒绝原因）；
     * epoch 不一致整批放弃（用户已删除/清空）。
     */
    public void acceptExtraction(Long userId, Long sourceMessageId, List<MemoryExtractionResult.Operation> ops,
                                 Integer expectedEpoch) {
        if (ops == null || ops.isEmpty()) {
            return;
        }
        String requestId = sourceMessageId == null ? null : String.valueOf(sourceMessageId);
        AUserMemorySetting setting = settingOf(userId);
        if (setting.getEnabled() != AgentConstants.STATUS_ON
                || !setting.getMemoryEpoch().equals(expectedEpoch)) {
            log.info("[memory] 提炼任务纪元不一致或已关闭，整批放弃：userId={}, epoch={}/{}",
                    userId, expectedEpoch, setting.getMemoryEpoch());
            audit(userId, "EXTRACTION_REJECT", null, null, "MEMORY_TASK", "epoch-mismatch", requestId);
            return;
        }
        double minConfidence = agentProperties.getMemory().getExtractionMinConfidence();
        for (MemoryExtractionResult.Operation op : ops) {
            if (op == null || !"UPSERT".equals(op.action()) || op.content() == null) {
                continue;
            }
            Double confidence = op.confidence();
            if (confidence == null || confidence < minConfidence) {
                audit(userId, "EXTRACTION_REJECT", null, op.category(), "MEMORY_TASK", "low-confidence", requestId);
                continue;
            }
            try {
                validator.validateCategory(op.category());
                validator.validateContent(op.content());
                if (validator.isTemporaryExpression(op.content())) {
                    audit(userId, "EXTRACTION_REJECT", null, op.category(), "MEMORY_TASK",
                            "temporary-expression", requestId);
                    continue;
                }
                validateKey(op.memoryKey());
                upsert(userId, op.memoryKey(), AgentConstants.MEMORY_MODE_SINGLE, op.category(),
                        op.content(), AgentConstants.MEMORY_SOURCE_EXTRACTED, sourceMessageId,
                        java.math.BigDecimal.valueOf(confidence), "extraction");
            } catch (BusinessException e) {
                audit(userId, "EXTRACTION_REJECT", null, op.category(), "MEMORY_TASK", e.getMessage(), requestId);
            }
        }
    }

    // ---------------- 上下文注入 ----------------

    /**
     * 启用中的记忆按注入预算取回：显式/手动来源优先、最近确认在前，拼为分类前缀行文本；
     * 总开关关闭返回 "无"。调用方负责随机边界包裹，绝不拼进 system。
     */
    public String enabledMemoriesForPrompt(Long userId) {
        if (!enabled(userId)) {
            return "无";
        }
        List<AUserMemory> memories = memoryMapper.selectList(Wrappers.<AUserMemory>lambdaQuery()
                .eq(AUserMemory::getUserId, userId)
                .eq(AUserMemory::getStatus, AgentConstants.STATUS_ON)
                .orderByDesc(AUserMemory::getSourceType)
                .orderByDesc(AUserMemory::getLastConfirmedAt)
                .last("LIMIT " + agentProperties.getMemory().getMaxInjectedItems()));
        if (memories.isEmpty()) {
            return "无";
        }
        StringBuilder sb = new StringBuilder();
        for (AUserMemory memory : memories) {
            sb.append("- [").append(memory.getCategory()).append("] ").append(memory.getContent()).append('\n');
        }
        return sb.toString();
    }

    // ---------------- 内部 ----------------

    /** 按 userId+key+dedupe 查重合并：无则插入，有则按来源优先级覆盖或仅刷新确认时间 */
    private AUserMemory upsert(Long userId, String memoryKey, int valueMode, String category, String content,
                               String sourceType, Long sourceMessageId, java.math.BigDecimal confidence, String scene) {
        String dedupeKey = valueMode == AgentConstants.MEMORY_MODE_MULTI ? fingerprint(content) : memoryKey;
        AUserMemory existing = memoryMapper.selectOne(Wrappers.<AUserMemory>lambdaQuery()
                .eq(AUserMemory::getUserId, userId)
                .eq(AUserMemory::getMemoryKey, memoryKey)
                .eq(AUserMemory::getDedupeKey, dedupeKey));
        if (existing == null) {
            AUserMemory memory = new AUserMemory();
            memory.setUserId(userId);
            memory.setMemoryKey(memoryKey);
            memory.setValueMode(valueMode);
            memory.setDedupeKey(dedupeKey);
            memory.setCategory(category);
            memory.setContent(content.strip());
            memory.setSourceType(sourceType);
            memory.setSourceMessageId(sourceMessageId);
            memory.setConfidence(confidence);
            memory.setStatus(AgentConstants.STATUS_ON);
            memory.setVersion(0);
            memory.setLastConfirmedAt(LocalDateTime.now());
            memoryMapper.insert(memory);
            audit(userId, "CREATE", memory.getId(), category, operatorOf(sourceType), scene);
            return memory;
        }
        // 自动候选不得静默覆盖用户显式/手动内容；同内容只刷新确认时间
        boolean userOwned = AgentConstants.MEMORY_SOURCE_MANUAL.equals(existing.getSourceType())
                || AgentConstants.MEMORY_SOURCE_EXPLICIT.equals(existing.getSourceType());
        boolean autoIncoming = AgentConstants.MEMORY_SOURCE_EXTRACTED.equals(sourceType);
        if (autoIncoming && (userOwned || !existing.getContent().equals(content.strip()))) {
            audit(userId, "EXTRACTION_REJECT", existing.getId(), category, "MEMORY_TASK", "conflict-keep-old");
            return existing;
        }
        existing.setContent(content.strip());
        existing.setCategory(category);
        existing.setSourceType(sourceType);
        existing.setConfidence(confidence);
        existing.setSourceMessageId(sourceMessageId);
        existing.setVersion(existing.getVersion() + 1);
        existing.setLastConfirmedAt(LocalDateTime.now());
        memoryMapper.updateById(existing);
        audit(userId, "UPDATE", existing.getId(), category, operatorOf(sourceType), scene);
        return existing;
    }

    private AUserMemory ownedMemory(Long userId, Long id) {
        AUserMemory memory = memoryMapper.selectOne(Wrappers.<AUserMemory>lambdaQuery()
                .eq(AUserMemory::getId, id)
                .eq(AUserMemory::getUserId, userId));
        if (memory == null) {
            // 越权与不存在统一"不存在"，不区分暴露资源存在性
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return memory;
    }

    private void validateKey(String memoryKey) {
        if (memoryKey == null || !KEY_PATTERN.matcher(memoryKey).matches()) {
            throw new BusinessException(ErrorCode.AI_INPUT_INVALID, "记忆键不合法");
        }
    }

    private int normalizeMode(Integer valueMode) {
        return valueMode != null && valueMode == AgentConstants.MEMORY_MODE_MULTI
                ? AgentConstants.MEMORY_MODE_MULTI : AgentConstants.MEMORY_MODE_SINGLE;
    }

    /** 规范化内容指纹（MULTI 去重键）：去空白后 SHA-256 */
    private String fingerprint(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.strip().replaceAll("\\s+", "").getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private String operatorOf(String sourceType) {
        return AgentConstants.MEMORY_SOURCE_EXTRACTED.equals(sourceType) ? "MEMORY_TASK" : "USER";
    }

    private void audit(Long userId, String operation, Long memoryId, String category,
                       String operatorType, String resultCode) {
        audit(userId, operation, memoryId, category, operatorType, resultCode, null);
    }

    /** requestId：来源关联（提炼任务=触发消息 id 字符串），便于审计行与轮次对齐 */
    private void audit(Long userId, String operation, Long memoryId, String category,
                       String operatorType, String resultCode, String requestId) {
        try {
            AUserMemoryAudit audit = new AUserMemoryAudit();
            audit.setUserId(userId);
            audit.setOperation(operation);
            audit.setMemoryId(memoryId);
            audit.setCategory(category);
            audit.setOperatorType(operatorType);
            audit.setResultCode(resultCode);
            audit.setRequestId(requestId);
            auditMapper.insert(audit);
        } catch (Exception e) {
            log.warn("[memory] 审计写入失败（已忽略）：userId={}, op={}: {}", userId, operation, e.getMessage());
        }
    }
}
