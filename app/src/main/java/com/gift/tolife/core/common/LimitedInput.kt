package com.gift.tolife.core.common

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** 有上限地读入内存，避免超大文件（manifest / 配置 JSON）直接 OOM。 */
object LimitedInput {

    fun readBytes(stream: InputStream, limit: Long, what: String): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) {
                throw IOException("$what 过大（上限 ${limit / (1024 * 1024)} MiB）")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
