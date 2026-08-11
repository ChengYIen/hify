package com.hify.common.util;

/**
 * Token 估算工具（启发式近似，非精确 tokenizer）.
 *
 * <p>本工具不引入 tiktoken 等重型依赖，用字符级启发式估算消息的 token 数，
 * 供上下文裁剪（token 预算）做取舍判断。估算原则：</p>
 * <ul>
 *   <li>中文/日文等宽字符 ≈ 1 token / 字</li>
 *   <li>ASCII ≈ 1 token / 4 字符（约等于常见英文 tokenizer 密度）</li>
 *   <li>其他字符 ≈ 1 token / 2 字符</li>
 *   <li>每条消息另加固定开销（role 等结构字段）</li>
 * </ul>
 *
 * <p>对超预算场景宁可多估不可少估：裁剪是"丢旧保新"，高估会让上下文更保守，
 * 不会导致溢出。</p>
 */
public final class TokenEstimator {

    private static final int CJK_TOKENS_PER_CHAR = 1;
    private static final double ASCII_TOKENS_PER_CHAR = 0.25;
    private static final double OTHER_TOKENS_PER_CHAR = 0.5;
    private static final int MESSAGE_OVERHEAD_TOKENS = 4;

    private TokenEstimator() {
    }

    /**
     * 估算一段文本的 token 数.
     *
     * @param text 文本，null 或空白返回 0
     * @return 估算 token 数
     */
    public static int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int cjk = 0;
        int ascii = 0;
        int other = 0;
        for (char c : text.toCharArray()) {
            if (c < 128) {
                ascii++;
            } else if (isWideChar(c)) {
                cjk++;
            } else {
                other++;
            }
        }
        return (int) (cjk * CJK_TOKENS_PER_CHAR
                + ascii * ASCII_TOKENS_PER_CHAR
                + other * OTHER_TOKENS_PER_CHAR)
                + MESSAGE_OVERHEAD_TOKENS;
    }

    /** 中日韩统一表意文字及日文假名视为"宽字符"，近似 1 token/字。 */
    private static boolean isWideChar(char c) {
        Character.UnicodeScript script = Character.UnicodeScript.of(c);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA;
    }
}
