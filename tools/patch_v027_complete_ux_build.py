#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# MainActivity: Kotlin TextView has setLineSpacing(), not a mutable lineSpacing property.
p = ROOT / 'app/src/main/java/com/ccwu/orbitime/MainActivity.kt'
s = p.read_text(encoding='utf-8')
s = s.replace('textSize = 15f; lineSpacing = dp(2).toFloat(); setPadding(0, 0, 0, dp(8))',
              'textSize = 15f; setLineSpacing(dp(2).toFloat(), 1f); setPadding(0, 0, 0, dp(8))')
p.write_text(s, encoding='utf-8')

# Speech: recover one unambiguously installed executable model that old builds forgot to enable.
p = ROOT / 'app/src/main/java/com/ccwu/orbitime/ImeSpeechController.kt'
s = p.read_text(encoding='utf-8')
old = '''    private fun executablePack(type: OrbitModelPackType): ModelPackManager.InstalledPack? {
        val pack = modelPacks.enabledPack(type) ?: return null
        return pack.takeIf { it.enabled && it.runtimeStatus.executable }
    }'''
new = '''    private fun executablePack(type: OrbitModelPackType): ModelPackManager.InstalledPack? {
        modelPacks.enabledPack(type)?.let { pack ->
            if (pack.enabled && pack.runtimeStatus.executable) return pack
        }
        // v0.27 migration: older builds could install a valid pack without setting a
        // preferred pack. Recover only when the choice is unambiguous.
        val executable = modelPacks.listInstalled().filter {
            it.manifest.type == type && it.runtimeStatus.executable
        }
        if (executable.size != 1) return null
        val candidate = executable.single()
        val enabled = modelPacks.setEnabled(candidate.manifest.packId, true)
        if (!enabled.success) return null
        return modelPacks.enabledPack(type)?.takeIf { it.enabled && it.runtimeStatus.executable }
    }'''
if old not in s and 'executable.size != 1' not in s:
    raise SystemExit('executablePack block not found')
if old in s:
    s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')

# Gradle: preserve normal assembleDebug and add a parallel independent test build type.
p = ROOT / 'app/build.gradle.kts'
s = p.read_text(encoding='utf-8')
if 'create("bundledModelsDebug")' not in s:
    needle = '''    buildFeatures { buildConfig = true }

    compileOptions {'''
    repl = '''    buildFeatures { buildConfig = true }

    buildTypes {
        getByName("debug")
        create("bundledModelsDebug") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".bundledmodels"
            versionNameSuffix = "-bundled-models-test"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("debug")
        }
    }

    compileOptions {'''
    if needle not in s:
        raise SystemExit('buildFeatures marker not found')
    s = s.replace(needle, repl, 1)

# Keep the aggregate module non-transitive so it cannot pull JVM/desktop artifacts,
# then add sherpa's official Android AAR coordinate used by the v1.13.8 Android demo.
aggregate = '''    implementation("com.github.k2-fsa:sherpa-onnx:1.13.8") {
        isTransitive = false
    }
'''
android_aar = '''    implementation("com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v1.13.8") {
        isTransitive = false
    }
'''
if aggregate not in s:
    raise SystemExit('pinned non-transitive sherpa aggregate dependency missing')
if 'com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v1.13.8' not in s:
    s = s.replace(aggregate, aggregate + android_aar, 1)
# Remove the previously rejected same-coordinate @aar experiment if it is present.
s = s.replace('''    implementation("com.github.k2-fsa:sherpa-onnx:1.13.8@aar") {
        isTransitive = false
    }
''', '')
p.write_text(s, encoding='utf-8')

# Validator: assert test variant, official Android AAR, and both CI artifacts without weakening any existing gates.
p = ROOT / 'tools/validate_mature_ime_assets.py'
s = p.read_text(encoding='utf-8')
needle = '''    require('com.github.k2-fsa:sherpa-onnx:1.13.8' in gradle, "pinned sherpa-onnx 1.13.8 dependency missing")
'''
repl = '''    require('com.github.k2-fsa:sherpa-onnx:1.13.8' in gradle, "pinned sherpa-onnx 1.13.8 aggregate dependency missing")
    require('com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v1.13.8' in gradle, "official sherpa Android AAR dependency missing")
    require('create("bundledModelsDebug")' in gradle, "bundledModelsDebug build type missing")
    require('applicationIdSuffix = ".bundledmodels"' in gradle, "bundled test applicationId suffix missing")
    require('versionNameSuffix = "-bundled-models-test"' in gradle, "bundled test version suffix missing")
'''
if 'official sherpa Android AAR dependency missing' not in s:
    if 'bundledModelsDebug build type missing' in s:
        old_block = '''    require('com.github.k2-fsa:sherpa-onnx:1.13.8' in gradle, "pinned sherpa-onnx 1.13.8 dependency missing")
    require('create("bundledModelsDebug")' in gradle, "bundledModelsDebug build type missing")
    require('applicationIdSuffix = ".bundledmodels"' in gradle, "bundled test applicationId suffix missing")
    require('versionNameSuffix = "-bundled-models-test"' in gradle, "bundled test version suffix missing")
'''
        if old_block not in s:
            raise SystemExit('existing validator bundled block not found')
        s = s.replace(old_block, repl, 1)
    else:
        if needle not in s:
            raise SystemExit('validator gradle marker not found')
        s = s.replace(needle, repl, 1)
needle = '''    require("orbit-ime-v0.27-debug-apk" in workflow, "v0.27 Actions artifact name missing")
'''
repl = '''    require("orbit-ime-v0.27-debug-apk" in workflow, "v0.27 Actions artifact name missing")
    require("orbit-ime-v0.27-bundled-models-test-apk" in workflow, "v0.27 bundled test artifact name missing")
'''
if 'bundled test artifact name missing' not in s:
    if needle not in s:
        raise SystemExit('validator workflow marker not found')
    s = s.replace(needle, repl, 1)
p.write_text(s, encoding='utf-8')

# CI: build and retain both APKs.
p = ROOT / '.github/workflows/build-apk.yml'
s = p.read_text(encoding='utf-8')
s = s.replace('''      - name: Build debug APK
        run: gradle assembleDebug --no-daemon
''', '''      - name: Build formal and bundled-model test APKs
        run: gradle assembleDebug assembleBundledModelsDebug --no-daemon
''')
if 'orbit-ime-v0.27-bundled-models-test-apk' not in s:
    s += '''
      - name: Upload bundled-models test APK
        uses: actions/upload-artifact@v4
        with:
          name: orbit-ime-v0.27-bundled-models-test-apk
          path: app/build/outputs/apk/bundledModelsDebug/app-bundledModelsDebug.apk
'''
p.write_text(s, encoding='utf-8')

print('complete v0.27 UX/build patch applied')
