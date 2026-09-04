package me.proton.android.lumo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.proton.android.lumo.data.repository.ThemeRepository
import me.proton.android.lumo.ui.theme.AppStyle
import javax.inject.Inject

/**
 * After the WebView removal the old MainActivityViewModel (which bridged Proton web events)
 * is no longer needed. This minimal ViewModel just owns the theme selection.
 */
@HiltViewModel
class MainThemeViewModel @Inject constructor(
    private val themeRepository: ThemeRepository,
) : ViewModel() {

    private val _theme = MutableStateFlow<AppStyle?>(null)
    val theme: StateFlow<AppStyle?> = _theme.asStateFlow()

    init {
        viewModelScope.launch {
            themeRepository.observeTheme(false).collect { style ->
                _theme.update { style }
            }
        }
    }

    fun setTheme(style: AppStyle) {
        viewModelScope.launch { themeRepository.saveTheme(style) }
    }
}
