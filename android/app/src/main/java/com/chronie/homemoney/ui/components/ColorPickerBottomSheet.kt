package com.chronie.homemoney.ui.components

import android.content.Context
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.chronie.homemoney.R
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerBottomSheet(
    currentColor: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    context: Context
) {
    var searchText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    val coroutineScope = rememberCoroutineScope()

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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = context.getString(R.string.color_picker_title),
                    style = MiuixTheme.textStyles.title3
                )
                IconButton(onClick = { 
                    coroutineScope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                }) {
                    Icon(Icons.Default.Close, contentDescription = context.getString(R.string.cancel))
                }
            }

            TextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                label = context.getString(R.string.color_picker_search_hint),
                useLabelAsPlaceholder = true,
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = context.getString(R.string.common_search))
                },
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
                modifier = Modifier.weight(1f),
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
                                coroutineScope.launch {
                                    sheetState.hide()
                                    onDismiss()
                                }
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
                modifier = Modifier.size(48.dp)
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
