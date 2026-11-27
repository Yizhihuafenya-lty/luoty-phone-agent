package com.luoty.agent.agent

import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Locale

/**
 * 极简本机静态 Web 服务：让 Agent 能把工作区里的网页真的跑在端口上。
 * 只读、限制在工作区内，支持 index.html、常见静态资源与 404。
 */
class LocalWebServer {

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var thread: Thread? = null

    @Volatile
    var port: Int = 0
        private set

    @Volatile
    var rootDir: File? = null
        private set

    val isRunning: Boolean get() = serverSocket?.isClosed == false

    fun start(port: Int, root: File): Result<String> {
        if (!root.isDirectory) return Result.failure(IllegalStateException("目录不存在：${root.path}"))
        stop()
        return runCatching {
            val socket = ServerSocket(port)
            serverSocket = socket
            this.port = socket.localPort
            rootDir = root
            val worker = Thread({ acceptLoop(socket, root) }, "luoty-web-$port")
            worker.isDaemon = true
            thread = worker
            worker.start()
            "http://${lanAddress() ?: "127.0.0.1"}:${socket.localPort}/"
        }
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        thread = null
        port = 0
        rootDir = null
    }

    private fun acceptLoop(socket: ServerSocket, root: File) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (_: SocketException) {
                break
            } catch (_: Exception) {
                continue
            }
            Thread({ handleClient(client, root) }, "luoty-web-client").apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun handleClient(client: Socket, root: File) {
        client.use { socket ->
            socket.soTimeout = 5_000
            val input = socket.getInputStream().bufferedReader()
            val requestLine = input.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2 || !parts[0].startsWith("GET")) {
                respond(socket.getOutputStream(), 405, "text/plain; charset=utf-8", "Method Not Allowed".toByteArray())
                return
            }
            // 读掉请求头
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
            }
            val rawPath = parts[1].substringBefore('?').substringBefore('#')
            val decoded = runCatching { java.net.URLDecoder.decode(rawPath, "UTF-8") }.getOrDefault("/")
            val relative = decoded.trimStart('/').ifBlank { "index.html" }
            val target = File(root, relative).canonicalFile
            val rootCanonical = root.canonicalFile
            if (target != rootCanonical && !target.path.startsWith(rootCanonical.path + File.separator)) {
                respond(socket.getOutputStream(), 403, "text/plain; charset=utf-8", "Forbidden".toByteArray())
                return
            }
            val file = if (target.isDirectory) File(target, "index.html") else target
            if (!file.isFile) {
                respond(
                    socket.getOutputStream(),
                    404,
                    "text/html; charset=utf-8",
                    "<h1>404</h1><p>${file.name} not found</p>".toByteArray(),
                )
                return
            }
            respond(
                socket.getOutputStream(),
                200,
                contentType(file.name.lowercase(Locale.US)),
                file.readBytes(),
            )
        }
    }

    private fun respond(out: OutputStream, code: Int, type: String, body: ByteArray) {
        val buffered = BufferedOutputStream(out)
        val header = buildString {
            append("HTTP/1.0 ").append(code).append(' ').append(reason(code)).append("\r\n")
            append("Content-Type: ").append(type).append("\r\n")
            append("Content-Length: ").append(body.size).append("\r\n")
            append("Cache-Control: no-cache\r\n")
            append("Connection: close\r\n\r\n")
        }
        buffered.write(header.toByteArray())
        buffered.write(body)
        buffered.flush()
    }

    private fun reason(code: Int): String = when (code) {
        200 -> "OK"
        403 -> "Forbidden"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        else -> "OK"
    }

    private fun contentType(name: String): String = when {
        name.endsWith(".html") || name.endsWith(".htm") -> "text/html; charset=utf-8"
        name.endsWith(".css") -> "text/css; charset=utf-8"
        name.endsWith(".js") -> "application/javascript; charset=utf-8"
        name.endsWith(".json") -> "application/json; charset=utf-8"
        name.endsWith(".svg") -> "image/svg+xml"
        name.endsWith(".png") -> "image/png"
        name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
        name.endsWith(".gif") -> "image/gif"
        name.endsWith(".webp") -> "image/webp"
        name.endsWith(".ico") -> "image/x-icon"
        name.endsWith(".txt") || name.endsWith(".md") -> "text/plain; charset=utf-8"
        name.endsWith(".pdf") -> "application/pdf"
        else -> "application/octet-stream"
    }

    private fun lanAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()
}
