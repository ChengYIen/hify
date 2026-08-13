package com.hify.module.knowledge.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本切块工具 —— 滑动窗口切分 + 换行优先 + 重叠.
 *
 * <p>将长文档切成检索粒度合适的块：目标块长 {@link #CHUNK_SIZE} 字符，
 * 相邻块重叠 {@link #CHUNK_OVERLAP} 字符保证语义连贯；
 * 窗口结束位置优先落在换行符上，避免切碎句子。</p>
 */
public final class TextChunker {

    /** 目标块长（字符） */
    private static final int CHUNK_SIZE = 500;

    /** 相邻块重叠长度（字符），保证上下文连贯 */
    private static final int CHUNK_OVERLAP = 50;

    private TextChunker() {
    }

    /**
     * 将文本切分为块列表.
     *
     * @param text 原始文本（可为 null / 空白）
     * @return 切块列表；文本为空时返回空列表
     */
    public static List<String> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = text.replace("\r\n", "\n").trim();
        List<String> chunks = new ArrayList<>();
        int length = normalized.length();
        int start = 0;

        while (start < length) {
            int end = Math.min(start + CHUNK_SIZE, length);
            // 未到末尾时，尽量在最近的换行处断开
            if (end < length) {
                int newline = normalized.lastIndexOf('\n', end);
                if (newline > start + CHUNK_SIZE / 2) {
                    end = newline;
                }
            }
            String chunk = normalized.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (end >= length) {
                break;
            }
            // 重叠滑动，保持上下文连贯
            start = end - CHUNK_OVERLAP;
        }
        return chunks;
    }
}
