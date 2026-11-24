package xavier.pedro.suavez.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withClip
import xavier.pedro.suavez.R

class SegmentedQuotaBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var doneFraction: Float = 0f
    private var pendingFraction: Float = 0f
    private var useAmberGradient: Boolean = false

    private val trackColor = ContextCompat.getColor(context, R.color.nevoa)
    private val doneStartColor = ContextCompat.getColor(context, R.color.verde_fundo)
    private val doneEndColor = ContextCompat.getColor(context, R.color.verde_casa)
    private val amberStartColor = ContextCompat.getColor(context, R.color.ambar_vez)
    private val amberEndColor = ContextCompat.getColor(context, R.color.ambar_vez_claro)
    private val pendingColor = ColorUtils.setAlphaComponent(
        ContextCompat.getColor(context, R.color.ambar_vez),
        115,
    )

    private var tealShader: Shader? = null
    private var amberShader: Shader? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = trackColor
        style = Paint.Style.FILL
    }
    private val donePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val pendingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pendingColor
        style = Paint.Style.FILL
    }

    private val boundsRect = RectF()
    private val clipPath = Path()

    @JvmOverloads
    fun setProgress(done: Float, pending: Float = 0f, useAmberGradient: Boolean = false) {
        val clampedDone = done.coerceIn(0f, 1f)
        val clampedPending = pending.coerceIn(0f, 1f - clampedDone)
        if (doneFraction != clampedDone ||
            pendingFraction != clampedPending ||
            this.useAmberGradient != useAmberGradient
        ) {
            doneFraction = clampedDone
            pendingFraction = clampedPending
            this.useAmberGradient = useAmberGradient
            donePaint.shader = if (useAmberGradient) amberShader else tealShader
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        boundsRect.set(0f, 0f, w.toFloat(), h.toFloat())
        val radius = h / 2f
        clipPath.reset()
        clipPath.addRoundRect(boundsRect, radius, radius, Path.Direction.CW)
        if (w > 0) {
            tealShader = LinearGradient(
                0f,
                0f,
                w.toFloat(),
                0f,
                doneStartColor,
                doneEndColor,
                Shader.TileMode.CLAMP,
            )
            amberShader = LinearGradient(
                0f,
                0f,
                w.toFloat(),
                0f,
                amberStartColor,
                amberEndColor,
                Shader.TileMode.CLAMP,
            )
        } else {
            tealShader = null
            amberShader = null
        }
        donePaint.shader = if (useAmberGradient) amberShader else tealShader
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val radius = h / 2f
        canvas.drawRoundRect(boundsRect, radius, radius, trackPaint)

        canvas.withClip(clipPath) {
            val doneWidth = w * doneFraction
            if (doneWidth > 0.5f) {
                drawRect(0f, 0f, doneWidth, h, donePaint)
            }
            val pendingWidth = w * pendingFraction
            if (pendingWidth > 0.5f) {
                drawRect(doneWidth, 0f, doneWidth + pendingWidth, h, pendingPaint)
            }
        }
    }
}
