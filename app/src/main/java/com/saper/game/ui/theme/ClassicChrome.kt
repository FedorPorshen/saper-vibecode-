package com.saper.game.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal val WindowPanel = Color(0xFFECE9D8)
internal val WindowInk = Color(0xFF18365F)

internal fun Modifier.classicBevel(inset: Boolean = false): Modifier = drawWithContent {
    drawContent()
    val shadow = Color(0xFF777466)
    val top = if (inset) shadow else Color.White
    val bottom = if (inset) Color.White else shadow
    val stroke = 2.dp.toPx()
    val edge = stroke / 2
    drawLine(top, Offset(edge, edge), Offset(size.width - edge, edge), stroke)
    drawLine(top, Offset(edge, edge), Offset(edge, size.height - edge), stroke)
    drawLine(bottom, Offset(edge, size.height - edge), Offset(size.width - edge, size.height - edge), stroke)
    drawLine(bottom, Offset(size.width - edge, edge), Offset(size.width - edge, size.height - edge), stroke)
}

internal fun DrawScope.drawRaisedCellEdges(left: Float, top: Float, dimensions: Size, bevel: Float) {
    val right = left + dimensions.width
    val bottom = top + dimensions.height
    drawPath(Path().apply {
        moveTo(left, bottom)
        lineTo(left, top)
        lineTo(right, top)
        lineTo(right - bevel, top + bevel)
        lineTo(left + bevel, top + bevel)
        lineTo(left + bevel, bottom - bevel)
        close()
    }, Color.White)
    drawPath(Path().apply {
        moveTo(left, bottom)
        lineTo(left + bevel, bottom - bevel)
        lineTo(right - bevel, bottom - bevel)
        lineTo(right - bevel, top + bevel)
        lineTo(right, top)
        lineTo(right, bottom)
        close()
    }, Color(0xFF808080))
}

internal fun Modifier.closedCellSurface(): Modifier = background(Color(0xFFBDBDBD)).drawWithContent {
    drawContent()
    drawRaisedCellEdges(0f, 0f, size, 4.dp.toPx())
}

@Composable
internal fun ClassicTitleBar(title: String, onMenu: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF67A8FF), Color(0xFF185DCB), Color(0xFF0846A4))))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "⚑  $title", Modifier.weight(1f).padding(vertical = 8.dp),
            color = Color.White, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        if (onMenu != null) ClassicButton("Меню", onMenu)
    }
}

@Composable
internal fun ClassicButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.closedCellSurface()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = WindowInk, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
