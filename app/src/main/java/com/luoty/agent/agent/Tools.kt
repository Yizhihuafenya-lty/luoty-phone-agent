package com.luoty.agent.agent

import android.content.Context
import com.luoty.agent.net.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class ToolException(message: String) : Exception(message)

data class ToolOutcome(val output: String, val isError: Boolean)

private const val MAX_TOOL_OUTPUT = 24_000
private const val MAX_READ_LINES = 800

/**
 * Agent 可用的本地工具：全部限制在应用私有工作区里，路径越界会被拒绝。
 * 这就是"手机上的 DSH"能真正改代码的部分。
 */
class ToolRegistry(context: Context) {

    val workspace: File = File(context.filesDir, "workspace").also { it.mkdirs() }
    private var activeRoot: File = workspace
    private val ioLock = Mutex()
    private val webServer = LocalWebServer()
    private val serverStore = com.luoty.agent.data.ServerStore(context)
    private val ssh = SshManager(serverStore, File(context.filesDir, "ssh_audit.log"))

    val specs: List<ToolSpec> = listOf(
        ToolSpec(
            name = "ssh_list_servers",
            description = "列出用户在设置里添加的远程服务器。执行远程命令前先用它确认有哪些机器。",
            parameters = schema(),
        ),
        ToolSpec(
            name = "ssh_exec",
            description = "在远程 Linux 服务器上执行一条命令并返回输出。默认使用当前会话选中的服务器。" +
                "命令会被安全策略审核：危险操作需要用户确认，破坏性命令直接拒绝。",
            parameters = schema(
                "server" to prop("string", "服务器名称或 ID，省略则用当前会话选中的服务器"),
                "command" to prop("string", "要执行的 shell 命令，尽量非交互"),
                "timeout_seconds" to prop("integer", "超时秒数，默认 30，上限 120"),
            ),
            required = listOf("command"),
        ),
        ToolSpec(
            name = "start_web_server",
            description = "把工作区里的静态网页跑在本机端口上（手机同 Wi-Fi 可直接访问），返回访问地址。" +
                "适合做完网页后让用户立刻看到效果。",
            parameters = schema(
                "port" to prop("integer", "端口，默认 8000"),
                "directory" to prop("string", "要托管的目录，默认工作区根目录，例如 web"),
            ),
        ),
        ToolSpec(
            name = "stop_web_server",
            description = "停止由 start_web_server 启动的静态服务。",
            parameters = schema(),
        ),
        ToolSpec(
            name = "list_files",
            description = "列出工作区某个目录下的文件与子目录。path 省略时为工作区根目录。",
            parameters = schema(
                "path" to prop("string", "相对工作区的目录路径，默认 \".\""),
                "depth" to prop("integer", "递归深度，1-3，默认 1"),
            ),
        ),
        ToolSpec(
            name = "read_file",
            description = "读取工作区内一个文本文件，返回带行号的内容。大文件请用 start_line/end_line 分段读。",
            parameters = schema(
                "path" to prop("string", "相对工作区的文件路径"),
                "start_line" to prop("integer", "起始行号，从 1 开始"),
                "end_line" to prop("integer", "结束行号，含"),
            ),
            required = listOf("path"),
        ),
        ToolSpec(
            name = "write_file",
            description = "写入（或整体覆盖）一个文件。父目录会自动创建。适合新建文件，大改前请先 read_file。",
            parameters = schema(
                "path" to prop("string", "相对工作区的文件路径"),
                "content" to prop("string", "完整文件内容（UTF-8）"),
            ),
            required = listOf("path", "content"),
        ),
        ToolSpec(
            name = "edit_file",
            description = "在文件里做精确字符串替换。old_string 必须与文件内容完全一致（含缩进），且默认要求唯一匹配。",
            parameters = schema(
                "path" to prop("string", "相对工作区的文件路径"),
                "old_string" to prop("string", "要被替换的原文片段"),
                "new_string" to prop("string", "替换成的新内容，可为空字符串"),
                "replace_all" to prop("boolean", "为 true 时替换所有匹配，默认 false"),
            ),
            required = listOf("path", "old_string", "new_string"),
        ),
        ToolSpec(
            name = "search_files",
            description = "在工作区里按关键字（不区分大小写）搜索文本内容，返回 文件:行号: 内容。",
            parameters = schema(
                "query" to prop("string", "要搜索的关键字"),
                "path" to prop("string", "限定搜索的子目录，默认工作区根目录"),
                "max_results" to prop("integer", "最多返回多少条，默认 40，上限 200"),
            ),
            required = listOf("query"),
        ),
        ToolSpec(
            name = "delete_path",
            description = "删除工作区里的文件或目录（目录会递归删除）。",
            parameters = schema(
                "path" to prop("string", "相对工作区的路径"),
            ),
            required = listOf("path"),
        ),
        ToolSpec(
            name = "update_tasks",
            description = "维护任务面板（显示在输入框上方）。多步骤工作先拆成任务清单，执行中更新状态，" +
                "完成后标 DONE。每次调用传完整列表，不要只传增量。",
            parameters = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject().put(
                        "tasks",
                        JSONObject()
                            .put("type", "array")
                            .put("description", "完整任务列表")
                            .put(
                                "items",
                                JSONObject()
                                    .put("type", "object")
                                    .put(
                                        "properties",
                                        JSONObject()
                                            .put("text", prop("string", "任务描述"))
                                            .put(
                                                "status",
                                                prop(
                                                    "string",
                                                    "状态：PENDING / IN_PROGRESS / DONE",
                                                    listOf("PENDING", "IN_PROGRESS", "DONE"),
                                                ),
                                            ),
                                    )
                                    .put("required", JSONArray(listOf("text"))),
                            ),
                    ),
                ),
            required = listOf("tasks"),
        ),
        ToolSpec(
            name = "ask_user",
            description = "当你不确定用户想要什么，或需要用户在几个方案里选一个时，用这个工具提问。" +
                "问题会显示在输入框上方，用户点选后把结果返回给你。能用工具查证的事情不要问。",
            parameters = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject()
                        .put("question", prop("string", "要问用户的问题，一句话说清"))
                        .put(
                            "options",
                            JSONObject()
                                .put("type", "array")
                                .put("description", "2-4 个可选项，用户可以点选")
                                .put("items", JSONObject().put("type", "string")),
                        ),
                ),
            required = listOf("question"),
        ),
        ToolSpec(
            name = "run_command",
            description = "在工作区目录执行一条 shell 命令（Android 内置 sh/toybox，能力有限），返回退出码与输出。",
            parameters = schema(
                "command" to prop("string", "要执行的命令，例如 ls -la 或 cat app.txt"),
                "timeout_seconds" to prop("integer", "超时秒数，1-120，默认 30"),
            ),
            required = listOf("command"),
        ),
    )

    suspend fun testSsh(server: com.luoty.agent.data.SshServer): String = ssh.testConnection(server)

    fun sshServers(): List<com.luoty.agent.data.SshServer> = serverStore.load()

    suspend fun execute(
        name: String,
        argumentsJson: String,
        workspaceRoot: File? = null,
    ): ToolOutcome = ioLock.withLock {
        activeRoot = (workspaceRoot ?: workspace).also { it.mkdirs() }.canonicalFile
        withContext(Dispatchers.IO) {
            val args = runCatching { JSONObject(argumentsJson.ifBlank { "{}" }) }.getOrElse {
                return@withContext ToolOutcome("参数不是合法 JSON：${argumentsJson.take(300)}", true)
            }
            try {
                val output = when (name) {
                    "list_files" -> listFiles(args)
                    "read_file" -> readFile(args)
                    "write_file" -> writeFile(args)
                    "edit_file" -> editFile(args)
                    "search_files" -> searchFiles(args)
                    "delete_path" -> deletePath(args)
                    "run_command" -> runCommand(args)
                    "start_web_server" -> startWebServer(args)
                    "stop_web_server" -> stopWebServer()
                    "ssh_list_servers" -> sshListServers()
                    "ssh_exec" -> sshExec(args)
                    else -> throw ToolException("未知工具：$name")
                }
                ToolOutcome(output.limit(MAX_TOOL_OUTPUT), false)
            } catch (e: ToolException) {
                ToolOutcome(e.message ?: "工具执行失败", true)
            } catch (e: Exception) {
                ToolOutcome("工具执行异常：${e.message ?: e.javaClass.simpleName}", true)
            }
        }
    }

    // ------------------------------------------------------------ 各工具实现

    private fun listFiles(args: JSONObject): String {
        val dir = resolve(args.optString("path", "."))
        if (!dir.exists()) throw ToolException("路径不存在：${rel(dir)}")
        if (dir.isFile) return "${rel(dir)}  ${humanSize(dir.length())}"
        val depth = args.optInt("depth", 1).coerceIn(1, 3)
        val sb = StringBuilder()
        sb.append(if (rel(dir).isEmpty()) "/" else rel(dir)).append("/\n")
        var visited = 0
        var truncated = false

        fun walk(current: File, level: Int) {
            if (level > depth) return
            val children = current.listFiles()
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?: return
            for (child in children) {
                if (visited >= 300) {
                    truncated = true
                    return
                }
                visited++
                val indent = "  ".repeat(level)
                if (child.isDirectory) {
                    sb.append(indent).append(child.name).append("/\n")
                    if (child.name == ".git" || child.name == "node_modules" || child.name == "build") continue
                    walk(child, level + 1)
                } else {
                    sb.append(indent).append(child.name)
                        .append("  (").append(humanSize(child.length())).append(")\n")
                }
            }
        }
        walk(dir, 1)
        if (truncated) sb.append("…（条目过多，已截断）\n")
        return sb.toString().trimEnd()
    }

    private fun readFile(args: JSONObject): String {
        val file = resolve(args.optString("path"))
        if (!file.isFile) throw ToolException("文件不存在：${rel(file)}")
        if (file.length() > 1_000_000L) throw ToolException("文件超过 1MB，请改用 search_files 或 run_command 处理")
        val bytes = file.readBytes()
        if (bytes.any { it == 0.toByte() }) throw ToolException("这是二进制文件，无法按文本读取")
        val text = String(bytes, Charsets.UTF_8)
        val lines = text.lines()
        val start = args.optInt("start_line", 1).coerceIn(1, maxOf(1, lines.size))
        if (start > lines.size) throw ToolException("起始行超出范围（文件共 ${lines.size} 行）")
        val requestedEnd = args.optInt("end_line", 0)
        val end = if (requestedEnd in 1..lines.size) requestedEnd else minOf(lines.size, start + MAX_READ_LINES - 1)
        if (end < start) throw ToolException("end_line 小于 start_line")
        val sb = StringBuilder()
        for (i in start..end) {
            sb.append(i.toString().padStart(4)).append("| ").append(lines[i - 1]).append('\n')
        }
        if (end < lines.size) {
            sb.append("…（共 ").append(lines.size).append(" 行；用 start_line=").append(end + 1)
                .append(" 继续读）")
        }
        return sb.toString().trimEnd()
    }

    private fun writeFile(args: JSONObject): String {
        val path = args.optString("path").trim()
        if (path.isEmpty()) throw ToolException("path 不能为空")
        val content = args.optString("content", "")
        val file = resolve(path)
        if (file.isDirectory) throw ToolException("目标是一个目录：${rel(file)}")
        val existed = file.exists()
        val oldSize = if (existed) file.length() else 0L
        file.parentFile?.mkdirs()
        file.writeText(content, Charsets.UTF_8)
        val lineCount = if (content.isEmpty()) 0 else content.count { it == '\n' } + 1
        return buildString {
            append(if (existed) "已覆盖 " else "已创建 ").append(rel(file))
            append("（").append(lineCount).append(" 行，").append(humanSize(file.length())).append("）")
            if (existed) append("\n原大小 ").append(humanSize(oldSize))
        }
    }

    private fun editFile(args: JSONObject): String {
        val file = resolve(args.optString("path"))
        if (!file.isFile) throw ToolException("文件不存在：${rel(file)}")
        if (file.length() > 1_000_000L) throw ToolException("文件超过 1MB，请先用 read_file 确认范围")
        val oldString = args.optString("old_string")
        if (oldString.isEmpty()) throw ToolException("old_string 不能为空")
        val newString = args.optString("new_string", "")
        val replaceAll = args.optBoolean("replace_all", false)
        val text = file.readText()
        val first = text.indexOf(oldString)
        if (first < 0) {
            throw ToolException(
                "old_string 在文件中找不到。请先用 read_file 查看原文（注意空格、缩进、换行要完全一致）。",
            )
        }
        val occurrences = countOccurrences(text, oldString)
        if (occurrences > 1 && !replaceAll) {
            throw ToolException(
                "old_string 匹配到 $occurrences 处。请补充上下文使其唯一，或设置 replace_all=true。",
            )
        }
        val updated = if (replaceAll) text.replace(oldString, newString) else text.replaceFirst(oldString, newString)
        val lineNo = text.substring(0, first).count { it == '\n' } + 1
        file.writeText(updated, Charsets.UTF_8)
        return "已修改 ${rel(file)}（第 $lineNo 行，共替换 ${if (replaceAll) occurrences else 1} 处，" +
            "现在 ${humanSize(file.length())}）"
    }

    private fun searchFiles(args: JSONObject): String {
        val query = args.optString("query")
        if (query.isEmpty()) throw ToolException("query 不能为空")
        val root = resolve(args.optString("path", "."))
        if (!root.exists()) throw ToolException("路径不存在：${rel(root)}")
        val limit = args.optInt("max_results", 40).coerceIn(1, 200)
        val lower = query.lowercase()
        val skipDirs = setOf(".git", "node_modules", "build", ".gradle", ".idea", ".kotlin", "dist", "target", "__pycache__")
        val hits = mutableListOf<String>()
        var scanned = 0
        var truncated = false

        fun walk(file: File) {
            if (hits.size >= limit || truncated) return
            if (file.isDirectory) {
                if (file.name in skipDirs) return
                file.listFiles()?.forEach { walk(it) }
                return
            }
            if (!file.isFile || file.length() > 1_000_000L) return
            scanned++
            val bytes = runCatching { file.readBytes() }.getOrNull() ?: return
            if (bytes.any { it == 0.toByte() }) return
            val lines = String(bytes, Charsets.UTF_8).lines()
            for ((idx, line) in lines.withIndex()) {
                if (line.lowercase().contains(lower)) {
                    hits += "${rel(file)}:${idx + 1}: ${line.trim().take(240)}"
                    if (hits.size >= limit) {
                        truncated = true
                        return
                    }
                }
            }
        }
        walk(root)
        if (hits.isEmpty()) return "没有找到「$query」。已扫描 $scanned 个文件。"
        return buildString {
            append("找到 ").append(hits.size).append(" 处匹配（扫描 ").append(scanned).append(" 个文件）")
            append(if (truncated) "，仅显示前 $limit 条" else "")
            append("：\n").append(hits.joinToString("\n"))
        }
    }

    private fun deletePath(args: JSONObject): String {
        val file = resolve(args.optString("path"))
        if (file == activeRoot) throw ToolException("不能删除工作区根目录")
        if (!file.exists()) throw ToolException("路径不存在：${rel(file)}")
        val count = countFiles(file)
        val ok = file.deleteRecursively()
        if (!ok && file.exists()) throw ToolException("删除失败：${rel(file)}")
        return "已删除 ${rel(file)}（$count 个文件）"
    }

    private fun startWebServer(args: JSONObject): String {
        val port = args.optInt("port", 8000).coerceIn(1024, 65535)
        val dir = resolve(args.optString("directory", "."))
        if (!dir.isDirectory) throw ToolException("目录不存在：${rel(dir)}")
        val url = webServer.start(port, dir).getOrElse {
            throw ToolException("启动失败：${it.message ?: "端口可能被占用"}")
        }
        return "已启动静态服务：$url\n目录：${rel(dir).ifEmpty { "/" }}\n（手机与电脑同一局域网即可打开；改文件后刷新页面就能看到）"
    }

    private fun stopWebServer(): String {
        if (!webServer.isRunning) return "当前没有在运行的静态服务"
        webServer.stop()
        return "已停止静态服务"
    }

    private fun sshListServers(): String {
        val servers = serverStore.load()
        if (servers.isEmpty()) return "还没有添加服务器，请让用户到「设置 → 服务器」里添加。"
        return servers.joinToString("\n") { "· ${it.label}  ${it.target}" }
    }

    private suspend fun sshExec(args: JSONObject): String {
        val name = args.optString("server").trim()
        if (name.isEmpty()) throw ToolException("没有指定服务器，请先让用户在加号菜单里选一台。")
        val server = serverStore.findByName(name)
            ?: throw ToolException("找不到服务器「$name」。可先用 ssh_list_servers 查看已添加的机器。")
        val command = args.optString("command").trim()
        if (command.isEmpty()) throw ToolException("command 不能为空")
        val timeout = args.optInt("timeout_seconds", 30).coerceIn(5, 120)
        val result = try {
            ssh.exec(server, command, timeout)
        } catch (e: SshException) {
            throw ToolException(e.message ?: "SSH 执行失败")
        }
        return buildString {
            append(server.label).append("  exit=").append(result.exitCode).append('\n')
            append(if (result.output.isBlank()) "（无输出）" else result.output)
        }
    }

    private fun runCommand(args: JSONObject): String {
        val command = args.optString("command").trim()
        if (command.isEmpty()) throw ToolException("command 不能为空")
        val timeout = args.optInt("timeout_seconds", 30).coerceIn(1, 120)
        val process = try {
            ProcessBuilder("/system/bin/sh", "-c", command)
                .directory(activeRoot)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            throw ToolException("无法启动 shell：${e.message}")
        }
        val buffer = StringBuilder()
        val reader = Thread {
            runCatching {
                process.inputStream.bufferedReader().use { reader ->
                    val chars = CharArray(4096)
                    while (true) {
                        val n = reader.read(chars)
                        if (n < 0) break
                        synchronized(buffer) { buffer.append(chars, 0, n) }
                        if (buffer.length > 40_000) {
                            process.destroy()
                            break
                        }
                    }
                }
            }
        }
        reader.isDaemon = true
        reader.start()
        val finished = process.waitFor(timeout.toLong(), TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            reader.join(500)
            val partial = synchronized(buffer) { buffer.toString() }
            return "命令超时（${timeout}s），已强制结束。\n" + partial.take(4000)
        }
        reader.join(1000)
        val output = synchronized(buffer) { buffer.toString() }.trimEnd()
        val exit = process.exitValue()
        return buildString {
            append("exit=").append(exit).append('\n')
            append(if (output.isBlank()) "（无输出）" else output.take(20_000))
        }
    }

    // ------------------------------------------------------------ 路径与格式化

    fun resolve(path: String): File {
        val raw = path.trim().ifBlank { "." }
        val root = activeRoot
        val file = File(root, raw).canonicalFile
        if (file != root && !file.path.startsWith(root.path + File.separator)) {
            throw ToolException("路径越界，只能访问工作区内的文件：$path")
        }
        return file
    }

    fun rel(file: File): String {
        val root = activeRoot
        return file.canonicalFile.toRelativeString(root).let { if (it == ".") "" else it }
            .replace(File.separatorChar, '/')
    }

    private fun countOccurrences(text: String, needle: String): Int {
        var count = 0
        var index = text.indexOf(needle)
        while (index >= 0) {
            count++
            index = text.indexOf(needle, index + needle.length)
        }
        return count
    }

    private fun countFiles(file: File): Int {
        if (file.isFile) return 1
        var count = 0
        file.walkTopDown().forEach { if (it.isFile) count++ }
        return count
    }

    private fun humanSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }

    private fun String.limit(max: Int): String =
        if (length <= max) this else take(max) + "\n…（输出过长，已截断）"

    private fun prop(type: String, desc: String, enum: List<String> = emptyList()): JSONObject =
        JSONObject().put("type", type).put("description", desc).apply {
            if (enum.isNotEmpty()) put("enum", JSONArray(enum))
        }

    private fun schema(
        vararg props: Pair<String, JSONObject>,
        required: List<String> = emptyList(),
    ): JSONObject {
        val properties = JSONObject()
        props.forEach { (key, value) -> properties.put(key, value) }
        return JSONObject()
            .put("type", "object")
            .put("properties", properties)
            .put("required", JSONArray(required))
    }
}
