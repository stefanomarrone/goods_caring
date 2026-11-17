package it.marrone.goodscaring.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.marrone.goodscaring.domain.daysLeftText
import it.marrone.goodscaring.domain.formatAmount
import it.marrone.goodscaring.domain.formatDate
import it.marrone.goodscaring.ui.GoodUi

/** Current stock, coverage and consumption of a good. */
@Composable
fun StockSummary(item: GoodUi, modifier: Modifier = Modifier) {
  val good = item.good
  val status = item.status
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      text = good.formatAmount(status.currentStock),
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text = daysLeftText(status.daysLeft),
      style = MaterialTheme.typography.titleMedium,
      color = if (item.isUnderThreshold) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
    status.runOutEpochDay?.let { runOut ->
      if (status.daysLeft!! > 0) {
        Text("Coperto fino a ${formatDate(runOut - 1)}", style = MaterialTheme.typography.bodyMedium)
      }
    }
    Text(
      "Consumo: ${good.formatAmount(status.dailyConsumption)} al giorno · avviso a ${good.alertDaysBefore} giorni",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
