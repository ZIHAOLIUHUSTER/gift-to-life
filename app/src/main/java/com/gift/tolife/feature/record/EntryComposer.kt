package com.gift.tolife.feature.record

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gift.tolife.core.ui.UiTestTags
import com.gift.tolife.core.ui.component.PaperCard
import com.gift.tolife.core.ui.component.pressScale
import com.gift.tolife.core.ui.theme.ContentPadding
import com.gift.tolife.core.ui.theme.LifeMotion

/**
 * 书写区（2026-10-27 精修）。
 *
 * 目标仍是「一张安静的书写案」，但把「纸」做得更像纸：
 * - 16dp 大圆角 + 极轻投影，聚焦时投影和主色边一起出现，不闪；
 * - 正文与动作之间一条发丝线，像稿纸的横线；
 * - 保存收成墨绿胶囊，按下有轻微回弹与一次轻触感。
 */
@Composable
fun EntryComposer(
    value: String,
    onValueChange: (String) -> Unit,
    pendingImageUri: Uri?,
    isSaving: Boolean,
    onSave: () -> Unit,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    var focused by remember { mutableStateOf(false) }
    val parsed = remember(value) { EntryInputParser.parse(value) }
    val canSave = !isSaving && parsed.canSave(pendingImageUri != null)
    val haptic = LocalHapticFeedback.current
    val saveInteraction = remember { MutableInteractionSource() }

    val borderColor by animateColorAsState(
        targetValue = if (focused) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        },
        animationSpec = tween(LifeMotion.FAST),
        label = "composerBorder",
    )
    val elevation by animateDpAsState(
        targetValue = if (focused) 3.dp else 1.dp,
        animationSpec = tween(LifeMotion.FAST),
        label = "composerElevation",
    )

    PaperCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = elevation,
        borderAlpha = 0.6f,
        borderColor = borderColor,
    ) {
        Column(
            modifier = Modifier.padding(
                start = ContentPadding,
                end = ContentPadding,
                top = 15.dp,
                bottom = 12.dp,
            )
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp, max = 200.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .focusRequester(focusRequester)
                    .testTag(UiTestTags.COMPOSER_INPUT),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = "此刻，想到什么？",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.66f),
                            )
                        }
                        innerTextField()
                    }
                },
            )

            if (pendingImageUri != null) {
                Spacer(Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model = pendingImageUri,
                        contentDescription = "选中的图片",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    // 关闭按钮：28dp 视觉 + 48dp 触控区，且带 scrim，避免白图标压在浅色照片上看不见
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(48.dp)
                            .clickable(onClick = onClearImage),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.42f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "移除图片",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onPickImage)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = if (pendingImageUri == null) "图片" else "更换图片",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSave()
                    },
                    enabled = canSave,
                    interactionSource = saveInteraction,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .pressScale(saveInteraction, pressedScale = 0.96f)
                        .testTag(UiTestTags.COMPOSER_SAVE),
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        disabledElevation = 0.dp,
                    ),
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(parsed.mode.saveLabel)
                    }
                }
            }
        }
    }
}
