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
        versionCode = 27
        versionName = "0.27.0"
    }

    buildFeatures { buildConfig = true }

    buildTypes {
        getByName("debug")
        create("bundledModelsDebug") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".bundledmodels"
            versionNameSuffix = "-bundled-models-test"
            resValue("string", "app_name", "Orbit IME Test")
            resValue("string", "ime_name", "Orbit IME Test")
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    // The sherpa Android AAR already contains the Android Java/JNI runtime.
    // JitPack's aggregate module also exposes JVM/desktop artifacts; keeping those
    // transitives on Android duplicates the same API classes and native variants.
    implementation("com.github.k2-fsa:sherpa-onnx:1.13.8") {
        isTransitive = false
    }
    implementation("com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v1.13.8") {
        isTransitive = false
    }
}

val orbitSkipMatureImeData = providers.gradleProperty("orbitSkipMatureImeData")
    .map { it.toBoolean() }.orElse(false)
val orbitPython = providers.environmentVariable("ORBIT_PYTHON").orNull
    ?: if (System.getProperty("os.name").lowercase().contains("windows")) "python" else "python3"

val testImeDataPipeline by tasks.registering(Exec::class) {
    group = "orbit ime"
    description = "Run offline tests for the established mature dictionary pipeline"
    workingDir(rootProject.projectDir)
    commandLine(orbitPython, "tools/test_ime_data_pipeline_v020.py")
}

val testV023DataPipeline by tasks.registering(Exec::class) {
    group = "orbit ime"
    description = "Run offline tests for v0.23 domain/frequency/association augmentation"
    workingDir(rootProject.projectDir)
    commandLine(orbitPython, "tools/test_ime_data_pipeline_v023.py")
}

val testModelPackPipeline by tasks.registering(Exec::class) {
    group = "orbit ime"
    description = "Run offline tests for the .orbitpack builder/integrity format"
    workingDir(rootProject.projectDir)
    commandLine(orbitPython, "tools/test_model_pack_pipeline.py")
}

val prepareMatureImeAssets by tasks.registering(Exec::class) {
    group = "orbit ime"
    description = "Download pinned audited base dictionaries and generate compact offline IME assets"
    workingDir(rootProject.projectDir)
    commandLine(orbitPython, "tools/prepare_mature_ime_data.py", "--output", "app/src/main/assets/ime")
    dependsOn(testImeDataPipeline)
    onlyIf { !orbitSkipMatureImeData.get() }
}

val augmentLicensedImeAssets by tasks.registering(Exec::class) {
    group = "orbit ime"
    description = "Add pinned CC-CEDICT translation/lexicon data and Unicode Emoji 17.0 assets"
    workingDir(rootProject.projectDir)
    commandLine(orbitPython, "tools/augment_v018_data.py", "--output", "app/src/main/assets/ime")
    dependsOn(prepareMatureImeAssets)
    onlyIf { !orbitSkipMatureImeData.get() }
}

val augmentV020ImeAssets by tasks.registering(Exec::class) {
    group = "orbit ime"
    description = "Add broad English normalization, CC-CEDICT four-character boost and software vocabulary"
    workingDir(rootProject.projectDir)
    commandLine(orbitPython, "tools/augment_v020_data.py", "--output", "app/src/main/assets/ime")
    dependsOn(augmentLicensedImeAssets)
    onlyIf { !orbitSkipMatureImeData.get() }
}

val augmentV023ImeAssets by tasks.registering(Exec::class) {
    group = "orbit ime"
    description = "Add THUOCL domains, FrequencyWords usage ranking and precomputed associations"
    workingDir(rootProject.projectDir)
    commandLine(orbitPython, "tools/augment_v023_data.py", "--output", "app/src/main/assets/ime")
    dependsOn(augmentV020ImeAssets, testV023DataPipeline)
    onlyIf { !orbitSkipMatureImeData.get() }
}

val validateMatureImeAssets by tasks.registering(Exec::class) {
    group = "orbit ime"
    description = "Reject incomplete packs, regressions, wrong versions or forbidden capabilities"
    workingDir(rootProject.projectDir)
    commandLine(orbitPython, "tools/validate_mature_ime_assets.py", "--assets", "app/src/main/assets/ime")
    dependsOn(augmentV023ImeAssets, testModelPackPipeline)
    onlyIf { !orbitSkipMatureImeData.get() }
}

tasks.named("preBuild") {
    dependsOn(validateMatureImeAssets, testModelPackPipeline, testV023DataPipeline)
}
