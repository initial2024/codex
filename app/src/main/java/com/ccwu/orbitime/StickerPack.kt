package com.ccwu.orbitime

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException


data class StickerDefinition(
    val id: String,
    val label: String,
    val petId: String,
    val mood: PetAvatarRenderer.StickerMood,
    val variant: StickerVariant,
    val fallbackText: String,
)

object StickerPack {
    private data class MoodMeta(
        val variant: StickerVariant,
        val baseMood: PetAvatarRenderer.StickerMood,
        val name: String,
        val fallback: String,
    )

    private val moodMeta = listOf(
        MoodMeta(StickerVariant.HAPPY, PetAvatarRenderer.StickerMood.HAPPY, "开心", "😄"),
        MoodMeta(StickerVariant.LOVE, PetAvatarRenderer.StickerMood.LOVE, "喜欢", "🥰"),
        MoodMeta(StickerVariant.ANGRY, PetAvatarRenderer.StickerMood.ANGRY, "生气", "😤"),
        MoodMeta(StickerVariant.SAD, PetAvatarRenderer.StickerMood.HAPPY, "难过", "😢"),
        MoodMeta(StickerVariant.SURPRISED, PetAvatarRenderer.StickerMood.HAPPY, "惊讶", "😮"),
        MoodMeta(StickerVariant.SLEEPY, PetAvatarRenderer.StickerMood.HAPPY, "困了", "😴"),
        MoodMeta(StickerVariant.OK, PetAvatarRenderer.StickerMood.HAPPY, "好的", "👍"),
        MoodMeta(StickerVariant.CONFUSED, PetAvatarRenderer.StickerMood.HAPPY, "疑惑", "🤔"),
    )

    val all: List<StickerDefinition> = PetRepository.PetCatalog.all.flatMap { pet ->
        moodMeta.map { meta ->
            StickerDefinition(
                id = "${pet.id}_${meta.variant.name.lowercase()}",
                label = "${pet.name}·${meta.name}",
                // New v0.19 catalog variants reuse one of the eight stable visual
                // archetypes, so every sticker renders instead of falling back to Orbi.
                petId = pet.visualBaseId,
                mood = meta.baseMood,
                variant = meta.variant,
                fallbackText = meta.fallback,
            )
        }
    }

    fun byId(id: String): StickerDefinition? = all.firstOrNull { it.id == id }

    fun orderedFor(currentPetId: String): List<StickerDefinition> =
        (all.filter { it.petId == currentPetId } + all.filter { it.petId != currentPetId }).distinctBy { it.id }

    fun uri(context: Context, sticker: StickerDefinition): Uri =
        Uri.parse("content://${context.packageName}.stickers/sticker/${sticker.id}.png")
}

object StickerRenderer {
    fun fileFor(context: Context, sticker: StickerDefinition): File {
        val skin = SkinManager.current(context)
        val dir = File(context.cacheDir, "orbit-stickers").apply { mkdirs() }
        val file = File(dir, "${sticker.id}-${skin.id}.png")
        if (!file.isFile || file.length() <= 0L) render(sticker, skin, file)
        return file
    }

    private fun render(sticker: StickerDefinition, skin: OrbitSkin, target: File) {
        val size = 384
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(bitmap)
        PetAvatarRenderer.drawSticker(
            canvas = canvas,
            width = size.toFloat(),
            height = size.toFloat(),
            petId = sticker.petId,
            mood = sticker.mood,
            skin = skin,
        )
        StickerOverlayRenderer.draw(
            canvas = canvas,
            width = size.toFloat(),
            height = size.toFloat(),
            variant = sticker.variant,
            skin = skin,
        )
        target.outputStream().use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw IllegalStateException("failed to encode sticker")
            }
        }
        bitmap.recycle()
    }
}

class OrbitStickerProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? =
        if (stickerFor(uri) != null) MIME_PNG else null

    @Throws(FileNotFoundException::class)
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("stickers are read-only")
        val sticker = stickerFor(uri) ?: throw FileNotFoundException("unknown sticker")
        val appContext = context ?: throw FileNotFoundException("provider unavailable")
        val file = try {
            StickerRenderer.fileFor(appContext, sticker)
        } catch (error: Exception) {
            throw FileNotFoundException(error.message ?: "sticker render failed")
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun stickerFor(uri: Uri): StickerDefinition? {
        val raw = uri.lastPathSegment?.removeSuffix(".png") ?: return null
        return StickerPack.byId(raw)
    }

    companion object {
        const val MIME_PNG = "image/png"
    }
}
