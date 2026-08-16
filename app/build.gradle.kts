
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

android {
    namespace = "id.ditzzy.scalara"
    compileSdk = 36 
    
    // disable linter
    lint {
        checkReleaseBuilds = false
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

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material)
    implementation(libs.androidx.startup.runtime)
    implementation(libs.androidx.interpolator)
    implementation(libs.androidx.splashscreen)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
}
