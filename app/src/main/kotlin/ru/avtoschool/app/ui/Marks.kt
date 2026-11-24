package ru.avtoschool.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

internal enum class Mark { ROAD, HOME, PEOPLE, GROUP, CAR, SETTINGS, SYNC, SEARCH, ADD, BACK, ARROW, CATEGORY, PERSON, CLOSE }

@Composable
internal fun MarkIcon(mark: Mark, modifier: Modifier = Modifier.size(24.dp), color: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val k = size.minDimension / 24f
        val lineStyle = Stroke(1.7f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun circle(x: Float, y: Float, radius: Float) = drawCircle(color, radius * k, Offset(x * k, y * k), style = lineStyle)
        fun lines(vararg points: Float) {
            val path = Path().apply {
                moveTo(points[0] * k, points[1] * k)
                for (i in 2 until points.size step 2) lineTo(points[i] * k, points[i + 1] * k)
            }
            drawPath(path, color, style = lineStyle)
        }
        fun frame(x: Float, y: Float, width: Float, height: Float, r: Float) =
            drawRoundRect(color, Offset(x * k, y * k), Size(width * k, height * k), CornerRadius(r * k), style = lineStyle)
        when (mark) {
            Mark.ROAD -> { lines(4f, 21f, 9f, 3f); lines(20f, 21f, 15f, 3f); lines(12f, 4f, 12f, 7f); lines(12f, 11f, 12f, 14f); lines(12f, 18f, 12f, 21f) }
            Mark.HOME -> { lines(3f, 11f, 12f, 3f, 21f, 11f); lines(5f, 10f, 5f, 21f, 10f, 21f, 10f, 15f, 14f, 15f, 14f, 21f, 19f, 21f, 19f, 10f) }
            Mark.PEOPLE -> { circle(9f, 8f, 3f); lines(3f, 20f, 3f, 17f, 6f, 14f, 12f, 14f, 15f, 17f, 15f, 20f); lines(16f, 5f, 19f, 7f, 19f, 10f, 16f, 12f); lines(18f, 14f, 21f, 17f, 21f, 20f) }
            Mark.GROUP -> { frame(3f, 5f, 18f, 16f, 3f); lines(3f, 10f, 21f, 10f); lines(8f, 3f, 8f, 7f); lines(16f, 3f, 16f, 7f); lines(8f, 15f, 16f, 15f) }
            Mark.CAR -> { frame(3f, 10f, 18f, 8f, 2f); lines(5f, 10f, 7f, 5f, 17f, 5f, 19f, 10f); lines(5f, 18f, 5f, 21f); lines(19f, 18f, 19f, 21f); lines(7f, 14f, 8f, 14f); lines(16f, 14f, 17f, 14f) }
            Mark.SETTINGS -> { lines(4f, 6f, 10f, 6f); lines(14f, 6f, 20f, 6f); circle(12f, 6f, 2f); lines(4f, 12f, 6f, 12f); lines(10f, 12f, 20f, 12f); circle(8f, 12f, 2f); lines(4f, 18f, 14f, 18f); lines(18f, 18f, 20f, 18f); circle(16f, 18f, 2f) }
            Mark.SYNC -> {
                drawArc(color, 205f, 142f, false, Offset(4f * k, 4f * k), Size(16f * k, 16f * k), style = lineStyle)
                drawArc(color, 25f, 142f, false, Offset(4f * k, 4f * k), Size(16f * k, 16f * k), style = lineStyle)
                lines(20f, 4f, 20f, 10f, 14f, 10f); lines(4f, 20f, 4f, 14f, 10f, 14f)
            }
            Mark.SEARCH -> { circle(10f, 10f, 5.5f); lines(14f, 14f, 20f, 20f) }
            Mark.ADD -> { lines(5f, 12f, 19f, 12f); lines(12f, 5f, 12f, 19f) }
            Mark.BACK -> { lines(14f, 5f, 7f, 12f, 14f, 19f); lines(7f, 12f, 21f, 12f) }
            Mark.ARROW -> lines(9f, 5f, 16f, 12f, 9f, 19f)
            Mark.CATEGORY -> { frame(3f, 5f, 18f, 14f, 3f); lines(7f, 15f, 10f, 9f, 13f, 15f); lines(8f, 13f, 12f, 13f); lines(16f, 10f, 18f, 10f); lines(16f, 14f, 18f, 14f) }
            Mark.PERSON -> { circle(12f, 8f, 4f); lines(4f, 21f, 4f, 18f, 8f, 15f, 16f, 15f, 20f, 18f, 20f, 21f) }
            Mark.CLOSE -> { lines(6f, 6f, 18f, 18f); lines(6f, 18f, 18f, 6f) }
        }
    }
}
