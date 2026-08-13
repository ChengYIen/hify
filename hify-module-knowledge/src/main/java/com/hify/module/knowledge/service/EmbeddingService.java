package com.hify.module.knowledge.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.util.RedisUtil;
import com.hify.shared.llm.LlmProviderApi;
import com.hify.shared.llm.dto.EmbeddingResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Embedding 服务 —— 封装向量化调用 + Redis 缓存 + 分批 + 降级.
 * <p>
 * 通过 shared {@link LlmProviderApi} 调用 provider 模块，不感知底层厂商。
 * 向量统一转成 pgvector 字面量字符串（如 {@code "[0.1,0.2,...]"}）供向量库直接使用。
 * </p>
 *
 * <h3>缓存与降级</h3>
 * <ul>
 *   <li>Embedding 是确定性纯函数，同一文本向量恒定 → 可安全缓存（24h），文档重索引 / 重复查询命中率高</li>
 *   <li>Redis 不可用时跳过缓存直调 LLM，绝不因 Redis 挂掉报 500（CLAUDE.md §43）</li>
 *   <li>未命中文本按每批 {@link #BATCH_SIZE} 条调用，规避 OpenAI 单请求 2048 条上限</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    /** 单次 Embedding 调用最多文本数（OpenAI 单请求上限 2048，保守取 100） */
    private static final int BATCH_SIZE = 100;

    /** Embedding 缓存 TTL（小时） */
    private static final long CACHE_TTL_HOURS = 24;

    /** 缓存 key 前缀，遵循 hify:{module}:{entity}:{identifier} 规范 */
    private static final String CACHE_KEY_PREFIX = "hify:knowledge:embedding:";

    private final LlmProviderApi llmProviderApi;

    /**
     * Redis 工具（可选注入）—— Redis 未装配/不可用时为 null，缓存自动跳过直调 LLM（CLAUDE.md §43）.
     * <p>与 {@code AgentServiceImpl} 同模式：{@link RedisUtil} 是条件 Bean
     * （{@code @ConditionalOnBean(RedisTemplate.class)}），不能作为必选依赖，否则 Redis 环境缺失时应用启动失败。</p>
     */
    @Autowired(required = false)
    private RedisUtil redisUtil;

    /**
     * 单个文本向量化.
     *
     * @param modelId Embedding 模型配置 ID
     * @param text    文本
     * @return pgvector 字面量（如 {@code "[0.1,0.2,...]"}）
     */
    public String embed(Long modelId, String text) {
        return embedAll(modelId, List.of(text)).get(0);
    }

    /**
     * 批量向量化，返回与入参顺序一致的向量字面量列表.
     *
     * @param modelId Embedding 模型配置 ID
     * @param texts   待向量化文本
     * @return 向量字面量列表
     */
    public List<String> embedAll(Long modelId, List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>(Collections.nCopies(texts.size(), null));
        List<String> missed = new ArrayList<>();
        List<Integer> missedIndexes = new ArrayList<>();

        // 先查缓存，命中直接复用
        for (int i = 0; i < texts.size(); i++) {
            String cached = getCached(modelId, texts.get(i));
            if (cached != null) {
                result.set(i, cached);
            } else {
                missedIndexes.add(i);
                missed.add(texts.get(i));
            }
        }

        // 未命中部分分批调用 LLM
        for (int from = 0; from < missed.size(); from += BATCH_SIZE) {
            int to = Math.min(from + BATCH_SIZE, missed.size());
            List<String> batch = missed.subList(from, to);
            EmbeddingResponseDTO response = llmProviderApi.embed(modelId, batch);
            List<List<Float>> embeddings = response.getEmbeddings();
            if (embeddings == null || embeddings.size() != batch.size()) {
                throw new BizException(ErrorCode.LLM_CALL_FAILED,
                        "Embedding 返回数量与入参不符: 期望=" + batch.size()
                                + ", 实际=" + (embeddings == null ? 0 : embeddings.size()));
            }
            for (int j = 0; j < batch.size(); j++) {
                String literal = toVectorLiteral(embeddings.get(j));
                int originalIndex = missedIndexes.get(from + j);
                result.set(originalIndex, literal);
                putCached(modelId, batch.get(j), literal);
            }
        }
        return result;
    }

    // ----------------------------------------------------------------
    // 私有方法
    // ----------------------------------------------------------------

    /** List<Float> → pgvector 字面量 "[0.1,0.2,...]" */
    private String toVectorLiteral(List<Float> vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vec.get(i));
        }
        return sb.append(']').toString();
    }

    private String cacheKey(Long modelId, String text) {
        return CACHE_KEY_PREFIX + modelId + ":" + DigestUtil.md5Hex(text);
    }

    /** 读缓存：Redis 未装配返回未命中；失败降级为未命中，直接调 LLM */
    private String getCached(Long modelId, String text) {
        if (redisUtil == null) {
            return null;
        }
        try {
            return redisUtil.get(cacheKey(modelId, text));
        } catch (Exception e) {
            log.warn("Embedding 缓存读取失败，降级直调: {}", e.getMessage());
            return null;
        }
    }

    /** 写缓存：Redis 未装配跳过；失败仅告警，不阻断主流程 */
    private void putCached(Long modelId, String text, String literal) {
        if (redisUtil == null) {
            return;
        }
        try {
            redisUtil.set(cacheKey(modelId, text), literal, CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Embedding 缓存写入失败，忽略: {}", e.getMessage());
        }
    }
}
