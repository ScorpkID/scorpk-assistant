package com.scorpk.assistant.ui.account

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scorpk.assistant.appContainer
import com.scorpk.assistant.data.remote.supabase.Profile
import com.scorpk.assistant.data.remote.supabase.Subscription
import com.scorpk.assistant.data.remote.supabase.SupabaseErrors
import com.scorpk.assistant.data.remote.supabase.TaskItem
import com.scorpk.assistant.data.repository.AuthState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer
    private val auth = container.authRepository
    private val tasksRepo = container.taskRepository
    private val chatRepository = container.chatRepository

    val authState: StateFlow<AuthState> = auth.authState
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Loading)

    val conversationCount: StateFlow<Int> = chatRepository.conversationCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val commandCount: StateFlow<Int> = chatRepository.userCommandCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    private val _tasks = MutableStateFlow<List<TaskItem>>(emptyList())
    val tasks: StateFlow<List<TaskItem>> = _tasks.asStateFlow()

    private val _subscription = MutableStateFlow<Subscription?>(null)
    /** Plan del usuario; null se muestra como Free. */
    val subscription: StateFlow<Subscription?> = _subscription.asStateFlow()

    private val _planLoaded = MutableStateFlow(false)
    /** true cuando ya se consultó el plan: evita mostrar la invitación a Pro a un suscriptor mientras carga. */
    val planLoaded: StateFlow<Boolean> = _planLoaded.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            auth.callbackMessages.collect { _messages.emit(it) }
        }
        viewModelScope.launch {
            authState.distinctUntilChanged { old, new ->
                old is AuthState.SignedIn && new is AuthState.SignedIn && old.userId == new.userId
            }.collect { state ->
                if (state is AuthState.SignedIn) {
                    refreshUserData()
                } else {
                    _profile.value = null
                    _tasks.value = emptyList()
                    _subscription.value = null
                    _planLoaded.value = false
                }
            }
        }
    }

    fun signIn(email: String, password: String) = runBusy {
        auth.signInWithEmail(email, password).onFailure { report(it) }
    }

    fun signUp(email: String, password: String) = runBusy {
        auth.signUpWithEmail(email, password)
            .onSuccess { needsConfirmation ->
                if (needsConfirmation) _messages.emit("Cuenta creada. Revisa tu correo para confirmarla.")
            }
            .onFailure { report(it) }
    }

    fun signInWithGoogle() = runBusy { auth.signInWithGoogle().onFailure { report(it) } }

    fun signInWithGithub() = runBusy { auth.signInWithGithub().onFailure { report(it) } }

    fun signOut() = runBusy { auth.signOut().onFailure { report(it) } }

    fun saveProfile(fullName: String, customWakeWord: String) = runBusy {
        auth.saveProfile(fullName, customWakeWord)
            .onSuccess {
                _profile.value = it
                _messages.emit("Perfil actualizado.")
            }
            .onFailure { report(it) }
    }

    fun addTask(title: String) {
        if (title.isBlank()) return
        runBusy {
            tasksRepo.addTask(title)
                .onSuccess { task -> _tasks.value = listOf(task) + _tasks.value }
                .onFailure { report(it) }
        }
    }

    fun toggleTask(task: TaskItem) {
        val id = task.id ?: return
        val updated = task.copy(isCompleted = !task.isCompleted)
        _tasks.value = _tasks.value.map { if (it.id == id) updated else it }
        viewModelScope.launch {
            tasksRepo.setCompleted(id, updated.isCompleted).onFailure {
                _tasks.value = _tasks.value.map { t -> if (t.id == id) task else t }
                report(it)
            }
        }
    }

    fun deleteTask(task: TaskItem) {
        val id = task.id ?: return
        val previous = _tasks.value
        _tasks.value = previous.filterNot { it.id == id }
        viewModelScope.launch {
            tasksRepo.deleteTask(id).onFailure {
                _tasks.value = previous
                report(it)
            }
        }
    }

    fun refreshUserData() = runBusy {
        auth.getProfile().onSuccess { _profile.value = it }.onFailure { report(it) }
        tasksRepo.tasks().onSuccess { _tasks.value = it }.onFailure { report(it) }
        // El plan es informativo: si no se puede leer, se muestra Free sin molestar con un error.
        container.subscriptionRepository.current()
            .onSuccess {
                _subscription.value = it
                _planLoaded.value = true
            }
            .onFailure { Log.w(TAG, "No se pudo leer la suscripción", it) }
        container.connectorManager.syncWithRemote()?.let { _messages.emit(it) }
    }

    fun clearLocalHistory() {
        viewModelScope.launch {
            chatRepository.clearHistory()
            _messages.emit("Historial local borrado.")
        }
    }

    private fun runBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } finally {
                _busy.value = false
            }
        }
    }

    private suspend fun report(error: Throwable) {
        Log.e(TAG, "Operación de Supabase fallida", error)
        _messages.emit(SupabaseErrors.describe(error))
    }

    private companion object {
        const val TAG = "ScorpkAssistant"
    }
}
