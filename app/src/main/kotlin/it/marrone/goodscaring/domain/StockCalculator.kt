package it.marrone.goodscaring.domain

import it.marrone.goodscaring.data.Good
import it.marrone.goodscaring.data.GoodEvent
import it.marrone.goodscaring.data.RateChange
import it.marrone.goodscaring.data.Refill
import it.marrone.goodscaring.data.StockSet
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** One row of the day-by-day ledger of a good. */
data class DayEntry(
  val epochDay: Long,
  /** Stock at the beginning of the day, before refills. */
  val startStock: Double,
  val refilled: Double,
  /** Set when the stock was corrected/initialised on this day. */
  val stockSetTo: Double?,
  val dailyConsumption: Double,
  /** Actually consumed (capped by the available stock). For today it is the planned value. */
  val consumed: Double,
  val endStock: Double,
  val events: List<GoodEvent>,
)

data class StockStatus(
  /** Stock available today (today's refills included, today's consumption not yet deducted). */
  val currentStock: Double,
  val dailyConsumption: Double,
  /** Number of days, today included, covered by [currentStock]. Null if consumption is zero. */
  val daysLeft: Int?,
  /** First day not covered by the stock. Null if consumption is zero. */
  val runOutEpochDay: Long?,
) {
  fun isUnderThreshold(alertDaysBefore: Int): Boolean = daysLeft != null && daysLeft <= alertDaysBefore
}

/**
 * Replays the events of a good one day at a time.
 *
 * Consumption of a day is deducted at the end of that day and can never exceed the stock
 * available (the recipient cannot consume what they don't have).
 */
object StockCalculator {

  private const val EPSILON = 1e-9

  fun ledger(good: Good, todayEpochDay: Long): List<DayEntry> {
    val events = good.events.sortedWith(compareBy({ it.epochDay }, { it.createdAt }))
    if (events.isEmpty()) return emptyList()
    val byDay = events.groupBy { it.epochDay }
    val firstDay = min(events.first().epochDay, todayEpochDay)
    // Events dated in the future are applied when their day comes.
    val lastDay = todayEpochDay

    val rows = ArrayList<DayEntry>((lastDay - firstDay + 1).toInt())
    var stock = 0.0
    var rate = 0.0
    for (day in firstDay..lastDay) {
      val start = stock
      var refilled = 0.0
      var setTo: Double? = null
      val dayEvents = byDay[day].orEmpty()
      for (event in dayEvents) {
        when (event) {
          is Refill -> {
            stock += event.amount
            refilled += event.amount
          }
          is StockSet -> {
            stock = event.stock
            setTo = event.stock
            // Refills registered earlier on the same day are included in the new value.
            refilled = 0.0
          }
          is RateChange -> rate = event.dailyConsumption
        }
      }
      val consumed = min(rate, max(stock, 0.0))
      val end = stock - consumed
      rows += DayEntry(day, start, refilled, setTo, rate, consumed, end, dayEvents)
      if (day < todayEpochDay) stock = end
    }
    return rows
  }

  fun status(good: Good, todayEpochDay: Long): StockStatus {
    val today = ledger(good, todayEpochDay).lastOrNull()
    val stock = today?.let { it.endStock + it.consumed } ?: 0.0
    val rate = today?.dailyConsumption ?: 0.0
    if (rate <= 0.0) return StockStatus(stock, rate, null, null)
    val days = max(0, floor(stock / rate + EPSILON).toInt())
    return StockStatus(stock, rate, days, todayEpochDay + days)
  }

  /** Daily consumption in effect today (latest rate change not in the future). */
  fun currentRate(good: Good, todayEpochDay: Long): Double =
    good.events
      .filterIsInstance<RateChange>()
      .filter { it.epochDay <= todayEpochDay }
      .maxWithOrNull(compareBy({ it.epochDay }, { it.createdAt }))
      ?.dailyConsumption ?: 0.0
}
