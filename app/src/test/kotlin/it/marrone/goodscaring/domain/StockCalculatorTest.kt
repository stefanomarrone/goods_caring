package it.marrone.goodscaring.domain

import it.marrone.goodscaring.data.Good
import it.marrone.goodscaring.data.GoodEvent
import it.marrone.goodscaring.data.RateChange
import it.marrone.goodscaring.data.Refill
import it.marrone.goodscaring.data.StockSet
import it.marrone.goodscaring.notifications.StockCheckWorker
import it.marrone.goodscaring.notifications.StockCheckWorker.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StockCalculatorTest {

  private val start = 20_000L
  private var counter = 0L

  private fun good(vararg events: GoodEvent, alertDays: Int = 7) =
    Good(id = "g", name = "Sigarette", unit = "pacchetti", quickRefillAmount = 10.0, alertDaysBefore = alertDays, events = events.toList())

  private fun set(day: Long, stock: Double) = StockSet("s${counter++}", day, counter, stock)

  private fun refill(day: Long, amount: Double) = Refill("r${counter++}", day, counter, amount)

  private fun rate(day: Long, value: Double) = RateChange("c${counter++}", day, counter, value)

  @Test
  fun `stock decreases by one pack per day`() {
    val g = good(set(start, 10.0), rate(start, 1.0))
    val status = StockCalculator.status(g, start + 3)
    assertEquals(7.0, status.currentStock, 0.0)
    assertEquals(7, status.daysLeft)
    assertEquals(start + 10, status.runOutEpochDay)
  }

  @Test
  fun `refill is added to stock`() {
    val g = good(set(start, 2.0), rate(start, 1.0), refill(start + 1, 10.0))
    assertEquals(11.0, StockCalculator.status(g, start + 1).currentStock, 0.0)
  }

  @Test
  fun `stock never goes below zero`() {
    val g = good(set(start, 2.0), rate(start, 1.0), refill(start + 5, 4.0))
    val ledger = StockCalculator.ledger(g, start + 5)
    assertEquals(0.0, ledger[3].endStock, 0.0)
    assertEquals(0.0, ledger[3].consumed, 0.0)
    assertEquals(4.0, StockCalculator.status(g, start + 5).currentStock, 0.0)
  }

  @Test
  fun `rate change applies from its day`() {
    val g = good(set(start, 20.0), rate(start, 2.0), rate(start + 2, 1.0))
    // Day 0 and 1 consume 2 each, then 1.
    assertEquals(16.0, StockCalculator.status(g, start + 2).currentStock, 0.0)
    assertEquals(15.0, StockCalculator.status(g, start + 3).currentStock, 0.0)
  }

  @Test
  fun `money with decimals`() {
    val g = good(set(start, 15.0), rate(start, 2.0))
    assertEquals(7, StockCalculator.status(g, start).daysLeft)
  }

  @Test
  fun `zero consumption never runs out`() {
    val g = good(set(start, 5.0), rate(start, 0.0))
    assertNull(StockCalculator.status(g, start).daysLeft)
  }

  @Test
  fun `notification decision`() {
    val low = good(set(start, 7.0), rate(start, 1.0))
    val status = StockCalculator.status(low, start)
    assertEquals(Decision.NOTIFY, StockCheckWorker.decide(low, status, start))

    val notifiedToday = low.copy(alertActive = true, lastNotifiedEpochDay = start)
    assertEquals(Decision.NONE, StockCheckWorker.decide(notifiedToday, status, start))
    assertEquals(Decision.NOTIFY, StockCheckWorker.decide(notifiedToday, status, start + 1))

    val once = notifiedToday.copy(repeatDailyReminder = false)
    assertEquals(Decision.NONE, StockCheckWorker.decide(once, status, start + 1))

    val high = good(set(start, 8.0), rate(start, 1.0)).copy(alertActive = true)
    assertEquals(Decision.RESET, StockCheckWorker.decide(high, StockCalculator.status(high, start), start))
  }
}
