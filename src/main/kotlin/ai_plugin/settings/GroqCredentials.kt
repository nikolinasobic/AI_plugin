package ai_plugin.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe

internal object GroqCredentials {

    private const val SERVICE = "AI_Plugin_Groq"
    private const val USER = "groq_api_key"

    private val attributes = CredentialAttributes(SERVICE, USER)

    fun loadKey(): String? =
        PasswordSafe.instance.getPassword(attributes)?.takeIf { it.isNotBlank() }

    fun saveKey(key: String?) {
        val creds = if (key.isNullOrBlank()) null else Credentials(USER, key)
        PasswordSafe.instance.set(attributes, creds)
    }
}
