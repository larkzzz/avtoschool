package ru.avtoschool.server

import ru.avtoschool.shared.Catalog
import ru.avtoschool.shared.Change
import ru.avtoschool.shared.SchoolRecord
import ru.avtoschool.shared.Snapshot
import ru.avtoschool.shared.lessonStatuses
import ru.avtoschool.shared.studentStatuses
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Types
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal class ApiFailure(val status: Int, message: String) : RuntimeException(message)

internal class Database(private val url: String, private val user: String, private val password: String) {
    private fun <T> transaction(readOnly: Boolean = false, work: (Connection) -> T): T =
        DriverManager.getConnection(url, user, password).use { connection ->
            connection.isReadOnly = readOnly
            if (readOnly) connection.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
            connection.autoCommit = false
            try {
                val result = work(connection)
                connection.commit()
                result
            } catch (error: Exception) {
                runCatching { connection.rollback() }.onFailure(error::addSuppressed)
                throw error
            }
        }

    fun initDemo() = transaction { connection ->
        connection.createStatement().use { sql ->
            sql.execute("SELECT pg_advisory_xact_lock(7363441)")
            val exists = sql.executeQuery("SELECT to_regclass('public.categories') IS NOT NULL").use {
                it.next()
                it.getBoolean(1)
            }
            if (!exists) {
                listOf("schema.sql", "seed.sql").forEach { file ->
                    val source = checkNotNull(javaClass.getResourceAsStream("/database/$file"))
                        .bufferedReader(Charsets.UTF_8).use { it.readText() }
                    sql.execute(source)
                }
            }
        }
    }

    fun ping() = transaction(readOnly = true) { connection ->
        connection.createStatement().use { it.execute("SELECT id FROM categories LIMIT 1") }
    }

    fun snapshot(): Snapshot = transaction(readOnly = true) { connection ->
        val capturedAt = connection.createStatement().use { statement ->
            statement.executeQuery("SELECT transaction_timestamp()").use {
                it.next()
                it.getTimestamp(1).toInstant()
            }
        }
        val tables = Catalog.entries.associate { catalog ->
            val version = if (catalog.editable) "version" else "1 AS version"
            // Имена таблиц и столбцов берутся из enum, а не из HTTP-запроса.
            val query = "SELECT id, $version, ${catalog.columns.joinToString()} FROM ${catalog.table} ORDER BY id"
            catalog.table to connection.createStatement().use { statement ->
                statement.executeQuery(query).use { rows ->
                    buildList {
                        while (rows.next()) {
                            val fields = catalog.columns.associateWith {
                                if (it == "starts_at") rows.getObject(it, LocalDateTime::class.java)
                                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                else rows.getString(it)
                            }
                            add(SchoolRecord(rows.getLong("id"), rows.getInt("version"), fields))
                        }
                    }
                }
            }
        }
        Snapshot(capturedAt.toString(), tables)
    }

    fun save(catalog: Catalog, change: Change) {
        if (change.fields.keys != catalog.columns.toSet()) throw ApiFailure(400, "Неверный набор полей формы.")
        val (id, version) = change
        if (id != null && (id <= 0 || (version ?: 0) <= 0)) {
            throw ApiFailure(400, "Неверный номер или версия записи.")
        }
        if (id == null && version != null) throw ApiFailure(400, "Для новой записи версия не указывается.")
        val f = Fields(change.fields)
        val (procedure, args) = when (catalog) {
            Catalog.STUDENTS -> "save_student" to listOf(
                f.text("name", "ФИО", 3, 120),
                f.text("phone", "Телефон", 11, 16).also {
                    if (!it.matches(Regex("""\+[0-9]{10,15}"""))) throw ApiFailure(400, "Телефон: + и от 10 до 15 цифр.")
                },
                f.id("group_id"), f.choice("status", studentStatuses), f.date("enrolled_on")
            )
            Catalog.GROUPS -> "save_group" to listOf(
                f.text("title", "Название", 1, 40), f.id("category_id"), f.id("instructor_id"),
                f.date("start_date"), f.date("end_date"), f.number("capacity", "Вместимость", 1..40)
            )
            Catalog.LESSONS -> "save_lesson" to listOf(
                f.id("student_id"), f.id("instructor_id"), f.id("car_id"), f.timestamp("starts_at"),
                f.number("duration_minutes", "Длительность", 30..180), f.choice("status", lessonStatuses),
                f.text("note", "Комментарий", 0, 300)
            )
            else -> throw ApiFailure(405, "Справочник доступен только для просмотра.")
        }
        if (catalog == Catalog.GROUPS && f.date("end_date") < f.date("start_date")) {
            throw ApiFailure(400, "Дата окончания не может быть раньше даты начала.")
        }
        transaction { connection ->
            val placeholders = List(args.size + 2) { "?" }.joinToString()
            connection.prepareStatement("CALL $procedure($placeholders)").use { sql ->
                if (id == null) sql.setNull(1, Types.BIGINT) else sql.setLong(1, id)
                if (version == null) sql.setNull(2, Types.INTEGER) else sql.setInt(2, version)
                args.forEachIndexed { index, value -> sql.setObject(index + 3, value) }
                sql.execute()
            }
        }
    }

    private class Fields(private val values: Map<String, String>) {
        fun text(key: String, label: String, min: Int, max: Int): String = values.getValue(key).trim().also {
            if (it.length !in min..max || '\u0000' in it) throw ApiFailure(400, "$label: допустимая длина $min–$max симв.")
        }
        fun id(key: String): Long = values.getValue(key).toLongOrNull()?.takeIf { it > 0 }
            ?: throw ApiFailure(400, "Выберите значение из справочника.")
        fun choice(key: String, choices: Map<String, String>): String = values.getValue(key).takeIf { it in choices }
            ?: throw ApiFailure(400, "Неизвестный статус.")
        fun number(key: String, label: String, range: IntRange): Int =
            values.getValue(key).toIntOrNull()?.takeIf { it in range }
                ?: throw ApiFailure(400, "$label: допустимо от ${range.first} до ${range.last}.")
        fun date(key: String): LocalDate = runCatching {
            LocalDate.parse(values.getValue(key)).also { require(it.year in 2000..2100) }
        }.getOrElse { throw ApiFailure(400, "Дата должна быть в формате ГГГГ-ММ-ДД (годы 2000–2100).") }
        fun timestamp(key: String): LocalDateTime = runCatching {
            LocalDateTime.parse(values.getValue(key)).also { require(it.year in 2000..2100 && it.nano == 0) }
        }.getOrElse { throw ApiFailure(400, "Укажите дату и время занятия в формате ГГГГ-ММ-ДД ЧЧ:ММ.") }
    }
}
