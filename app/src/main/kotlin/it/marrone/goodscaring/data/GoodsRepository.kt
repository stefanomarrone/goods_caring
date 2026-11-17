package it.marrone.goodscaring.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import it.marrone.goodscaring.domain.StockCalculator
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Values edited in the "good" form. */
data class GoodForm(
  val id: String?,
  val name: String,
  val unit: String,
  val decimals: Int,
  val dailyConsumption: Double,
  val quickRefillAmount: Double,
  val alertDaysBefore: Int,
  val notificationsEnabled: Boolean,
  val repeatDailyReminder: Boolean,
  /** Only used when creating a new good. */
  val initialStock: Double,
)

class GoodsRepository(private val dataStore: DataStore<AppData>) {

  val data: Flow<AppData> = dataStore.data

  /** Returns the id of the created event. */
  suspend fun addRefill(goodId: String, amount: Double, epochDay: Long, note: String): String =
    addEvent(goodId, Refill(newId(), epochDay, now(), amount, note))

  /** Returns the id of the created event. */
  suspend fun setStock(goodId: String, stock: Double, epochDay: Long, note: String): String =
    addEvent(goodId, StockSet(newId(), epochDay, now(), stock, note))

  suspend fun deleteEvent(goodId: String, eventId: String) = updateGood(goodId) { good ->
    good.copy(events = good.events.filterNot { it.id == eventId })
  }

  suspend fun saveGood(form: GoodForm, todayEpochDay: Long) {
    dataStore.updateData { data ->
      val existing = form.id?.let { id -> data.goods.find { it.id == id } }
      val good =
        if (existing == null) {
          Good(
            id = newId(),
            name = form.name,
            unit = form.unit,
            decimals = form.decimals,
            quickRefillAmount = form.quickRefillAmount,
            alertDaysBefore = form.alertDaysBefore,
            notificationsEnabled = form.notificationsEnabled,
            repeatDailyReminder = form.repeatDailyReminder,
            events =
              listOf(
                StockSet(newId(), todayEpochDay, now(), form.initialStock, "Giacenza iniziale"),
                RateChange(newId(), todayEpochDay, now(), form.dailyConsumption, "Consumo iniziale"),
              ),
          )
        } else {
          val events =
            if (StockCalculator.currentRate(existing, todayEpochDay) == form.dailyConsumption) {
              existing.events
            } else {
              // A new consumption applies from today; replace any change already made today.
              existing.events.filterNot { it is RateChange && it.epochDay == todayEpochDay } +
                RateChange(newId(), todayEpochDay, now(), form.dailyConsumption, "Consumo modificato")
            }
          existing.copy(
            name = form.name,
            unit = form.unit,
            decimals = form.decimals,
            quickRefillAmount = form.quickRefillAmount,
            alertDaysBefore = form.alertDaysBefore,
            notificationsEnabled = form.notificationsEnabled,
            repeatDailyReminder = form.repeatDailyReminder,
            events = events,
            // Re-evaluate notifications against the new configuration.
            alertActive = false,
            lastNotifiedEpochDay = null,
          )
        }
      val goods =
        if (existing == null) data.goods + good
        else data.goods.map { if (it.id == good.id) good else it }
      data.copy(goods = goods)
    }
  }

  suspend fun deleteGood(goodId: String) {
    dataStore.updateData { data -> data.copy(goods = data.goods.filterNot { it.id == goodId }) }
  }

  suspend fun updateSettings(settings: AppSettings) {
    dataStore.updateData { it.copy(settings = settings) }
  }

  suspend fun updateAlertState(goodId: String, active: Boolean, notifiedEpochDay: Long?) =
    updateGood(goodId) { good ->
      good.copy(
        alertActive = active,
        lastNotifiedEpochDay = notifiedEpochDay ?: good.lastNotifiedEpochDay,
      )
    }

  private suspend fun addEvent(goodId: String, event: GoodEvent): String {
    updateGood(goodId) { good -> good.copy(events = good.events + event) }
    return event.id
  }

  private suspend fun updateGood(goodId: String, transform: (Good) -> Good) {
    dataStore.updateData { data ->
      data.copy(goods = data.goods.map { if (it.id == goodId) transform(it) else it })
    }
  }

  companion object {
    fun create(context: Context): GoodsRepository =
      GoodsRepository(
        DataStoreFactory.create(serializer = AppDataSerializer) {
          context.dataStoreFile("goods_caring.json")
        }
      )

    private fun newId() = UUID.randomUUID().toString()

    private fun now() = System.currentTimeMillis()

    /** Data proposed at first launch: one pack of cigarettes and 2 € per day. */
    fun defaultData(today: Long = LocalDate.now().toEpochDay()): AppData =
      AppData(
        goods =
          listOf(
            Good(
              id = newId(),
              name = "Sigarette",
              unit = "pacchetti",
              decimals = 0,
              quickRefillAmount = 10.0,
              events =
                listOf(
                  StockSet(newId(), today, now(), 0.0, "Giacenza iniziale"),
                  RateChange(newId(), today, now(), 1.0, "Consumo iniziale"),
                ),
            ),
            Good(
              id = newId(),
              name = "Soldi",
              unit = "€",
              decimals = 2,
              quickRefillAmount = 20.0,
              events =
                listOf(
                  StockSet(newId(), today, now(), 0.0, "Giacenza iniziale"),
                  RateChange(newId(), today, now(), 2.0, "Consumo iniziale"),
                ),
            ),
          )
      )
  }
}

private object AppDataSerializer : Serializer<AppData> {
  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  // Computed once so that ids stay stable until the data is first written.
  override val defaultValue: AppData by lazy { GoodsRepository.defaultData() }

  override suspend fun readFrom(input: InputStream): AppData =
    try {
      json.decodeFromString(AppData.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
      throw CorruptionException("Unable to read app data", e)
    }

  override suspend fun writeTo(t: AppData, output: OutputStream) {
    output.write(json.encodeToString(AppData.serializer(), t).encodeToByteArray())
  }
}
