package com.ccwu.orbitime

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.min

enum class StickerVariant {
    HAPPY,
    LOVE,
    ANGRY,
    SAD,
    SURPRISED,
    SLEEPY,
    OK,
    CONFUSED,
}

/** Adds state-specific decoration on top of the existing pet artwork. */
object StickerOverlayRenderer {
    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        variant: StickerVariant,
        skin: OrbitSkin,
    ) {
        if (width <= 0f || height <= 0f) return
        val size = min(width, height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val cx = width * 0.5f
        val cy = height * 0.5f

        when (variant) {
            StickerVariant.HAPPY,
            StickerVariant.LOVE,
            StickerVariant.ANGRY -> Unit // already rendered by PetAvatarRenderer

            StickerVariant.SAD -> {
                paint.style = Paint.Style.FILL
                paint.color = Color.rgb(75, 160, 255)
                canvas.drawOval(RectF(cx - size * 0.34f, cy + size * 0.08f, cx - size * 0.26f, cy + size * 0.25f), paint)
                canvas.drawOval(RectF(cx + size * 0.26f, cy + size * 0.08f, cx + size * 0.34f, cy + size * 0.25f), paint)
            }

            StickerVariant.SURPRISED -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = size * 0.035f
                paint.color = skin.warningColor
                canvas.drawLine(cx + size * 0.30f, cy - size * 0.42f, cx + size * 0.33f, cy - size * 0.22f, paint)
                canvas.drawCircle(cx + size * 0.34f, cy - size * 0.14f, size * 0.018f, paint)
                canvas.drawLine(cx - size * 0.34f, cy - size * 0.38f, cx - size * 0.38f, cy - size * 0.21f, paint)
            }

            StickerVariant.SLEEPY -> {
                paint.style = Paint.Style.FILL
                paint.color = skin.accentColor
                paint.textAlign = Paint.Align.CENTER
                paint.isFakeBoldText = true
                paint.textSize = size * 0.18f
                canvas.drawText("Z", cx + size * 0.31f, cy - size * 0.30f, paint)
                paint.textSize = size * 0.12f
                canvas.drawText("z", cx + size * 0.42f, cy - size * 0.43f, paint)
            }

            StickerVariant.OK -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = size * 0.045f
                paint.color = Color.rgb(68, 200, 120)
                canvas.drawLine(cx - size * 0.38f, cy + size * 0.32f, cx - size * 0.28f, cy + size * 0.42f, paint)
                canvas.drawLine(cx - size * 0.28f, cy + size * 0.42f, cx - size * 0.08f, cy + size * 0.20f, paint)
            }

            StickerVariant.CONFUSED -> {
                paint.style = Paint.Style.FILL
                paint.color = skin.accentColor
                paint.textAlign = Paint.Align.CENTER
                paint.isFakeBoldText = true
                paint.textSize = size * 0.20f
                canvas.drawText("?", cx + size * 0.35f, cy - size * 0.26f, paint)
                paint.textSize = size * 0.13f
                canvas.drawText("?", cx - size * 0.38f, cy - size * 0.34f, paint)
            }
        }
    }
}
