package com.exxhypex.hypexdownloader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.glassPanel
import top.yukonga.miuix.kmp.material.GlassShape
import com.exxhypex.hypexdownloader.ui.theme.SurfaceCard
import com.exxhypex.hypexdownloader.ui.theme.SurfaceCardBorder

/**
 * HYPEX glass surface backed by the Miuix Glass renderer.
 *
 * Miuix uses the supplied backdrop for shader-based glass on API 33+.
 * Its runtime capability check selects this opaque fallback on older Android versions.
 */
@Composable
fun HypexGlassPanel(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    borderColor: Color = SurfaceCardBorder,
    fallbackColor: Color = SurfaceCard.copy(alpha = 0.96f),
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .glassPanel(
                backdrop = backdrop,
                shape = GlassShape(cornerRadius),
                alpha = 0.98f,
                shading = false,
                fallback = Modifier.clip(shape).background(fallbackColor)
            )
            .border(1.dp, borderColor.copy(alpha = 0.62f), shape)
    ) {
        content()
    }
}
