package it.marrone.goodscaring.domain

import it.marrone.goodscaring.data.Good
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ITALIAN)
private val shortDateFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)

fun formatDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(dateFormatter)

fun formatShortDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(shortDateFormatter)

fun formatNumber(value: Double, decimals: Int): String =
  NumberFormat.getNumberInstance(Locale.ITALIAN)
    .apply {
      minimumFractionDigits = decimals
      maximumFractionDigits = decimals
    }
    .format(value)

fun Good.formatAmount(value: Double): String = "${formatNumber(value, decimals)} $unit"

/** Parses user input accepting both "," and "." as decimal separator. */
fun parseAmount(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

fun daysLeftText(daysLeft: Int?): String =
  when (daysLeft) {
    null -> "Nessun consumo impostato"
    0 -> "Esaurito"
    1 -> "Basta solo per oggi"
    else -> "Basta per $daysLeft giorni"
  }
