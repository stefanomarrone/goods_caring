package it.marrone.goodscaring

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Home : NavKey

@Serializable data class GoodDetail(val goodId: String) : NavKey

/** Edit an existing good, or create a new one when [goodId] is null. */
@Serializable data class EditGood(val goodId: String?) : NavKey

@Serializable data object SettingsKey : NavKey
