package com.chronie.homemoney.ui.components

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.chronie.homemoney.R
import com.chronie.homemoney.core.common.Language
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/**
 * A bottom-sheet list that lets the user select an app language from all [Language] entries.
 *
 * Displays each language with its English name and native name. A search field filters
 * the list by either name. The currently selected language is highlighted, and tapping
 * a row invokes [onLanguageSelected] and dismisses the sheet.
 *
 * @param show whether the bottom sheet is visible.
 * @param currentLanguage the currently active [Language].
 * @param onLanguageSelected callback invoked with the newly chosen [Language].
 * @param onDismiss callback to hide the bottom sheet.
 * @param context Android [Context] for string resources.
 */
@Composable
fun LanguageSelectorBottomSheet(
    show: Boolean,
    currentLanguage: Language,
    onLanguageSelected: (Language) -> Unit,
    onDismiss: () -> Unit,
    context: Context
) {
    var searchText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val allLanguages = remember { Language.entries }

    val filteredLanguages = remember(searchText) {
        if (searchText.isBlank()) {
            allLanguages
        } else {
            allLanguages.filter { language ->
                language.englishName.contains(searchText, ignoreCase = true) ||
                language.localName.contains(searchText, ignoreCase = true)
            }
        }
    }

    WindowBottomSheet(
        show = show,
        title = context.getString(R.string.select_language),
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
                label = context.getString(R.string.common_search),
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredLanguages) { language ->
                    LanguageItem(
                        language = language,
                        isSelected = language == currentLanguage,
                        onClick = {
                            onLanguageSelected(language)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageItem(
    language: Language,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (isSelected) {
            MiuixTheme.colorScheme.surfaceVariant
        } else {
            MiuixTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = language.englishName,
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = language.localName,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary
                )
            }
        }
    }
}