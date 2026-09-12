package com.ccwu.orbitime

import org.json.JSONObject

enum class OrbitModelPackType(val wireValue: String) {
    TRANSLATION("translation"),
    ASR("asr"),
    TTS("tts"),
    VOICE_CLONE("voice_clone");

    companion object {
        fun fromWire(raw: String): OrbitModelPackType = values().firstOrNull { it.wireValue == raw }
            ?: throw IllegalArgumentException("unsupported pack type: $raw")
    }
}

enum class OrbitModelRuntime(val wireValue: String) {
    ONNX("onnx"),
    GGUF("gguf"),
    SHERPA_ONNX("sherpa_onnx");

    companion object {
        fun fromWire(raw: String): OrbitModelRuntime = values().firstOrNull { it.wireValue == raw }
            ?: throw IllegalArgumentException("unsupported runtime: $raw")
    }
}

data class OrbitModelPackManifest(
    val packFormat: Int,
    val packId: String,
    val displayName: String,
    val version: String,
    val type: OrbitModelPackType,
    val runtime: OrbitModelRuntime,
    val modelName: String,
    val sourceUrl: String,
    val license: String,
    val commercialUse: Boolean,
    val redistribution: String,
    val languages: List<String>,
    val sizeMb: Int,
    val minRamMb: Int,
    val recommendedRamMb: Int,
    val requiresPermissions: List<String>,
    val privacy: String,
    val disclaimerRequired: Boolean,
    val description: String,
    val experimental: Boolean,
    val modelFamily: String,
    val runtimeConfig: Map<String, String>,
) {
    val permissionSummary: String
        get() = if (requiresPermissions.isEmpty()) "无额外运行时权限" else requiresPermissions.joinToString(", ")

    val commercialSummary: String
        get() = if (commercialUse) "清单声明允许商用（仍需遵守许可证）" else "清单声明不可商用/仅研究用途"

    fun runtimeValue(key: String, fallback: String = ""): String = runtimeConfig[key]?.takeIf { it.isNotBlank() } ?: fallback
}

object OrbitModelPackManifestParser {
    private val packIdPattern = Regex("^[a-z0-9][a-z0-9._-]{2,79}$")
    private val languagePattern = Regex("^[A-Za-z0-9_-]{2,24}$")
    private val runtimeKeyPattern = Regex("^[a-z0-9_]{1,64}$")

    fun parse(rawJson: String): OrbitModelPackManifest {
        val json = JSONObject(rawJson)
        val manifest = OrbitModelPackManifest(
            packFormat = json.getInt("pack_format"),
            packId = json.getString("pack_id").trim(),
            displayName = json.getString("display_name").trim(),
            version = json.getString("version").trim(),
            type = OrbitModelPackType.fromWire(json.getString("type").trim()),
            runtime = OrbitModelRuntime.fromWire(json.getString("runtime").trim()),
            modelName = json.getString("model_name").trim(),
            sourceUrl = json.getString("source_url").trim(),
            license = json.getString("license").trim(),
            commercialUse = json.getBoolean("commercial_use"),
            redistribution = json.getString("redistribution").trim().lowercase(),
            languages = json.getJSONArray("languages").let { array ->
                buildList {
                    for (index in 0 until array.length()) add(array.getString(index).trim())
                }
            },
            sizeMb = json.optInt("size_mb", 0),
            minRamMb = json.optInt("min_ram_mb", 0),
            recommendedRamMb = json.optInt("recommended_ram_mb", 0),
            requiresPermissions = json.optJSONArray("requires_permissions")?.let { array ->
                buildList {
                    for (index in 0 until array.length()) add(array.getString(index).trim())
                }
            }.orEmpty(),
            privacy = json.getString("privacy").trim().lowercase(),
            disclaimerRequired = json.optBoolean("disclaimer_required", true),
            description = json.optString("description", "").trim(),
            experimental = json.optBoolean("experimental", false),
            modelFamily = json.optString("model_family", "").trim().lowercase(),
            runtimeConfig = json.optJSONObject("runtime_config")?.let(::readRuntimeConfig).orEmpty(),
        )
        validate(manifest)
        return manifest
    }

    private fun readRuntimeConfig(json: JSONObject): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val iterator = json.keys()
        while (iterator.hasNext()) {
            val key = iterator.next().trim().lowercase()
            require(runtimeKeyPattern.matches(key)) { "invalid runtime_config key: $key" }
            val value = json.get(key).toString().trim()
            require(value.length <= 800) { "runtime_config value too long: $key" }
            result[key] = value
        }
        require(result.size <= 64) { "too many runtime_config entries" }
        return result
    }

    private fun validate(manifest: OrbitModelPackManifest) {
        require(manifest.packFormat == CURRENT_PACK_FORMAT) { "unsupported pack_format=${manifest.packFormat}" }
        require(packIdPattern.matches(manifest.packId)) { "invalid pack_id" }
        require(manifest.displayName.isNotBlank() && manifest.displayName.length <= 120) { "invalid display_name" }
        require(manifest.version.isNotBlank() && manifest.version.length <= 64) { "invalid version" }
        require(manifest.modelName.isNotBlank() && manifest.modelName.length <= 180) { "invalid model_name" }
        require(manifest.sourceUrl.startsWith("https://")) { "source_url must use https" }
        require(manifest.sourceUrl.length <= 500) { "source_url too long" }
        require(manifest.license.isNotBlank() && manifest.license.length <= 128) { "invalid license" }
        require(manifest.redistribution in setOf("allowed", "conditional", "prohibited", "unknown")) { "invalid redistribution value" }
        require(manifest.languages.isNotEmpty() && manifest.languages.size <= 64) { "invalid languages" }
        require(manifest.languages.all { languagePattern.matches(it) }) { "invalid language code" }
        require(manifest.sizeMb in 0..16384) { "invalid size_mb" }
        require(manifest.minRamMb in 0..65536) { "invalid min_ram_mb" }
        require(manifest.recommendedRamMb in 0..65536) { "invalid recommended_ram_mb" }
        require(manifest.recommendedRamMb == 0 || manifest.minRamMb == 0 || manifest.recommendedRamMb >= manifest.minRamMb) {
            "recommended_ram_mb must be >= min_ram_mb"
        }
        require(manifest.privacy == "offline_only") { "Orbit accepts offline_only packs only" }
        require(manifest.disclaimerRequired) { "disclaimer_required must be true" }
        require(manifest.requiresPermissions.none { it == "android.permission.INTERNET" }) { "model packs may not require INTERNET" }
        require(manifest.requiresPermissions.size <= 8) { "too many permissions" }
        require(manifest.description.length <= 1200) { "description too long" }
        require(manifest.modelFamily.length <= 80) { "model_family too long" }
    }

    const val CURRENT_PACK_FORMAT = 1
}
