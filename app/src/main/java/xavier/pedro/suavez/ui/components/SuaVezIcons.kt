package xavier.pedro.suavez.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import xavier.pedro.suavez.ui.state.TaskIconHint
import xavier.pedro.suavez.ui.theme.DisplayFontFamily
import xavier.pedro.suavez.ui.theme.SuaVezTheme

enum class SuaVezIconType {
    TASKS,
    SLIDERS,
    HOME,
    USER,
    COPY,
    SHARE,
    CHECK,
    DROP,
    CART,
    RECEIPT,
    TRASH,
    CALENDAR,
    FRIDGE,
    LEAF,
    PENCIL,
    PLUS,
    BACK,
    ARROW_RIGHT,
    STAR,
    MORE_VERT,
    EYE,
    EYE_OFF,
}

fun TaskIconHint.toSuaVezIcon(): SuaVezIconType = when (this) {
    TaskIconHint.DROP -> SuaVezIconType.DROP
    TaskIconHint.TRASH -> SuaVezIconType.TRASH
    TaskIconHint.CART -> SuaVezIconType.CART
    TaskIconHint.RECEIPT -> SuaVezIconType.RECEIPT
    TaskIconHint.FRIDGE -> SuaVezIconType.FRIDGE
    TaskIconHint.LEAF -> SuaVezIconType.LEAF
    TaskIconHint.CALENDAR -> SuaVezIconType.CALENDAR
}

@Composable
fun SuaVezMark(
    size: Dp = 28.dp,
    modifier: Modifier = Modifier,
) {
    val colors = SuaVezTheme.colors
    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val w = this.size.width
        val h = this.size.height
        val s = minOf(w, h) / 100f

        val strokeWidth = 14f * s
        val radius = 40f * s
        val arcTopLeft = Offset(50f * s - radius, 50f * s - radius)
        val arcSize = Size(radius * 2f, radius * 2f)

        // 3 arcos do anel (2 teal, 1 ambar) exatamente como #mark
        val sweep = 80f
        drawArc(
            color = colors.teal,
            startAngle = -90f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
        drawArc(
            color = colors.teal,
            startAngle = 30f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
        drawArc(
            color = colors.sol,
            startAngle = 150f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )

        // Casa central
        val housePath = Path().apply {
            moveTo(50f * s, 30f * s)
            lineTo(71f * s, 49f * s)
            lineTo(66f * s, 49f * s)
            lineTo(66f * s, 69f * s)
            lineTo(34f * s, 69f * s)
            lineTo(34f * s, 49f * s)
            lineTo(29f * s, 49f * s)
            close()
        }
        drawPath(path = housePath, color = colors.teal)
        drawPath(
            path = housePath,
            color = colors.teal,
            style = Stroke(width = 3f * s, join = StrokeJoin.Round),
        )

        // Recorte do check no centro da casa
        val checkPath = Path().apply {
            moveTo(42f * s, 57f * s)
            lineTo(48f * s, 63f * s)
            lineTo(59f * s, 51f * s)
        }
        drawPath(
            path = checkPath,
            color = Color.Transparent,
            style = Stroke(
                width = 5.5f * s,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
            blendMode = BlendMode.Clear,
        )
    }
}

@Composable
fun SuaVezWordmark(
    markSize: Dp = 28.dp,
    fontSize: TextUnit = 21.sp,
    gap: Dp = 6.dp,
    modifier: Modifier = Modifier,
) {
    val colors = SuaVezTheme.colors
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        SuaVezMark(size = markSize)
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = colors.ink, fontWeight = FontWeight.SemiBold)) {
                    append("sua ")
                }
                withStyle(SpanStyle(color = colors.teal, fontWeight = FontWeight.SemiBold)) {
                    append("vez")
                }
            },
            fontFamily = DisplayFontFamily,
            fontSize = fontSize,
            lineHeight = fontSize,
            letterSpacing = (-0.02).em,
        )
    }
}

@Composable
fun SuaVezIcon(
    type: SuaVezIconType,
    tint: Color,
    size: Dp = 22.dp,
    strokeWidthDp: Dp = 2.dp,
    softFill: Color? = null,
    filled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val u = this.size.width / 24f
        val stroke = Stroke(
            width = (strokeWidthDp.toPx() * (24f / 24f)),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        drawSuaVezVector(type = type, u = u, tint = tint, stroke = stroke, softFill = softFill, filled = filled)
    }
}

private fun DrawScope.drawSuaVezVector(
    type: SuaVezIconType,
    u: Float,
    tint: Color,
    stroke: Stroke,
    softFill: Color?,
    filled: Boolean,
) {
    when (type) {
        SuaVezIconType.TASKS -> {
            if (softFill != null) {
                drawRoundRect(
                    color = softFill,
                    topLeft = Offset(5f * u, 4f * u),
                    size = Size(14f * u, 17f * u),
                    cornerRadius = CornerRadius(2f * u, 2f * u),
                )
            }
            drawRoundRect(
                color = tint,
                topLeft = Offset(5f * u, 4f * u),
                size = Size(14f * u, 17f * u),
                cornerRadius = CornerRadius(2f * u, 2f * u),
                style = stroke,
            )
            val p = Path().apply {
                moveTo(9f * u, 4f * u)
                lineTo(9f * u, 3f * u)
                lineTo(15f * u, 3f * u)
                lineTo(15f * u, 4f * u)
                moveTo(9f * u, 13f * u)
                lineTo(11f * u, 15f * u)
                lineTo(15f * u, 11f * u)
            }
            drawPath(p, color = tint, style = stroke)
        }

        SuaVezIconType.SLIDERS -> {
            val lines = listOf(
                5f to 4f..10f,
                5f to 14f..20f,
                12f to 4f..6f,
                12f to 10f..20f,
                19f to 4f..14f,
                19f to 18f..20f,
            )
            lines.forEach { (x, range) ->
                drawLine(tint, Offset(x * u, range.start * u), Offset(x * u, range.endInclusive * u), stroke.width, StrokeCap.Round)
            }
            drawLine(tint, Offset(3f * u, 10f * u), Offset(7f * u, 10f * u), stroke.width, StrokeCap.Round)
            drawLine(tint, Offset(10f * u, 6f * u), Offset(14f * u, 6f * u), stroke.width, StrokeCap.Round)
            drawLine(tint, Offset(17f * u, 14f * u), Offset(21f * u, 14f * u), stroke.width, StrokeCap.Round)
        }

        SuaVezIconType.HOME -> {
            val p = Path().apply {
                moveTo(4f * u, 11f * u)
                lineTo(12f * u, 4f * u)
                lineTo(20f * u, 11f * u)
                lineTo(20f * u, 20f * u)
                lineTo(15f * u, 20f * u)
                lineTo(15f * u, 14f * u)
                lineTo(9f * u, 14f * u)
                lineTo(9f * u, 20f * u)
                lineTo(4f * u, 20f * u)
                close()
            }
            if (softFill != null) drawPath(p, color = softFill)
            drawPath(p, color = tint, style = stroke)
        }

        SuaVezIconType.USER -> {
            if (softFill != null) {
                drawCircle(softFill, radius = 4f * u, center = Offset(12f * u, 8f * u))
            }
            drawCircle(tint, radius = 4f * u, center = Offset(12f * u, 8f * u), style = stroke)
            val arc = Path().apply {
                moveTo(4f * u, 21f * u)
                cubicTo(4f * u, 17f * u, 7.5f * u, 15f * u, 12f * u, 15f * u)
                cubicTo(16.5f * u, 15f * u, 20f * u, 17f * u, 20f * u, 21f * u)
            }
            if (softFill != null) drawPath(arc, color = softFill)
            drawPath(arc, color = tint, style = stroke)
        }

        SuaVezIconType.COPY -> {
            drawRoundRect(
                color = tint,
                topLeft = Offset(9f * u, 9f * u),
                size = Size(11f * u, 11f * u),
                cornerRadius = CornerRadius(2f * u, 2f * u),
                style = stroke,
            )
            val p = Path().apply {
                moveTo(5f * u, 15f * u)
                lineTo(5f * u, 6f * u)
                quadraticTo(5f * u, 4f * u, 7f * u, 4f * u)
                lineTo(15f * u, 4f * u)
            }
            drawPath(p, color = tint, style = stroke)
        }

        SuaVezIconType.SHARE -> {
            val p = Path().apply {
                moveTo(12f * u, 15f * u)
                lineTo(12f * u, 3f * u)
                moveTo(7f * u, 8f * u)
                lineTo(12f * u, 3f * u)
                lineTo(17f * u, 8f * u)
                moveTo(5f * u, 13f * u)
                lineTo(5f * u, 19f * u)
                quadraticTo(5f * u, 20f * u, 6f * u, 20f * u)
                lineTo(18f * u, 20f * u)
                quadraticTo(19f * u, 20f * u, 19f * u, 19f * u)
                lineTo(19f * u, 13f * u)
            }
            drawPath(p, color = tint, style = stroke)
        }

        SuaVezIconType.CHECK -> {
            val p = Path().apply {
                moveTo(5f * u, 12.5f * u)
                lineTo(10f * u, 17.5f * u)
                lineTo(19f * u, 7.5f * u)
            }
            drawPath(p, color = tint, style = stroke)
        }

        SuaVezIconType.DROP -> {
            val p = Path().apply {
                moveTo(12f * u, 3f * u)
                cubicTo(15f * u, 6.5f * u, 18f * u, 10f * u, 18f * u, 14f * u)
                cubicTo(18f * u, 17.5f * u, 15.3f * u, 20f * u, 12f * u, 20f * u)
                cubicTo(8.7f * u, 20f * u, 6f * u, 17.5f * u, 6f * u, 14f * u)
                cubicTo(6f * u, 10f * u, 9f * u, 6.5f * u, 12f * u, 3f * u)
                close()
            }
            drawPath(p, color = tint, style = stroke)
        }

        SuaVezIconType.CART -> {
            drawCircle(tint, radius = 1.2f * u, center = Offset(9f * u, 20f * u))
            drawCircle(tint, radius = 1.2f * u, center = Offset(18f * u, 20f * u))
            val p = Path().apply {
                moveTo(2f * u, 3f * u)
                lineTo(5f * u, 3f * u)
                lineTo(7.5f * u, 15f * u)
                lineTo(18.5f * u, 15f * u)
                lineTo(21f * u, 7f * u)
                lineTo(6f * u, 7f * u)
            }
            drawPath(p, color = tint, style = stroke)
        }

        SuaVezIconType.RECEIPT -> {
            val p = Path().apply {
                moveTo(6f * u, 3f * u)
                lineTo(18f * u, 3f * u)
                lineTo(18f * u, 21f * u)
                lineTo(15f * u, 19f * u)
                lineTo(12f * u, 21f * u)
                lineTo(9f * u, 19f * u)
                lineTo(6f * u, 21f * u)
                close()
                moveTo(9f * u, 8f * u)
                lineTo(15f * u, 8f * u)
                moveTo(9f * u, 12f * u)
                lineTo(15f * u, 12f * u)
            }
            drawPath(p, color = tint, style = stroke)
        }

        SuaVezIconType.TRASH -> {
            val p = Path().apply {
                moveTo(4f * u, 7f * u)
                lineTo(20f * u, 7f * u)
                moveTo(9f * u, 7f * u)
                lineTo(9f * u, 4f * u)
                lineTo(15f * u, 4f * u)
                lineTo(15f * u, 7f * u)
                moveTo(6f * u, 7f * u)
                lineTo(7f * u, 20f * u)
                lineTo(17f * u, 20f * u)
                lineTo(18f * u, 7f * u)
            }
            drawPath(p, color = tint, style = stroke)
        }

        SuaVezIconType.CALENDAR -> {
            drawRoundRect(
                color = tint,
                topLeft = Offset(4f * u, 5f * u),
                size = Size(16f * u, 16f * u),
                cornerRadius = CornerRadius(2f * u, 2f * u),
                style = stroke,
            )
            drawLine(tint, Offset(4f * u, 10f * u), Offset(20f * u, 10f * u), stroke.width, StrokeCap.Round)
            drawLine(tint, Offset(8f * u, 3f * u), Offset(8f * u, 7f * u), stroke.width, StrokeCap.Round)
            drawLine(tint, Offset(16f * u, 3f * u), Offset(16f * u, 7f * u), stroke.width, StrokeCap.Round)
        }

        SuaVezIconType.FRIDGE -> {
            drawRoundRect(
                color = tint,
                topLeft = Offset(6f * u, 3f * u),
                size = Size(12f * u, 18f * u),
                cornerRadius = CornerRadius(2f * u, 2f * u),
                style = stroke,
            )
            drawLine(tint, Offset(6f * u, 11f * u), Offset(18f * u, 11f * u), stroke.width, StrokeCap.Round)
            drawLine(tint, Offset(9f * u, 7f * u), Offset(9f * u, 9f * u), stroke.width, StrokeCap.Round)
            drawLine(tint, Offset(9f * u, 14f * u), Offset(9f * u, 17f * u), stroke.width, StrokeCap.Round)
        }

        SuaVezIconType.LEAF -> {
            val p = Path().apply {
                moveTo(5f * u, 19f * u)
                cubicTo(5f * u, 10f * u, 10f * u, 5f * u, 20f * u, 5f * u)
                cubicTo(20f * u, 15f * u, 15f * u, 20f * u, 6f * u, 20f * u)
                close()
                moveTo(5f * u, 19f * u)
                quadraticTo(9f * u, 13f * u, 15f * u, 9f * u)
            }
            drawPath(p, color = tint, style = stroke)
        }

        SuaVezIconType.PENCIL -> {
            val p = Path().apply {
                moveTo(4f * u, 20f * u)
                lineTo(8f * u, 20f * u)
                lineTo(19f * u, 9f * u)
                lineTo(15f * u, 5f * u)
                lineTo(4f * u, 16f * u)
                close()
                moveTo(13.5f * u, 6.5f * u)
                lineTo(17.5f * u, 10.5f * u)
            }
            drawPath(p, color = tint, style = stroke)
        }

        SuaVezIconType.PLUS -> {
            drawLine(tint, Offset(12f * u, 5f * u), Offset(12f * u, 19f * u), stroke.width, StrokeCap.Round)
            drawLine(tint, Offset(5f * u, 12f * u), Offset(19f * u, 12f * u), stroke.width, StrokeCap.Round)
        }

        SuaVezIconType.BACK -> {
            val p = Path().apply {
                moveTo(19f * u, 12f * u)
                lineTo(5f * u, 12f * u)
                moveTo(11f * u, 6f * u)
                lineTo(5f * u, 12f * u)
                lineTo(11f * u, 18f * u)
            }
            drawPath(p, color = tint, style = stroke)
        }

        SuaVezIconType.ARROW_RIGHT -> {
            val p = Path().apply {
                moveTo(5f * u, 12f * u)
                lineTo(19f * u, 12f * u)
                moveTo(13f * u, 6f * u)
                lineTo(19f * u, 12f * u)
                lineTo(13f * u, 18f * u)
            }
            drawPath(p, color = tint, style = stroke)
        }

        SuaVezIconType.STAR -> {
            val p = Path().apply {
                moveTo(12f * u, 2.5f * u)
                lineTo(14.9f * u, 8.5f * u)
                lineTo(21.5f * u, 9.4f * u)
                lineTo(16.7f * u, 14f * u)
                lineTo(17.9f * u, 20.5f * u)
                lineTo(12f * u, 17.4f * u)
                lineTo(6.1f * u, 20.5f * u)
                lineTo(7.3f * u, 14f * u)
                lineTo(2.5f * u, 9.4f * u)
                lineTo(9.1f * u, 8.5f * u)
                close()
            }
            if (filled) {
                drawPath(p, color = tint)
            } else {
                drawPath(p, color = tint, style = stroke)
            }
        }

        SuaVezIconType.MORE_VERT -> {
            drawCircle(tint, radius = 1.6f * u, center = Offset(12f * u, 5.5f * u))
            drawCircle(tint, radius = 1.6f * u, center = Offset(12f * u, 12f * u))
            drawCircle(tint, radius = 1.6f * u, center = Offset(12f * u, 18.5f * u))
        }

        SuaVezIconType.EYE -> {
            val p = Path().apply {
                moveTo(2f * u, 12f * u)
                cubicTo(5f * u, 7f * u, 8.5f * u, 5f * u, 12f * u, 5f * u)
                cubicTo(15.5f * u, 5f * u, 19f * u, 7f * u, 22f * u, 12f * u)
                cubicTo(19f * u, 17f * u, 15.5f * u, 19f * u, 12f * u, 19f * u)
                cubicTo(8.5f * u, 19f * u, 5f * u, 17f * u, 2f * u, 12f * u)
                close()
            }
            drawPath(p, color = tint, style = stroke)
            drawCircle(tint, radius = 3f * u, center = Offset(12f * u, 12f * u), style = stroke)
        }

        SuaVezIconType.EYE_OFF -> {
            val p = Path().apply {
                moveTo(2f * u, 12f * u)
                cubicTo(5f * u, 7f * u, 8.5f * u, 5f * u, 12f * u, 5f * u)
                cubicTo(15.5f * u, 5f * u, 19f * u, 7f * u, 22f * u, 12f * u)
                cubicTo(19f * u, 17f * u, 15.5f * u, 19f * u, 12f * u, 19f * u)
                cubicTo(8.5f * u, 19f * u, 5f * u, 17f * u, 2f * u, 12f * u)
                close()
            }
            drawPath(p, color = tint, style = stroke)
            drawLine(tint, Offset(4f * u, 4f * u), Offset(20f * u, 20f * u), stroke.width, StrokeCap.Round)
        }
    }
}

@Composable
fun GoogleLogoIcon(
    size: Dp = 18.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val u = this.size.width / 24f
        val stroke = Stroke(width = 3.6f * u, cap = StrokeCap.Butt)
        val topLeft = Offset(3.5f * u, 3.5f * u)
        val arcSize = Size(17f * u, 17f * u)

        drawArc(Color(0xFFEA4335), startAngle = -140f, sweepAngle = 95f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
        drawArc(Color(0xFFFBBC05), startAngle = 140f, sweepAngle = 80f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
        drawArc(Color(0xFF34A853), startAngle = 45f, sweepAngle = 95f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
        drawArc(Color(0xFF4285F4), startAngle = -5f, sweepAngle = 55f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(12f * u, 12f * u),
            end = Offset(21.5f * u, 12f * u),
            strokeWidth = 3.4f * u,
        )
    }
}
