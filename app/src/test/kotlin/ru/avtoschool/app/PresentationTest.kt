package ru.avtoschool.app

import org.junit.Assert.*
import org.junit.Test
import ru.avtoschool.shared.Catalog
import ru.avtoschool.shared.SchoolRecord
import ru.avtoschool.shared.Snapshot

class PresentationTest {
    private val students = mapOf("name" to "Дарья Соловьёва", "phone" to "+79990000212",
        "group_id" to "4", "status" to "STUDYING", "enrolled_on" to "2026-11-18")

    @Test fun `search resolves related record and handles Russian spelling`() {
        val data = Snapshot("2026-11-24T10:00:00Z", mapOf(
            "students" to listOf(SchoolRecord(12, 1, students)),
            "study_groups" to listOf(SchoolRecord(4, 1, mapOf("title" to "С-26/01")))
        ))
        assertEquals(listOf(12L), data.search(Catalog.STUDENTS, "СоЛоВьЕвА с-26/01").map { it.id })
        assertTrue(data.search(Catalog.STUDENTS, "Соколов").isEmpty())
    }

    @Test fun `formatted phone with either prefix is searchable`() {
        assertTrue(matches("8 (999) 000-02-12", listOf(students.getValue("phone"))))
        assertFalse(matches("+7 (999) 000-02-15", listOf(students.getValue("phone"))))
        assertEquals("+79990000212", validate(Catalog.STUDENTS, students + ("phone" to "+7 (999) 000-02-12")).fields["phone"])
    }

    @Test fun `invalid calendar date and missing group are rejected`() {
        assertNotNull(validate(Catalog.STUDENTS, students + ("enrolled_on" to "2026-02-29")).error)
        assertNotNull(validate(Catalog.STUDENTS, students + ("group_id" to "0")).error)
        assertNull(validate(Catalog.STUDENTS, students).error)
    }

    @Test fun `group cannot end before it starts`() {
        val group = mapOf("title" to "Б-26/03", "category_id" to "1", "instructor_id" to "2",
            "start_date" to "2026-11-25", "end_date" to "2026-11-20", "capacity" to "15")
        assertNotNull(validate(Catalog.GROUPS, group).error)
        assertNull(validate(Catalog.GROUPS, group + ("end_date" to "2027-02-01")).error)
        assertNotNull(validate(Catalog.GROUPS, group + mapOf("end_date" to "2027-02-01", "capacity" to "41")).error)
    }

    @Test fun `lesson start is normalized and duration is checked`() {
        val lesson = mapOf("student_id" to "1", "instructor_id" to "1", "car_id" to "1", "status" to "PLANNED",
            "starts_at" to "2026-11-25 09:00", "duration_minutes" to "30", "note" to "")
        val result = validate(Catalog.LESSONS, lesson)
        assertNull(result.error)
        assertEquals("2026-11-25T09:00", result.fields["starts_at"])
        assertNotNull(validate(Catalog.LESSONS, lesson + ("duration_minutes" to "29")).error)
    }

    @Test fun `settings permit local HTTP and reject embedded credentials or paths`() {
        val input = ru.avtoschool.app.data.Connection("  http://10.0.2.2:8080/ ", " test-key ")
        assertEquals("http://10.0.2.2:8080", input.checked().url)
        assertEquals("test-key", input.checked().token)
        assertThrows(IllegalArgumentException::class.java) { input.copy(url = "http://user@localhost:8080").checked() }
        assertThrows(IllegalArgumentException::class.java) { input.copy(url = "http://localhost/api").checked() }
    }
}
