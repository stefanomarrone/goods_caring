package it.marrone.goodscaring.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Root of everything persisted by the app. */
@Serializable
data class AppData(
  val settings: AppSettings = AppSettings(),
  val goods: List<Good> = emptyList(),
)

@Serializable
data class AppSettings(
  val recipientName: String = "Mio fratello",
  /** Time of day at which the daily stock check (and notifications) runs. */
  val notificationHour: Int = 9,
  val notificationMinute: Int = 0,
)

/**
 * A good handed over to the recipient (e.g. cigarettes, money).
 *
 * The stock is never stored directly: it is derived from [events] (initial stock, refills,
 * corrections and changes of daily consumption), so that history can be shown day by day and
 * edited retroactively.
 */
@Serializable
data class Good(
  val id: String,
  val name: String,
  val unit: String,
  /** Number of decimals used to display/enter quantities (0 for packs, 2 for money). */
  val decimals: Int = 0,
  /** Amount proposed by default when tapping "Ricarica". */
  val quickRefillAmount: Double,
  /** A notification is sent when the good runs out in this many days or less. */
  val alertDaysBefore: Int = 7,
  val notificationsEnabled: Boolean = true,
  /** If true, remind every day while under threshold; otherwise notify only once per shortage. */
  val repeatDailyReminder: Boolean = true,
  val events: List<GoodEvent> = emptyList(),
  /** Notification bookkeeping, handled by the stock check worker. */
  val alertActive: Boolean = false,
  val lastNotifiedEpochDay: Long? = null,
)

@Serializable
sealed interface GoodEvent {
  val id: String
  /** Day the event refers to ([java.time.LocalDate.toEpochDay]). */
  val epochDay: Long
  /** Creation instant (millis), used to order events that happen on the same day. */
  val createdAt: Long
  val note: String
}

/** Goods handed over to the recipient: added to the stock. */
@Serializable
@SerialName("refill")
data class Refill(
  override val id: String,
  override val epochDay: Long,
  override val createdAt: Long,
  val amount: Double,
  override val note: String = "",
) : GoodEvent

/** Stock set to an absolute value: initial stock or a manual correction. */
@Serializable
@SerialName("set_stock")
data class StockSet(
  override val id: String,
  override val epochDay: Long,
  override val createdAt: Long,
  val stock: Double,
  override val note: String = "",
) : GoodEvent

/** Daily consumption applied from [epochDay] onwards. */
@Serializable
@SerialName("rate")
data class RateChange(
  override val id: String,
  override val epochDay: Long,
  override val createdAt: Long,
  val dailyConsumption: Double,
  override val note: String = "",
) : GoodEvent
