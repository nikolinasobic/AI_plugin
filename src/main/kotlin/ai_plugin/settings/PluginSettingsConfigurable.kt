package ai_plugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class PluginSettingsConfigurable : Configurable {

    private var passwordField: JBPasswordField? = null

    override fun getDisplayName() = "AI Plugin"

    override fun createComponent(): JComponent {
        val field = JBPasswordField().also { passwordField = it }
        val hint = JBLabel("Stored securely in the OS keychain.").apply {
            foreground = JBColor.GRAY
        }
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Groq API key:", field)
            .addComponent(hint)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    private fun fieldText() = String(passwordField?.password ?: charArrayOf())

    override fun isModified() = fieldText() != (GroqCredentials.loadKey() ?: "")

    override fun apply() {
        GroqCredentials.saveKey(fieldText().trim().ifEmpty { null })
    }

    override fun reset() {
        passwordField?.text = GroqCredentials.loadKey() ?: ""
    }

    override fun disposeUIResources() {
        passwordField = null
    }
}
