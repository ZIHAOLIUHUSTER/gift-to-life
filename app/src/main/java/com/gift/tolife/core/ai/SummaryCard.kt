package com.gift.tolife.core.ai

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException

/** 卡片类型。旧数据/解析失败时退化为 [PLAIN]。 */
enum class SummaryCardType { FACT, QUOTE, INSIGHT, PLAIN }

/**
 * 一张总结卡片（对应提示词契约里的 cards 元素）。
 *
 * @param time     仅引语卡使用：这句原话写下的时间（模型给的粗略时间）
 * @param evidence 仅洞察卡使用：观察的依据
 */
data class SummaryCard(
    val type: SummaryCardType,
    val label: String,
    val title: String?,
    val text: String,
    val time: String? = null,
    val evidence: String? = null,
)

/**
 * 一副总结牌：一张关键词封面 + N 张卡片 + 一封正文。
 *
 * @param isStructured false 表示这是旧版纯文本总结（或解析失败），渲染时按段落切卡。
 */
data class SummaryDeck(
    val keyword: String,
    val cards: List<SummaryCard>,
    val letter: String,
    val isStructured: Boolean,
)

/**
 * 把模型返回的 JSON 解析成 [SummaryDeck]。
 *
 * 三层宽容（2026-09-24 线上踩过坑之后加的）：
 * 1. 去 ``` 围栏与前后解释文字；
 * 2. **字段别名**：不同模型的键名会飘（`content`/`date`/`summary`…），逐个兜底；
 * 3. **绝不因为"形状不对"就报错**——只要里面有能读的文字，[parseOrPlain] 一定给出一副牌。
 */
object SummaryCardParser {

    /** 纯文本兜底时最多切几张卡（否则一段草稿能切出二十多张，翻到手酸）。 */
    private const val MAX_PLAIN_CARDS = 6

    private val DEFAULT_LABELS = mapOf(
        SummaryCardType.FACT to "事实",
        SummaryCardType.QUOTE to "一句话",
        SummaryCardType.INSIGHT to "你没注意到的",
    )

    // 键名别名：模型不一定守着契约
    private val TEXT_KEYS = listOf("text", "content", "body", "desc", "description", "detail")
    private val TITLE_KEYS = listOf("title", "name", "heading", "headline")
    private val TIME_KEYS = listOf("time", "date", "when", "at", "timestamp")
    private val LABEL_KEYS = listOf("label", "tag", "badge", "kind")
    private val EVIDENCE_KEYS = listOf("evidence", "basis", "reason", "proof", "source")
    private val CARDS_KEYS = listOf("cards", "moments", "items", "list", "fragments", "highlights")
    private val LETTER_KEYS = listOf("letter", "summary", "text", "content", "body", "message")
    private val KEYWORD_KEYS = listOf("keyword", "theme", "title", "headline")
    private val NOISE_KEYS = setOf("type", "label", "kind", "tag")

    /** 去掉 ``` 围栏与首尾噪声，拿到一个 JSON 对象串；拿不到返回 null。 */
    fun extractJsonObject(raw: String): String? {
        jsonCandidates(raw).lastOrNull()?.let { return it }
        val text = raw.trim()
        if (text.isEmpty()) return null
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return text.substring(start, end + 1)
    }

    /**
     * 扫出文本里所有**顶层平衡**的 `{...}`。
     *
     * 用途：模型常在思考/草稿里写好几种版本的 JSON，最后那版才是定稿。
     * 也用于"只拿到思考、没有正文"时把定稿捞出来（4.4.3）。
     */
    fun jsonCandidates(raw: String): List<String> {
        val text = raw.trim()
        if (text.isEmpty()) return emptyList()
        val out = mutableListOf<String>()
        var depth = 0
        var start = -1
        var inString = false
        var escaped = false
        for ((i, c) in text.withIndex()) {
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                '}' -> if (depth > 0) {
                    depth--
                    if (depth == 0 && start >= 0) {
                        out += text.substring(start, i + 1)
                        start = -1
                    }
                }
            }
        }
        return out
    }

    private fun str(obj: JsonObject, keys: List<String>): String? =
        keys.firstNotNullOfOrNull { key ->
            obj.get(key)
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString
                ?.trim()
                ?.ifBlank { null }
        }

    fun parse(raw: String): SummaryDeck? = parseDetailed(raw)?.deck

    /** 解析结果 + 命中的那段 JSON（存进数据库的就是它）。 */
    data class ParsedDeck(val deck: SummaryDeck, val json: String)

    fun parseDetailed(raw: String): ParsedDeck? {
        // 从后往前试：模型可能写了草稿再写定稿，定稿在后面
        for (json in jsonCandidates(raw).asReversed()) {
            val parsed = try {
                JsonParser().parse(json)
            } catch (e: JsonSyntaxException) {
                continue
            }
            if (!parsed.isJsonObject) continue
            parseObject(parsed.asJsonObject)?.let { return ParsedDeck(it, json) }
        }
        return null
    }

    private fun parseObject(root: JsonObject): SummaryDeck? {

        val letter = str(root, LETTER_KEYS).orEmpty()
        val keyword = str(root, KEYWORD_KEYS).orEmpty()

        val cards = mutableListOf<SummaryCard>()
        CARDS_KEYS
            .firstNotNullOfOrNull { key -> root.get(key)?.takeIf { it.isJsonArray } }
            ?.asJsonArray
            ?.forEach { element -> if (element.isJsonObject) cardFrom(element.asJsonObject)?.let { cards += it } }

        if (cards.isEmpty() && letter.isEmpty()) return null
        return SummaryDeck(
            keyword = keyword.ifBlank { "这段时间" },
            cards = cards,
            letter = letter,
            isStructured = true,
        )
    }

    private fun cardFrom(obj: JsonObject): SummaryCard? {
        val text = str(obj, TEXT_KEYS) ?: return null
        val evidence = str(obj, EVIDENCE_KEYS)
        val label = str(obj, LABEL_KEYS)
        val time = str(obj, TIME_KEYS)
        val declared = str(obj, listOf("type"))?.lowercase()

        val type = when {
            declared?.contains("quote") == true -> SummaryCardType.QUOTE
            declared?.contains("insight") == true -> SummaryCardType.INSIGHT
            declared?.contains("fact") == true -> SummaryCardType.FACT
            evidence != null -> SummaryCardType.INSIGHT
            label?.contains("一句话") == true -> SummaryCardType.QUOTE
            label?.contains("原话") == true -> SummaryCardType.QUOTE
            time != null && text.length <= 80 -> SummaryCardType.QUOTE
            else -> SummaryCardType.FACT
        }
        return SummaryCard(
            type = type,
            label = label ?: DEFAULT_LABELS.getValue(type),
            title = str(obj, TITLE_KEYS),
            text = text,
            time = time,
            evidence = evidence,
        )
    }

    /**
     * 把一段内容变成可读文本：是 JSON 就把里面所有字符串抽出来，否则原样返回。
     * 这样即便模型没按契约返回，也不会把 `{"keyword":...` 这种噪声摆到用户面前。
     */
    internal fun salvageText(raw: String): String {
        val json = extractJsonObject(raw) ?: return raw
        val root = runCatching { JsonParser().parse(json) }.getOrNull() ?: return raw
        val out = mutableListOf<String>()
        fun walk(element: JsonElement, key: String?) {
            when {
                element.isJsonPrimitive && element.asJsonPrimitive.isString -> {
                    val value = element.asString.trim()
                    if (value.isNotEmpty() && key !in NOISE_KEYS) out += value
                }
                element.isJsonArray -> element.asJsonArray.forEach { walk(it, key) }
                element.isJsonObject -> element.asJsonObject.entrySet()
                    .forEach { (k, v) -> walk(v, k) }
            }
        }
        walk(root, null)
        return if (out.isEmpty()) raw else out.joinToString("\n\n")
    }

    /**
     * 旧版纯文本总结 / 模型没有按格式返回 → 退化牌组：按段落切卡，
     * 顺手清掉 markdown 的粗体星号（线上旧数据里有）。
     */
    fun asPlainDeck(content: String): SummaryDeck {
        val cleaned = salvageText(content)
            .replace(Regex("\\*\\*"), "")
            .replace(Regex("(?m)^#{1,6}\\s*"), "")
        val paragraphs = cleaned.split("\n\n", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(MAX_PLAIN_CARDS)
        if (paragraphs.isEmpty()) {
            return SummaryDeck(keyword = "", cards = emptyList(), letter = "", isStructured = false)
        }
        val cards = paragraphs.mapIndexed { index, p ->
            SummaryCard(
                type = SummaryCardType.PLAIN,
                label = if (index == 0) "回顾" else "其一",
                title = null,
                text = p,
            )
        }
        return SummaryDeck(keyword = paragraphs.first().take(24), cards = cards, letter = "", isStructured = false)
    }

    /** 优先结构化解析，失败则退化为纯文本牌组——**永远不会返回空**（只要内容非空）。 */
    fun parseOrPlain(content: String): SummaryDeck = parse(content) ?: asPlainDeck(content)

    /**
     * 列表/回收站里显示的一行摘要。
     *
     * 卡片总结存的是 JSON，直接 take(40) 会露出 `{"keyword":"...` 这种噪声，
     * 所以统一从这里取：有 keyword 就显示关键词，否则退回首段文字。
     */
    fun displayTitle(content: String, maxChars: Int = 40): String {
        val deck = parse(content)
        val text = if (deck != null) {
            listOf(deck.keyword, deck.letter.replace(Regex("\\s+"), " ").trim())
                .filter { it.isNotBlank() }
                .joinToString(" · ")
        } else {
            salvageText(content).replace(Regex("\\*\\*"), "").replace(Regex("\\s+"), " ").trim()
        }
        return text.take(maxChars) + if (text.length > maxChars) "..." else ""
    }
}
