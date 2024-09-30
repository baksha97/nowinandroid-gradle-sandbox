import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.provider.Property
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import com.squareup.kotlinpoet.*
import java.io.File

abstract class GenerateTypeSafeCatalogTask : DefaultTask() {

    @get:Input
    abstract val catalogName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val catalogs = project.extensions.getByType<VersionCatalogsExtension>()
        val catalog = catalogs.named(catalogName.get())

        val fileSpec = FileSpec.builder("com.example.catalog", "GeneratedCatalog")
            .addImport("org.gradle.api.artifacts", "MinimalExternalModuleDependency", "VersionCatalog", "ExternalModuleDependencyBundle")
            .addImport("org.gradle.api.provider", "Provider")
            .addImport("org.gradle.plugin.use", "PluginDependency")
            .addImport("java.util", "Optional")

        // Add extension function
        val extensionFun = FunSpec.builder("orElseThrowIllegalArgs")
            .receiver(ClassName("java.util", "Optional").parameterizedBy(TypeVariableName("T")))
            .addTypeVariable(TypeVariableName("T"))
            .addParameter("alias", String::class)
            .addParameter("type", String::class)
            .returns(TypeVariableName("T"))
            .addStatement("return this.orElseThrow { IllegalArgumentException(\"\$type alias '\$alias' not found\") }")
            .build()
        fileSpec.addFunction(extensionFun)

        // Generate main wrapper class
        val generatedCatalogClass = TypeSpec.classBuilder("GeneratedCatalog")
            .primaryConstructor(FunSpec.constructorBuilder()
                .addParameter("catalog", ClassName("org.gradle.api.artifacts", "VersionCatalog"))
                .build())
            .addProperty(PropertySpec.builder("catalog", ClassName("org.gradle.api.artifacts", "VersionCatalog"))
                .initializer("catalog")
                .build())
            .addProperty(PropertySpec.builder("versions", ClassName("", "Versions"))
                .initializer("Versions(catalog)")
                .build())
            .addProperty(PropertySpec.builder("libraries", ClassName("", "Libraries"))
                .initializer("Libraries(catalog)")
                .build())
            .addProperty(PropertySpec.builder("bundles", ClassName("", "Bundles"))
                .initializer("Bundles(catalog)")
                .build())
            .addProperty(PropertySpec.builder("plugins", ClassName("", "Plugins"))
                .initializer("Plugins(catalog)")
                .build())
            .build()
        fileSpec.addType(generatedCatalogClass)

        // Generate Versions class
        val versionsClass = TypeSpec.classBuilder("Versions")
            .primaryConstructor(FunSpec.constructorBuilder()
                .addParameter("catalog", ClassName("org.gradle.api.artifacts", "VersionCatalog"))
                .build())
            .addProperty(PropertySpec.builder("catalog", ClassName("org.gradle.api.artifacts", "VersionCatalog"))
                .initializer("catalog")
                .build())

        catalog.versionAliases.forEach { alias ->
            val property = PropertySpec.builder(alias.toCamelCase(), String::class)
                .getter(FunSpec.getterBuilder()
                    .addStatement("return catalog.findVersion(%S).orElseThrowIllegalArgs(%S, %S).requiredVersion", alias, alias, "Version")
                    .build())
                .build()
            versionsClass.addProperty(property)
        }
        fileSpec.addType(versionsClass.build())

        // Generate Libraries class
        val librariesClass = TypeSpec.classBuilder("Libraries")
            .primaryConstructor(FunSpec.constructorBuilder()
                .addParameter("catalog", ClassName("org.gradle.api.artifacts", "VersionCatalog"))
                .build())
            .addProperty(PropertySpec.builder("catalog", ClassName("org.gradle.api.artifacts", "VersionCatalog"))
                .initializer("catalog")
                .build())

        catalog.libraryAliases.forEach { alias ->
            val property = PropertySpec.builder(alias.toCamelCase(), ClassName("org.gradle.api.provider", "Provider")
                .parameterizedBy(ClassName("org.gradle.api.artifacts", "MinimalExternalModuleDependency")))
                .getter(FunSpec.getterBuilder()
                    .addStatement("return catalog.findLibrary(%S).orElseThrowIllegalArgs(%S, %S)", alias, alias, "Library")
                    .build())
                .build()
            librariesClass.addProperty(property)
        }
        fileSpec.addType(librariesClass.build())

        // Generate Bundles class
        val bundlesClass = TypeSpec.classBuilder("Bundles")
            .primaryConstructor(FunSpec.constructorBuilder()
                .addParameter("catalog", ClassName("org.gradle.api.artifacts", "VersionCatalog"))
                .build())
            .addProperty(PropertySpec.builder("catalog", ClassName("org.gradle.api.artifacts", "VersionCatalog"))
                .initializer("catalog")
                .build())

        catalog.bundleAliases.forEach { alias ->
            val property = PropertySpec.builder(alias.toCamelCase(), ClassName("org.gradle.api.provider", "Provider")
                .parameterizedBy(ClassName("org.gradle.api.artifacts", "ExternalModuleDependencyBundle")))
                .getter(FunSpec.getterBuilder()
                    .addStatement("return catalog.findBundle(%S).orElseThrowIllegalArgs(%S, %S)", alias, alias, "Bundle")
                    .build())
                .build()
            bundlesClass.addProperty(property)
        }
        fileSpec.addType(bundlesClass.build())

        // Generate Plugins class
        val pluginsClass = TypeSpec.classBuilder("Plugins")
            .primaryConstructor(FunSpec.constructorBuilder()
                .addParameter("catalog", ClassName("org.gradle.api.artifacts", "VersionCatalog"))
                .build())
            .addProperty(PropertySpec.builder("catalog", ClassName("org.gradle.api.artifacts", "VersionCatalog"))
                .initializer("catalog")
                .build())

        catalog.pluginAliases.forEach { alias ->
            val property = PropertySpec.builder(alias.toCamelCase(), ClassName("org.gradle.api.provider", "Provider")
                .parameterizedBy(ClassName("org.gradle.plugin.use", "PluginDependency")))
                .getter(FunSpec.getterBuilder()
                    .addStatement("return catalog.findPlugin(%S).orElseThrowIllegalArgs(%S, %S)", alias, alias, "Plugin")
                    .build())
                .build()
            pluginsClass.addProperty(property)
        }
        fileSpec.addType(pluginsClass.build())

        val outputFile = outputDir.get().file("GeneratedCatalog.kt").asFile
        outputFile.parentFile.mkdirs()
        fileSpec.build().writeTo(outputFile)
    }

    private fun String.toCamelCase(): String = split("-", "_", ".")
        .mapIndexed { index, s -> if (index == 0) s else s.capitalize() }
        .joinToString("")
}
