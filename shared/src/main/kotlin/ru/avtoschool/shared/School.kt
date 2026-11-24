package ru.avtoschool.shared

enum class Catalog(val table: String, val title: String, val editable: Boolean, val columns: List<String>) {
    CATEGORIES("categories", "Категории", false, listOf("code", "title", "hours")),
    INSTRUCTORS("instructors", "Инструкторы", false, listOf("name", "phone", "certificate")),
    CARS("cars", "Автопарк", false, listOf("model", "plate", "transmission", "category_id")),
    GROUPS("study_groups", "Группы", true, listOf("title", "category_id", "instructor_id", "start_date", "end_date", "capacity")),
    STUDENTS("students", "Ученики", true, listOf("name", "phone", "group_id", "status", "enrolled_on")),
    LESSONS("lessons", "Вождение", true, listOf("student_id", "instructor_id", "car_id", "starts_at", "duration_minutes", "status", "note"));

    companion object {
        fun fromTable(table: String): Catalog? = entries.find { it.table == table }
    }
}

data class SchoolRecord(val id: Long, val version: Int, val fields: Map<String, String>) {
    operator fun get(field: String): String = fields[field].orEmpty()
}

data class Snapshot(val capturedAt: String, val tables: Map<String, List<SchoolRecord>>) {
    operator fun get(catalog: Catalog): List<SchoolRecord> = tables[catalog.table].orEmpty()
    fun find(catalog: Catalog, id: String): SchoolRecord? = this[catalog].find { it.id.toString() == id }
}

data class Change(val id: Long? = null, val version: Int? = null, val fields: Map<String, String>)
data class ApiError(val message: String)

val studentStatuses = linkedMapOf("STUDYING" to "Учится", "PAUSED" to "На паузе", "GRADUATED" to "Выпустился")
val lessonStatuses = linkedMapOf("PLANNED" to "Запланировано", "DONE" to "Проведено", "CANCELED" to "Отменено")
val transmissions = linkedMapOf("MANUAL" to "МКПП", "AUTO" to "АКПП")
