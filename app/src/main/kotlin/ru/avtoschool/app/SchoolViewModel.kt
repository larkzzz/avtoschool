package ru.avtoschool.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.avtoschool.app.data.Connection
import ru.avtoschool.app.data.ConnectionError
import ru.avtoschool.app.data.LocalSchool
import ru.avtoschool.app.data.RemoteError
import ru.avtoschool.app.data.SchoolApi
import ru.avtoschool.shared.Catalog
import ru.avtoschool.shared.Change
import ru.avtoschool.shared.Snapshot

internal enum class DataState(val label: String) {
    LOADING("Загрузка"), DEMO("Деморежим"), ONLINE("Сервер доступен"), OFFLINE("Локальная копия")
}

internal data class SchoolState(
    val snapshot: Snapshot? = null,
    val connection: Connection? = null,
    val mode: DataState = DataState.LOADING,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

internal class SchoolViewModel(application: Application) : AndroidViewModel(application) {
    private val local = LocalSchool(application)
    private val preferences = application.getSharedPreferences("connection", Application.MODE_PRIVATE)
    private val gson = Gson()
    var state by mutableStateOf(SchoolState())
        private set

    init {
        val url = preferences.getString("url", null)
        val connection = url?.let { Connection(it, preferences.getString("token", "").orEmpty()) }
        state = state.copy(connection = connection)
        launch {
            val cached = withContext(Dispatchers.IO) {
                if (connection == null) local.load(DEMO) ?: installDemo() else local.load(connection.source())
            }
            state = state.copy(snapshot = cached, mode = if (connection == null) DataState.DEMO else DataState.OFFLINE)
            if (connection != null) sync(connection)
        }
    }

    fun refresh() {
        val connection = state.connection ?: return
        launch { sync(connection) }
    }

    fun connect(connection: Connection, success: () -> Unit) = launch {
        val validated = connection.checked()
        val snapshot = withContext(Dispatchers.IO) {
            val fresh = download(validated)
            preferences.edit().putString("url", validated.url).putString("token", validated.token).apply()
            fresh
        }
        state = state.copy(snapshot = snapshot, connection = validated, mode = DataState.ONLINE, message = "Подключение установлено.")
        success()
    }

    fun demo(success: () -> Unit) = launch {
        val snapshot = withContext(Dispatchers.IO) {
            val result = installDemo()
            preferences.edit().clear().apply()
            result
        }
        state = state.copy(snapshot = snapshot, connection = null, mode = DataState.DEMO, message = "Открыты демонстрационные данные.")
        success()
    }

    fun save(catalog: Catalog, change: Change, success: () -> Unit) = launch {
        val connection = state.connection?.takeIf { state.mode == DataState.ONLINE }
            ?: throw RemoteError("Для записи сначала подключитесь к серверу.")
        try {
            withContext(Dispatchers.IO) { SchoolApi(connection).save(catalog, change) }
        } catch (error: ConnectionError) {
            state = state.copy(mode = DataState.OFFLINE,
                error = "Не удалось подтвердить сохранение. Закройте форму и обновите данные, прежде чем повторять запись.")
            return@launch
        }
        success()
        try {
            val snapshot = withContext(Dispatchers.IO) { download(connection) }
            state = state.copy(snapshot = snapshot, message = "Запись сохранена.")
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            state = state.copy(mode = DataState.OFFLINE,
                error = "Запись сохранена на сервере, но локальная копия не обновилась. Нажмите «Обновить».")
        }
    }

    fun clearNotices() {
        state = state.copy(message = null, error = null)
    }

    private suspend fun sync(connection: Connection) {
        try {
            val snapshot = withContext(Dispatchers.IO) { download(connection) }
            state = state.copy(snapshot = snapshot, mode = DataState.ONLINE, message = "Данные обновлены.")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            state = state.copy(mode = DataState.OFFLINE)
            throw error
        }
    }

    private fun download(connection: Connection): Snapshot {
        local.replace(SchoolApi(connection).snapshot(), connection.source())
        return checkNotNull(local.load(connection.source()))
    }

    private fun installDemo(): Snapshot {
        val snapshot = getApplication<Application>().assets.open("demo.json").bufferedReader(Charsets.UTF_8).use {
            gson.fromJson(it, Snapshot::class.java)
        }
        local.replace(snapshot, DEMO)
        return checkNotNull(local.load(DEMO))
    }

    private fun launch(work: suspend () -> Unit) {
        if (state.busy) return
        state = state.copy(busy = true, error = null, message = null)
        viewModelScope.launch {
            try {
                work()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val detail = if (error is RemoteError || error is IllegalArgumentException) error.message else null
                state = state.copy(error = detail ?: "Не удалось обработать данные. Повторите попытку.")
            } finally {
                state = state.copy(busy = false)
            }
        }
    }

    private fun Connection.source(): String = url
    private companion object {
        const val DEMO = "demo"
    }
}
