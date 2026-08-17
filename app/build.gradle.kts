import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
}

// Signing credentials resolution order:
// 1. Environment variables (used in CI, e.g. GitHub Actions Secrets)
// 2. Local release.properties file (gitignored, for local release builds)
// Neither is required for debug builds.
val keystorePropsFile = rootProject.file("release.properties")
val keystoreProps = Properties()

if (keystorePropsFile.exists()) {
    FileInputStream(keystorePropsFile).use { keystoreProps.load(it) }
}

fun signingValue(propKey: String, envKey: String): String? =
    System.getenv(envKey) ?: keystoreProps.getProperty(propKey)

val resolvedStoreFile = signingValue("storeFile", "SIGNING_STORE_FILE")
val resolvedStorePassword = signingValue("storePassword", "SIGNING_STORE_PASSWORD")
val resolvedKeyAlias = signingValue("keyAlias", "SIGNING_KEY_ALIAS")
val resolvedKeyPassword = signingValue("keyPassword", "SIGNING_KEY_PASSWORD")

val hasValidSigningProps = listOf(
    resolvedStoreFile, resolvedStorePassword, resolvedKeyAlias, resolvedKeyPassword
).all { !it.isNullOrBlank() }

// Derive versionName/versionCode from git tags so releases stay traceable to source.
// Falls back to a safe default when git or tags are unavailable (e.g. a shallow clone
// with no tags, or building outside a git checkout entirely).
// Uses ProviderFactory.exec (not the deprecated Project.exec, removed in Gradle 9.0).
fun gitCommand(vararg args: String): String? = try {
    val result = providers.exec {
        commandLine("git", *args)
        isIgnoreExitValue = true
    }
    result.standardOutput.asText.get().trim().ifBlank { null }
} catch (e: Exception) {
    null
}

val gitTag = gitCommand("describe", "--tags", "--abbrev=0")
val gitCommitCount = gitCommand("rev-list", "--count", "HEAD")?.toIntOrNull()

// Tags are expected in the form "vMAJOR.MINOR.PATCH", e.g. "v1.2.0".
val resolvedVersionName = gitTag?.removePrefix("v") ?: "0.0.0-dev"
val resolvedVersionCode = gitCommitCount ?: 1

// Generates a string-array resource listing every locale this app actually
// ships translated strings for, by scanning res/values-* directory names —
// so the in-app language picker (LocaleUtils) never needs a hardcoded list
// in Java that could drift out of sync with the translations that exist.
// Adding a new res/values-<qualifier>/strings.xml is enough on its own;
// nothing in Java needs to change to make that language selectable.
val generatedLocalesDir = layout.buildDirectory.dir("generated/locales/res")

val generateLocalesList by tasks.registering {
    val resDir = file("src/main/res")
    val outputDir = generatedLocalesDir

    inputs.dir(resDir).withPropertyName("resDir").skipWhenEmpty(false)
    outputs.dir(outputDir)

    // Matches BCP-47-ish resource qualifiers Android uses for language
    // directories: "es", "pt-rBR" (region), "b+sr+Latn" (script/BCP-47
    // extension form). Deliberately excludes non-language qualifiers
    // (night, v24, sw600dp, land, and so on) that also live under res/.
    val localeDirPattern = Regex("""^values-(b\+[a-zA-Z0-9+]+|[a-z]{2,3}(-r[A-Z]{2})?)$""")
    val nonLocaleQualifiers = setOf(
        "values-night", "values-land", "values-port", "values-v21", "values-v23",
        "values-v24", "values-v26", "values-v28", "values-v29", "values-v31"
    )

    doLast {
        val detected = sortedSetOf("en") // default/base values/ is always English per this app's convention.

        resDir.listFiles { file -> file.isDirectory }?.forEach { dir ->
            val name = dir.name
            if (name in nonLocaleQualifiers) return@forEach
            val match = localeDirPattern.matchEntire(name) ?: return@forEach
            val qualifier = match.groupValues[1]

            // Convert the resource-qualifier form to a BCP-47 tag consumable
            // by Locale.forLanguageTag(): "pt-rBR" -> "pt-BR", "b+sr+Latn" -> "sr-Latn".
            val tag = when {
                qualifier.startsWith("b+") -> qualifier.removePrefix("b+").replace("+", "-")
                qualifier.contains("-r") -> qualifier.replace("-r", "-")
                else -> qualifier
            }
            detected.add(tag)
        }

        val xml = buildString {
            appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
            appendLine("<resources>")
            appendLine("""    <string-array name="available_locale_tags" translatable="false">""")
            detected.forEach { tag -> appendLine("""        <item>$tag</item>""") }
            appendLine("    </string-array>")
            appendLine("</resources>")
        }

        val outFile = outputDir.get().file("values/generated_locales.xml").asFile
        outFile.parentFile.mkdirs()
        outFile.writeText(xml)
    }
}

android {
    namespace = "id.ditzzy.scalara"
    compileSdk = 36 
    
    // disable linter
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
        
    signingConfigs {
        if (hasValidSigningProps) {
            create("release") {
                storeFile = rootProject.file(resolvedStoreFile!!)
                storePassword = resolvedStorePassword
                keyAlias = resolvedKeyAlias
                keyPassword = resolvedKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "id.ditzzy.scalara"
        minSdk = 24 
        targetSdk = 36  
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName
        
        vectorDrawables { 
            useSupportLibrary = true
        }
    }

    sourceSets {
        getByName("main") {
            res.srcDir(generatedLocalesDir)
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17 
        targetCompatibility = JavaVersion.VERSION_17 
    }

    buildTypes {
        release {
            if (hasValidSigningProps) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        viewBinding = true
        aidl = true
    }
    packaging {
        resources {
            resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
        }
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:deprecation")
}

// generatedLocalesDir is registered as a res.srcDir() below, so AGP wires it
// into several per-variant tasks — not just the merge step, but also
// generateResources/generateResValues, which run earlier and read the same
// directory. Every one of those needs an explicit dependency on
// generateLocalesList, or Gradle's task-validation flags an implicit
// dependency (as of 8.x this fails the build, not just a warning). Matched
// by class name (rather than fixed task names like "mergeDebugResources")
// so this holds for every build variant without listing them here
// individually.
tasks.matching {
    val n = it.javaClass.name
    n.contains("MergeResources") || n.contains("GenerateResources") || n.contains("GenerateResValues")
}.configureEach {
    dependsOn(generateLocalesList)
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material)
    implementation(libs.androidx.startup.runtime)
    implementation(libs.androidx.interpolator)
    implementation(libs.androidx.splashscreen)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.gson)
}