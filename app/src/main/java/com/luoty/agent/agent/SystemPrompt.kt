package com.luoty.agent.agent

import com.luoty.agent.data.PERM_FULL
import com.luoty.agent.data.PERM_READONLY
import com.luoty.agent.data.Session

import com.luoty.agent.data.AppSettings

object SystemPrompt {

    fun build(
        settings: AppSettings,
        workspacePath: String,
        goal: String = "",
        planMode: Boolean = false,
        summary: String = "",
        summaryCount: Int = 0,
    ): String {
        return buildString {
            append(com.luoty.agent.data.CORE_SYSTEM_PROMPT)
            if (settings.systemPrompt.isNotBlank()) {
                append("\n\n## 用户附加指令（可编辑）\n")
                append(settings.systemPrompt.trim())
            }
            append("\n\n## 运行环境\n")
            append("- 工作区：").append(workspacePath).append("\n")
            append("- 平台：Android 手机，工具里的路径都相对工作区，例如 src/main.py\n")
            append("- 可用工具：list_files、read_file、write_file、edit_file、search_files、delete_path、run_command\n")
            append("- 说明：对话历史里出现的工具结果由本机真实执行，可直接引用。")

            if (goal.isNotBlank()) {
                append("\n\n## 当前目标\n").append(goal.trim())
                append("\n（这是用户设置的长期目标，每一步都要服务于它；偏离时要主动拉回。）")
            }

            if (planMode) {
                append("\n\n## 计划模式（开启）\n")
                append("现在只做分析和计划：先读必要的文件，然后输出一份可执行的步骤清单（改哪些文件、怎么改、怎么验证）。")
                append("不要调用 write_file / edit_file / delete_path / run_command，也不要 ssh_exec / 起服务；等用户确认计划后切到「建造」再执行。")
            }

            if (settings.teamMode) {
                append("\n\n## 团队协作模式（开启）\n")
                append("把任务当成一个小团队来做：先以「架构」视角拆分任务，再以「实现」视角逐步落地，")
                append("最后以「审查」视角自查（边界情况、失败路径、是否验证过）。")
                append("每一步都写清楚当前角色在做什么，但不要输出无关的扮演台词。")
            }

            append("\n\n## 任务清单习惯\n")
            append("遇到多步骤任务，先用 update_tasks 列出 2-6 个任务；每完成一步就更新状态（IN_PROGRESS → DONE），")
            append("让输入框上方的任务面板始终反映真实进度。简单的一步问题不用建清单。")

            if (summary.isNotBlank()) {
                append("\n\n## 早期对话摘要（已压缩 ").append(summaryCount).append(" 条消息）\n")
                append(summary.trim())
            }
        }
    }

/** 每轮追加的小块上下文：放时间、设备、服务器、工具用法，避免污染系统前缀缓存 */
fun runtimeContext(
    settings: AppSettings,
    session: Session,
    workspacePath: String,
    serverLabel: String,
): String {
    val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date())
    val tz = java.util.TimeZone.getDefault().id
    val device = "${android.os.Build.MODEL} / Android ${android.os.Build.VERSION.RELEASE}"
    val perm = when (settings.permissionMode) {
        PERM_FULL -> "完全"
        PERM_READONLY -> "只读"
        else -> "每次确认"
    }
    return buildString {
        append("[runtime-context] ")
        append(now).append(' ').append(tz)
        append(" | 设备 ").append(device)
        append(" | 工作区 ").append(workspacePath)
        append(" | 权限 ").append(perm)
        append(" | 模型 ").append(settings.model)
        append(" | 上下文 ").append(settings.contextWindow)
        append(" | 服务器 ").append(serverLabel.ifBlank { "未选择" })
        append("\n[skill-catalog] ")
        append("附件中的 PDF/Word/TXT/ZIP 已抽成文本随消息发送，直接读即可；")
        append("网页用 start_web_server 起在本机并返回局域网地址；")
        append("远程 Linux 用 ssh_exec（危险命令会自动要求确认）；")
        append("多步骤任务先用 update_tasks 建清单；不确定时用 ask_user。")
    }
}
}
