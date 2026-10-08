package org.notifledger.app.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.composables.icons.lucide.Calendar
import com.composables.icons.lucide.Lucide
import org.notifledger.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Date text field (YYYY-MM-DD) with a calendar button that opens a date picker.
 * The text stays editable so dates can still be typed by hand.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    var showPicker by remember { mutableStateOf(false) }
    val dateInvalid = value.isNotBlank() && runCatching { LocalDate.parse(value) }.isFailure

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label ?: stringResource(R.string.date)) },
        singleLine = true,
        isError = dateInvalid,
        supportingText = if (dateInvalid) {
            { Text(stringResource(R.string.date_invalid)) }
        } else {
            null
        },
        trailingIcon = {
            IconButton(onClick = { showPicker = true }) {
                Icon(Lucide.Calendar, contentDescription = stringResource(R.string.date))
            }
        },
        modifier = modifier,
    )

    if (showPicker) {
        val initialMillis = remember(value) {
            runCatching {
                LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }.getOrNull()
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            val picked = Instant.ofEpochMilli(it)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .toString()
                            onValueChange(picked)
                        }
                        showPicker = false
                    },
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
