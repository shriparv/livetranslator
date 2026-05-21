package com.example.ui

import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.network.TranslationResult

@Composable
fun CameraPreviewView(
    controller: LifecycleCameraController,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                this.controller = controller
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = modifier
    )
}

@Composable
fun CornerAccentsOverlay(
    modifier: Modifier = Modifier
) {
    val accentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        val strokeWidth = 3.dp.toPx()
        val accentLength = 24.dp.toPx()

        // Top Left Corner Bracket
        drawLine(accentColor, Offset(0f, 0f), Offset(accentLength, 0f), strokeWidth)
        drawLine(accentColor, Offset(0f, 0f), Offset(0f, accentLength), strokeWidth)

        // Top Right Corner Bracket
        drawLine(accentColor, Offset(size.width, 0f), Offset(size.width - accentLength, 0f), strokeWidth)
        drawLine(accentColor, Offset(size.width, 0f), Offset(size.width, accentLength), strokeWidth)

        // Bottom Left Corner Bracket
        drawLine(accentColor, Offset(0f, size.height), Offset(accentLength, size.height), strokeWidth)
        drawLine(accentColor, Offset(0f, size.height), Offset(0f, size.height - accentLength), strokeWidth)

        // Bottom Right Corner Bracket
        drawLine(accentColor, Offset(size.width, size.height), Offset(size.width - accentLength, size.height), strokeWidth)
        drawLine(accentColor, Offset(size.width, size.height), Offset(size.width, size.height - accentLength), strokeWidth)
    }
}

@Composable
fun LaserScannerOverlay(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
    val yFraction by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scannerY"
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val scannerHeight = maxHeight
        val offsetY = scannerHeight * yFraction

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(offsetY)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                    )
                )
        )

        Spacer(
            modifier = Modifier
                .offset(y = offsetY - 2.dp)
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary,
                            MaterialTheme.colorScheme.primary,
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

@Composable
fun TranslationArOverlay(
    translationResult: TranslationResult,
    onPhraseClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val phrases = translationResult.phrases ?: return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        phrases.forEachIndexed { index, phrase ->
            // Convert coordinate percentages to fractions [0.0, 1.0]
            val xFraction = (phrase.x.coerceIn(0, 100)) / 100f
            val yFraction = (phrase.y.coerceIn(0, 100)) / 100f
            val wFraction = (phrase.w.coerceIn(8, 100)) / 100f
            val hFraction = (phrase.h.coerceIn(8, 100)) / 100f

            // Calculate bounding box dimensions based on actual viewport dimensions
            val boxWidth = maxWidth * wFraction
            val boxHeight = maxHeight * hFraction
            val boxLeft = maxWidth * xFraction
            val boxTop = maxHeight * yFraction

            Column(
                modifier = Modifier
                    .offset(x = boxLeft, y = boxTop)
                    .width(boxWidth)
                    .heightIn(min = 36.dp, max = boxHeight)
                    .padding(2.dp)
                    .clickable { onPhraseClick(phrase.translated) }
                    .testTag("phrase_overlay_$index"),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Original Source Text Tag Header (White pill as in Design HTML)
                if (phrase.original.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(2.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = phrase.original.uppercase(),
                            color = Color.Black,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Main Translated text with a thick left Accent Border
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Thick Left Border Indicator matching design theme
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .heightIn(min = 24.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = phrase.translated,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start,
                        lineHeight = 12.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                            .weight(1f)
                    )
                }
            }
        }
    }
}

