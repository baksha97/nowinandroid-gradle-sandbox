abstract class GenerateTypeSafeCatalogTask : DefaultTask() {

    @get:Input
    abstract val catalogName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val catalogs = project.extensions.getByType<VersionCatalogsExtension>()
        val catalog = catalogs.named(catalogName.get())

        val generatedCode = buildString {
            appendLine("package com.example.catalog")
            appendLine()
            appendLine("import org.gradle.api.artifacts.MinimalExternalModuleDependency")
            appendLine("import org.gradle.api.provider.Provider")
            appendLine("import org.gradle.plugin.use.PluginDependency")
            appendLine("import org.gradle.api.artifacts.VersionCatalog")
            appendLine("import org.gradle.api.artifacts.ExternalModuleDependencyBundle") // Ensure correct import
            appendLine("import java.util.Optional")
            appendLine()

            // Define the extension function
            appendLine("fun <T> Optional<T>.orElseThrowIllegalArgs(alias: String, type: String): T {")
            appendLine("    return this.orElseThrow { IllegalArgumentException(\"\$type alias '\$alias' not found\") }")
            appendLine("}")
            appendLine()

            // Generate the main wrapper class
            appendLine("data class GeneratedCatalog(val catalog: VersionCatalog) {")
            appendLine("    val versions = Versions(catalog)")
            appendLine("    val libraries = Libraries(catalog)")
            appendLine("    val bundles = Bundles(catalog)")
            appendLine("    val plugins = Plugins(catalog)")
            appendLine("}")
            appendLine()

            // Generate the Versions data class
            appendLine("data class Versions(val catalog: VersionCatalog) {")
            catalog.getVersionAliases().forEach { alias ->
                appendLine("    val ${alias.toCamelCase()}: String")
                appendLine("        get() = catalog.findVersion(\"$alias\").orElseThrowIllegalArgs(\"$alias\", \"Version\").requiredVersion")
            }
            appendLine("}")
            appendLine()

            // Generate the Libraries data class
            appendLine("data class Libraries(val catalog: VersionCatalog) {")
            catalog.getLibraryAliases().forEach { alias ->
                appendLine("    val ${alias.toCamelCase()}: Provider<MinimalExternalModuleDependency>")
                appendLine("        get() = catalog.findLibrary(\"$alias\").orElseThrowIllegalArgs(\"$alias\", \"Library\")")
            }
            appendLine("}")
            appendLine()

            // Generate the Bundles data class
            appendLine("data class Bundles(val catalog: VersionCatalog) {")
            catalog.getBundleAliases().forEach { alias ->
                appendLine("    val ${alias.toCamelCase()}: Provider<ExternalModuleDependencyBundle>")
                appendLine("        get() = catalog.findBundle(\"$alias\").orElseThrowIllegalArgs(\"$alias\", \"Bundle\")")
            }
            appendLine("}")
            appendLine()

            // Generate the Plugins data class
            appendLine("data class Plugins(val catalog: VersionCatalog) {")
            catalog.getPluginAliases().forEach { alias ->
                appendLine("    val ${alias.toCamelCase()}: Provider<PluginDependency>")
                appendLine("        get() = catalog.findPlugin(\"$alias\").orElseThrowIllegalArgs(\"$alias\", \"Plugin\")")
            }
            appendLine("}")
        }

        val outputFile = outputDir.get().file("GeneratedCatalog.kt").asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(generatedCode)
    }

    private fun String.toCamelCase(): String = split("-", "_", ".")
        .joinToString("") { it.capitalize() }
        .decapitalize()
}



tasks.register<GenerateTypeSafeCatalogTask>("generateTypeSafeCatalog") {
    outputDir.set(layout.buildDirectory.dir("generated/sources/versionCatalog"))
    catalogName.set("libs") // Use your actual catalog name here
}