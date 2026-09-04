package me.proton.android.lumo.chat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.proton.android.lumo.data.db.entity.LumoEntity
import me.proton.android.lumo.data.repository.LumoRepository
import javax.inject.Inject

@HiltViewModel
class LumoManagerViewModel @Inject constructor(
    private val lumoRepository: LumoRepository,
) : ViewModel() {

    val lumos: StateFlow<List<LumoEntity>> =
        lumoRepository.observeAll().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    private val _editing = MutableStateFlow<LumoEntity?>(null)
    val editing: StateFlow<LumoEntity?> = _editing.asStateFlow()

    /**
     * Pass `null` to start fresh (new Lumo), or a Lumo id to load it from the DB for editing.
     */
    fun beginEditing(lumoId: Long?) {
        viewModelScope.launch {
            _editing.value = lumoId?.let { lumoRepository.getById(it) }
        }
    }

    fun saveEditing(
        name: String,
        description: String,
        systemPrompt: String,
        modelName: String?,
        temperature: Float?,
        accentColor: String,
        avatarTag: String,
    ) {
        val editing = _editing.value
        viewModelScope.launch {
            if (editing == null) {
                lumoRepository.create(
                    name = name,
                    description = description,
                    systemPrompt = systemPrompt,
                    modelName = modelName?.ifBlank { null },
                    temperature = temperature,
                    accentColor = accentColor,
                    avatarTag = avatarTag,
                )
            } else {
                lumoRepository.update(
                    editing.copy(
                        name = name.ifBlank { editing.name },
                        description = description,
                        systemPrompt = systemPrompt,
                        modelName = modelName?.ifBlank { null },
                        temperature = temperature,
                        accentColor = accentColor,
                        avatarTag = avatarTag,
                    )
                )
            }
            _editing.value = null
        }
    }

    fun cancelEditing() {
        _editing.value = null
    }

    fun delete(lumo: LumoEntity) {
        viewModelScope.launch { lumoRepository.delete(lumo) }
    }

    fun archive(lumoId: Long) {
        viewModelScope.launch { lumoRepository.archive(lumoId) }
    }
}
