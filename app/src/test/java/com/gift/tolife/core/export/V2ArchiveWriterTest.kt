package com.gift.tolife.core.export

import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.EntryType
import com.google.gson.Gson
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class V2ArchiveWriterTest {

    @get:Rule
    val temp = TemporaryFolder()

    // ---- 导出格式 ----

    @Test
    fun `writeAndVerify round trip keeps fields images and deleted flag`() {
        val imageA = temp.newFile("a.webp").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val imageB = temp.newFile("b.webp").apply { writeBytes(ByteArray(64) { it.toByte() }) }
        val backup = temp.newFile("backup.gtlbackup")
        val missing = File(temp.root, "missing.webp")

        val entries = listOf(
            Entry(id = 1, content = "第一条", imagePath = imageA.absolutePath, createdAt = 111, updatedAt = 222),
            Entry(id = 2, content = "回收站里的", imagePath = imageB.absolutePath, createdAt = 333, isDeleted = true),
            Entry(id = 3, content = "无图", createdAt = 444, summaryStart = 1, summaryEnd = 2, summaryModel = "m"),
            Entry(id = 4, content = "缺图", imagePath = missing.absolutePath, createdAt = 555)
        )

        val result = backup.outputStream().use { out ->
            V2ArchiveWriter(out).use { writer ->
                writer.addEntry(entries[0], listOf("闪念", "文摘"), imageA)
                writer.addEntry(entries[1], listOf("事记"), imageB)
                writer.addEntry(entries[2], emptyList(), null)
                writer.addEntry(entries[3], emptyList(), missing)
                writer.finish()
            }
        }

        assertEquals(4, result.totalCount)
        assertEquals(1, result.deletedCount)
        assertEquals(2, result.imageCount)
        assertEquals(1, result.missingImageCount)

        val report = BackupArchiveVerifier.verify(backup)
        assertEquals(4, report.entryCount)
        assertEquals(2, report.imageCount)

        val manifest = readManifest(backup)
        assertEquals(2, manifest.formatVersion)
        assertEquals(4, manifest.entryCount)
        assertEquals(listOf("闪念", "文摘"), manifest.entries[0].tags)
        assertFalse(manifest.entries[0].isDeleted)
        assertTrue(manifest.entries[1].isDeleted)
        assertEquals(EntryType.NORMAL.name, manifest.entries[0].type)
        assertEquals(222L, manifest.entries[0].updatedAt)
        assertEquals("m", manifest.entries[2].summaryModel)
        assertNull(manifest.entries[3].imageEntry)
        assertNull(manifest.entries[3].imageSha256)

        ZipFile(backup).use { zip ->
            val first = manifest.entries[0]
            val firstBytes = zip.getInputStream(zip.getEntry(first.imageEntry!!)!!).readBytes()
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), firstBytes)
            assertEquals(sha256(firstBytes), first.imageSha256)

            val second = manifest.entries[1]
            val secondBytes = zip.getInputStream(zip.getEntry(second.imageEntry!!)!!).readBytes()
            assertEquals(sha256(secondBytes), second.imageSha256)
        }
    }

    @Test
    fun `shared image file gets distinct zip entries instead of crashing`() {
        val shared = temp.newFile("shared.webp").apply { writeBytes(byteArrayOf(9, 9, 9)) }
        val backup = temp.newFile("shared.gtlbackup")

        val result = backup.outputStream().use { out ->
            V2ArchiveWriter(out).use { writer ->
                writer.addEntry(Entry(id = 1, content = "A", imagePath = shared.absolutePath), emptyList(), shared)
                writer.addEntry(Entry(id = 2, content = "B", imagePath = shared.absolutePath), emptyList(), shared)
                writer.finish()
            }
        }

        assertEquals(2, result.imageCount)
        assertEquals(0, result.missingImageCount)
        val names = readManifest(backup).entries.mapNotNull { it.imageEntry }
        assertEquals(2, names.size)
        assertEquals(2, names.toSet().size)
        assertEquals(BackupArchiveVerifier.Report(entryCount = 2, imageCount = 2), BackupArchiveVerifier.verify(backup))
    }

    // ---- 校验器 ----

    @Test
    fun `verifier rejects archive without manifest`() {
        val file = temp.newFile("no-manifest.gtlbackup")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("images/x.webp"))
            zip.write(byteArrayOf(1))
            zip.closeEntry()
        }
        assertThrows(IOException::class.java) { BackupArchiveVerifier.verify(file) }
    }

    @Test
    fun `verifier rejects manifest referencing a missing image`() {
        val file = temp.newFile("missing-image.gtlbackup")
        val manifest = BackupV2Manifest(
            exportedAt = 1,
            entryCount = 1,
            entries = listOf(
                BackupV2Entry(
                    id = 1, content = "x", type = "NORMAL", createdAt = 1,
                    imageEntry = "images/nope.webp", imageSha256 = "00"
                )
            )
        )
        writeManifestOnly(file, Gson().toJson(manifest))
        val error = assertThrows(IOException::class.java) { BackupArchiveVerifier.verify(file) }
        assertTrue(error.message!!.contains("不存在"))
    }

    @Test
    fun `verifier rejects entry count mismatch`() {
        val file = temp.newFile("count-mismatch.gtlbackup")
        val manifest = BackupV2Manifest(
            exportedAt = 1,
            entryCount = 2,
            entries = listOf(BackupV2Entry(id = 1, content = "x", type = "NORMAL", createdAt = 1))
        )
        writeManifestOnly(file, Gson().toJson(manifest))
        val error = assertThrows(IOException::class.java) { BackupArchiveVerifier.verify(file) }
        assertTrue(error.message!!.contains("条目数不匹配"))
    }

    @Test
    fun `verifier rejects unsupported format version`() {
        val file = temp.newFile("v3.gtlbackup")
        val manifest = BackupV2Manifest(formatVersion = 3, exportedAt = 1, entryCount = 0, entries = emptyList())
        writeManifestOnly(file, Gson().toJson(manifest))
        val error = assertThrows(IOException::class.java) { BackupArchiveVerifier.verify(file) }
        assertTrue(error.message!!.contains("版本"))
    }

    @Test
    fun `verifier rejects manifest without entries field`() {
        val file = temp.newFile("no-entries.gtlbackup")
        writeManifestOnly(file, "{\"exportedAt\":1,\"entryCount\":0}")
        assertThrows(IOException::class.java) { BackupArchiveVerifier.verify(file) }
    }

    @Test
    fun `verifier rejects truncated archive`() {
        val valid = temp.newFile("valid.gtlbackup")
        valid.outputStream().use { out ->
            V2ArchiveWriter(out).use { writer ->
                writer.addEntry(Entry(id = 1, content = "x"), emptyList(), null)
                writer.finish()
            }
        }
        val bytes = valid.readBytes()
        val truncated = temp.newFile("truncated.gtlbackup")
        truncated.writeBytes(bytes.copyOf(bytes.size / 2))
        assertThrows(IOException::class.java) { BackupArchiveVerifier.verify(truncated) }
    }

    @Test
    fun `readManifest returns manifest without modifying data`() {
        val backup = temp.newFile("read-manifest.gtlbackup")
        backup.outputStream().use { out ->
            V2ArchiveWriter(out, appVersion = "4.2.2").use { writer ->
                writer.addEntry(Entry(id = 1, content = "x", isDeleted = true), listOf("文摘"), null)
                writer.finish()
            }
        }
        val manifest = BackupArchiveVerifier.readManifest(backup)
        assertEquals("4.2.2", manifest.appVersion)
        assertEquals(1, manifest.entryCount)
        assertTrue(manifest.entries[0].isDeleted)
        assertEquals(listOf("文摘"), manifest.entries[0].tags)
    }

    @Test
    fun `readManifest fails when manifest is missing`() {
        val file = temp.newFile("no-manifest-2.gtlbackup")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("images/x.webp"))
            zip.write(byteArrayOf(1))
            zip.closeEntry()
        }
        assertThrows(IOException::class.java) { BackupArchiveVerifier.readManifest(file) }
    }

    // ---- helpers ----

    private fun readManifest(file: File): BackupV2Manifest {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry(BackupV2Config.MANIFEST_ENTRY_NAME)!!
            val json = zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8)
            return Gson().fromJson(json, BackupV2Manifest::class.java)
        }
    }

    private fun writeManifestOnly(file: File, json: String) {
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(BackupV2Config.MANIFEST_ENTRY_NAME))
            zip.write(json.toByteArray())
            zip.closeEntry()
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
