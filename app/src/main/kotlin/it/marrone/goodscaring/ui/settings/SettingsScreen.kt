package it.marrone.goodscaring.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import it.marrone.goodscaring.data.AppSettings
import it.marrone.goodscaring.notifications.Notifications

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  settings: AppSettings,
  onBack: () -> Unit,
  onSave: (AppSettings) -> Unit,
  onTestNotification: () -> Unit,
) {
  val context = LocalContext.current
  var recipient by rememberSaveable { mutableStateOf(settings.recipientName) }
  var hour by rememberSaveable { mutableIntStateOf(settings.notificationHour) }
  var minute by rememberSaveable { mutableIntStateOf(settings.notificationMinute) }
  var showTimePicker by rememberSaveable { mutableStateOf(false) }
  var notificationsAllowed by rememberSaveable { mutableStateOf(Notifications.canNotify(context)) }
  LifecycleResumeEffect(Unit) {
    notificationsAllowed = Notifications.canNotify(context)
    onPauseOrDispose {}
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Impostazioni") },
        navigationIcon = {
          IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro") }
        },
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      OutlinedTextField(
        value = recipient,
        onValueChange = { recipient = it },
        label = { Text("Destinatario dei beni") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Orario del controllo giornaliero", style = MaterialTheme.typography.titleSmall)
        OutlinedButton(onClick = { showTimePicker = true }) { Text("%02d:%02d".format(hour, minute)) }
        Text(
          "A quest'ora l'app controlla le scorte e ti avvisa dei beni in esaurimento. " +
            "La soglia di avviso si imposta per ogni bene.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Button(
        enabled = recipient.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
        onClick = {
          onSave(settings.copy(recipientName = recipient.trim(), notificationHour = hour, notificationMinute = minute))
          onBack()
        },
      ) {
        Text("Salva")
      }

      Text("Notifiche", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
      if (!notificationsAllowed) {
        Text(
          "Le notifiche sono disattivate per questa app: non riceverai gli avvisi.",
          color = MaterialTheme.colorScheme.error,
        )
        OutlinedButton(
          onClick = {
            context.startActivity(
              Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
          }
        ) {
          Text("Apri impostazioni notifiche")
        }
      }
      OutlinedButton(onClick = onTestNotification, enabled = notificationsAllowed) { Text("Invia notifica di prova") }
    }
  }

  if (showTimePicker) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    AlertDialog(
      onDismissRequest = { showTimePicker = false },
      text = { TimePicker(state = state) },
      confirmButton = {
        TextButton(
          onClick = {
            hour = state.hour
            minute = state.minute
            showTimePicker = false
          }
        ) {
          Text("OK")
        }
      },
      dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Annulla") } },
    )
  }
}
