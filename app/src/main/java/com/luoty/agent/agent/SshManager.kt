package com.luoty.agent.agent

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import com.luoty.agent.data.ServerStore
import com.luoty.agent.data.SshServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale

class SshException(message: String, val offeredFingerprint: String? = null) : Exception(message)

/**
 * SSH 通道：密码 / 私钥登录，强制校验主机指纹；所有命令写入本地审计日志。
 */
class SshManager(
    private val store: ServerStore,
    private val auditFile: File,
) {

    data class ExecResult(val output: String, val exitCode: Int)

    suspend fun testConnection(server: SshServer): String = withContext(Dispatchers.IO) {
        val result = exec(server, "echo luoty-ok; uname -sr; whoami", timeoutSeconds = 15)
        if (!result.output.contains("luoty-ok")) {
            throw SshException("连接成功但返回内容异常：${result.output.take(200)}")
        }
        "连接成功：${result.output.lines().filter { it.isNotBlank() && it != "luoty-ok" }.joinToString(" / ")}"
    }

    suspend fun exec(
        server: SshServer,
        command: String,
        timeoutSeconds: Int = 30,
    ): ExecResult = withContext(Dispatchers.IO) {
        val jsch = JSch()
        if (server.authType == "key") {
            if (server.secret.isBlank()) throw SshException("没有配置私钥")
            val passphrase = server.passphrase.takeIf { it.isNotBlank() }?.toByteArray()
            runCatching {
                jsch.addIdentity("luoty-${server.id}", server.secret.toByteArray(), null, passphrase)
            }.getOrElse { throw SshException("私钥加载失败：${it.message}") }
        }
        val session = jsch.getSession(server.username, server.host, server.port)
        if (server.authType == "password") {
            if (server.secret.isBlank()) throw SshException("没有配置密码")
            session.setPassword(server.secret)
        }
        val repo = FingerprintRepository(server.trustedFingerprint)
        session.hostKeyRepository = repo
        session.setConfig("StrictHostKeyChecking", "yes")
        session.setConfig("PreferredAuthentications", if (server.authType == "key") "publickey" else "password,keyboard-interactive")
        session.setConfig("ServerAliveInterval", "15")
        session.timeout = 15_000

        try {
            session.connect(15_000)
        } catch (e: Exception) {
            val message = e.message.orEmpty()
            val fingerprint = repo.offeredFingerprint
            when {
                message.contains("UnknownHostKey", ignoreCase = true) || message.contains("UnknownHost", ignoreCase = true) ->
                    throw SshException(
                        "首次连接，需要信任服务器指纹。\n指纹：${fingerprint ?: "未知"}\n请确认这是你要连的机器，再点「信任并保存」。",
                        fingerprint,
                    )
                message.contains("HostKeyChanged", ignoreCase = true) || message.contains("has changed", ignoreCase = true) ->
                    throw SshException(
                        "服务器指纹变了！可能存在中间人攻击。\n当前指纹：${fingerprint ?: "未知"}\n若确认是你换了密钥，请先清除已保存指纹再重连。",
                        fingerprint,
                    )
                else -> throw SshException("连接失败：${e.message ?: e.javaClass.simpleName}")
            }
        }

        val channel = session.openChannel("exec") as ChannelExec
        channel.setCommand(command)
        val stderr = ByteArrayOutputStream()
        channel.setErrStream(stderr)
        val out = ByteArrayOutputStream()
        try {
            channel.connect(10_000)
            val input = channel.inputStream
            val deadline = System.currentTimeMillis() + timeoutSeconds * 1000L
            val buffer = ByteArray(8192)
            while (System.currentTimeMillis() < deadline) {
                while (input.available() > 0 && out.size() < 200_000) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                }
                if (channel.isClosed) break
                if (out.size() >= 200_000) break
                Thread.sleep(40)
            }
            if (!channel.isClosed) {
                channel.disconnect()
                throw SshException("命令超时（${timeoutSeconds}s），已断开通道")
            }
            val exit = channel.exitStatus
            val text = buildString {
                append(out.toString(Charsets.UTF_8.name()))
                val err = stderr.toString(Charsets.UTF_8.name())
                if (err.isNotBlank()) {
                    if (isNotEmpty()) append('\n')
                    append("[stderr]\n").append(err)
                }
            }.trim().let {
                if (it.length > 30_000) it.take(30_000) + "\n…（输出过长已截断）" else it
            }
            audit(server, command, exit, text)
            ExecResult(text, exit)
        } finally {
            runCatching { channel.disconnect() }
            runCatching { session.disconnect() }
        }
    }

    private fun audit(server: SshServer, command: String, exit: Int, output: String) {
        runCatching {
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())
            val line = "$time | ${server.target} | exit=$exit | ${command.replace('\n', ' ').take(300)} | ${output.take(120).replace('\n', ' ')}\n"
            auditFile.appendText(line)
        }
    }

    /** 主机指纹（SHA256 / Base64，与 ssh-keygen -lf 一致的风格） */
    private class FingerprintRepository(var expected: String) : HostKeyRepository {
        @Volatile
        var offeredFingerprint: String? = null

        override fun check(host: String?, key: ByteArray?): Int {
            if (key == null) return HostKeyRepository.NOT_INCLUDED
            val fingerprint = fingerprint(key, type = null)
            offeredFingerprint = fingerprint
            return when {
                expected.isBlank() -> HostKeyRepository.NOT_INCLUDED
                expected == fingerprint -> HostKeyRepository.OK
                else -> HostKeyRepository.CHANGED
            }
        }

        override fun getHostKey(): Array<HostKey>? = null
        override fun getHostKey(host: String?, type: String?): Array<HostKey>? = null
        override fun add(hostkey: HostKey?, userinfo: UserInfo?) {}
        override fun remove(host: String?, type: String?) {}
        override fun remove(host: String?, type: String?, key: ByteArray?) {}
        override fun getKnownHostsRepositoryID(): String = "luoty-agent"

        private fun fingerprint(key: ByteArray, type: String?): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(key)
            val encoded = Base64.getEncoder().withoutPadding().encodeToString(digest)
            return type?.let { "$it $encoded" } ?: encoded
        }
    }
}
