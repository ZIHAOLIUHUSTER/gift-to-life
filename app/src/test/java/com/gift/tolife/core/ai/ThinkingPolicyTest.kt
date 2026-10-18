package com.gift.tolife.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 思考策略与降级梯子。
 *
 * 背景：总结默认要"思考但限制长度"——不限制时思考会吃光输出预算、正文变空。
 * 而各家服务商认的参数不同（SiliconFlow 认 thinking_budget，DeepSeek 官方认 reasoning_effort/thinking），
 * 所以不认就逐级丢，不能直接失败。
 */
class ThinkingPolicyTest {

    @Test
    fun `设置项映射到策略`() {
        assertEquals(ThinkingPolicy.OFF, ThinkingPolicy.fromSetting("off"))
        assertEquals(ThinkingPolicy.HIGH_BOUNDED, ThinkingPolicy.fromSetting("high"))
        assertEquals(ThinkingPolicy.HIGH_UNBOUNDED, ThinkingPolicy.fromSetting("deep"))
        // 脏数据按默认处理，不能崩
        assertEquals(ThinkingPolicy.HIGH_BOUNDED, ThinkingPolicy.fromSetting(null))
        assertEquals(ThinkingPolicy.HIGH_BOUNDED, ThinkingPolicy.fromSetting("whatever"))
    }

    @Test
    fun `默认策略是 high 且带思维链上限`() {
        val p = ThinkingPolicy.HIGH_BOUNDED
        assertTrue(p.enabled)
        assertEquals("high", p.effort)
        assertEquals(2048, p.budgetTokens)
        assertFalse(ThinkingPolicy.OFF.enabled)
        assertNull(ThinkingPolicy.OFF.effort)
    }

    @Test
    fun `降级梯子逐级丢掉服务商不认的参数`() {
        val plan = SummaryCallPlan.initial(ThinkingPolicy.HIGH_BOUNDED)
        assertTrue(plan.jsonMode)
        assertEquals(8192, plan.maxTokens)
        assertEquals(2048, plan.policy.budgetTokens)

        // 1 丢 thinking_budget
        val s1 = plan.degraded(1)
        assertNull(s1.policy.budgetTokens)
        assertTrue(s1.policy.enabled)

        // 2 丢 response_format
        val s2 = s1.degraded(2)
        assertFalse(s2.jsonMode)

        // 3 丢思考
        val s3 = s2.degraded(3)
        assertFalse(s3.policy.enabled)
        assertNull(s3.policy.effort)

        // 4 缩小输出上限
        val s4 = s3.degraded(4)
        assertEquals(SummaryCallPlan.MIN_OUTPUT_TOKENS, s4.maxTokens)

        // 梯子有底：越界不再变化
        assertEquals(s4, s4.degraded(99))
    }
}
