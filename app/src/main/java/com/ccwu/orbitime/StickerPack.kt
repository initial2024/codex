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
    val fallbackText: String,
)

object StickerPack {
    private val moodMeta = listOf(
        Triple(PetAvatarRenderer.StickerMood.HAPPY, "开心", "😄"),
        Triple(PetAvatarRenderer.StickerMood.LOVE, "喜欢", "🥰"),
        Triple(PetAvatarRenderer.StickerMood.ANGRY, "生气", "😤"),
    )

    val all: List<StickerDefinition> = PetRepository.PetCatalog.all.flatMap { pet ->
        moodMeta.map { (mood, moodName, fallback) ->
            StickerDefinition(
                id = "${pet.id}_${mood.name.lowercase()}",
                label = "${pet.name}·$moodName",
                petId = pet.id,
                mood = mood,
                fallbackText = fallback,
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
        val dir = File(context.cacheDir, "orbit-stickers").apply { mkdirs() }
        val file = File(dir, "${sticker.id}.png")
        if (!file.isFile || file.length() <= 0L) render(context, sticker, file)
        return file
    }

    private fun render(context: Context, sticker: StickerDefinition, target: File) {
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
            skin = SkinManager.current(context),
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
