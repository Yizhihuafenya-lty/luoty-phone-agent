package com.luoty.agent.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * 附件仓库：把用户选的图片 / PDF 复制到应用私有目录。
 * 图片按多模态 data URL 发送；PDF 在导入时抽文本，随消息作为文本发送。
 */
class AttachmentStore(private val context: Context) {

    private val dir = File(context.filesDir, "attachments").also { it.mkdirs() }

    init {
        runCatching { PDFBoxResourceLoader.init(context.applicationContext) }
    }

    suspend fun import(uri: Uri, kind: String): Attachment = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = queryName(uri) ?: if (kind == "image") "image.jpg" else "document.pdf"
        val mime = resolver.getType(uri).orEmpty().ifBlank {
            if (kind == "image") "image/jpeg" else "application/pdf"
        }
        val extension = name.substringAfterLast('.', "").lowercase()
        val ext = when {
            kind == "image" && mime.contains("png") -> "png"
            kind == "image" && mime.contains("webp") -> "webp"
            kind == "image" -> "jpg"
            kind == "file" -> extension.take(8).ifBlank { "bin" }
            else -> "pdf"
        }
        val target = File(dir, "${UUID.randomUUID()}.$ext")
        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("无法读取这个文件")
        val text = when (kind) {
            "pdf" -> extractPdfText(target)
            "file" -> extractFileText(target, extension)
            else -> null
        }
        Attachment(
            id = UUID.randomUUID().toString(),
            kind = kind,
            path = target.absolutePath,
            name = name,
            mime = mime,
            extractedText = text,
            size = target.length(),
        )
    }

    fun delete(attachment: Attachment) {
        runCatching { File(attachment.path).delete() }
    }

    private fun extractPdfText(file: File): String? = runCatching {
        PDDocument.load(file).use { document ->
            PDFTextStripper().apply { sortByPosition = true }.getText(document)
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }?.take(200_000)

    private fun extractFileText(file: File, extension: String): String? = when {
        extension in TEXT_EXTENSIONS -> runCatching { file.readText().take(200_000) }.getOrNull()
        extension == "docx" -> runCatching {
            java.util.zip.ZipFile(file).use { zip ->
                val entry = zip.getEntry("word/document.xml") ?: return@use null
                zip.getInputStream(entry).bufferedReader().use { it.readText() }
            }
        }.getOrNull()?.let { xml ->
            xml.replace(Regex("</w:p>"), "\n")
                .replace(Regex("<[^>]+>"), "")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .trim()
                .take(200_000)
        }.takeIf { !it.isNullOrBlank() }

        extension == "zip" -> runCatching {
            java.util.zip.ZipFile(file).use { zip ->
                zip.entries().asSequence().take(200).joinToString("\n") { entry ->
                    "${entry.name}  ${entry.size} B"
                }
            }
        }.getOrNull()?.let { "压缩包内容：\n$it" }

        else -> null
    }

    /** 图片压缩并编码成 OpenAI 兼容的 data URL */
    fun encodeImageDataUrl(path: String): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
        val bitmap = BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        "data:image/jpeg;base64,$base64"
    }.getOrNull()

    private fun queryName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()

    private companion object {
        val TEXT_EXTENSIONS = setOf(
            "txt", "md", "markdown", "json", "csv", "xml", "html", "htm", "css",
            "js", "ts", "jsx", "tsx", "kt", "java", "py", "go", "rs", "c", "cpp",
            "h", "sh", "yml", "yaml", "toml", "ini", "log", "sql", "gradle", "properties",
        )
    }
}
