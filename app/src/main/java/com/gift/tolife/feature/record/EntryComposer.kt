package com.gift.tolife.feature.record

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gift.tolife.R
import com.gift.tolife.core.ui.UiTestTags

@Composable
fun EntryComposer(
    value: String,
    onValueChange: (String) -> Unit,
    pendingImageUri: Uri?,
    isSaving: Boolean,
    onSave: () -> Unit,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 200.dp)
                    .focusRequester(focusRequester)
                    .testTag(UiTestTags.COMPOSER_INPUT),
                placeholder = { Text("此刻的想法...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(8.dp)
            )

            if (pendingImageUri != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model = pendingImageUri,
                        contentDescription = "选中的图片",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    IconButton(onClick = onClearImage, modifier = Modifier.align(Alignment.TopEnd)) {
                        Icon(Icons.Filled.Close, contentDescription = "移除图片", tint = MaterialTheme.colorScheme.surface)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val canSave = !isSaving && EntrySavePolicy.canSave(value, pendingImageUri != null)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onPickImage) {
                    Icon(painterResource(R.drawable.ic_add_photo), contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (pendingImageUri == null) "添加图片" else "更换图片")
                }

                Button(
                    onClick = onSave,
                    enabled = canSave,
                    modifier = Modifier.testTag(UiTestTags.COMPOSER_SAVE),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("保存")
                    }
                }
            }
        }
    }
}
