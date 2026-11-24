package ru.avtoschool.app.data

import com.google.gson.Gson
import ru.avtoschool.shared.ApiError
import ru.avtoschool.shared.Catalog
import ru.avtoschool.shared.Change
import ru.avtoschool.shared.Snapshot
import java.net.HttpURLConnection
import java.net.URI
import java.net.SocketTimeoutException

internal data class Connection(val url: String = "http://10.0.2.2:8080", val token: String = "school-demo") {
    fun checked(): Connection {
        val base = url.trim().trimEnd('/')
        val uri = runCatching { URI(base) }.getOrNull()
        require(uri != null && uri.scheme in setOf("http", "https") && uri.host != null &&
            uri.path.isNullOrEmpty() && uri.userInfo == null && uri.query == null && uri.fragment == null &&
            (uri.port == -1 || uri.port in 1..65535)) { "Введите адрес вида http://10.0.2.2:8080 без пути." }
        val accessKey = token.trim()
        require(accessKey.length in 1..256 && accessKey.all { it in '!'..'~' }) { "Введите ключ доступа без пробелов." }
        return Connection(base, accessKey)
    }
}

internal open class RemoteError(message: String, cause: Throwable? = null) : Exception(message, cause)
internal class ConnectionError(message: String, cause: Throwable) : RemoteError(message, cause)

internal class SchoolApi(private val connection: Connection) {
    private val gson = Gson()
    fun snapshot(): Snapshot = gson.fromJson(request("GET", "/api/v1/snapshot"), Snapshot::class.java)
        ?: throw RemoteError("Сервер вернул пустой снимок данных.")
    fun save(catalog: Catalog, change: Change) {
        request("POST", "/api/v1/catalogs/${catalog.table}", gson.toJson(change))
    }

    private fun request(method: String, endpoint: String, payload: String? = null): String {
        val http = URI(connection.url + endpoint).toURL().openConnection() as HttpURLConnection
        try {
            http.requestMethod = method
            http.instanceFollowRedirects = false
            http.connectTimeout = 4_000
            http.readTimeout = 8_000
            http.setRequestProperty("Accept", "application/json")
            http.setRequestProperty("Authorization", "Bearer ${connection.token}")
            if (payload != null) {
                http.doOutput = true
                http.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                http.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload) }
            }
            val status = http.responseCode
            if (status == HttpURLConnection.HTTP_NO_CONTENT) return ""
            if (status in 200..299) return http.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val message = runCatching {
                http.errorStream?.bufferedReader(Charsets.UTF_8)?.use { gson.fromJson(it, ApiError::class.java)?.message }
            }.getOrNull()
            throw RemoteError(message ?: "Сервер вернул ошибку HTTP $status.")
        } catch (error: SocketTimeoutException) {
            throw ConnectionError("Сервер не ответил вовремя. Проверьте адрес и сеть.", error)
        } catch (error: java.io.IOException) {
            throw ConnectionError("Не удалось соединиться с сервером. Проверьте адрес и сеть.", error)
        } finally {
            http.disconnect()
        }
    }
}
