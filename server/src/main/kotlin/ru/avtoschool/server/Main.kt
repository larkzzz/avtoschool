package ru.avtoschool.server

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import ru.avtoschool.shared.ApiError
import ru.avtoschool.shared.Catalog
import ru.avtoschool.shared.Change
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.sql.SQLException
import java.util.concurrent.Executors

private const val MAX_REQUEST = 16_384
private val gson = Gson()

fun main(args: Array<String>) {
    val env = System.getenv()
    val address = env["SCHOOL_HOST"] ?: "127.0.0.1"
    val port = env["SCHOOL_PORT"]?.toInt() ?: 8080
    val token = env["SCHOOL_TOKEN"] ?: "school-demo"
    require(token.isNotBlank() && (address == "127.0.0.1" || address == "::1" || token != "school-demo")) {
        "Для доступа из локальной сети установите собственный SCHOOL_TOKEN."
    }
    val database = Database(
        env["SCHOOL_DB_URL"] ?: "jdbc:postgresql://127.0.0.1:5432/avtoschool",
        env["SCHOOL_DB_USER"] ?: "avtoschool",
        env["SCHOOL_DB_PASSWORD"] ?: "avtoschool_demo"
    )
    if ("--init-demo" in args) database.initDemo()
    database.ping()
    val executor = Executors.newFixedThreadPool(6)
    val server = HttpServer.create(InetSocketAddress(address, port), 32)
    server.executor = executor
    server.createContext("/") { exchange ->
        exchange.use {
            try {
                route(exchange, database, token)
            } catch (error: ApiFailure) {
                exchange.write(error.status, ApiError(error.message.orEmpty()))
            } catch (error: SQLException) {
                val (status, message) = when (error.sqlState) {
                    "P2001" -> 409 to error.message.orEmpty().substringBefore('\n').removePrefix("ERROR: ")
                    "P2002" -> 409 to "Запись уже изменилась. Обновите данные и повторите правку."
                    "23P01" -> 409 to "В это время ученик, инструктор или автомобиль уже занят."
                    "23505" -> 409 to "Запись с таким телефоном или названием уже существует."
                    "23503" -> 409 to "Связанная запись не найдена. Обновите данные."
                    "23514", "23502", "22001" -> 400 to "Данные не отвечают ограничениям базы."
                    "40001", "40P01" -> 409 to "Данные одновременно меняются. Повторите попытку."
                    else -> {
                        System.err.println("Database error: ${error.sqlState}")
                        503 to "База данных временно недоступна."
                    }
                }
                exchange.write(status, ApiError(message))
            } catch (error: Exception) {
                System.err.println("Request error: ${error.javaClass.simpleName}")
                runCatching { exchange.write(500, ApiError("Не удалось обработать запрос.")) }
            }
        }
    }
    Runtime.getRuntime().addShutdownHook(Thread { server.stop(1); executor.shutdown() })
    server.start()
    println("Avtoschool API listening on $address:$port")
}

private fun route(exchange: HttpExchange, db: Database, token: String) {
    val path = exchange.requestURI.path
    val method = exchange.requestMethod
    if (path == "/health") {
        if (method != "GET") throw ApiFailure(405, "Используйте GET.")
        db.ping()
        exchange.write(200, mapOf("status" to "ok"))
        return
    }
    if (!path.startsWith("/api/v1/")) throw ApiFailure(404, "Адрес не найден.")
    val supplied = exchange.requestHeaders.getFirst("Authorization").orEmpty()
    if (!MessageDigest.isEqual("Bearer $token".toByteArray(Charsets.UTF_8), supplied.toByteArray(Charsets.UTF_8))) {
        exchange.responseHeaders.set("WWW-Authenticate", "Bearer")
        throw ApiFailure(401, "Проверьте ключ доступа в настройках.")
    }
    when {
        path == "/api/v1/snapshot" -> {
            if (method != "GET") throw ApiFailure(405, "Используйте GET.")
            exchange.write(200, db.snapshot())
        }
        path.startsWith("/api/v1/catalogs/") -> {
            val catalog = Catalog.fromTable(path.removePrefix("/api/v1/catalogs/"))
                ?: throw ApiFailure(404, "Справочник не найден.")
            if (!catalog.editable || method != "POST") throw ApiFailure(405, "Операция не поддерживается.")
            if (!exchange.requestHeaders.getFirst("Content-Type").orEmpty().substringBefore(';').trim()
                    .equals("application/json", ignoreCase = true)) throw ApiFailure(415, "Ожидается application/json.")
            val payload = exchange.requestBody.readNBytes(MAX_REQUEST + 1)
            if (payload.size > MAX_REQUEST) throw ApiFailure(413, "Слишком большой запрос.")
            db.save(catalog, parseChange(payload.toString(Charsets.UTF_8)))
            exchange.write(204)
        }
        else -> throw ApiFailure(404, "Адрес не найден.")
    }
}

private fun parseChange(body: String): Change {
    return try {
        val objectJson = JsonParser.parseString(body).asJsonObject
        if (objectJson.keySet().any { it !in setOf("id", "version", "fields") }) throw IllegalStateException()
        fun number(key: String): Long? = objectJson.get(key)?.takeUnless { it.isJsonNull }?.let {
            if (!it.isJsonPrimitive || !it.asJsonPrimitive.isNumber) throw IllegalStateException()
            it.asString.toLong()
        }
        val fields = (objectJson.get("fields") as? JsonObject)?.entrySet()?.associate { (key, value) ->
            if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) throw IllegalStateException()
            key to value.asString
        } ?: throw IllegalStateException()
        val version = number("version")?.let(Math::toIntExact)
        Change(number("id"), version, fields)
    } catch (_: JsonParseException) {
        throw ApiFailure(400, "Некорректный JSON.")
    } catch (_: RuntimeException) {
        throw ApiFailure(400, "Некорректная структура запроса.")
    }
}

private fun HttpExchange.write(status: Int, value: Any? = null) {
    responseHeaders.set("Cache-Control", "no-store")
    if (value == null) {
        sendResponseHeaders(status, -1)
    } else {
        val bytes = gson.toJson(value).toByteArray(Charsets.UTF_8)
        responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.write(bytes)
    }
}
