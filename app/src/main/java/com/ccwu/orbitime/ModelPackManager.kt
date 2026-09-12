package com.ccwu.orbitime

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class ModelPackManager(private val context: Context) {
    data class StagedPack internal constructor(
        internal val tempFile: File,
        val manifest: OrbitModelPackManifest,
        val licensePreview: String,
        val noticePreview: String,
        val warnings: List<String>,
        val packedBytes: Long,
        val unpackedBytes: Long,
        val fileCount: Int,
    )

    data class InstalledPack(
        val manifest: OrbitModelPackManifest,
        val directory: File,
        val enabled: Boolean,
        val installedBytes: Long,
        val runtimeStatus: OrbitModelRuntimeRegistry.RuntimeStatus,
    )

    data class OperationResult(val success: Boolean, val message: String)

    private val rootDir = File(context.filesDir, PACK_ROOT).apply { mkdirs() }
    private val stageDir = File(context.cacheDir, PACK_STAGE_ROOT).apply { mkdirs() }
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Copies a user-selected .orbitpack into app-private cache and performs a full
     * checksum/security inspection. The pack is not installed until the user accepts
     * the disclaimer in the settings UI.
     */
    fun stage(uri: Uri): StagedPack {
        require(ProGate.isProUnlocked(context)) { "Pro is required for local model packs" }
        val temp = File(stageDir, "stage-${UUID.randomUUID()}.orbitpack")
        try {
            copyUriToFile(uri, temp)
            val inspected = inspectZip(temp)
            return StagedPack(
                tempFile = temp,
                manifest = inspected.manifest,
                licensePreview = inspected.licensePreview,
                noticePreview = inspected.noticePreview,
                warnings = inspected.warnings,
                packedBytes = temp.length(),
                unpackedBytes = inspected.unpackedBytes,
                fileCount = inspected.fileCount,
            )
        } catch (t: Throwable) {
            temp.delete()
            throw t
        }
    }

    fun discard(staged: StagedPack) {
        staged.tempFile.delete()
    }

    fun install(staged: StagedPack): OperationResult {
        if (!ProGate.isProUnlocked(context)) return OperationResult(false, "需要 Pro 才能安装模型包")
        if (!staged.tempFile.isFile) return OperationResult(false, "暂存模型包已不存在，请重新选择")

        return runCatching {
            // Re-inspect immediately before extraction. This avoids trusting a stale
            // staged object if the cache file was externally corrupted.
            val inspected = inspectZip(staged.tempFile)
            require(inspected.manifest.packId == staged.manifest.packId) { "staged manifest changed" }

            val target = File(rootDir, inspected.manifest.packId)
            val installTemp = File(rootDir, ".install-${inspected.manifest.packId}-${UUID.randomUUID()}")
            require(!installTemp.exists()) { "temporary install path collision" }
            installTemp.mkdirs()
            try {
                extractVerified(staged.tempFile, installTemp)
                File(installTemp, INSTALL_META).writeText(
                    JSONObject()
                        .put("installed_at", System.currentTimeMillis())
                        .put("app_version", BuildConfig.VERSION_NAME)
                        .put("pack_id", inspected.manifest.packId)
                        .toString(2),
                    Charsets.UTF_8,
                )

                val backup = if (target.exists()) File(rootDir, ".backup-${target.name}-${UUID.randomUUID()}") else null
                if (backup != null) {
                    require(target.renameTo(backup)) { "failed to move existing pack to backup" }
                }
                val moved = installTemp.renameTo(target)
                if (!moved) {
                    if (backup != null && !target.exists()) backup.renameTo(target)
                    error("failed to finalize model-pack install")
                }
                backup?.deleteRecursively()
            } catch (t: Throwable) {
                installTemp.deleteRecursively()
                throw t
            }

            staged.tempFile.delete()
            OperationResult(
                true,
                "已安装 ${inspected.manifest.displayName}。v0.22 只完成安全安装/管理；神经推理将在后续运行时版本接入。",
            )
        }.getOrElse { OperationResult(false, "安装失败：${it.message ?: it.javaClass.simpleName}") }
    }

    fun listInstalled(): List<InstalledPack> {
        if (!rootDir.isDirectory) return emptyList()
        return rootDir.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isDirectory && !it.name.startsWith('.') }
            .mapNotNull { directory ->
                runCatching {
                    val manifest = OrbitModelPackManifestParser.parse(File(directory, MANIFEST).readText(Charsets.UTF_8))
                    InstalledPack(
                        manifest = manifest,
                        directory = directory,
                        enabled = enabledPackId(manifest.type) == manifest.packId,
                        installedBytes = directorySize(directory),
                        runtimeStatus = OrbitModelRuntimeRegistry.statusFor(manifest),
                    )
                }.getOrNull()
            }
            .sortedWith(compareBy({ it.manifest.type.ordinal }, { it.manifest.displayName.lowercase() }))
            .toList()
    }

    fun setEnabled(packId: String, enabled: Boolean): OperationResult {
        if (!ProGate.isProUnlocked(context)) return OperationResult(false, "需要 Pro")
        val pack = listInstalled().firstOrNull { it.manifest.packId == packId }
            ?: return OperationResult(false, "模型包不存在")
        val key = enabledKey(pack.manifest.type)
        if (enabled) prefs.edit().putString(key, packId).apply()
        else if (prefs.getString(key, null) == packId) prefs.edit().remove(key).apply()
        val runtime = OrbitModelRuntimeRegistry.statusFor(pack.manifest)
        return OperationResult(
            true,
            if (enabled) "已设为 ${pack.manifest.type.wireValue} 首选包。${runtime.label}" else "已停用 ${pack.manifest.displayName}",
        )
    }

    fun uninstall(packId: String): OperationResult {
        val pack = listInstalled().firstOrNull { it.manifest.packId == packId }
            ?: return OperationResult(false, "模型包不存在")
        if (enabledPackId(pack.manifest.type) == packId) prefs.edit().remove(enabledKey(pack.manifest.type)).apply()
        return if (pack.directory.deleteRecursively()) OperationResult(true, "已卸载 ${pack.manifest.displayName}")
        else OperationResult(false, "卸载失败，请重试")
    }

    fun enabledPack(type: OrbitModelPackType): InstalledPack? {
        val id = enabledPackId(type) ?: return null
        return listInstalled().firstOrNull { it.manifest.packId == id }
    }

    private data class Inspection(
        val manifest: OrbitModelPackManifest,
        val licensePreview: String,
        val noticePreview: String,
        val warnings: List<String>,
        val unpackedBytes: Long,
        val fileCount: Int,
    )

    private fun inspectZip(file: File): Inspection {
        require(file.length() in 1..MAX_PACK_BYTES) { "模型包体积不合法或超过 ${MAX_PACK_BYTES / MB} MB" }
        ZipFile(file).use { zip ->
            val entries = zip.entries().toList()
            require(entries.size <= MAX_ENTRIES) { "模型包文件数超过上限 $MAX_ENTRIES" }
            val fileEntries = entries.filterNot { it.isDirectory }
            val byName = linkedMapOf<String, ZipEntry>()
            fileEntries.forEach { entry ->
                val safe = normalizedEntryName(entry.name)
                require(safe !in byName) { "模型包包含重复路径：$safe" }
                byName[safe] = entry
            }
            REQUIRED_ROOT_FILES.forEach { require(it in byName) { "缺少必要文件：$it" } }

            val manifestRaw = readEntryLimited(zip, byName.getValue(MANIFEST), MAX_MANIFEST_BYTES)
            val manifest = OrbitModelPackManifestParser.parse(manifestRaw)
            val license = readEntryLimited(zip, byName.getValue(LICENSE), MAX_TEXT_BYTES)
            val notice = readEntryLimited(zip, byName.getValue(NOTICE), MAX_TEXT_BYTES)
            require(license.isNotBlank()) { "LICENSE.txt 为空" }
            require(notice.isNotBlank()) { "NOTICE.txt 为空" }
            val checksumsRaw = readEntryLimited(zip, byName.getValue(CHECKSUMS), MAX_CHECKSUM_BYTES)
            val expected = parseChecksums(checksumsRaw)

            var total = 0L
            var count = 0
            fileEntries.forEach { entry ->
                val safe = normalizedEntryName(entry.name)
                if (safe == CHECKSUMS) return@forEach
                val expectedHash = expected[safe] ?: error("checksums.sha256 未覆盖：$safe")
                val result = digestEntry(zip, entry, MAX_ENTRY_BYTES)
                require(result.sha256.equals(expectedHash, ignoreCase = true)) { "SHA-256 校验失败：$safe" }
                total += result.bytes
                require(total <= MAX_UNPACKED_BYTES) { "解包后体积超过上限 ${MAX_UNPACKED_BYTES / MB} MB" }
                count++
            }
            val actualPaths = fileEntries.map { normalizedEntryName(it.name) }.filterNot { it == CHECKSUMS }.toSet()
            require(expected.keys == actualPaths) { "checksums.sha256 存在缺失或多余路径" }

            val warnings = buildList {
                if (!manifest.commercialUse) add("该包声明不可商用/仅研究用途。")
                if (manifest.redistribution != "allowed") add("再分发状态：${manifest.redistribution}；请自行核对许可证义务。")
                if (manifest.experimental) add("该包标记为 experimental。")
                if (manifest.requiresPermissions.isNotEmpty()) add("未来运行时可能需要：${manifest.permissionSummary}。v0.22 不会自动申请这些权限。")
                add(OrbitModelRuntimeRegistry.statusFor(manifest).label)
            }

            return Inspection(
                manifest = manifest,
                licensePreview = license.take(PREVIEW_CHARS),
                noticePreview = notice.take(PREVIEW_CHARS),
                warnings = warnings,
                unpackedBytes = total,
                fileCount = count,
            )
        }
    }

    private fun extractVerified(packFile: File, destination: File) {
        ZipFile(packFile).use { zip ->
            val entries = zip.entries().toList()
            val checksumEntry = entries.firstOrNull { normalizedEntryName(it.name) == CHECKSUMS }
                ?: error("missing checksums.sha256")
            val expected = parseChecksums(readEntryLimited(zip, checksumEntry, MAX_CHECKSUM_BYTES))
            var total = 0L
            entries.forEach { entry ->
                val safe = normalizedEntryName(entry.name)
                if (entry.isDirectory) {
                    File(destination, safe).mkdirs()
                    return@forEach
                }
                val output = File(destination, safe)
                output.parentFile?.mkdirs()
                val digest = MessageDigest.getInstance("SHA-256")
                var written = 0L
                zip.getInputStream(entry).use { raw ->
                    BufferedInputStream(raw).use { input ->
                        BufferedOutputStream(FileOutputStream(output)).use { stream ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                written += read
                                total += read
                                require(written <= MAX_ENTRY_BYTES) { "单文件解包体积过大：$safe" }
                                require(total <= MAX_UNPACKED_BYTES) { "解包总量超过安全上限" }
                                stream.write(buffer, 0, read)
                                digest.update(buffer, 0, read)
                            }
                        }
                    }
                }
                if (safe != CHECKSUMS) {
                    val expectedHash = expected[safe] ?: error("checksum missing for $safe")
                    require(digest.toHex().equals(expectedHash, ignoreCase = true)) { "extracted checksum mismatch: $safe" }
                }
            }
        }
    }

    private fun copyUriToFile(uri: Uri, target: File) {
        val input = context.contentResolver.openInputStream(uri) ?: error("无法读取所选文件")
        input.use { raw ->
            BufferedInputStream(raw).use { source ->
                BufferedOutputStream(FileOutputStream(target)).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_PACK_BYTES) { "模型包超过 ${MAX_PACK_BYTES / MB} MB" }
                        output.write(buffer, 0, read)
                    }
                }
            }
        }
        require(target.length() > 0L) { "所选模型包为空" }
    }

    private data class DigestResult(val sha256: String, val bytes: Long)

    private fun digestEntry(zip: ZipFile, entry: ZipEntry, limit: Long): DigestResult {
        val digest = MessageDigest.getInstance("SHA-256")
        var count = 0L
        zip.getInputStream(entry).use { raw ->
            BufferedInputStream(raw).use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    count += read
                    require(count <= limit) { "zip entry too large: ${entry.name}" }
                    digest.update(buffer, 0, read)
                }
            }
        }
        return DigestResult(digest.toHex(), count)
    }

    private fun readEntryLimited(zip: ZipFile, entry: ZipEntry, limit: Long): String {
        val bytes = ArrayList<Byte>()
        var count = 0L
        zip.getInputStream(entry).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                count += read
                require(count <= limit) { "metadata entry too large: ${entry.name}" }
                for (index in 0 until read) bytes.add(buffer[index])
            }
        }
        return ByteArray(bytes.size) { bytes[it] }.toString(Charsets.UTF_8)
    }

    private fun parseChecksums(raw: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        raw.lineSequence().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith('#') }.forEach { line ->
            val match = CHECKSUM_LINE.matchEntire(line) ?: error("invalid checksum line")
            val hash = match.groupValues[1].lowercase()
            val path = normalizedEntryName(match.groupValues[2].trim().removePrefix("*"))
            require(path != CHECKSUMS) { "checksums.sha256 cannot checksum itself" }
            require(path !in result) { "duplicate checksum path: $path" }
            result[path] = hash
        }
        require(result.isNotEmpty()) { "checksums.sha256 is empty" }
        return result
    }

    private fun normalizedEntryName(raw: String): String {
        val normalized = raw.replace('\\', '/').trimStart('/')
        require(normalized.isNotBlank()) { "empty zip path" }
        require(!raw.startsWith('/') && !raw.startsWith('\\')) { "absolute zip path is not allowed" }
        require(!normalized.contains(':')) { "zip path with drive/scheme is not allowed" }
        val parts = normalized.split('/')
        require(parts.none { it == ".." || it.isBlank() }) { "unsafe zip path: $raw" }
        require(normalized.length <= 400) { "zip path too long" }
        return normalized
    }

    private fun enabledPackId(type: OrbitModelPackType): String? = prefs.getString(enabledKey(type), null)
    private fun enabledKey(type: OrbitModelPackType): String = "enabled_${type.wireValue}"

    private fun directorySize(directory: File): Long = directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun MessageDigest.toHex(): String = digest().joinToString("") { "%02x".format(it) }

    companion object {
        private const val PACK_ROOT = "orbit-model-packs"
        private const val PACK_STAGE_ROOT = "orbit-model-pack-staging"
        private const val PREFS = "orbit_model_pack_state"
        private const val MANIFEST = "manifest.json"
        private const val LICENSE = "LICENSE.txt"
        private const val NOTICE = "NOTICE.txt"
        private const val CHECKSUMS = "checksums.sha256"
        private const val INSTALL_META = "orbit-install.json"
        private val REQUIRED_ROOT_FILES = setOf(MANIFEST, LICENSE, NOTICE, CHECKSUMS)
        private val CHECKSUM_LINE = Regex("^([0-9A-Fa-f]{64})\\s+(.+)$")
        private const val BUFFER_SIZE = 128 * 1024
        private const val PREVIEW_CHARS = 2200
        private const val MAX_ENTRIES = 1024
        private const val MB = 1024L * 1024L
        private const val MAX_PACK_BYTES = 8L * 1024L * MB
        private const val MAX_UNPACKED_BYTES = 12L * 1024L * MB
        private const val MAX_ENTRY_BYTES = 8L * 1024L * MB
        private const val MAX_MANIFEST_BYTES = 256L * 1024L
        private const val MAX_TEXT_BYTES = 1024L * 1024L
        private const val MAX_CHECKSUM_BYTES = 4L * 1024L * 1024L
    }
}
