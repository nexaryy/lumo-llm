package me.proton.android.lumo.ui.text

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * A piece of UI text that can be either a raw string or a string-resource id.
 * Useful in ViewModels that don't have a [Context] (so they can't resolve resources directly).
 */
sealed interface UiText {
    data class StringText(val value: String) : UiText
    data class ResText(@StringRes val resId: Int) : UiText

    fun getText(context: Context): String = when (this) {
        is StringText -> value
        is ResText -> context.getString(resId)
    }
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.StringText -> value
    is UiText.ResText -> LocalContext.current.getString(resId)
}
