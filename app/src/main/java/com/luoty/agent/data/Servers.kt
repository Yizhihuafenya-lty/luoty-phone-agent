package com.luoty.agent.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 用 Android Keystore 加密服务器密码 / 私钥，明文不落盘 */
object SecretBox {
    private const val ALIAS = "luoty_agent_ssh_secrets"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun secretKey(): SecretKey {
        val store = keyStore()
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
        }.getOrDefault("")
    }

    fun decrypt(data: String): String {
        if (data.isEmpty()) return ""
        return runCatching {
            val bytes = Base64.decode(data, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, 12)
            val encrypted = bytes.copyOfRange(12, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrDefault("")
    }
}

data class SshServer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authType: String = "password", // password | key
    val secret: String = "",
    val passphrase: String = "",
    val trustedFingerprint: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    val label: String get() = if (name.isBlank()) "$username@$host" else name
    val target: String get() = "$username@$host:$port"

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("host", host)
        put("port", port)
        put("username", username)
        put("authType", authType)
        put("secret", SecretBox.encrypt(secret))
        put("passphrase", SecretBox.encrypt(passphrase))
        put("trustedFingerprint", trustedFingerprint)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject): SshServer = SshServer(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = o.optString("name", ""),
            host = o.optString("host", ""),
            port = o.optInt("port", 22),
            username = o.optString("username", ""),
            authType = o.optString("authType", "password"),
            secret = SecretBox.decrypt(o.optString("secret", "")),
            passphrase = SecretBox.decrypt(o.optString("passphrase", "")),
            trustedFingerprint = o.optString("trustedFingerprint", ""),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        )
    }
}

class ServerStore(context: Context) {
    private val prefs = context.getSharedPreferences("luoty_agent_servers", Context.MODE_PRIVATE)

    fun load(): List<SshServer> {
        val raw = prefs.getString("servers", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { SshServer.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun save(servers: List<SshServer>) {
        prefs.edit().putString("servers", JSONArray(servers.map { it.toJson() }).toString()).apply()
    }

    fun find(id: String): SshServer? = load().firstOrNull { it.id == id }

    fun findByName(name: String): SshServer? =
        load().firstOrNull { it.name.equals(name, ignoreCase = true) || it.id == name }
}
