package com.luoty.agent.agent

/**
 * 远程命令安全策略：硬禁 → 拒绝；只读模式 → 只放行读操作；危险命令 → 必须人工确认。
 * 这是"安全审核"的核心，不依赖模型自觉。
 */
object RemoteCommandPolicy {

    data class Decision(
        val allowed: Boolean,
        val needsApproval: Boolean,
        val reason: String,
    )

    private val HARD_BLOCK = listOf(
        Regex("""\brm\b[^\n]*\s+/(\s|$)"""),
        Regex("""\brm\b[^\n]*\s+/\*"""),
        Regex("""\brm\b[^\n]*\s+/(etc|usr|var|bin|sbin|boot|lib|lib64|home|root|opt|srv|dev|proc|sys)(\s|/|$)"""),
        Regex("""\bmkfs(\.\w+)?\b"""),
        Regex("""\bdd\b[^\n]*of=/dev/"""),
        Regex(""":\(\)\s*\{.*\};\s*:"""),
        Regex("""\b(shutdown|poweroff|halt|reboot)\b"""),
        Regex("""\b(passwd|userdel|groupdel)\b"""),
        Regex("""\bchmod\s+-R\s+777\s+/(\s|$)"""),
        Regex("""\bcurl\b[^\n|]*\|\s*(sudo\s+)?(ba)?sh\b"""),
        Regex("""\bwget\b[^\n|]*\|\s*(sudo\s+)?(ba)?sh\b"""),
        Regex(""">\s*/etc/(passwd|shadow|sudoers)"""),
    )

    private val DANGEROUS = listOf(
        Regex("""\brm\b"""),
        Regex("""\bmv\b"""),
        Regex("""\bchown\b"""),
        Regex("""\bchmod\b"""),
        Regex("""\btruncate\b"""),
        Regex("""\b(systemctl|service)\s+(stop|restart|disable|mask)\b"""),
        Regex("""\bkill\s+-9\b"""),
        Regex("""\b(apt|apt-get|yum|dnf|apk|pacman)\s+(install|remove|purge|upgrade|update)\b"""),
        Regex("""\bdocker\s+(rm|rmi|prune|stop|kill)\b"""),
        Regex("""\bgit\s+(reset\s+--hard|clean\s+-f|push\b)"""),
        Regex("""\b(sed|perl)\s+-i\b"""),
        Regex("""\b(iptables|ufw|firewall-cmd)\b"""),
        Regex("""\bcrontab\s+-r\b"""),
        Regex("""\b(dropdb|mysql\s+-e\s*["']?drop)\b"""),
        Regex(""">|\btee\b"""),
        Regex("""\bsudo\b"""),
    )

    private val READ_ONLY = setOf(
        "ls", "cat", "head", "tail", "grep", "egrep", "zgrep", "find", "ps", "top", "df", "du",
        "free", "uptime", "uname", "whoami", "id", "hostname", "date", "stat", "file", "wc",
        "systemctl", "journalctl", "docker", "ss", "netstat", "ip", "ifconfig", "ping", "dig",
        "nslookup", "curl", "wget", "git", "awk", "sed", "sort", "uniq", "cut", "tr", "echo",
        "pwd", "cd", "env", "printenv", "which", "type", "lsof", "vmstat", "iostat", "mpstat",
    )

    fun evaluate(command: String, permissionMode: String): Decision {
        val raw = command.trim()
        if (raw.isEmpty()) return Decision(false, false, "命令为空")
        HARD_BLOCK.firstOrNull { it.containsMatchIn(raw) }?.let {
            return Decision(false, false, "这条命令被安全策略硬性禁止（可能破坏主机）：${raw.take(80)}")
        }
        val readonlyMode = permissionMode == com.luoty.agent.data.PERM_READONLY
        if (readonlyMode && !isReadOnly(raw)) {
            return Decision(false, false, "当前是只读权限，只允许查看类命令；写操作请先切换权限。")
        }
        val dangerous = DANGEROUS.any { it.containsMatchIn(raw) }
        val confirmAll = permissionMode == com.luoty.agent.data.PERM_CONFIRM
        return when {
            dangerous -> Decision(true, true, "危险操作，需要你确认")
            confirmAll -> Decision(true, true, "每次确认模式：执行前需要你确认")
            else -> Decision(true, false, "")
        }
    }

    private fun isReadOnly(command: String): Boolean {
        if (Regex("""(^|\s)(>|>>|tee)\s|\|\s*(sh|bash|sudo)\b""").containsMatchIn(command)) return false
        if (Regex("""\bfind\b[^\n]*(-delete|-exec\s+rm)""").containsMatchIn(command)) return false
        if (Regex("""\bsed\b[^\n]*\s-i\b""").containsMatchIn(command)) return false
        if (Regex("""\bgit\b\s+(push|reset|clean|checkout|commit|merge|rebase)""").containsMatchIn(command)) return false
        // 允许管道与 && 串联，但每一段的首个词（及关键子命令）都必须属于只读用法
        return command.split(Regex("""\s*(?:&&|\|\||;|\|)\s*"""))
            .filter { it.isNotBlank() }
            .all { segment ->
                val tokens = segment.trim().split(Regex("""\s+"""))
                val first = tokens.firstOrNull()?.substringBefore('/')?.lowercase() ?: return@all false
                if (first !in READ_ONLY) return@all false
                val second = tokens.getOrNull(1)?.lowercase().orEmpty()
                when (first) {
                    "systemctl" -> second in setOf(
                        "status", "show", "cat", "list-units", "list-unit-files",
                        "is-active", "is-enabled", "list-timers",
                    )
                    "journalctl" -> !second.startsWith("-f")
                    "docker" -> second in setOf("ps", "images", "logs", "inspect", "stats", "version", "info")
                    "git" -> second in setOf(
                        "status", "log", "diff", "show", "branch", "remote", "rev-parse", "ls-files",
                    )
                    "sed" -> "-i" !in tokens
                    else -> true
                }
            }
    }
}
