package ru.avtoschool.app

import ru.avtoschool.shared.Catalog
import ru.avtoschool.shared.SchoolRecord
import ru.avtoschool.shared.Snapshot
import ru.avtoschool.shared.lessonStatuses
import ru.avtoschool.shared.studentStatuses
import ru.avtoschool.shared.transmissions
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

internal data class RecordText(val title: String, val subtitle: String, val badge: String = "", val detail: String = "")

internal fun Snapshot.describe(catalog: Catalog, record: SchoolRecord): RecordText = when (catalog) {
    Catalog.CATEGORIES -> RecordText(record["title"], "Категория ${record["code"]}", "${record["hours"]} ч")
    Catalog.INSTRUCTORS -> RecordText(record["name"], record["phone"], "Свид. ${record["certificate"]}")
    Catalog.CARS -> RecordText(record["model"], "${record["plate"]} · ${ref(Catalog.CATEGORIES, record["category_id"])}",
        transmissions[record["transmission"]].orEmpty())
    Catalog.GROUPS -> {
        val occupied = this[Catalog.STUDENTS].count { it["group_id"] == record.id.toString() }
        RecordText(record["title"], ref(Catalog.INSTRUCTORS, record["instructor_id"]), "$occupied / ${record["capacity"]} мест",
            "${showDate(record["start_date"])} — ${showDate(record["end_date"])} · ${ref(Catalog.CATEGORIES, record["category_id"])}")
    }
    Catalog.STUDENTS -> RecordText(record["name"], "${ref(Catalog.GROUPS, record["group_id"])} · ${record["phone"]}",
        studentStatuses[record["status"]].orEmpty(), "Зачисление: ${showDate(record["enrolled_on"])}")
    Catalog.LESSONS -> RecordText(ref(Catalog.STUDENTS, record["student_id"]),
        "${showDateTime(record["starts_at"])} · ${record["duration_minutes"]} мин",
        lessonStatuses[record["status"]].orEmpty(),
        listOf(ref(Catalog.INSTRUCTORS, record["instructor_id"]), ref(Catalog.CARS, record["car_id"]), record["note"])
            .filter(String::isNotBlank).joinToString(" · "))
}

private fun Snapshot.ref(catalog: Catalog, id: String): String =
    find(catalog, id)?.let { referenceLabel(catalog, it) }.orEmpty().ifBlank { "—" }

internal fun referenceLabel(catalog: Catalog, record: SchoolRecord): String = when (catalog) {
    Catalog.CATEGORIES -> record["code"]
    Catalog.GROUPS -> record["title"]
    Catalog.STUDENTS, Catalog.INSTRUCTORS -> record["name"]
    Catalog.CARS -> "${record["model"]} · ${record["plate"]}"
    Catalog.LESSONS -> showDateTime(record["starts_at"])
}

internal fun Snapshot.search(catalog: Catalog, query: String): List<SchoolRecord> {
    val result = this[catalog].filter { record ->
        if (query.isBlank()) true else {
            val text = describe(catalog, record)
            val fields = catalog.columns.filterNot { it.endsWith("_id") }.map { record[it] }
            matches(query, fields + listOf(text.title, text.subtitle, text.badge, text.detail))
        }
    }
    return when (catalog) {
        Catalog.LESSONS -> result.sortedBy { it["starts_at"] }
        Catalog.STUDENTS, Catalog.INSTRUCTORS -> result.sortedBy { normalize(it["name"]) }
        else -> result
    }
}

private fun normalize(value: String): String = value.lowercase(Locale.ROOT).replace('ё', 'е')

internal fun matches(query: String, fields: List<String>): Boolean {
    val haystack = normalize(fields.joinToString(" "))
    val phones = fields.filter { it.matches(Regex("""[+][0-9]{10,15}""")) }
    val digits = query.filter { it in '0'..'9' }
    if (digits.length >= 4 && query.all { it in "0123456789 +-()" } &&
        phones.any { digits in it || (digits.length == 11 && digits.startsWith("8") && "7${digits.drop(1)}" in it) }) return true
    return normalize(query.trim()).split(Regex("\\s+")).all(haystack::contains)
}

internal fun showDate(value: String): String = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
}.getOrDefault(value)

internal fun showDateTime(value: String): String = runCatching {
    LocalDateTime.parse(value).format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm"))
}.getOrDefault(value)

internal fun defaultFields(catalog: Catalog, now: LocalDateTime = LocalDateTime.now()): Map<String, String> {
    val today = now.toLocalDate()
    val defaults = when (catalog) {
        Catalog.GROUPS -> mapOf("start_date" to today.toString(), "end_date" to today.plusMonths(3).toString(), "capacity" to "16")
        Catalog.STUDENTS -> mapOf("status" to "STUDYING", "enrolled_on" to today.toString())
        Catalog.LESSONS -> mapOf("status" to "PLANNED", "duration_minutes" to "60",
            "starts_at" to now.plusHours(1).truncatedTo(ChronoUnit.HOURS).toString())
        else -> emptyMap()
    }
    return catalog.columns.associateWith { defaults[it].orEmpty() }
}

internal data class Validation(val fields: Map<String, String>, val error: String? = null)

internal fun validate(catalog: Catalog, input: Map<String, String>): Validation {
    val f = input.mapValues { (key, value) ->
        val trimmed = value.trim()
        when (key) {
            "starts_at" -> trimmed.replace(' ', 'T')
            "phone" -> trimmed.replace(Regex("""[\s()-]"""), "")
            else -> trimmed
        }
    }
    fun value(key: String) = f[key].orEmpty()
    fun date(key: String) = runCatching { LocalDate.parse(value(key)) }.getOrNull()?.takeIf { it.year in 2000..2100 }
    fun reference(key: String) = (value(key).toLongOrNull() ?: 0) > 0
    fun number(key: String, range: IntRange) = value(key).toIntOrNull()?.let { it in range } == true
    val error = when (catalog) {
        Catalog.STUDENTS -> when {
            value("name").length !in 3..120 -> "Введите ФИО (от 3 до 120 символов)."
            !value("phone").matches(Regex("""\+[0-9]{10,15}""")) -> "Телефон: + и от 10 до 15 цифр."
            !reference("group_id") -> "Выберите группу."
            value("status") !in studentStatuses -> "Выберите статус ученика."
            date("enrolled_on") == null -> "Укажите дату зачисления в формате ГГГГ-ММ-ДД."
            else -> null
        }
        Catalog.GROUPS -> {
            val start = date("start_date")
            val end = date("end_date")
            when {
                value("title").length !in 1..40 -> "Введите название группы (до 40 символов)."
                !reference("category_id") || !reference("instructor_id") -> "Выберите категорию и инструктора."
                start == null || end == null -> "Укажите даты в формате ГГГГ-ММ-ДД."
                end < start -> "Дата окончания не может быть раньше даты начала."
                !number("capacity", 1..40) -> "Вместимость — от 1 до 40 человек."
                else -> null
            }
        }
        Catalog.LESSONS -> when {
            !reference("student_id") || !reference("instructor_id") || !reference("car_id") -> "Выберите ученика, инструктора и автомобиль."
            runCatching { LocalDateTime.parse(value("starts_at")) }.getOrNull()?.let { it.year in 2000..2100 && it.nano == 0 } != true ->
                "Укажите начало в формате ГГГГ-ММ-ДД ЧЧ:ММ."
            !number("duration_minutes", 30..180) -> "Длительность — от 30 до 180 минут."
            value("status") !in lessonStatuses -> "Выберите статус занятия."
            value("note").length > 300 -> "Комментарий — не более 300 символов."
            else -> null
        }
        else -> "Справочник доступен только для просмотра."
    }
    return Validation(f, error)
}
