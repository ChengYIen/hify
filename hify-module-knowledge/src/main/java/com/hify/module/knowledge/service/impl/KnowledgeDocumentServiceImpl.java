package com.hify.module.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.util.TokenEstimator;
import com.hify.module.knowledge.controller.dto.DocumentChunkResponse;
import com.hify.module.knowledge.controller.dto.KnowledgeDocumentResponse;
import com.hify.module.knowledge.repository.ChunkVectorRepository;
import com.hify.module.knowledge.repository.KnowledgeDocumentMapper;
import com.hify.module.knowledge.repository.KnowledgeMapper;
import com.hify.module.knowledge.repository.entity.KnowledgeDocumentEntity;
import com.hify.module.knowledge.repository.entity.KnowledgeEntity;
import com.hify.module.knowledge.service.EmbeddingService;
import com.hify.module.knowledge.service.KnowledgeDocumentService;
import com.hify.module.knowledge.service.dto.ChunkDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识库文档业务实现.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_FILE_TYPES = Set.of("txt", "md", "pdf");
    private static final int CHUNK_SIZE_TOKENS = 512;
    private static final int CHUNK_OVERLAP_TOKENS = 64;

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeMapper knowledgeMapper;
    private final EmbeddingService embeddingService;
    private final ChunkVectorRepository chunkVectorRepository;
    private final @Qualifier("asyncExecutor") ThreadPoolTaskExecutor asyncExecutor;
    @Value("${hify.upload-dir:upload}")
    private String uploadDir;

    @Override
    public IPage<KnowledgeDocumentResponse> pageByKnowledge(Long knowledgeId, int page, int size) {
        Page<KnowledgeDocumentEntity> p = new Page<>(page, size);
        Page<KnowledgeDocumentEntity> result = knowledgeDocumentMapper.selectPage(p,
                new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .eq(KnowledgeDocumentEntity::getKnowledgeId, knowledgeId)
                        .orderByDesc(KnowledgeDocumentEntity::getId));
        return result.convert(this::toResponse);
    }

    @Override
    public KnowledgeDocumentResponse getById(Long id) {
        KnowledgeDocumentEntity entity = knowledgeDocumentMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND, "id=" + id);
        }
        return toResponse(entity);
    }

    @Override
    public Long upload(Long knowledgeId, MultipartFile file) {
        if (knowledgeMapper.selectById(knowledgeId) == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NOT_FOUND, "id=" + knowledgeId);
        }
        String filename = file.getOriginalFilename();
        String fileType = extensionOf(filename);
        if (!ALLOWED_FILE_TYPES.contains(fileType)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅支持 txt/md/pdf 文件");
        }
        if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(ErrorCode.PARAM_RANGE, "文件大小不能超过 10MB");
        }

        KnowledgeDocumentEntity entity = new KnowledgeDocumentEntity();
        entity.setKnowledgeId(knowledgeId);
        entity.setFilename(filename != null ? filename : "unnamed." + fileType);
        entity.setFileType(fileType);
        entity.setFileSize(file.getSize());
        entity.setStatus("PENDING");
        entity.setChunkCount(0);
        knowledgeDocumentMapper.insert(entity);

        try {
            Path dir = Path.of(uploadDir, String.valueOf(knowledgeId));
            Files.createDirectories(dir);
            Path target = dir.resolve(entity.getId() + "_" + sanitize(filename)).toAbsolutePath();
            file.transferTo(target);
            entity.setFileUrl(target.toString());
            knowledgeDocumentMapper.updateById(entity);
        } catch (IOException e) {
            knowledgeDocumentMapper.deleteById(entity.getId());
            log.warn("文件落盘失败: documentId={}, filename={}", entity.getId(), filename, e);
            throw new BizException(ErrorCode.IO_ERROR, "文件保存失败: " + e.getMessage());
        }

        asyncExecutor.execute(() -> processDocument(entity.getId()));
        log.info("文档上传完成，开始异步处理: documentId={}, kbId={}", entity.getId(), knowledgeId);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (knowledgeDocumentMapper.selectById(id) == null) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND, "id=" + id);
        }
        knowledgeDocumentMapper.deleteById(id);
        chunkVectorRepository.logicalDeleteByDocument(id);
        log.info("KnowledgeDocument 删除成功: id={}", id);
    }

    @Override
    public List<DocumentChunkResponse> listChunks(Long documentId) {
        if (knowledgeDocumentMapper.selectById(documentId) == null) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND, "id=" + documentId);
        }
        return chunkVectorRepository.listByDocument(documentId).stream()
                .map(chunk -> DocumentChunkResponse.builder()
                        .id(chunk.getId())
                        .knowledgeId(chunk.getKnowledgeId())
                        .documentId(chunk.getDocumentId())
                        .chunkIndex(chunk.getChunkIndex())
                        .content(chunk.getContent())
                        .createdAt(chunk.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public void indexContent(Long documentId, String content) {
        if (content == null || content.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文档内容为空");
        }
        KnowledgeDocumentEntity doc = knowledgeDocumentMapper.selectById(documentId);
        if (doc == null) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND, "id=" + documentId);
        }
        KnowledgeEntity knowledge = knowledgeMapper.selectById(doc.getKnowledgeId());
        if (knowledge == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NOT_FOUND, "id=" + doc.getKnowledgeId());
        }
        if (knowledge.getEmbeddingModelId() == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_CONFIG_INVALID,
                    "知识库未配置 Embedding 模型: id=" + knowledge.getId());
        }
        // 状态置为处理中，避免重复提交
        updateStatus(documentId, "PROCESSING", null);
        log.info("文档开始异步索引: documentId={}, knowledgeId={}", documentId, knowledge.getId());

        // 切块→Embedding→写 pgvector 全部在 asyncExecutor 异步执行，
        // 不占用 Tomcat 线程；失败通过 status=FAILED + errorMessage 反映。
        Long modelId = knowledge.getEmbeddingModelId();
        Long knowledgeId = doc.getKnowledgeId();
        asyncExecutor.execute(() -> {
            try {
                List<ChunkDTO> chunks = splitChunks(content);
                chunks = embedChunks(modelId, chunks);
                saveChunks(documentId, knowledgeId, chunks);
            } catch (Exception e) {
                log.error("文档内容索引失败: documentId={}", documentId, e);
                updateStatus(documentId, "FAILED", e.getMessage());
            }
        });
    }

    /** 文档处理管线：只串联五步 + 状态管理，任何一步失败都落 FAILED. */
    private void processDocument(Long documentId) {
        try {
            KnowledgeDocumentEntity doc = knowledgeDocumentMapper.selectById(documentId);
            if (doc == null) {
                return;
            }
            KnowledgeEntity knowledge = knowledgeMapper.selectById(doc.getKnowledgeId());
            if (knowledge == null || knowledge.getEmbeddingModelId() == null) {
                throw new BizException(ErrorCode.KNOWLEDGE_CONFIG_INVALID,
                        "知识库未配置 Embedding 模型: id=" + doc.getKnowledgeId());
            }

            // 1. 状态更新
            updateStatus(documentId, "PROCESSING", null);
            // 2. 解析
            String text = extractText(doc.getFileUrl(), doc.getFileType());
            // 3. 分块
            List<ChunkDTO> chunks = splitChunks(text);
            // 4. 向量化
            chunks = embedChunks(knowledge.getEmbeddingModelId(), chunks);
            // 5. 存储
            saveChunks(doc.getId(), doc.getKnowledgeId(), chunks);
        } catch (Exception e) {
            log.error("文档处理管线失败: documentId={}", documentId, e);
            updateStatus(documentId, "FAILED", e.getMessage());
        }
    }

    /** 解析：txt/md 按 UTF-8 读取；pdf 用 PDFBox 提取文字层，扫描版返回失败. */
    private String extractText(String filePath, String fileType) throws IOException {
        if (filePath == null || filePath.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件路径为空");
        }
        Path path = Path.of(filePath);
        if ("pdf".equalsIgnoreCase(fileType)) {
            String text;
            try (PDDocument pdf = PDDocument.load(path.toFile())) {
                text = new PDFTextStripper().getText(pdf);
            }
            if (text == null || text.isBlank()) {
                throw new BizException(ErrorCode.KNOWLEDGE_DOC_PARSE_FAILED,
                        "扫描版 PDF 无文字层，一期不支持");
            }
            return text;
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** 分块：递归按 token 预算切割，边界优先级 段落 > 句子 > 字符截断. */
    private List<ChunkDTO> splitChunks(String text) {
        if (text == null || text.isBlank()) {
            throw new BizException(ErrorCode.KNOWLEDGE_DOC_PARSE_FAILED, "文档内容为空");
        }
        List<String> raw = splitRecursive(text.replace("\r\n", "\n").trim());
        if (raw.isEmpty()) {
            throw new BizException(ErrorCode.KNOWLEDGE_DOC_PARSE_FAILED, "文档内容切片后为空");
        }
        List<ChunkDTO> chunks = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            String content = raw.get(i);
            chunks.add(ChunkDTO.builder()
                    .chunkIndex(i)
                    .content(content)
                    .tokenCount(TokenEstimator.estimate(content))
                    .build());
        }
        return chunks;
    }

    private List<String> splitRecursive(String text) {
        if (TokenEstimator.estimate(text) <= CHUNK_SIZE_TOKENS) {
            return List.of(text);
        }
        int split = findSplitIndex(text);
        if (split <= 0 || split >= text.length()) {
            return List.of(text);
        }
        int overlapStart = findOverlapStart(text, split);
        List<String> chunks = new ArrayList<>();
        chunks.addAll(splitRecursive(text.substring(0, split)));
        chunks.addAll(splitRecursive(text.substring(overlapStart)));
        return chunks;
    }

    private int findSplitIndex(String text) {
        double tokensPerChar = (double) TokenEstimator.estimate(text) / text.length();
        int target = (int) (CHUNK_SIZE_TOKENS / tokensPerChar);
        if (target >= text.length()) {
            return text.length();
        }

        int minStart = Math.max(1, text.length() / 4);
        int paragraph = text.lastIndexOf("\n\n", target);
        if (paragraph >= minStart) {
            return paragraph + 2;
        }
        for (int i = Math.min(target, text.length() - 1); i >= minStart; i--) {
            if (isSentenceEnd(text.charAt(i))) {
                return i + 1;
            }
        }
        return Math.max(minStart, target);
    }

    private boolean isSentenceEnd(char c) {
        return c == '。' || c == '？' || c == '！' || c == '.' || c == '?' || c == '!';
    }

    /** 从 split 位置往前回退约 overlap token 的字符数，让下一块携带上一块尾部. */
    private int findOverlapStart(String text, int split) {
        String left = text.substring(0, split);
        if (left.isBlank()) {
            return split;
        }
        double tokensPerChar = (double) TokenEstimator.estimate(left) / left.length();
        int overlapChars = (int) (CHUNK_OVERLAP_TOKENS / tokensPerChar);
        if (overlapChars >= split) {
            overlapChars = split - 1;
        }
        return split - Math.max(1, overlapChars);
    }

    /**
     * 向量化：复用 EmbeddingService（100 条/批 + Redis 缓存）。
     * Provider 适配层已按 data[].index 排序，DTO 保证返回顺序与入参一致，这里按原顺序补 embedding。
     */
    private List<ChunkDTO> embedChunks(Long modelId, List<ChunkDTO> chunks) {
        if (chunks.isEmpty()) {
            return chunks;
        }
        List<String> texts = chunks.stream().map(ChunkDTO::getContent).collect(Collectors.toList());
        List<String> vectors = embeddingService.embedAll(modelId, texts);
        if (vectors.size() != chunks.size()) {
            throw new BizException(ErrorCode.LLM_CALL_FAILED,
                    "Embedding 返回数量与块数不符: 期望=" + chunks.size()
                            + ", 实际=" + vectors.size());
        }
        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).setEmbedding(vectors.get(i));
        }
        return chunks;
    }

    /** 存储：批量写入 document_chunk，成功后置 DONE 并刷新知识库统计. */
    private void saveChunks(Long documentId, Long knowledgeId, List<ChunkDTO> chunks) {
        chunkVectorRepository.deleteByDocument(documentId);
        chunkVectorRepository.batchInsertChunks(knowledgeId, documentId, chunks);

        KnowledgeDocumentEntity doc = knowledgeDocumentMapper.selectById(documentId);
        if (doc != null) {
            doc.setChunkCount(chunks.size());
            knowledgeDocumentMapper.updateById(doc);
        }
        updateStatus(documentId, "DONE", null);
        refreshKnowledgeStats(knowledgeId);
        log.info("文档索引完成: documentId={}, chunks={}", documentId, chunks.size());
    }

    private void refreshKnowledgeStats(Long knowledgeId) {
        KnowledgeEntity knowledge = knowledgeMapper.selectById(knowledgeId);
        if (knowledge == null) {
            return;
        }
        knowledge.setChunkCount(chunkVectorRepository.countByKnowledge(knowledgeId));
        knowledge.setDocCount(knowledgeDocumentMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .eq(KnowledgeDocumentEntity::getKnowledgeId, knowledgeId)
                        .eq(KnowledgeDocumentEntity::getStatus, "DONE")).intValue());
        knowledgeMapper.updateById(knowledge);
    }

    private void updateStatus(Long documentId, String status, String errorMessage) {
        KnowledgeDocumentEntity doc = knowledgeDocumentMapper.selectById(documentId);
        if (doc == null) {
            return;
        }
        doc.setStatus(status);
        doc.setErrorMessage(errorMessage);
        knowledgeDocumentMapper.updateById(doc);
    }

    private String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String sanitize(String filename) {
        if (filename == null) {
            return "file";
        }
        return filename.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private KnowledgeDocumentResponse toResponse(KnowledgeDocumentEntity entity) {
        return KnowledgeDocumentResponse.builder()
                .id(entity.getId())
                .knowledgeId(entity.getKnowledgeId())
                .filename(entity.getFilename())
                .fileType(entity.getFileType())
                .fileSize(entity.getFileSize())
                .fileUrl(entity.getFileUrl())
                .status(entity.getStatus())
                .chunkCount(entity.getChunkCount())
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
