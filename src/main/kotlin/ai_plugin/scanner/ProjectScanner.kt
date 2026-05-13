package ai_plugin.scanner

import ai_plugin.config.PluginConfig
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

object ProjectScanner {

    private data class LanguageConfig(
        val extensions: Set<String>,
        val displayName: String,
        val importRegex: Regex,
        val toFilenames: (groups: List<String>) -> List<String>,
    )

    private val LANGUAGE_CONFIGS = listOf(
        LanguageConfig(
            extensions = setOf("kt", "kts"),
            displayName = "Kotlin",
            importRegex = Regex("""^import\s+([\w.]+)""", RegexOption.MULTILINE),
            toFilenames = { g -> if (g.isEmpty()) emptyList() else listOf(g[0].substringAfterLast('.') + ".kt") }
        ),
        LanguageConfig(
            extensions = setOf("java"),
            displayName = "Java",
            importRegex = Regex("""^import\s+(?:static\s+)?([\w.]+);""", RegexOption.MULTILINE),
            toFilenames = { g -> if (g.isEmpty()) emptyList() else listOf(g[0].substringAfterLast('.') + ".java") }
        ),
        LanguageConfig(
            extensions = setOf("py"),
            displayName = "Python",
            importRegex = Regex("""^(?:from\s+([\w.]+)\s+import|import\s+([\w.]+))""", RegexOption.MULTILINE),
            toFilenames = { g ->
                val module = g.firstOrNull { it.isNotEmpty() } ?: return@LanguageConfig emptyList()
                listOf(module.replace('.', '/') + ".py")
            }
        ),
        LanguageConfig(
            extensions = setOf("ts", "tsx"),
            displayName = "TypeScript",
            importRegex = Regex("""from\s+['"](\.[^'"]+)['"]"""),
            toFilenames = { g ->
                if (g.isEmpty()) emptyList()
                else {
                    val base = g[0].substringAfterLast('/')
                    listOf("$base.ts", "$base.tsx", "$base/index.ts", "$base/index.tsx")
                }
            }
        ),
        LanguageConfig(
            extensions = setOf("js", "jsx", "mjs"),
            displayName = "JavaScript",
            importRegex = Regex("""(?:from\s+['"]|require\s*\(\s*['"])(\.[^'"]+)['"]"""),
            toFilenames = { g ->
                if (g.isEmpty()) emptyList()
                else {
                    val base = g[0].substringAfterLast('/')
                    listOf("$base.js", "$base.jsx", "$base/index.js")
                }
            }
        ),
        LanguageConfig(
            extensions = setOf("go"),
            displayName = "Go",
            importRegex = Regex(""""([\w./]+)""""),
            toFilenames = { g -> if (g.isEmpty()) emptyList() else listOf(g[0].substringAfterLast('/') + ".go") }
        ),
        LanguageConfig(
            extensions = setOf("rs"),
            displayName = "Rust",
            importRegex = Regex("""^use\s+(?:crate|super|self)::([\w:]+)""", RegexOption.MULTILINE),
            toFilenames = { g ->
                if (g.isEmpty()) emptyList()
                else {
                    val module = g[0].substringBeforeLast("::").replace("::", "/")
                    listOf("$module.rs", "$module/mod.rs")
                }
            }
        ),
        LanguageConfig(
            extensions = setOf("cpp", "cc", "cxx", "h", "hpp"),
            displayName = "C++",
            importRegex = Regex("""^#include\s+"([^"]+)"""", RegexOption.MULTILINE),
            toFilenames = { g -> if (g.isEmpty()) emptyList() else listOf(g[0].substringAfterLast('/')) }
        ),
        LanguageConfig(
            extensions = setOf("c"),
            displayName = "C",
            importRegex = Regex("""^#include\s+"([^"]+)"""", RegexOption.MULTILINE),
            toFilenames = { g -> if (g.isEmpty()) emptyList() else listOf(g[0].substringAfterLast('/')) }
        ),
        LanguageConfig(
            extensions = setOf("cs"),
            displayName = "C#",
            importRegex = Regex("""^using\s+([\w.]+);""", RegexOption.MULTILINE),
            toFilenames = { g -> if (g.isEmpty()) emptyList() else listOf(g[0].substringAfterLast('.') + ".cs") }
        ),
        LanguageConfig(
            extensions = setOf("rb"),
            displayName = "Ruby",
            importRegex = Regex("""^require(?:_relative)?\s+['"]([^'"]+)['"]""", RegexOption.MULTILINE),
            toFilenames = { g -> if (g.isEmpty()) emptyList() else listOf(g[0].substringAfterLast('/') + ".rb") }
        ),
        LanguageConfig(
            extensions = setOf("swift"),
            displayName = "Swift",
            importRegex = Regex("""^import\s+(\w+)""", RegexOption.MULTILINE),
            toFilenames = { g -> if (g.isEmpty()) emptyList() else listOf(g[0] + ".swift") }
        ),
        LanguageConfig(
            extensions = setOf("scala"),
            displayName = "Scala",
            importRegex = Regex("""^import\s+([\w.]+)""", RegexOption.MULTILINE),
            toFilenames = { g -> if (g.isEmpty()) emptyList() else listOf(g[0].substringAfterLast('.') + ".scala") }
        ),
        LanguageConfig(
            extensions = setOf("php"),
            displayName = "PHP",
            importRegex = Regex("""(?:include|require)(?:_once)?\s*\(?\s*['"]([^'"]+)['"]\s*\)?""", RegexOption.MULTILINE),
            toFilenames = { g -> if (g.isEmpty()) emptyList() else listOf(g[0].substringAfterLast('/')) }
        ),
    )

    private val CONFIG_BY_EXT: Map<String, LanguageConfig> = buildMap {
        LANGUAGE_CONFIGS.forEach { cfg -> cfg.extensions.forEach { ext -> put(ext, cfg) } }
    }

    private val ALL_SOURCE_EXTENSIONS = CONFIG_BY_EXT.keys

    // Build/config files that always provide useful project context
    private val CONFIG_FILENAMES = setOf(
        "plugin.xml", "build.gradle.kts", "build.gradle", "settings.gradle.kts",
        "pom.xml", "package.json", "tsconfig.json", "Cargo.toml", "go.mod",
        "pyproject.toml", "requirements.txt", "Gemfile", "CMakeLists.txt",
    )

    fun languageDisplayName(extension: String?): String =
        CONFIG_BY_EXT[extension?.lowercase()]?.displayName ?: "code"

    fun collectContext(project: Project, currentFile: VirtualFile?): String {
        val projectPath = project.basePath ?: return ""
        val root = File(projectPath)
        val builder = StringBuilder()
        val included = mutableSetOf<String>()

        // Single walk — builds a filename→files index used by all steps below
        val fileIndex: Map<String, List<File>> = root.walkTopDown()
            .onEnter { dir -> dir.name !in PluginConfig.EXCLUDED_DIRS }
            .filter { it.isFile }
            .groupBy { it.name }

        if (currentFile != null) {
            val config = CONFIG_BY_EXT[currentFile.extension?.lowercase()]

            // 1. Current file — always most relevant
            addFile(File(currentFile.path), root, builder, included)

            // 2. Files this file imports — direct dependencies
            if (config != null) {
                resolveImports(currentFile, config, fileIndex).forEach { file ->
                    addFile(file, root, builder, included)
                }
            }

            // 3. Build/config files — give AI project structure awareness
            CONFIG_FILENAMES.flatMap { fileIndex[it] ?: emptyList() }
                .sortedBy { it.path }
                .forEach { addFile(it, root, builder, included) }

            // 4. Fill remaining budget with other same-language files
            if (config != null) {
                fileIndex.values.flatten()
                    .filter { it.extension?.lowercase() in config.extensions }
                    .sortedBy { it.path }
                    .forEach { addFile(it, root, builder, included) }
            }
        } else {
            // No current file — include config files first, then all source files
            CONFIG_FILENAMES.flatMap { fileIndex[it] ?: emptyList() }
                .sortedBy { it.path }
                .forEach { addFile(it, root, builder, included) }
            fileIndex.values.flatten()
                .filter { it.extension?.lowercase() in ALL_SOURCE_EXTENSIONS }
                .sortedBy { it.path }
                .forEach { addFile(it, root, builder, included) }
        }

        return builder.toString()
    }

    private fun resolveImports(vf: VirtualFile, config: LanguageConfig, fileIndex: Map<String, List<File>>): List<File> {
        val text = try { File(vf.path).readText() } catch (_: Exception) { return emptyList() }
        return config.importRegex.findAll(text)
            .flatMap { match ->
                val groups = match.groupValues.drop(1).filter { it.isNotEmpty() }
                config.toFilenames(groups).asSequence()
            }
            .distinct()
            .mapNotNull { name -> fileIndex[name.substringAfterLast('/')]?.firstOrNull() }
            .toList()
    }

    private fun addFile(file: File, root: File, builder: StringBuilder, included: MutableSet<String>) {
        if (builder.length >= PluginConfig.MAX_CONTEXT_CHARS) return
        if (!file.exists() || !file.isFile) return
        val canonical = try { file.canonicalPath } catch (_: Exception) { file.absolutePath }
        if (canonical in included) return
        included.add(canonical)
        try {
            val rel = try { file.relativeTo(root).path } catch (_: Exception) { file.path }
            val entry = "// File: $rel\n${file.readText()}\n\n"
            if (builder.length + entry.length <= PluginConfig.MAX_CONTEXT_CHARS) builder.append(entry)
        } catch (_: Exception) {
            // skip unreadable files
        }
    }
}
