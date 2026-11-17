package it.marrone.goodscaring

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import it.marrone.goodscaring.notifications.Notifications
import it.marrone.goodscaring.ui.AppViewModel
import it.marrone.goodscaring.ui.detail.GoodDetailScreen
import it.marrone.goodscaring.ui.edit.EditGoodScreen
import it.marrone.goodscaring.ui.home.HomeScreen
import it.marrone.goodscaring.ui.settings.SettingsScreen

@Composable
fun MainNavigation(viewModel: AppViewModel = viewModel()) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val backStack = rememberNavBackStack(Home)

  LifecycleResumeEffect(Unit) {
    viewModel.refreshToday()
    onPauseOrDispose {}
  }
  RequestNotificationPermission()

  val ui = state
  if (ui == null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    return
  }

  fun back() {
    backStack.removeLastOrNull()
  }

  NavDisplay(
    backStack = backStack,
    onBack = ::back,
    entryProvider =
      entryProvider {
        entry<Home> {
          HomeScreen(
            state = ui,
            onOpenGood = { backStack.add(GoodDetail(it)) },
            onAddGood = { backStack.add(EditGood(null)) },
            onOpenSettings = { backStack.add(SettingsKey) },
            onRefill = { good, amount, day, note, onDone -> viewModel.refill(good.id, amount, day, note, onDone) },
            onUndo = viewModel::deleteEvent,
          )
        }
        entry<GoodDetail> { key ->
          val item = ui.goods.find { it.good.id == key.goodId }
          if (item == null) {
            // The good has been deleted.
            LaunchedEffect(Unit) { back() }
          } else {
            GoodDetailScreen(
              item = item,
              today = ui.today,
              onBack = ::back,
              onEdit = { backStack.add(EditGood(key.goodId)) },
              onRefill = { amount, day, note -> viewModel.refill(key.goodId, amount, day, note) },
              onSetStock = { stock, day, note -> viewModel.setStock(key.goodId, stock, day, note) },
              onDeleteEvent = { viewModel.deleteEvent(key.goodId, it) },
            )
          }
        }
        entry<EditGood> { key ->
          val good = key.goodId?.let { id -> ui.goods.find { it.good.id == id }?.good }
          EditGoodScreen(
            good = good,
            today = ui.today,
            onBack = ::back,
            onSave = {
              viewModel.saveGood(it)
              back()
            },
            onDelete = {
              viewModel.deleteGood(key.goodId!!)
              // Leave both the edit and the detail screen of the deleted good.
              backStack.removeAll { k -> k is EditGood || k is GoodDetail }
            },
          )
        }
        entry<SettingsKey> {
          SettingsScreen(
            settings = ui.settings,
            onBack = ::back,
            onSave = viewModel::saveSettings,
            onTestNotification = viewModel::sendTestNotification,
          )
        }
      },
  )
}

@Composable
private fun RequestNotificationPermission() {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
  val context = LocalContext.current
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
  LaunchedEffect(Unit) {
    if (!Notifications.canNotify(context)) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
  }
}
