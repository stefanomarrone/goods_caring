package it.marrone.goodscaring.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.marrone.goodscaring.data.Good
import it.marrone.goodscaring.data.GoodEvent
import it.marrone.goodscaring.data.RateChange
import it.marrone.goodscaring.data.Refill
import it.marrone.goodscaring.data.StockSet
import it.marrone.goodscaring.domain.DayEntry
import it.marrone.goodscaring.domain.StockCalculator
import it.marrone.goodscaring.domain.formatAmount
import it.marrone.goodscaring.domain.formatShortDate
import it.marrone.goodscaring.ui.GoodUi
import it.marrone.goodscaring.ui.components.AmountDialog
import it.marrone.goodscaring.ui.components.ConfirmDialog
import it.marrone.goodscaring.ui.components.StockSummary

private enum class DialogType { REFILL, CORRECT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoodDetailScreen(
  item: GoodUi,
  today: Long,
  onBack: () -> Unit,
  onEdit: () -> Unit,
  onRefill: (amount: Double, epochDay: Long, note: String) -> Unit,
  onSetStock: (stock: Double, epochDay: Long, note: String) -> Unit,
  onDeleteEvent: (eventId: String) -> Unit,
) {
  val good = item.good
  val ledger = remember(good, today) { StockCalculator.ledger(good, today).asReversed() }
  var dialog by rememberSaveable { mutableStateOf<DialogType?>(null) }
  var eventToDelete by remember { mutableStateOf<GoodEvent?>(null) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(good.name) },
        navigationIcon = {
          IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro") }
        },
        actions = { IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Modifica") } },
      )
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding =
        PaddingValues(
          start = 16.dp,
          end = 16.dp,
          top = padding.calculateTopPadding() + 8.dp,
          bottom = padding.calculateBottomPadding() + 16.dp,
        ),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      item {
        Card(modifier = Modifier.fillMaxWidth()) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StockSummary(item)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Button(onClick = { dialog = DialogType.REFILL }, modifier = Modifier.weight(1f)) { Text("Ricarica") }
              OutlinedButton(onClick = { dialog = DialogType.CORRECT }, modifier = Modifier.weight(1f)) {
                Text("Correggi giacenza")
              }
            }
          }
        }
      }
      item {
        Text(
          "Storico giornaliero",
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
      }
      items(ledger, key = { it.epochDay }) { entry ->
        DayRow(good, entry, isToday = entry.epochDay == today, onDeleteEvent = { eventToDelete = it })
        HorizontalDivider()
      }
    }
  }

  when (dialog) {
    DialogType.REFILL ->
      AmountDialog(
        title = "Ricarica ${good.name.lowercase()}",
        label = "Quantità (${good.unit})",
        initialAmount = good.quickRefillAmount,
        decimals = good.decimals,
        today = today,
        confirmLabel = "Ricarica",
        onDismiss = { dialog = null },
        onConfirm = { amount, day, note ->
          dialog = null
          onRefill(amount, day, note)
        },
      )
    DialogType.CORRECT ->
      AmountDialog(
        title = "Correggi giacenza",
        label = "Giacenza effettiva (${good.unit})",
        initialAmount = item.status.currentStock,
        decimals = good.decimals,
        today = today,
        confirmLabel = "Salva",
        allowZero = true,
        onDismiss = { dialog = null },
        onConfirm = { amount, day, note ->
          dialog = null
          onSetStock(amount, day, note.ifEmpty { "Correzione" })
        },
      )
    null -> Unit
  }

  eventToDelete?.let { event ->
    ConfirmDialog(
      title = "Eliminare la voce?",
      text = "\"${eventLabel(good, event)}\" del ${formatShortDate(event.epochDay)} verrà eliminata e lo storico ricalcolato.",
      confirmLabel = "Elimina",
      onDismiss = { eventToDelete = null },
      onConfirm = {
        onDeleteEvent(event.id)
        eventToDelete = null
      },
    )
  }
}

@Composable
private fun DayRow(good: Good, entry: DayEntry, isToday: Boolean, onDeleteEvent: (GoodEvent) -> Unit) {
  Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        if (isToday) "Oggi" else formatShortDate(entry.epochDay),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.weight(1f),
      )
      Text("Fine giornata: ${good.formatAmount(entry.endStock)}", style = MaterialTheme.typography.bodyMedium)
    }
    val details = buildList {
      if (entry.refilled > 0) add("+${good.formatAmount(entry.refilled)} ricevuti")
      add("−${good.formatAmount(entry.consumed)} ${if (isToday) "previsti" else "consumati"}")
      if (entry.consumed < entry.dailyConsumption) add("scorta insufficiente")
    }
    Text(
      details.joinToString(" · "),
      style = MaterialTheme.typography.bodySmall,
      color =
        if (entry.consumed < entry.dailyConsumption) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    entry.events.forEach { event ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          eventLabel(good, event) + if (event.note.isNotEmpty()) " — ${event.note}" else "",
          style = MaterialTheme.typography.bodySmall,
          color = eventColor(event),
          modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        IconButton(onClick = { onDeleteEvent(event) }, modifier = Modifier.size(36.dp)) {
          Icon(Icons.Default.Delete, contentDescription = "Elimina", modifier = Modifier.size(18.dp))
        }
      }
    }
  }
}

private fun eventLabel(good: Good, event: GoodEvent): String =
  when (event) {
    is Refill -> "Ricarica di ${good.formatAmount(event.amount)}"
    is StockSet -> "Giacenza impostata a ${good.formatAmount(event.stock)}"
    is RateChange -> "Consumo impostato a ${good.formatAmount(event.dailyConsumption)}/giorno"
  }

@Composable
private fun eventColor(event: GoodEvent): Color =
  when (event) {
    is Refill -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.tertiary
  }
