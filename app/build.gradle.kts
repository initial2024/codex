plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ccwu.orbitime"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ccwu.orbitime"
        minSdk = 26
        targetSdk = 35
        versionCode = 19
        versionName = "0.19.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

// Normal user-test builds must materialize and validate every pinned mature
// offline data pack. Runtime still has no INTERNET permission.
val orbitSkipMatureImeData = providers.gradleProperty("orbitSkipMatureImeData")
    .map { it.toBoolean() }
    .orElse(false)
val orbitPython = providers.environmentVariable("ORBIT_PYTHON").orNull
    ?: if (System.getProperty("os.name").lowercase().contains("windows")) "python" else "python3"

val testImeDataPipeline by tasks.registering(Exec::class) {
    group = "orbit ime"
    description = "Run offline tests for Orbit IME dictionary parsing, licensing and packing"
    workingDir(rootProject.projectDir)
    commandLine(orbitPython, "tools/test_ime_data_pipeline.py")
}

val prepareMatureImeAssets by tasks.registering(Exec::class) {
    group = "orbit ime"
    description = "Download pinned audited base dictionaries and generate compact offline IME assets"
    workingDir(rootProject.projectDir)
    commandLine(
        orbitPython,
        "tools/prepare_mature_ime_data.py",
        "--output",
        "app/src/main/assets/ime",
    )
    dependsOn(testImeDataPipeline)
    onlyIf { !orbitSkipMatureImeData.get() }
}

// Introduced in v0.18 and retained by v0.19: CC-CEDICT lexicon/translation
// plus Unicode Emoji. The task name is historical; the stage is mandatory.
val augmentLicensedImeAssets by tasks.registering(Exec::class) {
    group = "orbit ime"
    description = "Add pinned CC-CEDICT translation/lexicon data and Unicode Emoji 17.0 assets"
    workingDir(rootProject.projectDir)
    commandLine(
        orbitPython,
        "tools/augment_v018_data.py",
        "--output",
        "app/src/main/assets/ime",
    )
    dependsOn(prepareMatureImeAssets)
    onlyIf { !orbitSkipMatureImeData.get() }
}

val validateMatureImeAssets by tasks.registering(Exec::class) {
    group = "orbit ime"
    description = "Reject incomplete mature packs, feature regressions, wrong versions or forbidden capabilities"
    workingDir(rootProject.projectDir)
    commandLine(
        orbitPython,
        "tools/validate_mature_ime_assets.py",
        "--assets",
        "app/src/main/assets/ime",
    )
    dependsOn(augmentLicensedImeAssets)
    onlyIf { !orbitSkipMatureImeData.get() }
}

tasks.named("preBuild") {
    dependsOn(validateMatureImeAssets)
}
