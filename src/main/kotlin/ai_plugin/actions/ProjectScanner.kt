package ai_plugin.actions

import com.intellij.openapi.project.Project
import java.io.File

object ProjectScanner {

    private const val MAX_CHARS = 50_000 // safe limit, well under Groq's token limit

    fun collectContext(project: Project): String {
        val projectPath = project.basePath ?: return ""

        val excluded = setOf("build", ".gradle", ".idea", ".git", "node_modules", "out", "tmp")

        val files = File(projectPath)
            .walkTopDown()
            .onEnter { dir -> dir.name !in excluded }
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.path }
            .toList()

        val builder = StringBuilder()
        for (file in files) {
            val content = file.readText()
            val entry = "// File: ${file.relativeTo(File(projectPath)).path}\n$content\n\n"
            if (builder.length + entry.length > MAX_CHARS) break
            builder.append(entry)
        }

        return builder.toString()
    }
}
