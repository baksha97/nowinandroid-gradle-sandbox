/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.provider.Property
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType


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