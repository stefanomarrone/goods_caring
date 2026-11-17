package it.marrone.goodscaring.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.marrone.goodscaring.data.Good
import it.marrone.goodscaring.data.GoodForm
import it.marrone.goodscaring.domain.StockCalculator
import it.marrone.goodscaring.domain.formatNumber
import it.marrone.goodscaring.domain.parseAmount
import it.marrone.goodscaring.ui.components.ConfirmDialog

/** Creates a new good when [good] is null, edits it otherwise. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditGoodScreen(
  good: Good?,
  today: Long,
  onBack: () -> Unit,
  onSave: (GoodForm) -> Unit,
  onDelete: () -> Unit,
) {
  fun Double.asText(decimals: Int) = formatNumber(this, decimals).replace(".", "")

  val decimals0 = good?.decimals ?: 0
  var name by rememberSaveable { mutableStateOf(good?.name ?: "") }
  var unit by rememberSaveable { mutableStateOf(good?.unit ?: "") }
  var allowDecimals by rememberSaveable { mutableStateOf(decimals0 > 0) }
  var rate by rememberSaveable {
    mutableStateOf(good?.let { StockCalculator.currentRate(it, today).asText(decimals0) } ?: "1")
  }
  var quick by rememberSaveable { mutableStateOf(good?.quickRefillAmount?.asText(decimals0) ?: "10") }
  var alertDays by rememberSaveable { mutableStateOf((good?.alertDaysBefore ?: 7).toString()) }
  var notifications by rememberSaveable { mutableStateOf(good?.notificationsEnabled ?: true) }
  var repeatDaily by rememberSaveable { mutableStateOf(good?.repeatDailyReminder ?: true) }
  var initialStock by rememberSaveable { mutableStateOf("0") }
  var confirmDelete by rememberSaveable { mutableStateOf(false) }

  val rateValue = parseAmount(rate)?.takeIf { it >= 0 }
  val quickValue = parseAmount(quick)?.takeIf { it > 0 }
  val alertValue = alertDays.trim().toIntOrNull()?.takeIf { it in 0..365 }
  val initialValue = parseAmount(initialStock)?.takeIf { it >= 0 }
  val valid =
    name.isNotBlank() && unit.isNotBlank() && rateValue != null && quickValue != null && alertValue != null &&
      (good != null || initialValue != null)

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(if (good == null) "Nuovo bene" else "Modifica ${good.name}") },
        navigationIcon = {
          IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro") }
        },
        actions = {
          if (good != null) {
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "Elimina") }
          }
        },
      )
    }
  ) { padding ->
    Column(
      modifier =
        Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      val decimalKeyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal)
      Field("Nome (es. Sigarette)", name, { name = it }, isError = name.isBlank())
      Field("Unità di misura (es. pacchetti, €)", unit, { unit = it }, isError = unit.isBlank())
      SwitchRow("Quantità con decimali (es. soldi)", allowDecimals) { allowDecimals = it }
      Field("Consumo giornaliero", rate, { rate = it }, isError = rateValue == null, keyboard = decimalKeyboard)
      if (good != null) {
        Text(
          "Se modifichi il consumo, il nuovo valore si applica da oggi; lo storico resta invariato.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (good == null) {
        Field("Giacenza attuale", initialStock, { initialStock = it }, isError = initialValue == null, keyboard = decimalKeyboard)
      }
      Field("Quantità ricarica rapida", quick, { quick = it }, isError = quickValue == null, keyboard = decimalKeyboard)
      Field(
        "Avvisami quando mancano (giorni)",
        alertDays,
        { alertDays = it },
        isError = alertValue == null,
        keyboard = KeyboardOptions(keyboardType = KeyboardType.Number),
      )
      SwitchRow("Notifiche attive", notifications) { notifications = it }
      SwitchRow("Ripeti l'avviso ogni giorno finché non ricarico", repeatDaily, enabled = notifications) {
        repeatDaily = it
      }
      Button(
        enabled = valid,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        onClick = {
          onSave(
            GoodForm(
              id = good?.id,
              name = name.trim(),
              unit = unit.trim(),
              decimals = if (allowDecimals) 2 else 0,
              dailyConsumption = rateValue!!,
              quickRefillAmount = quickValue!!,
              alertDaysBefore = alertValue!!,
              notificationsEnabled = notifications,
              repeatDailyReminder = repeatDaily,
              initialStock = initialValue ?: 0.0,
            )
          )
        },
      ) {
        Text("Salva")
      }
    }
  }

  if (confirmDelete && good != null) {
    ConfirmDialog(
      title = "Eliminare ${good.name}?",
      text = "Verranno eliminati anche tutto lo storico e le ricariche registrate.",
      confirmLabel = "Elimina",
      onDismiss = { confirmDelete = false },
      onConfirm = {
        confirmDelete = false
        onDelete()
      },
    )
  }
}

@Composable
private fun Field(
  label: String,
  value: String,
  onValueChange: (String) -> Unit,
  isError: Boolean = false,
  keyboard: KeyboardOptions = KeyboardOptions.Default,
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    label = { Text(label) },
    singleLine = true,
    isError = isError,
    keyboardOptions = keyboard,
    modifier = Modifier.fillMaxWidth(),
  )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
    Text(label, modifier = Modifier.weight(1f))
    Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
  }
}
