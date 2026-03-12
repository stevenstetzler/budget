package com.vidalabs.budget.ui.components

import android.widget.NumberPicker
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun MonthYearPickerDialogWheel(
    initial: YearMonth,
    minYear: Int = 2000,
    maxYear: Int = 2035,
    onDismiss: () -> Unit,
    onConfirm: (YearMonth) -> Unit
) {
    val monthNames = remember {
        (1..12).map { m ->
            Month.of(m).getDisplayName(TextStyle.SHORT, Locale.getDefault())
        }.toTypedArray()
    }

    var pickedMonth by remember { mutableStateOf(initial.monthValue) } // 1..12
    var pickedYear by remember { mutableStateOf(initial.year.coerceIn(minYear, maxYear)) }
    
    val isDark = isSystemInDarkTheme()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select month") },
        text = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                // Month wheel
                AndroidView(
                    modifier = Modifier.weight(1f),
                    factory = { context ->
                        NumberPicker(context).apply {
                            minValue = 1
                            maxValue = 12
                            displayedValues = monthNames
                            value = pickedMonth
                            wrapSelectorWheel = true
                            if (isDark) {
                                setTextColor(android.graphics.Color.WHITE)
                            }
                            setOnValueChangedListener { _, oldVal, newVal ->
                                pickedMonth = newVal
                                // Auto-wrap year logic
                                if (oldVal == 12 && newVal == 1) {
                                    if (pickedYear < maxYear) pickedYear++
                                } else if (oldVal == 1 && newVal == 12) {
                                    if (pickedYear > minYear) pickedYear--
                                }
                            }
                        }
                    },
                    update = { picker ->
                        if (picker.value != pickedMonth) picker.value = pickedMonth
                    }
                )

                // Year wheel
                AndroidView(
                    modifier = Modifier.weight(1f),
                    factory = { context ->
                        NumberPicker(context).apply {
                            minValue = minYear
                            maxValue = maxYear
                            value = pickedYear
                            wrapSelectorWheel = false
                            if (isDark) {
                                setTextColor(android.graphics.Color.WHITE)
                            }
                            setOnValueChangedListener { _, _, newVal ->
                                pickedYear = newVal
                            }
                        }
                    },
                    update = { picker ->
                        if (picker.value != pickedYear) picker.value = pickedYear
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(YearMonth.of(pickedYear, pickedMonth))
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthDayYearPickerDialogWheel(
    initial: LocalDate,
    minYear: Int = 2000,
    maxYear: Int = 2035,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    var showCalendarView by remember { mutableStateOf(false) }

    if (showCalendarView) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showCalendarView = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val selectedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        onConfirm(selectedDate) // Exit completely
                    }
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCalendarView = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
        return
    }

    val monthNames = remember {
        (1..12).map { m ->
            Month.of(m).getDisplayName(TextStyle.SHORT, Locale.getDefault())
        }.toTypedArray()
    }

    var pickedMonth by remember { mutableStateOf(initial.monthValue) } // 1..12
    var pickedYear by remember { mutableStateOf(initial.year.coerceIn(minYear, maxYear)) }
    var pickedDay by remember { mutableStateOf(initial.dayOfMonth) }   // 1..31

    val isDark = isSystemInDarkTheme()

    // Recompute the valid max day whenever month/year changes, clamp pickedDay if needed.
    val maxDay = remember(pickedYear, pickedMonth) {
        YearMonth.of(pickedYear, pickedMonth).lengthOfMonth()
    }
    LaunchedEffect(maxDay) {
        if (pickedDay > maxDay) pickedDay = maxDay
        if (pickedDay < 1) pickedDay = 1
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Select date")
                IconButton(onClick = { showCalendarView = true }) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Switch to calendar",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Month wheel
                AndroidView(
                    modifier = Modifier.weight(1f),
                    factory = { context ->
                        NumberPicker(context).apply {
                            minValue = 1
                            maxValue = 12
                            displayedValues = monthNames
                            value = pickedMonth
                            wrapSelectorWheel = true
                            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                            if (isDark) {
                                setTextColor(android.graphics.Color.WHITE)
                            }
                            setOnValueChangedListener { _, oldVal, newVal ->
                                pickedMonth = newVal
                                // Auto-wrap year logic
                                if (oldVal == 12 && newVal == 1) {
                                    if (pickedYear < maxYear) pickedYear++
                                } else if (oldVal == 1 && newVal == 12) {
                                    if (pickedYear > minYear) pickedYear--
                                }
                            }
                        }
                    },
                    update = { picker ->
                        if (picker.value != pickedMonth) picker.value = pickedMonth
                    }
                )

                // Day wheel (dynamic max)
                AndroidView(
                    modifier = Modifier.weight(1f),
                    factory = { context ->
                        NumberPicker(context).apply {
                            minValue = 1
                            maxValue = maxDay
                            value = pickedDay.coerceIn(1, maxDay)
                            wrapSelectorWheel = true
                            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                            if (isDark) {
                                setTextColor(android.graphics.Color.WHITE)
                            }
                            setOnValueChangedListener { _, _, newVal ->
                                pickedDay = newVal
                            }
                        }
                    },
                    update = { picker ->
                        // Update range first, then value
                        if (picker.maxValue != maxDay) picker.maxValue = maxDay
                        val clamped = pickedDay.coerceIn(1, maxDay)
                        if (picker.value != clamped) picker.value = clamped
                    }
                )

                // Year wheel
                AndroidView(
                    modifier = Modifier.weight(1f),
                    factory = { context ->
                        NumberPicker(context).apply {
                            minValue = minYear
                            maxValue = maxYear
                            value = pickedYear
                            wrapSelectorWheel = false
                            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                            if (isDark) {
                                setTextColor(android.graphics.Color.WHITE)
                            }
                            setOnValueChangedListener { _, _, newVal ->
                                pickedYear = newVal
                            }
                        }
                    },
                    update = { picker ->
                        if (picker.value != pickedYear) picker.value = pickedYear
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val date = LocalDate.of(pickedYear, pickedMonth, pickedDay.coerceIn(1, maxDay))
                onConfirm(date)
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
