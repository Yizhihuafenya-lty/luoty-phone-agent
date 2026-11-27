package com.luoty.agent.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files

class LocalWebServerTest {

    private fun tempDir(): File = Files.createTempDirectory("luoty-web").toFile()

    @Test
    fun servesIndexAndAssets() {
        val dir = tempDir()
        File(dir, "index.html").writeText("<h1>hello luoty</h1>")
        File(dir, "app.js").writeText("console.log('ok')")
        val server = LocalWebServer()
        try {
            server.start(0, dir).getOrThrow()
            val base = "http://127.0.0.1:${server.port}/"

            val index = get(base)
            assertEquals(200, index.first)
            assertTrue(index.second.contains("hello luoty"))

            val asset = get(base + "app.js")
            assertEquals(200, asset.first)
            assertTrue(asset.second.contains("console.log"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun rejectsMissingFileAndTraversal() {
        val dir = tempDir()
        File(dir, "index.html").writeText("ok")
        val server = LocalWebServer()
        try {
            server.start(0, dir).getOrThrow()
            assertEquals(404, get("http://127.0.0.1:${server.port}/nope.html").first)
            assertEquals(403, get("http://127.0.0.1:${server.port}/../build.gradle.kts").first)
        } finally {
            server.stop()
        }
    }

    private fun get(url: String): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 3000
        connection.readTimeout = 3000
        connection.requestMethod = "GET"
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        return code to text
    }
}
