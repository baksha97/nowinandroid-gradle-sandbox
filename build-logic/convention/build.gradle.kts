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

plugins {
    `kotlin-dsl`
}

group = "com.google.samples.apps.nowinandroid.buildlogic"

// Configure the build-logic plugins to target JDK 17
// This matches the JDK used to build the project, and is not related to what is running on device.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.android.tools.common)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.firebase.crashlytics.gradlePlugin)
    compileOnly(libs.firebase.performance.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
    implementation(libs.truth)
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}

gradlePlugin {
    plugins {
        register("androidApplicationCompose") {
            id = "nowinandroid.android.application.compose"
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }
        register("androidApplication") {
            id = "nowinandroid.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidApplicationJacoco") {
            id = "nowinandroid.android.application.jacoco"
            implementationClass = "AndroidApplicationJacocoConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "nowinandroid.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidLibrary") {
            id = "nowinandroid.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = "nowinandroid.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidLibraryJacoco") {
            id = "nowinandroid.android.library.jacoco"
            implementationClass = "AndroidLibraryJacocoConventionPlugin"
        }
        register("androidTest") {
            id = "nowinandroid.android.test"
            implementationClass = "AndroidTestConventionPlugin"
        }
        register("hilt") {
            id = "nowinandroid.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("androidRoom") {
            id = "nowinandroid.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }
        register("androidFirebase") {
            id = "nowinandroid.android.application.firebase"
            implementationClass = "AndroidApplicationFirebaseConventionPlugin"
        }
        register("androidFlavors") {
            id = "nowinandroid.android.application.flavors"
            implementationClass = "AndroidApplicationFlavorsConventionPlugin"
        }
        register("androidLint") {
            id = "nowinandroid.android.lint"
            implementationClass = "AndroidLintConventionPlugin"
        }
        register("jvmLibrary") {
            id = "nowinandroid.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}

tasks.register<GenerateTypeSafeCatalogTask>("generateTypeSafeCatalog") {
    outputDir.set(layout.buildDirectory.dir("generated/sources/versionCatalog"))
    catalogName.set("libs") // Use your actual catalog name here
}

sourceSets {
    main {
        kotlin.srcDir("build/generated/sources/versionCatalog")
    }
}

tasks.named("compileKotlin") {
    dependsOn("generateTypeSafeCatalog")
}

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