package me.proton.android.lumo.chat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.proton.android.lumo.data.repository.ApiConfigRepository
import me.proton.android.lumo.llm.model.ApiConfig
import me.proton.android.lumo.llm.model.LlmProviderType
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val apiConfigRepository: ApiConfigRepository,
) : ViewModel() {

    val apiConfig: StateFlow<ApiConfig> = apiConfigRepository.apiConfig
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ApiConfig(),
        )

    fun update(
        providerType: LlmProviderType? = null,
        baseUrl: String? = null,
        apiKey: String? = null,
        defaultModel: String? = null,
        defaultTemperature: Float? = null,
        defaultMaxTokens: Int? = null,
        requestTimeoutSeconds: Long? = null,
        connectTimeoutSeconds: Long? = null,
        extraHeadersRaw: String? = null,
        customBodyTemplate: String? = null,
        customMethod: String? = null,
        customResponsePath: String? = null,
    ) {
        viewModelScope.launch {
            val current = apiConfig.value
            apiConfigRepository.save(
                current.copy(
                    providerType = providerType ?: current.providerType,
                    baseUrl = baseUrl ?: current.baseUrl,
                    apiKey = apiKey ?: current.apiKey,
                    defaultModel = defaultModel ?: current.defaultModel,
                    defaultTemperature = defaultTemperature ?: current.defaultTemperature,
                    defaultMaxTokens = defaultMaxTokens ?: current.defaultMaxTokens,
                    requestTimeoutSeconds = requestTimeoutSeconds ?: current.requestTimeoutSeconds,
                    connectTimeoutSeconds = connectTimeoutSeconds ?: current.connectTimeoutSeconds,
                    extraHeadersRaw = extraHeadersRaw ?: current.extraHeadersRaw,
                    customBodyTemplate = customBodyTemplate ?: current.customBodyTemplate,
                    customMethod = customMethod ?: current.customMethod,
                    customResponsePath = customResponsePath ?: current.customResponsePath,
                )
            )
        }
    }
}
