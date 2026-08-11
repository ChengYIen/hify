package com.hify.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TokenEstimator 单元测试.
 */
class TokenEstimatorTest {

    @Test
    void should_return_zero_when_null_or_blank() {
        assertThat(TokenEstimator.estimate(null)).isZero();
        assertThat(TokenEstimator.estimate("")).isZero();
        assertThat(TokenEstimator.estimate("   \n\t ")).isZero();
    }

    @Test
    void should_estimate_chinese_near_one_token_per_char() {
        // 4 个汉字 ≈ 4 token + 每条消息固定开销 4 = 8
        assertThat(TokenEstimator.estimate("你好世界")).isEqualTo(8);
        assertThat(TokenEstimator.estimate("这是一段用来验证估算工具是否正常工作的中文文本")).isGreaterThan(20);
    }

    @Test
    void should_estimate_ascii_near_four_chars_per_token() {
        // 5 个 ascii ≈ 1.25 token → int 1 + 开销 4 = 5
        assertThat(TokenEstimator.estimate("hello")).isEqualTo(5);
        // 1000 ascii ≈ 250 + 4 = 254
        assertThat(TokenEstimator.estimate("a".repeat(1000))).isEqualTo(254);
    }

    @Test
    void should_estimate_mixed_cjk_and_ascii() {
        // 2 汉字(2) + 6 ascii(1.5) = 3.5 → int 3 + 4 = 7
        assertThat(TokenEstimator.estimate("你好 hello")).isEqualTo(7);
    }

    @Test
    void should_be_monotonic_with_text_length() {
        String shortText = "short";
        String longText = "这是一段比 short 明显更长且更密集的中文文本内容";
        assertThat(TokenEstimator.estimate(longText)).isGreaterThan(TokenEstimator.estimate(shortText));
    }
}
