package it.marrone.goodscaring.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.marrone.goodscaring.data.AppSettings
import it.marrone.goodscaring.data.Good
import it.marrone.goodscaring.data.RateChange
import it.marrone.goodscaring.data.StockSet
import it.marrone.goodscaring.domain.StockCalculator
import it.marrone.goodscaring.domain.formatAmount
import it.marrone.goodscaring.ui.AppUiState
import it.marrone.goodscaring.ui.GoodUi
import it.marrone.goodscaring.ui.components.AmountDialog
import it.marrone.goodscaring.ui.components.StockSummary
import it.marrone.goodscaring.ui.theme.GoodsCaringTheme
import java.time.LocalDate
import kotlinx.coroutines.launch
import androidx.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  state: AppUiState,
  onOpenGood: (String) -> Unit,
  onAddGood: () -> Unit,
  onOpenSettings: () -> Unit,
  onRefill: (good: Good, amount: Double, epochDay: Long, note: String, onDone: (String) -> Unit) -> Unit,
  onUndo: (goodId: String, eventId: String) -> Unit,
) {
  val snackbar = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  var refillGoodId by rememberSaveable { mutableStateOf<String?>(null) }

  fun refill(good: Good, amount: Double, day: Long, note: String) {
    onRefill(good, amount, day, note) { eventId ->
      scope.launch {
        val result =
          snackbar.showSnackbar(
            "Ricarica di ${good.formatAmount(amount)} registrata",
            actionLabel = "Annulla",
            duration = SnackbarDuration.Short,
          )
        if (result == SnackbarResult.ActionPerformed) onUndo(good.id, eventId)
      }
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("Goods Caring")
            Text("Per: ${state.settings.recipientName}", style = MaterialTheme.typography.bodyMedium)
          }
        },
        actions = {
          IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Impostazioni") }
        },
      )
    },
    floatingActionButton = {
      ExtendedFloatingActionButton(
        onClick = onAddGood,
        icon = { Icon(Icons.Default.Add, contentDescription = null) },
        text = { Text("Nuovo bene") },
      )
    },
    snackbarHost = { SnackbarHost(snackbar) },
  ) { padding ->
    if (state.goods.isEmpty()) {
      Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text("Nessun bene configurato.\nAggiungine uno con il pulsante in basso.")
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
          PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 88.dp,
          ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        items(state.goods, key = { it.good.id }) { item ->
          GoodCard(
            item = item,
            onClick = { onOpenGood(item.good.id) },
            onQuickRefill = { refill(item.good, item.good.quickRefillAmount, state.today, "") },
            onCustomRefill = { refillGoodId = item.good.id },
          )
        }
      }
    }
  }

  state.goods.find { it.good.id == refillGoodId }?.let { item ->
    AmountDialog(
      title = "Ricarica ${item.good.name.lowercase()}",
      label = "Quantità (${item.good.unit})",
      initialAmount = item.good.quickRefillAmount,
      decimals = item.good.decimals,
      today = state.today,
      confirmLabel = "Ricarica",
      onDismiss = { refillGoodId = null },
      onConfirm = { amount, day, note ->
        refillGoodId = null
        refill(item.good, amount, day, note)
      },
    )
  }
}

@Composable
private fun GoodCard(item: GoodUi, onClick: () -> Unit, onQuickRefill: () -> Unit, onCustomRefill: () -> Unit) {
  val warning = item.isUnderThreshold
  Card(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth(),
    colors =
      if (warning) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
      else CardDefaults.cardColors(),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(item.good.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (warning) {
          Icon(
            Icons.Default.Warning,
            contentDescription = "In esaurimento",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(28.dp),
          )
        }
      }
      StockSummary(item)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onQuickRefill, modifier = Modifier.weight(1f)) {
          Icon(Icons.Default.Add, contentDescription = null)
          Spacer(Modifier.size(4.dp))
          Text("Ricarica ${item.good.formatAmount(item.good.quickRefillAmount)}")
        }
        OutlinedButton(onClick = onCustomRefill) { Text("Altro…") }
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
  val today = LocalDate.of(2026, 10, 30).toEpochDay()
  fun good(id: String, name: String, unit: String, decimals: Int, stock: Double, rate: Double) =
    Good(
      id = id,
      name = name,
      unit = unit,
      decimals = decimals,
      quickRefillAmount = 10.0,
      events = listOf(StockSet("s$id", today, 0, stock), RateChange("r$id", today, 1, rate)),
    )
  val goods =
    listOf(good("1", "Sigarette", "pacchetti", 0, 5.0, 1.0), good("2", "Soldi", "€", 2, 40.0, 2.0)).map {
      GoodUi(it, StockCalculator.status(it, today))
    }
  GoodsCaringTheme {
    HomeScreen(AppUiState(today, AppSettings(), goods), {}, {}, {}, { _, _, _, _, _ -> }, { _, _ -> })
  }
}
