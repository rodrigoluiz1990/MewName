package com.mewname.app

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mewname.app.domain.AppLanguage

private val NavigationPurple = Color(0xFF7652D9)

internal val HomeNavigationContentHeight = 96.dp

/** Top notch surrounds the bubble button; the base extends to the screen edge. */
private object HomeNavigationShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val corner = with(density) { 22.dp.toPx() }
        val radius = with(density) { 36.dp.toPx() }
        val shoulder = with(density) { 10.dp.toPx() }
        val middle = size.width / 2f
        return Outline.Generic(Path().apply {
            moveTo(corner, 0f)
            lineTo(middle - radius - shoulder, 0f)
            cubicTo(middle - radius, 0f, middle - radius, radius, middle, radius)
            cubicTo(middle + radius, radius, middle + radius, 0f, middle + radius + shoulder, 0f)
            lineTo(size.width - corner, 0f)
            quadraticBezierTo(size.width, 0f, size.width, corner)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            lineTo(0f, corner)
            quadraticBezierTo(0f, 0f, corner, 0f)
            close()
        })
    }
}

@Composable
internal fun HomeGlassNavigation(
    language: AppLanguage,
    bubbleActive: Boolean,
    bubbleDescription: String,
    onBubbleClick: () -> Unit,
    onCalendarClick: () -> Unit,
    onNamesClick: () -> Unit,
    onChatClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    val appearance = LocalAppAppearance.current
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val dockColors = appearance.navigation.map { it.copy(alpha = 1f) }
    Box(Modifier.fillMaxWidth().height(HomeNavigationContentHeight + bottomInset)) {
        Box(Modifier.fillMaxWidth().height(64.dp + bottomInset).offset(y = 32.dp)
            .background(Brush.verticalGradient(dockColors), HomeNavigationShape)
            .border(0.5.dp, appearance.border, HomeNavigationShape))
        Row(Modifier.fillMaxWidth().offset(y = 40.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            NavigationIcon("calendar", lt(language, "Calendário", "Calendar", "Calendario"), onCalendarClick, Modifier.weight(1f))
            NavigationIcon("names", lt(language, "Definir nomes", "Name presets", "Definir nombres"), onNamesClick, Modifier.weight(1f))
            Spacer(Modifier.width(84.dp))
            NavigationIcon("chat", lt(language, "Chat — em breve", "Chat — coming soon", "Chat — próximamente"), onChatClick, Modifier.weight(1f), muted = true)
            NavigationIcon("profile", lt(language, "Perfil", "Profile", "Perfil"), onProfileClick, Modifier.weight(1f))
        }
        Box(Modifier.align(Alignment.TopCenter).size(64.dp)
            .shadow(6.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(if (bubbleActive) Color(0xFF7166C8) else Color.Transparent)
            .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
            .semantics {
                contentDescription = bubbleDescription
                stateDescription = if (bubbleActive) lt(language, "Ativo", "Active", "Activo") else lt(language, "Desativado", "Inactive", "Desactivado")
            }
            .clickable(role = Role.Button, onClick = onBubbleClick), contentAlignment = Alignment.Center) {
            if (bubbleActive) {
                Canvas(Modifier.size(24.dp)) {
                    drawRoundRect(Color.White, Offset(size.width * 0.15f, size.height * 0.15f),
                        Size(size.width * 0.7f, size.height * 0.7f), CornerRadius(2.dp.toPx()))
                }
            } else {
                Image(painterResource(R.drawable.ic_launcher), contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape))
            }
        }
    }
}

@Composable
private fun NavigationIcon(kind: String, description: String, onClick: () -> Unit, modifier: Modifier, muted: Boolean = false) {
    Box(modifier.height(48.dp).clip(CircleShape).semantics { contentDescription = description }
        .clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        NavigationGlyph(kind, if (muted) LocalAppAppearance.current.text.copy(alpha = 0.45f) else LocalAppAppearance.current.text, Modifier.size(21.dp))
    }
}

@Composable
private fun NavigationGlyph(kind: String, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val u = size.width / 24f
        val stroke = Stroke(1.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(x1*u,y1*u), Offset(x2*u,y2*u), 1.6f*u, StrokeCap.Round)
        when (kind) {
            "calendar" -> {
                drawRoundRect(color, Offset(3*u,5*u), Size(18*u,16*u), CornerRadius(2*u), style = stroke)
                line(3f,10f,21f,10f); line(8f,3f,8f,7f); line(16f,3f,16f,7f)
                line(8f,14f,9f,14f); line(14f,14f,15f,14f)
            }
            "names" -> {
                val path = Path().apply { moveTo(4*u,16*u); lineTo(16*u,4*u); lineTo(20*u,8*u); lineTo(8*u,20*u); lineTo(3*u,21*u); close() }
                drawPath(path, color, style = stroke); line(14f,6f,18f,10f)
            }
            "profile" -> {
                drawCircle(color, 4*u, Offset(12*u,7*u), style = stroke)
                drawPath(Path().apply { moveTo(4*u,21*u); cubicTo(4*u,12*u,20*u,12*u,20*u,21*u) }, color, style = stroke)
            }
            "chat" -> {
                drawPath(Path().apply { moveTo(5*u,18*u); cubicTo(-2*u,6*u,12*u,0f,20*u,7*u); cubicTo(27*u,17*u,16*u,23*u,9*u,19*u); lineTo(3*u,22*u); close() }, color, style = stroke)
            }
            "stop", "bubble" -> {
                drawCircle(color, 7*u, Offset(9*u,9*u), style = stroke)
                drawCircle(color.copy(alpha = 0.8f), 7*u, Offset(15*u,15*u), style = stroke)
                if (kind == "stop") drawRoundRect(color, Offset(12*u,12*u), Size(6*u,6*u), CornerRadius(u))
                else drawPath(Path().apply { moveTo(13*u,11*u); lineTo(18*u,15*u); lineTo(13*u,18*u); close() }, color)
            }
        }
    }
}