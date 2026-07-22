package com.chronie.homemoney.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chronie.homemoney.R
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import java.text.DateFormatSymbols
import java.time.LocalDate
import java.time.YearMonth

/**
 * Miuix date picker bottom sheet.
 *
 * Renders three [NumberPicker]s (year / month / day) inside a [WindowBottomSheet], driven by a
 * native [LocalDate] so callers no longer need epoch-millis conversion.
 *
 * The sheet is always composed; visibility is driven by [show]. When the user swipes down or
 * presses back, [onDismiss] is invoked via [WindowBottomSheet]'s onDismissRequest. When the user
 * taps Confirm, [onDateSelected] is invoked with the picked date and the sheet is dismissed.
 */
@Composable
fun MiuixDatePickerSheet(
    show: Boolean,
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    title: String? = null,
) {
    // Reset to the initial date every time the sheet is (re)opened.
    var year by remember(show) { mutableIntStateOf(initialDate.year) }
    var month by remember(show) { mutableIntStateOf(initialDate.monthValue) } // 1..12
    var day by remember(show) { mutableIntStateOf(initialDate.dayOfMonth) }

    // Keep the day within the valid range for the selected year/month.
    val daysInMonth = remember(year, month) { YearMonth.of(year, month).lengthOfMonth() }
    LaunchedEffect(daysInMonth) {
        if (day > daysInMonth) day = daysInMonth
    }

    val context = LocalContext.current
    val locale = context.resources.configuration.locales[0]
    val monthNames = remember(locale) {
        DateFormatSymbols.getInstance(locale).months.toList().take(12)
    }

    WindowBottomSheet(
        show = show,
        title = title,
        onDismissRequest = onDismiss,
        startAction = {
            TextButton(text = context.getString(R.string.cancel), onClick = onDismiss)
        },
        endAction = {
            TextButton(
                text = context.getString(R.string.confirm),
                onClick = {
                    onDateSelected(LocalDate.of(year, month, day))
                    onDismiss()
                }
            )
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NumberPicker(
                value = year,
                onValueChange = { year = it },
                modifier = Modifier.weight(1f),
                range = 1970..2100,
                label = { it.toString() },
            )
            NumberPicker(
                value = month,
                onValueChange = { month = it },
                modifier = Modifier.weight(1f),
                range = 1..12,
                label = { monthNames[it - 1] },
            )
            NumberPicker(
                value = day,
                onValueChange = { day = it },
                modifier = Modifier.weight(1f),
                range = 1..daysInMonth,
                label = { it.toString() },
            )
        }
    }
}
