package com.chronie.homemoney.ui.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.chronie.homemoney.R
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/**
 * A bottom-sheet color picker that lets the user browse and search predefined color swatches.
 *
 * Colors are organised into groups (red, orange, yellow, green, cyan, blue, indigo, purple,
 * pink, brown, gray, special) sourced from [ColorPickerData]. A search field filters colors
 * by name; selecting a color invokes [onColorSelected] with the chosen ARGB integer value
 * and dismisses the sheet.
 *
 * @param show whether the bottom sheet is visible.
 * @param currentColor the currently selected color value (ARGB int), used to show the check mark.
 * @param onColorSelected callback invoked with the newly selected color value.
 * @param onDismiss callback to hide the bottom sheet.
 * @param context Android [Context] for string resources (color names).
 */
@Composable
fun ColorPickerBottomSheet(
    show: Boolean,
    currentColor: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    context: Context
) {
    var searchText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    val colorGroups = remember { getColorGroups() }

    val filteredGroups = remember(searchText) {
        if (searchText.isBlank()) {
            colorGroups
        } else {
            colorGroups.map { group ->
                ColorGroup(
                    nameResId = group.nameResId,
                    colors = group.colors.filter { colorOption ->
                        context.getString(colorOption.nameResId).contains(searchText, ignoreCase = true)
                    }
                )
            }.filter { it.colors.isNotEmpty() }
        }
    }

    WindowBottomSheet(
        show = show,
        title = context.getString(R.string.color_picker_title),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            TextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                label = context.getString(R.string.color_picker_search_hint),
                useLabelAsPlaceholder = true,
                trailingIcon = if (searchText.isNotEmpty()) {
                    {
                        IconButton(onClick = {
                            searchText = ""
                            focusRequester.requestFocus()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = context.getString(R.string.cancel))
                        }
                    }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                    }
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                filteredGroups.forEach { group ->
                    item {
                        ColorGroupSection(
                            groupName = context.getString(group.nameResId),
                            colors = group.colors,
                            currentColor = currentColor,
                            onColorSelected = {
                                onColorSelected(it)
                                onDismiss()
                            },
                            context = context
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorGroupSection(
    groupName: String,
    colors: List<ColorOption>,
    currentColor: Int,
    onColorSelected: (Int) -> Unit,
    context: Context
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = groupName,
            style = MiuixTheme.textStyles.body1,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            colors.forEach { colorOption ->
                ColorItem(
                    colorOption = colorOption,
                    isSelected = colorOption.value == currentColor,
                    onClick = { onColorSelected(colorOption.value) },
                    context = context
                )
            }
        }
    }
}

@Composable
private fun ColorItem(
    colorOption: ColorOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    context: Context
) {
    Column(
        modifier = Modifier
            .width(64.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Surface(
                    shape = CircleShape,
                    color = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.size(56.dp)
                ) {}
            }

            Surface(
                shape = CircleShape,
                color = colorOption.color,
                modifier = Modifier.size(48.dp),
                border = if (colorOption.isDefault) BorderStroke(2.dp, MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.5f)) else null
            ) {}

            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = context.getString(R.string.confirm),
                    tint = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Text(
            text = context.getString(colorOption.nameResId),
            style = MiuixTheme.textStyles.footnote1,
            modifier = Modifier.padding(top = 4.dp),
            maxLines = 1
        )
    }
}
