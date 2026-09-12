package com.ccwu.orbitime

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

class StickerPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private var sticker: StickerDefinition? = null
    private var skin: OrbitSkin? = null

    fun bind(sticker: StickerDefinition, skin: OrbitSkin) {
        this.sticker = sticker
        this.skin = skin
        contentDescription = sticker.label
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = sticker ?: return
        val currentSkin = skin ?: return
        PetAvatarRenderer.drawSticker(
            canvas = canvas,
            width = width.toFloat(),
            height = height.toFloat(),
            petId = current.petId,
            mood = current.mood,
            skin = currentSkin,
        )
        StickerOverlayRenderer.draw(
            canvas = canvas,
            width = width.toFloat(),
            height = height.toFloat(),
            variant = current.variant,
            skin = currentSkin,
        )
    }
}
