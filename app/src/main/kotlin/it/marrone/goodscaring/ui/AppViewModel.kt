package it.marrone.goodscaring.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.marrone.goodscaring.GoodsApp
import it.marrone.goodscaring.data.AppSettings
import it.marrone.goodscaring.data.Good
import it.marrone.goodscaring.data.GoodForm
import it.marrone.goodscaring.domain.StockCalculator
import it.marrone.goodscaring.domain.StockStatus
import it.marrone.goodscaring.notifications.Notifications
import it.marrone.goodscaring.notifications.StockCheckWorker
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GoodUi(val good: Good, val status: StockStatus) {
  val isUnderThreshold: Boolean
    get() = status.isUnderThreshold(good.alertDaysBefore)
}

data class AppUiState(
  val today: Long,
  val settings: AppSettings,
  val goods: List<GoodUi>,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

  private val repository = (application as GoodsApp).repository
  private val today = MutableStateFlow(LocalDate.now().toEpochDay())

  val uiState: StateFlow<AppUiState?> =
    combine(repository.data, today) { data, day ->
        AppUiState(
          today = day,
          settings = data.settings,
          goods = data.goods.map { GoodUi(it, StockCalculator.status(it, day)) },
        )
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  /** Called when the app comes to foreground, the date may have changed. */
  fun refreshToday() {
    today.value = LocalDate.now().toEpochDay()
  }

  fun refill(
    goodId: String,
    amount: Double,
    epochDay: Long,
    note: String,
    onDone: (eventId: String) -> Unit = {},
  ) = launchAndCheck { onDone(repository.addRefill(goodId, amount, epochDay, note)) }

  fun setStock(goodId: String, stock: Double, epochDay: Long, note: String) = launchAndCheck {
    repository.setStock(goodId, stock, epochDay, note)
  }

  fun deleteEvent(goodId: String, eventId: String) = launchAndCheck {
    repository.deleteEvent(goodId, eventId)
  }

  fun saveGood(form: GoodForm) = launchAndCheck { repository.saveGood(form, today.value) }

  fun deleteGood(goodId: String) = viewModelScope.launch { repository.deleteGood(goodId) }

  fun saveSettings(settings: AppSettings) =
    viewModelScope.launch {
      val timeChanged = uiState.value?.settings?.let {
        it.notificationHour != settings.notificationHour || it.notificationMinute != settings.notificationMinute
      } ?: true
      repository.updateSettings(settings)
      if (timeChanged) StockCheckWorker.schedule(getApplication(), settings, replace = true)
    }

  fun sendTestNotification() {
    Notifications.show(
      getApplication(),
      notificationId = 0,
      title = "Notifica di prova",
      text = "Le notifiche di Goods Caring funzionano correttamente.",
    )
  }

  /** Any change may cross the alert threshold: re-evaluate notifications right away. */
  private fun launchAndCheck(block: suspend () -> Unit) =
    viewModelScope.launch {
      block()
      StockCheckWorker.runNow(getApplication())
    }
}
