package com.gift.tolife.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ModelConfigCodecTest {

    @Test
    fun `parses new export format`() {
        val values = ModelConfigCodec.parse(
            """{"version":2,"baseUrl":"https://api.example.com","tagModel":"tag-1","summaryModel":"sum-1","visionModel":"vis-1"}"""
        )
        assertEquals("https://api.example.com/", values.baseUrl)
        assertEquals("tag-1", values.tagModel)
        assertEquals("sum-1", values.summaryModel)
        assertEquals("vis-1", values.visionModel)
    }

    @Test
    fun `parses legacy AppSettings format and ignores api key`() {
        val values = ModelConfigCodec.parse(
            """{"apiKey":"sk-secret","baseUrl":"https://api.example.com/","tagModel":"a","summaryModel":"b","visionModel":"c","themeMode":"dark","biometricEnabled":true}"""
        )
        assertEquals("https://api.example.com/", values.baseUrl)
        assertEquals("a", values.tagModel)
        assertEquals("b", values.summaryModel)
        assertEquals("c", values.visionModel)
    }

    @Test
    fun `trims whitespace`() {
        val values = ModelConfigCodec.parse(
            """{"baseUrl":"  https://api.example.com  ","tagModel":" a ","summaryModel":"b","visionModel":"c"}"""
        )
        assertEquals("https://api.example.com/", values.baseUrl)
        assertEquals("a", values.tagModel)
    }

    @Test
    fun `rejects missing model fields`() {
        assertThrows(IllegalArgumentException::class.java) {
            ModelConfigCodec.parse("""{"baseUrl":"https://api.example.com"}""")
        }
    }

    @Test
    fun `rejects non https base url`() {
        assertThrows(IllegalArgumentException::class.java) {
            ModelConfigCodec.parse(
                """{"baseUrl":"http://api.example.com","tagModel":"a","summaryModel":"b","visionModel":"c"}"""
            )
        }
    }

    @Test
    fun `rejects future version`() {
        assertThrows(IllegalArgumentException::class.java) {
            ModelConfigCodec.parse(
                """{"version":4,"baseUrl":"https://api.example.com","tagModel":"a","summaryModel":"b","visionModel":"c"}"""
            )
        }
    }

    @Test
    fun `v3 解析独立总结地址并规范化`() {
        val v = ModelConfigCodec.parse(
            """{"version":3,"baseUrl":"https://a.example.com","tagModel":"t","summaryModel":"s","visionModel":"v",
               "summaryBaseUrl":"https://api.siliconflow.cn/v1/chat/completions"}"""
        )
        assertEquals("https://a.example.com/", v.baseUrl)
        assertEquals("https://api.siliconflow.cn/", v.summaryBaseUrl)
    }

    @Test
    fun `v2 老配置仍然接受 且总结跟随默认供应商`() {
        val v = ModelConfigCodec.parse(
            """{"version":2,"baseUrl":"https://api.example.com","tagModel":"t","summaryModel":"s","visionModel":"v"}"""
        )
        assertEquals("", v.summaryBaseUrl)
    }

    @Test
    fun `总结地址非法时报错`() {
        assertThrows(IllegalArgumentException::class.java) {
            ModelConfigCodec.parse(
                """{"version":3,"baseUrl":"https://api.example.com","tagModel":"t","summaryModel":"s","visionModel":"v",
                   "summaryBaseUrl":"http://insecure.example.com"}"""
            )
        }
    }

    @Test
    fun `rejects non object json`() {
        assertThrows(IllegalArgumentException::class.java) { ModelConfigCodec.parse("[1,2,3]") }
    }

    @Test
    fun `rejects empty json`() {
        assertThrows(IllegalArgumentException::class.java) { ModelConfigCodec.parse("") }
    }
}
