package com.ccwu.orbitime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class PetAvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private var profile: PetRepository.PetProfile? = null
    private var skin: OrbitSkin? = null

    fun bind(profile: PetRepository.PetProfile, skin: OrbitSkin) {
        this.profile = profile
        this.skin = skin
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pet = profile ?: return
        val currentSkin = skin ?: return
        PetAvatarRenderer.drawProfile(canvas, width.toFloat(), height.toFloat(), pet, currentSkin)
    }
}

object PetAvatarRenderer {
    enum class StickerMood { HAPPY, LOVE, ANGRY }

    fun drawProfile(
        canvas: Canvas,
        width: Float,
        height: Float,
        profile: PetRepository.PetProfile,
        skin: OrbitSkin,
    ) {
        drawPet(
            canvas = canvas,
            width = width,
            height = height,
            petId = profile.petId,
            stage = profile.stage,
            outfitId = profile.equippedOutfitId,
            skin = skin,
            stickerMood = null,
            framed = true,
        )
    }

    fun drawSticker(
        canvas: Canvas,
        width: Float,
        height: Float,
        petId: String,
        mood: StickerMood,
        skin: OrbitSkin,
    ) {
        drawPet(
            canvas = canvas,
            width = width,
            height = height,
            petId = petId,
            stage = 4,
            outfitId = null,
            skin = skin,
            stickerMood = mood,
            framed = false,
        )
    }

    private fun drawPet(
        canvas: Canvas,
        width: Float,
        height: Float,
        petId: String,
        stage: Int,
        outfitId: String?,
        skin: OrbitSkin,
        stickerMood: StickerMood?,
        framed: Boolean,
    ) {
        if (width <= 0f || height <= 0f) return
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = maxOf(2f, min(width, height) * 0.018f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        if (framed) {
            fill.color = skin.panelAltColor
            stroke.color = skin.borderColor
            val frame = RectF(3f, 3f, width - 3f, height - 3f)
            canvas.drawRoundRect(frame, min(width, height) * 0.12f, min(width, height) * 0.12f, fill)
            canvas.drawRoundRect(frame, min(width, height) * 0.12f, min(width, height) * 0.12f, stroke)
        }

        val centerX = width * 0.5f
        val centerY = height * if (framed) 0.53f else 0.50f
        val base = min(height * 0.66f, width * 0.28f)
        val stageScale = when (stage.coerceIn(1, 4)) {
            1 -> 0.72f
            2 -> 0.84f
            3 -> 0.94f
            else -> 1.0f
        }
        val size = base * stageScale
        val accent = petColor(petId, skin)
        val secondary = blend(accent, skin.accentColor, 0.38f)
        val dark = blend(accent, Color.BLACK, 0.38f)
        val light = blend(accent, Color.WHITE, 0.28f)

        if (stage >= 3) drawAura(canvas, centerX, centerY, size, secondary, stroke)
        if (stage >= 4) drawOrbitBits(canvas, centerX, centerY, size, skin.accentColor, fill)

        when (petId) {
            "kuro" -> drawKuro(canvas, centerX, centerY, size, accent, dark, light, fill, stroke)
            "pica" -> drawPica(canvas, centerX, centerY, size, accent, dark, light, fill, stroke)
            "voxel" -> drawVoxel(canvas, centerX, centerY, size, accent, dark, light, fill, stroke)
            "rune" -> drawRune(canvas, centerX, centerY, size, accent, dark, light, fill, stroke)
            "noctua" -> drawNoctua(canvas, centerX, centerY, size, accent, dark, light, fill, stroke)
            "nami" -> drawNami(canvas, centerX, centerY, size, accent, dark, light, fill, stroke)
            "aegis" -> drawAegis(canvas, centerX, centerY, size, accent, dark, light, fill, stroke)
            else -> drawOrbi(canvas, centerX, centerY, size, accent, dark, light, fill, stroke)
        }

        drawOutfit(canvas, centerX, centerY, size, outfitId, skin, fill, stroke)
        stickerMood?.let { drawStickerMood(canvas, centerX, centerY, size, it, skin, fill, stroke) }
    }

    private fun drawOrbi(canvas: Canvas, cx: Float, cy: Float, s: Float, accent: Int, dark: Int, light: Int, fill: Paint, stroke: Paint) {
        stroke.color = light
        stroke.strokeWidth = s * 0.055f
        canvas.drawOval(RectF(cx - s * 0.72f, cy - s * 0.28f, cx + s * 0.72f, cy + s * 0.28f), stroke)
        fill.color = accent
        canvas.drawCircle(cx, cy, s * 0.42f, fill)
        fill.color = dark
        canvas.drawCircle(cx - s * 0.14f, cy - s * 0.03f, s * 0.055f, fill)
        canvas.drawCircle(cx + s * 0.14f, cy - s * 0.03f, s * 0.055f, fill)
        stroke.color = dark
        stroke.strokeWidth = s * 0.035f
        canvas.drawArc(RectF(cx - s * 0.13f, cy + s * 0.02f, cx + s * 0.13f, cy + s * 0.18f), 10f, 160f, false, stroke)
        fill.color = light
        canvas.drawCircle(cx - s * 0.18f, cy - s * 0.20f, s * 0.06f, fill)
    }

    private fun drawKuro(canvas: Canvas, cx: Float, cy: Float, s: Float, accent: Int, dark: Int, light: Int, fill: Paint, stroke: Paint) {
        val body = Path().apply {
            moveTo(cx, cy - s * 0.55f)
            cubicTo(cx + s * 0.42f, cy - s * 0.20f, cx + s * 0.48f, cy + s * 0.30f, cx, cy + s * 0.48f)
            cubicTo(cx - s * 0.48f, cy + s * 0.30f, cx - s * 0.42f, cy - s * 0.20f, cx, cy - s * 0.55f)
            close()
        }
        fill.color = accent
        canvas.drawPath(body, fill)
        stroke.color = dark
        canvas.drawPath(body, stroke)
        fill.color = light
        canvas.drawCircle(cx - s * 0.14f, cy - s * 0.02f, s * 0.07f, fill)
        canvas.drawCircle(cx + s * 0.14f, cy - s * 0.02f, s * 0.07f, fill)
        fill.color = dark
        canvas.drawCircle(cx - s * 0.14f, cy - s * 0.02f, s * 0.032f, fill)
        canvas.drawCircle(cx + s * 0.14f, cy - s * 0.02f, s * 0.032f, fill)
    }

    private fun drawPica(canvas: Canvas, cx: Float, cy: Float, s: Float, accent: Int, dark: Int, light: Int, fill: Paint, stroke: Paint) {
        val diamond = Path().apply {
            moveTo(cx, cy - s * 0.55f)
            lineTo(cx + s * 0.45f, cy)
            lineTo(cx, cy + s * 0.52f)
            lineTo(cx - s * 0.45f, cy)
            close()
        }
        fill.color = accent
        canvas.drawPath(diamond, fill)
        stroke.color = light
        canvas.drawPath(diamond, stroke)
        stroke.color = dark
        stroke.strokeWidth = s * 0.035f
        canvas.drawLine(cx, cy - s * 0.55f, cx, cy + s * 0.52f, stroke)
        canvas.drawLine(cx - s * 0.45f, cy, cx + s * 0.45f, cy, stroke)
        fill.color = dark
        canvas.drawCircle(cx - s * 0.12f, cy - s * 0.08f, s * 0.045f, fill)
        canvas.drawCircle(cx + s * 0.12f, cy - s * 0.08f, s * 0.045f, fill)
    }

    private fun drawVoxel(canvas: Canvas, cx: Float, cy: Float, s: Float, accent: Int, dark: Int, light: Int, fill: Paint, stroke: Paint) {
        fill.color = accent
        val body = RectF(cx - s * 0.40f, cy - s * 0.30f, cx + s * 0.40f, cy + s * 0.38f)
        canvas.drawRoundRect(body, s * 0.08f, s * 0.08f, fill)
        val ears = Path().apply {
            moveTo(cx - s * 0.36f, cy - s * 0.26f)
            lineTo(cx - s * 0.28f, cy - s * 0.58f)
            lineTo(cx - s * 0.10f, cy - s * 0.30f)
            moveTo(cx + s * 0.36f, cy - s * 0.26f)
            lineTo(cx + s * 0.28f, cy - s * 0.58f)
            lineTo(cx + s * 0.10f, cy - s * 0.30f)
        }
        fill.color = accent
        canvas.drawPath(ears, fill)
        stroke.color = dark
        canvas.drawRoundRect(body, s * 0.08f, s * 0.08f, stroke)
        fill.color = light
        canvas.drawRect(cx - s * 0.20f, cy - s * 0.08f, cx - s * 0.10f, cy + s * 0.02f, fill)
        canvas.drawRect(cx + s * 0.10f, cy - s * 0.08f, cx + s * 0.20f, cy + s * 0.02f, fill)
        fill.color = dark
        canvas.drawRect(cx - s * 0.05f, cy + s * 0.12f, cx + s * 0.05f, cy + s * 0.18f, fill)
    }

    private fun drawRune(canvas: Canvas, cx: Float, cy: Float, s: Float, accent: Int, dark: Int, light: Int, fill: Paint, stroke: Paint) {
        fill.color = accent
        val slab = RectF(cx - s * 0.33f, cy - s * 0.52f, cx + s * 0.33f, cy + s * 0.50f)
        canvas.drawRoundRect(slab, s * 0.10f, s * 0.10f, fill)
        stroke.color = dark
        canvas.drawRoundRect(slab, s * 0.10f, s * 0.10f, stroke)
        stroke.color = light
        stroke.strokeWidth = s * 0.05f
        val rune = Path().apply {
            moveTo(cx, cy - s * 0.28f)
            lineTo(cx - s * 0.13f, cy - s * 0.05f)
            lineTo(cx + s * 0.10f, cy - s * 0.05f)
            lineTo(cx - s * 0.05f, cy + s * 0.25f)
        }
        canvas.drawPath(rune, stroke)
        fill.color = dark
        canvas.drawCircle(cx - s * 0.15f, cy + s * 0.34f, s * 0.035f, fill)
        canvas.drawCircle(cx + s * 0.15f, cy + s * 0.34f, s * 0.035f, fill)
    }

    private fun drawNoctua(canvas: Canvas, cx: Float, cy: Float, s: Float, accent: Int, dark: Int, light: Int, fill: Paint, stroke: Paint) {
        fill.color = accent
        canvas.drawOval(RectF(cx - s * 0.42f, cy - s * 0.48f, cx + s * 0.42f, cy + s * 0.50f), fill)
        stroke.color = dark
        canvas.drawOval(RectF(cx - s * 0.42f, cy - s * 0.48f, cx + s * 0.42f, cy + s * 0.50f), stroke)
        fill.color = light
        canvas.drawCircle(cx - s * 0.16f, cy - s * 0.10f, s * 0.15f, fill)
        canvas.drawCircle(cx + s * 0.16f, cy - s * 0.10f, s * 0.15f, fill)
        fill.color = dark
        canvas.drawCircle(cx - s * 0.16f, cy - s * 0.10f, s * 0.055f, fill)
        canvas.drawCircle(cx + s * 0.16f, cy - s * 0.10f, s * 0.055f, fill)
        val beak = Path().apply {
            moveTo(cx, cy + s * 0.02f)
            lineTo(cx - s * 0.08f, cy + s * 0.16f)
            lineTo(cx + s * 0.08f, cy + s * 0.16f)
            close()
        }
        fill.color = dark
        canvas.drawPath(beak, fill)
    }

    private fun drawNami(canvas: Canvas, cx: Float, cy: Float, s: Float, accent: Int, dark: Int, light: Int, fill: Paint, stroke: Paint) {
        fill.color = accent
        canvas.drawArc(RectF(cx - s * 0.44f, cy - s * 0.50f, cx + s * 0.44f, cy + s * 0.30f), 180f, 180f, true, fill)
        stroke.color = light
        canvas.drawArc(RectF(cx - s * 0.44f, cy - s * 0.50f, cx + s * 0.44f, cy + s * 0.30f), 180f, 180f, true, stroke)
        fill.color = dark
        canvas.drawCircle(cx - s * 0.13f, cy - s * 0.10f, s * 0.045f, fill)
        canvas.drawCircle(cx + s * 0.13f, cy - s * 0.10f, s * 0.045f, fill)
        stroke.color = accent
        stroke.strokeWidth = s * 0.055f
        for (offset in listOf(-0.28f, -0.09f, 0.09f, 0.28f)) {
            val x = cx + s * offset
            val path = Path().apply {
                moveTo(x, cy + s * 0.08f)
                cubicTo(x - s * 0.08f, cy + s * 0.24f, x + s * 0.09f, cy + s * 0.34f, x, cy + s * 0.52f)
            }
            canvas.drawPath(path, stroke)
        }
    }

    private fun drawAegis(canvas: Canvas, cx: Float, cy: Float, s: Float, accent: Int, dark: Int, light: Int, fill: Paint, stroke: Paint) {
        val fox = Path().apply {
            moveTo(cx - s * 0.40f, cy - s * 0.08f)
            lineTo(cx - s * 0.30f, cy - s * 0.54f)
            lineTo(cx - s * 0.08f, cy - s * 0.34f)
            lineTo(cx, cy - s * 0.42f)
            lineTo(cx + s * 0.08f, cy - s * 0.34f)
            lineTo(cx + s * 0.30f, cy - s * 0.54f)
            lineTo(cx + s * 0.40f, cy - s * 0.08f)
            lineTo(cx + s * 0.28f, cy + s * 0.34f)
            lineTo(cx, cy + s * 0.50f)
            lineTo(cx - s * 0.28f, cy + s * 0.34f)
            close()
        }
        fill.color = accent
        canvas.drawPath(fox, fill)
        stroke.color = light
        canvas.drawPath(fox, stroke)
        fill.color = dark
        canvas.drawOval(RectF(cx - s * 0.24f, cy - s * 0.10f, cx - s * 0.08f, cy + s * 0.02f), fill)
        canvas.drawOval(RectF(cx + s * 0.08f, cy - s * 0.10f, cx + s * 0.24f, cy + s * 0.02f), fill)
        canvas.drawCircle(cx, cy + s * 0.18f, s * 0.05f, fill)
    }

    private fun drawAura(canvas: Canvas, cx: Float, cy: Float, s: Float, color: Int, stroke: Paint) {
        stroke.color = withAlpha(color, 105)
        stroke.strokeWidth = s * 0.025f
        canvas.drawCircle(cx, cy, s * 0.62f, stroke)
        canvas.drawCircle(cx, cy, s * 0.72f, stroke)
    }

    private fun drawOrbitBits(canvas: Canvas, cx: Float, cy: Float, s: Float, color: Int, fill: Paint) {
        fill.color = withAlpha(color, 190)
        listOf(-0.55f to -0.40f, 0.58f to -0.24f, -0.52f to 0.42f, 0.50f to 0.46f).forEach { (dx, dy) ->
            canvas.drawCircle(cx + s * dx, cy + s * dy, s * 0.045f, fill)
        }
    }

    private fun drawOutfit(canvas: Canvas, cx: Float, cy: Float, s: Float, outfitId: String?, skin: OrbitSkin, fill: Paint, stroke: Paint) {
        when (outfitId) {
            "halo" -> {
                stroke.color = skin.accentColor
                stroke.strokeWidth = s * 0.045f
                canvas.drawOval(RectF(cx - s * 0.30f, cy - s * 0.72f, cx + s * 0.30f, cy - s * 0.56f), stroke)
            }
            "monocle" -> {
                stroke.color = skin.accentColor
                stroke.strokeWidth = s * 0.035f
                canvas.drawCircle(cx + s * 0.15f, cy - s * 0.05f, s * 0.12f, stroke)
                canvas.drawLine(cx + s * 0.26f, cy + s * 0.02f, cx + s * 0.34f, cy + s * 0.24f, stroke)
            }
            "study_hat" -> {
                fill.color = skin.controlKeyColor
                val cap = Path().apply {
                    moveTo(cx - s * 0.34f, cy - s * 0.56f)
                    lineTo(cx, cy - s * 0.72f)
                    lineTo(cx + s * 0.34f, cy - s * 0.56f)
                    lineTo(cx, cy - s * 0.40f)
                    close()
                }
                canvas.drawPath(cap, fill)
                stroke.color = skin.accentColor
                canvas.drawLine(cx + s * 0.25f, cy - s * 0.55f, cx + s * 0.34f, cy - s * 0.33f, stroke)
            }
            "signal_cloak" -> {
                fill.color = withAlpha(skin.accentColor, 115)
                val cloak = Path().apply {
                    moveTo(cx - s * 0.50f, cy + s * 0.08f)
                    lineTo(cx - s * 0.26f, cy + s * 0.60f)
                    lineTo(cx + s * 0.26f, cy + s * 0.60f)
                    lineTo(cx + s * 0.50f, cy + s * 0.08f)
                    close()
                }
                canvas.drawPath(cloak, fill)
            }
            "bow" -> {
                fill.color = skin.warningColor
                val left = Path().apply { moveTo(cx, cy + s * 0.40f); lineTo(cx - s * 0.24f, cy + s * 0.30f); lineTo(cx - s * 0.24f, cy + s * 0.50f); close() }
                val right = Path().apply { moveTo(cx, cy + s * 0.40f); lineTo(cx + s * 0.24f, cy + s * 0.30f); lineTo(cx + s * 0.24f, cy + s * 0.50f); close() }
                canvas.drawPath(left, fill)
                canvas.drawPath(right, fill)
                canvas.drawCircle(cx, cy + s * 0.40f, s * 0.07f, fill)
            }
            "orbit_ring" -> {
                stroke.color = skin.accentColor
                stroke.strokeWidth = s * 0.05f
                canvas.drawOval(RectF(cx - s * 0.72f, cy - s * 0.30f, cx + s * 0.72f, cy + s * 0.30f), stroke)
            }
            "matrix_aura" -> {
                fill.color = withAlpha(skin.accentColor, 210)
                for (i in -3..3) {
                    canvas.drawRect(cx + i * s * 0.13f - s * 0.018f, cy - s * 0.70f + (i and 1) * s * 0.09f, cx + i * s * 0.13f + s * 0.018f, cy - s * 0.63f + (i and 1) * s * 0.09f, fill)
                }
            }
            "aurora_tail" -> {
                stroke.color = withAlpha(skin.accentColor, 190)
                stroke.strokeWidth = s * 0.08f
                val tail = Path().apply {
                    moveTo(cx + s * 0.35f, cy + s * 0.28f)
                    cubicTo(cx + s * 0.80f, cy + s * 0.12f, cx + s * 0.70f, cy + s * 0.62f, cx + s * 0.48f, cy + s * 0.58f)
                }
                canvas.drawPath(tail, stroke)
            }
        }
    }

    private fun drawStickerMood(canvas: Canvas, cx: Float, cy: Float, s: Float, mood: StickerMood, skin: OrbitSkin, fill: Paint, stroke: Paint) {
        when (mood) {
            StickerMood.HAPPY -> {
                fill.color = skin.accentColor
                listOf(-0.62f to -0.46f, 0.62f to -0.42f, -0.58f to 0.46f).forEach { (dx, dy) ->
                    canvas.drawCircle(cx + s * dx, cy + s * dy, s * 0.055f, fill)
                }
            }
            StickerMood.LOVE -> {
                fill.color = Color.rgb(255, 92, 135)
                drawHeart(canvas, cx - s * 0.58f, cy - s * 0.45f, s * 0.18f, fill)
                drawHeart(canvas, cx + s * 0.55f, cy - s * 0.38f, s * 0.14f, fill)
            }
            StickerMood.ANGRY -> {
                stroke.color = skin.warningColor
                stroke.strokeWidth = s * 0.055f
                canvas.drawLine(cx - s * 0.58f, cy - s * 0.48f, cx - s * 0.38f, cy - s * 0.58f, stroke)
                canvas.drawLine(cx + s * 0.58f, cy - s * 0.48f, cx + s * 0.38f, cy - s * 0.58f, stroke)
                canvas.drawLine(cx - s * 0.55f, cy - s * 0.58f, cx - s * 0.45f, cy - s * 0.36f, stroke)
            }
        }
    }

    private fun drawHeart(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val path = Path().apply {
            moveTo(cx, cy + size * 0.42f)
            cubicTo(cx - size * 0.72f, cy, cx - size * 0.42f, cy - size * 0.58f, cx, cy - size * 0.18f)
            cubicTo(cx + size * 0.42f, cy - size * 0.58f, cx + size * 0.72f, cy, cx, cy + size * 0.42f)
            close()
        }
        canvas.drawPath(path, paint)
    }

    private fun petColor(id: String, skin: OrbitSkin): Int = when (id) {
        "kuro" -> Color.rgb(82, 91, 112)
        "pica" -> Color.rgb(120, 105, 255)
        "voxel" -> Color.rgb(53, 190, 150)
        "rune" -> Color.rgb(110, 126, 152)
        "noctua" -> Color.rgb(112, 92, 160)
        "nami" -> Color.rgb(72, 176, 218)
        "aegis" -> Color.rgb(225, 139, 74)
        else -> skin.accentColor
    }

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun blend(a: Int, b: Int, ratio: Float): Int {
        val r = ratio.coerceIn(0f, 1f)
        val inv = 1f - r
        return Color.rgb(
            (Color.red(a) * inv + Color.red(b) * r).toInt(),
            (Color.green(a) * inv + Color.green(b) * r).toInt(),
            (Color.blue(a) * inv + Color.blue(b) * r).toInt(),
        )
    }
}
