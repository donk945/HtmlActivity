package com.hfad.htmlactivity.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hfad.htmlactivity.HtmlActivityApp
import com.hfad.htmlactivity.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val loginSuccess: Boolean = false,
    val registerSuccess: Boolean = false
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository: AuthRepository =
        (application as HtmlActivityApp).authRepository

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = "请输入邮箱和密码") }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            authRepository.login(email.trim(), password).fold(
                onSuccess = { _uiState.update { it.copy(isLoading = false, loginSuccess = true) } },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, error = friendlyError(e)) } }
            )
        }
    }

    fun register(email: String, password: String, confirm: String, name: String? = null) {
        when {
            email.isBlank() || password.isBlank() -> {
                _uiState.update { it.copy(error = "请输入邮箱和密码") }
                return
            }
            password != confirm -> {
                _uiState.update { it.copy(error = "两次输入的密码不一致") }
                return
            }
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            // 先检测 PocketBase 服务是否连通
            authRepository.checkServer().fold(
                onSuccess = {
                    authRepository.register(email.trim(), password, confirm, name?.trim()).fold(
                        onSuccess = { _uiState.update { it.copy(isLoading = false, registerSuccess = true) } },
                        onFailure = { e -> _uiState.update { it.copy(isLoading = false, error = friendlyError(e)) } }
                    )
                },
                onFailure = {
                    _uiState.update {
                        it.copy(isLoading = false, error = "网络断开：无法连接 PocketBase 服务器，请确认服务器已启动")
                    }
                }
            )
        }
    }

    fun consumeLoginSuccess() = _uiState.update { it.copy(loginSuccess = false) }
    fun consumeRegisterSuccess() = _uiState.update { it.copy(registerSuccess = false) }
    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun friendlyError(e: Throwable): String = when (e) {
        is UnknownHostException -> "网络断开：无法连接服务器，请确认服务器已启动"
        is SocketTimeoutException -> "连接超时：请检查网络或服务器状态"
        is java.net.ConnectException -> "网络断开：无法连接服务器，请确认服务器已启动"
        is HttpException -> when (e.code()) {
            401 -> "邮箱或密码错误"
            403 -> "没有访问权限"
            404 -> "服务器地址错误或服务未启动"
            else -> e.message ?: "请求失败（${e.code()}）"
        }
        else -> e.message ?: "未知错误"
    }
}
