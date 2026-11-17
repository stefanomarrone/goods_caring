package it.marrone.goodscaring.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.marrone.goodscaring.domain.formatDate
import it.marrone.goodscaring.domain.formatNumber
import it.marrone.goodscaring.domain.parseAmount

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Dialog asking for an amount, a day (today or in the past) and an optional note.
 * Used both for refills and for stock corrections.
 */
@Composable
fun AmountDialog(
  title: String,
  label: String,
  initialAmount: Double,
  decimals: Int,
  today: Long,
  confirmLabel: String,
  onDismiss: () -> Unit,
  onConfirm: (amount: Double, epochDay: Long, note: String) -> Unit,
  allowZero: Boolean = false,
) {
  var amountText by rememberSaveable { mutableStateOf(formatNumber(initialAmount, decimals).replace(".", "")) }
  var day by rememberSaveable { mutableLongStateOf(today) }
  var note by rememberSaveable { mutableStateOf("") }
  var showDatePicker by rememberSaveable { mutableStateOf(false) }

  val amount = parseAmount(amountText)
  val valid = amount != null && (amount > 0 || (allowZero && amount == 0.0))

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = amountText,
          onValueChange = { amountText = it },
          label = { Text(label) },
          singleLine = true,
          isError = !valid,
          keyboardOptions =
            KeyboardOptions(keyboardType = if (decimals > 0) KeyboardType.Decimal else KeyboardType.Number),
        )
        OutlinedButton(onClick = { showDatePicker = true }) {
          Icon(Icons.Default.DateRange, contentDescription = null)
          Text("  " + if (day == today) "Oggi" else formatDate(day))
        }
        OutlinedTextField(
          value = note,
          onValueChange = { note = it },
          label = { Text("Nota (facoltativa)") },
          singleLine = true,
        )
      }
    },
    confirmButton = {
      TextButton(enabled = valid, onClick = { onConfirm(amount!!, day, note.trim()) }) { Text(confirmLabel) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
  )

  if (showDatePicker) {
    PastDatePickerDialog(
      initialEpochDay = day,
      today = today,
      onDismiss = { showDatePicker = false },
      onConfirm = {
        day = it
        showDatePicker = false
      },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PastDatePickerDialog(
  initialEpochDay: Long,
  today: Long,
  onDismiss: () -> Unit,
  onConfirm: (Long) -> Unit,
) {
  val state =
    rememberDatePickerState(
      initialSelectedDateMillis = initialEpochDay * MILLIS_PER_DAY,
      selectableDates =
        object : SelectableDates {
          override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis / MILLIS_PER_DAY <= today
        },
    )
  DatePickerDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(onClick = { state.selectedDateMillis?.let { onConfirm(it / MILLIS_PER_DAY) } }) { Text("OK") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
  ) {
    DatePicker(state = state)
  }
}

@Composable
fun ConfirmDialog(
  title: String,
  text: String,
  confirmLabel: String,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(text) },
    confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
  )
}
