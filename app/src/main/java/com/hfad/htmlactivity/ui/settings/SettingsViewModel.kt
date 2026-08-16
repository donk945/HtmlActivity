package com.hfad.htmlactivity.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hfad.htmlactivity.HtmlActivityApp
import com.hfad.htmlactivity.data.repository.AuthRepository
import com.hfad.htmlactivity.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val baseUrl: String = "",
    val model: String = "",
    val apiKey: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val loggedOut: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository: SettingsRepository =
        (application as HtmlActivityApp).settingsRepository
    private val authRepository: AuthRepository =
        (application as HtmlActivityApp).authRepository

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            settingsRepository.getSettings().fold(
                onSuccess = { s ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            baseUrl = s?.baseUrl.orEmpty(),
                            model = s?.model.orEmpty(),
                            apiKey = s?.apiKey.orEmpty()
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
            )
        }
    }

    fun save(baseUrl: String, model: String, apiKey: String) {
        when {
            baseUrl.isBlank() || model.isBlank() || apiKey.isBlank() -> {
                _uiState.update { it.copy(error = "请填写完整：服务器地址、模型、API Key") }
                return
            }
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, saved = false) }
            settingsRepository.saveSettings(baseUrl.trim(), model.trim(), apiKey.trim()).fold(
                onSuccess = { _uiState.update { it.copy(isSaving = false, saved = true) } },
                onFailure = { e -> _uiState.update { it.copy(isSaving = false, error = e.message) } }
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.update { it.copy(loggedOut = true) }
        }
    }

    fun consumeSaved() = _uiState.update { it.copy(saved = false) }
}
