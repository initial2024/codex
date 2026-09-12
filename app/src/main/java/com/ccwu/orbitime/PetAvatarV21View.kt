package com.ccwu.orbitime

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.min
import kotlin.math.sin

/**
 * v0.21 visual polish layer.
 *
 * The proven pet silhouettes stay intact, but the old outfit drawing is suppressed
 * and replaced by a smaller, coherent accessory system with lightweight idle motion.
 */
class PetAvatarV21View @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private var profile: PetRepository.PetProfile? = null
    private var skin: OrbitSkin? = null
    private var phase = 0f
    private var animator: ValueAnimator? = null

    fun bind(profile: PetRepository.PetProfile, skin: OrbitSkin) {
        this.profile = profile
        this.skin = skin
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2800L
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                postInvalidateOnAnimation()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p = profile ?: return
        val s = skin ?: return
        val wave = sin(phase.toDouble() * Math.PI * 2.0).toFloat()
        val bob = wave * min(width, height) * 0.012f
        val scale = 1f + wave * 0.008f

        canvas.save()
        canvas.translate(0f, bob)
        canvas.scale(scale, scale, width * 0.5f, height * 0.55f)
        PetAvatarRenderer.drawProfile(
            canvas,
            width.toFloat(),
            height.toFloat(),
            p.copy(equippedOutfitId = null),
            s,
        )
        PolishedPetOutfits.draw(canvas, width.toFloat(), height.toFloat(), p, s, phase)
        canvas.restore()
    }
}

private object PolishedPetOutfits {
    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        profile: PetRepository.PetProfile,
        skin: OrbitSkin,
        phase: Float,
    ) {
        val id = profile.equippedOutfitId ?: return
        val cx = width * 0.5f
        val cy = height * 0.53f
        val size = min(height * 0.66f, width * 0.28f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val accent = skin.accentColor
        val soft = blend(accent, Color.WHITE, 0.45f)
        val dark = blend(accent, Color.BLACK, 0.34f)

        when (id) {
            "halo" -> halo(canvas, cx, cy - size * 0.51f, size, accent, soft, paint, phase)
            "monocle" -> visor(canvas, cx, cy - size * 0.08f, size, accent, soft, paint)
            "study_hat" -> cap(canvas, cx, cy - size * 0.44f, size, dark, accent, paint)
            "signal_cloak" -> shoulderCape(canvas, cx, cy + size * 0.17f, size, dark, accent, paint)
            "bow" -> ribbon(canvas, cx, cy + size * 0.34f, size, accent, soft, paint)
            "orbit_ring" -> orbit(canvas, cx, cy + size * 0.07f, size, accent, soft, paint, phase)
            "matrix_aura" -> sparks(canvas, cx, cy, size, accent, soft, paint, phase)
            "aurora_tail" -> tail(canvas, cx, cy + size * 0.22f, size, accent, soft, paint)
        }

        catalogBadge(canvas, width, profile.catalogPetId, accent, soft, paint)
    }

    private fun halo(canvas: Canvas, cx: Float, cy: Float, s: Float, accent: Int, soft: Int, p: Paint, phase: Float) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.035f
        p.shader = LinearGradient(cx - s * .4f, cy, cx + s * .4f, cy, soft, accent, Shader.TileMode.CLAMP)
        canvas.drawOval(RectF(cx - s * .38f, cy - s * .10f, cx + s * .38f, cy + s * .10f), p)
        p.shader = null
        p.style = Paint.Style.FILL
        p.color = soft
        val x = cx + sin(phase.toDouble() * Math.PI * 2.0).toFloat() * s * .28f
        canvas.drawCircle(x, cy - s * .02f, s * .035f, p)
    }

    private fun visor(canvas: Canvas, cx: Float, cy: Float, s: Float, accent: Int, soft: Int, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = Color.argb(78, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawRoundRect(RectF(cx - s * .29f, cy - s * .10f, cx + s * .29f, cy + s * .10f), s * .08f, s * .08f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * .024f
        p.color = soft
        canvas.drawRoundRect(RectF(cx - s * .29f, cy - s * .10f, cx + s * .29f, cy + s * .10f), s * .08f, s * .08f, p)
    }

    private fun cap(canvas: Canvas, cx: Float, cy: Float, s: Float, dark: Int, accent: Int, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = dark
        val crown = Path().apply {
            moveTo(cx - s * .26f, cy)
            lineTo(cx, cy - s * .18f)
            lineTo(cx + s * .26f, cy)
            lineTo(cx, cy + s * .13f)
            close()
        }
        canvas.drawPath(crown, p)
        p.color = accent
        canvas.drawRoundRect(RectF(cx - s * .33f, cy + s * .06f, cx + s * .33f, cy + s * .12f), s * .03f, s * .03f, p)
    }

    private fun shoulderCape(canvas: Canvas, cx: Float, cy: Float, s: Float, dark: Int, accent: Int, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = Color.argb(190, Color.red(dark), Color.green(dark), Color.blue(dark))
        val path = Path().apply {
            moveTo(cx - s * .34f, cy - s * .13f)
            quadTo(cx, cy + s * .04f, cx + s * .34f, cy - s * .13f)
            lineTo(cx + s * .20f, cy + s * .25f)
            quadTo(cx, cy + s * .16f, cx - s * .20f, cy + s * .25f)
            close()
        }
        canvas.drawPath(path, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * .022f
        p.color = accent
        canvas.drawPath(path, p)
    }

    private fun ribbon(canvas: Canvas, cx: Float, cy: Float, s: Float, accent: Int, soft: Int, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = accent
        val left = Path().apply { moveTo(cx - s*.04f, cy); lineTo(cx - s*.25f, cy - s*.12f); lineTo(cx - s*.22f, cy + s*.14f); close() }
        val right = Path().apply { moveTo(cx + s*.04f, cy); lineTo(cx + s*.25f, cy - s*.12f); lineTo(cx + s*.22f, cy + s*.14f); close() }
        canvas.drawPath(left, p); canvas.drawPath(right, p)
        p.color = soft
        canvas.drawCircle(cx, cy, s * .075f, p)
    }

    private fun orbit(canvas: Canvas, cx: Float, cy: Float, s: Float, accent: Int, soft: Int, p: Paint, phase: Float) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * .025f
        p.color = Color.argb(175, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawOval(RectF(cx - s*.52f, cy - s*.17f, cx + s*.52f, cy + s*.17f), p)
        p.style = Paint.Style.FILL
        p.color = soft
        val angle = phase.toDouble() * Math.PI * 2.0
        canvas.drawCircle(cx + kotlin.math.cos(angle).toFloat()*s*.48f, cy + kotlin.math.sin(angle).toFloat()*s*.14f, s*.035f, p)
    }

    private fun sparks(canvas: Canvas, cx: Float, cy: Float, s: Float, accent: Int, soft: Int, p: Paint, phase: Float) {
        p.style = Paint.Style.FILL
        val offsets = listOf(-.48f to -.25f, .46f to -.30f, -.42f to .30f, .43f to .25f)
        offsets.forEachIndexed { index, pair ->
            p.color = if ((index + (phase * 4).toInt()) % 2 == 0) accent else soft
            canvas.drawCircle(cx + s*pair.first, cy + s*pair.second, s*(.024f + index*.003f), p)
        }
    }

    private fun tail(canvas: Canvas, cx: Float, cy: Float, s: Float, accent: Int, soft: Int, p: Paint) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * .035f
        p.shader = LinearGradient(cx, cy, cx + s*.68f, cy + s*.28f, accent, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        val path = Path().apply {
            moveTo(cx + s*.22f, cy)
            cubicTo(cx + s*.50f, cy - s*.05f, cx + s*.47f, cy + s*.30f, cx + s*.69f, cy + s*.24f)
        }
        canvas.drawPath(path, p)
        p.shader = null
        p.color = soft
    }

    private fun catalogBadge(canvas: Canvas, width: Float, catalogId: String, accent: Int, soft: Int, p: Paint) {
        val hash = catalogId.hashCode()
        val r = width.coerceAtMost(180f) * .045f
        val x = width - r * 2.1f
        val y = r * 2.1f
        p.style = Paint.Style.FILL
        p.color = if ((hash and 1) == 0) accent else soft
        canvas.drawCircle(x, y, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(1.5f, r * .25f)
        p.color = Color.WHITE
        canvas.drawCircle(x, y, r * .52f, p)
    }

    private fun blend(a: Int, b: Int, ratio: Float): Int {
        val t = ratio.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) * (1f - t) + Color.red(b) * t).toInt(),
            (Color.green(a) * (1f - t) + Color.green(b) * t).toInt(),
            (Color.blue(a) * (1f - t) + Color.blue(b) * t).toInt(),
        )
    }
}
