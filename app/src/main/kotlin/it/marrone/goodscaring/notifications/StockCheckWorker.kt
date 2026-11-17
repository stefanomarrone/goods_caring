package it.marrone.goodscaring.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import it.marrone.goodscaring.GoodsApp
import it.marrone.goodscaring.data.AppSettings
import it.marrone.goodscaring.data.Good
import it.marrone.goodscaring.domain.StockCalculator
import it.marrone.goodscaring.domain.StockStatus
import it.marrone.goodscaring.domain.formatAmount
import it.marrone.goodscaring.domain.formatDate
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/** Daily check: notifies the goods that will run out within their configured threshold. */
class StockCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result {
    val repository = (applicationContext as GoodsApp).repository
    val data = repository.data.first()
    val today = LocalDate.now().toEpochDay()
    for (good in data.goods) {
      val status = StockCalculator.status(good, today)
      when (decide(good, status, today)) {
        Decision.NOTIFY -> {
          Notifications.show(
            applicationContext,
            good.id.hashCode(),
            title = "${good.name}: scorta in esaurimento",
            text = message(good, status, data.settings),
          )
          repository.updateAlertState(good.id, active = true, notifiedEpochDay = today)
        }
        Decision.RESET -> repository.updateAlertState(good.id, active = false, notifiedEpochDay = null)
        Decision.NONE -> Unit
      }
    }
    return Result.success()
  }

  enum class Decision { NOTIFY, RESET, NONE }

  companion object {
    private const val PERIODIC_WORK = "daily_stock_check"
    private const val IMMEDIATE_WORK = "immediate_stock_check"

    fun decide(good: Good, status: StockStatus, today: Long): Decision {
      val under = status.isUnderThreshold(good.alertDaysBefore)
      return when {
        !under -> if (good.alertActive) Decision.RESET else Decision.NONE
        !good.notificationsEnabled -> Decision.NONE
        !good.alertActive -> Decision.NOTIFY
        good.repeatDailyReminder && (good.lastNotifiedEpochDay ?: Long.MIN_VALUE) < today -> Decision.NOTIFY
        else -> Decision.NONE
      }
    }

    fun message(good: Good, status: StockStatus, settings: AppSettings): String {
      val left = status.daysLeft ?: 0
      val who = settings.recipientName
      return if (left == 0) {
        "$who ha esaurito ${good.name.lowercase()}. Rimangono ${good.formatAmount(status.currentStock)}."
      } else {
        "Rimangono ${good.formatAmount(status.currentStock)} per $who: " +
          "bastano per $left ${if (left == 1) "giorno" else "giorni"} " +
          "(fino a ${formatDate(status.runOutEpochDay!! - 1)})."
      }
    }

    /** (Re)schedules the daily check at the time configured in [settings]. */
    fun schedule(context: Context, settings: AppSettings, replace: Boolean) {
      val now = LocalDateTime.now()
      var next = now.toLocalDate().atTime(settings.notificationHour, settings.notificationMinute)
      if (!next.isAfter(now)) next = next.plusDays(1)
      val request =
        PeriodicWorkRequestBuilder<StockCheckWorker>(1, TimeUnit.DAYS)
          .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
          .build()
      WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(
          PERIODIC_WORK,
          if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
          request,
        )
    }

    /** Runs a check right away (e.g. after changing the configuration). */
    fun runNow(context: Context) {
      WorkManager.getInstance(context)
        .enqueueUniqueWork(IMMEDIATE_WORK, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<StockCheckWorker>().build())
    }
  }
}
