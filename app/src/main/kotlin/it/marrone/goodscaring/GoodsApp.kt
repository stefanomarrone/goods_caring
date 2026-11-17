package it.marrone.goodscaring

import android.app.Application
import it.marrone.goodscaring.data.GoodsRepository
import it.marrone.goodscaring.notifications.Notifications
import it.marrone.goodscaring.notifications.StockCheckWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GoodsApp : Application() {

  val repository: GoodsRepository by lazy { GoodsRepository.create(this) }

  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  override fun onCreate() {
    super.onCreate()
    Notifications.createChannel(this)
    appScope.launch {
      StockCheckWorker.schedule(this@GoodsApp, repository.data.first().settings, replace = false)
    }
  }
}
