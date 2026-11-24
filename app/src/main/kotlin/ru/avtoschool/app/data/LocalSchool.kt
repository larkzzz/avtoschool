package ru.avtoschool.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import ru.avtoschool.shared.Catalog
import ru.avtoschool.shared.SchoolRecord
import ru.avtoschool.shared.Snapshot

internal class LocalSchool(context: Context) : SQLiteOpenHelper(context, "school.db", null, 1) {
    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE categories(id INTEGER PRIMARY KEY, version INTEGER NOT NULL, code TEXT NOT NULL, title TEXT NOT NULL, hours INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE instructors(id INTEGER PRIMARY KEY, version INTEGER NOT NULL, name TEXT NOT NULL, phone TEXT NOT NULL, certificate TEXT NOT NULL)")
        db.execSQL("CREATE TABLE cars(id INTEGER PRIMARY KEY, version INTEGER NOT NULL, model TEXT NOT NULL, plate TEXT NOT NULL, transmission TEXT NOT NULL, category_id INTEGER NOT NULL REFERENCES categories(id))")
        db.execSQL("CREATE TABLE study_groups(id INTEGER PRIMARY KEY, version INTEGER NOT NULL, title TEXT NOT NULL, category_id INTEGER NOT NULL REFERENCES categories(id), instructor_id INTEGER NOT NULL REFERENCES instructors(id), start_date TEXT NOT NULL, end_date TEXT NOT NULL, capacity INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE students(id INTEGER PRIMARY KEY, version INTEGER NOT NULL, name TEXT NOT NULL, phone TEXT NOT NULL, group_id INTEGER NOT NULL REFERENCES study_groups(id), status TEXT NOT NULL, enrolled_on TEXT NOT NULL)")
        db.execSQL("CREATE TABLE lessons(id INTEGER PRIMARY KEY, version INTEGER NOT NULL, student_id INTEGER NOT NULL REFERENCES students(id), instructor_id INTEGER NOT NULL REFERENCES instructors(id), car_id INTEGER NOT NULL REFERENCES cars(id), starts_at TEXT NOT NULL, duration_minutes INTEGER NOT NULL, status TEXT NOT NULL, note TEXT NOT NULL)")
        db.execSQL("CREATE TABLE snapshot_meta(id INTEGER PRIMARY KEY CHECK (id = 1), captured_at TEXT NOT NULL, source TEXT NOT NULL)")
        db.execSQL("CREATE INDEX local_cars_category ON cars(category_id)")
        db.execSQL("CREATE INDEX local_groups_category ON study_groups(category_id)")
        db.execSQL("CREATE INDEX local_groups_instructor ON study_groups(instructor_id)")
        db.execSQL("CREATE INDEX local_students_group ON students(group_id)")
        db.execSQL("CREATE INDEX local_lessons_student ON lessons(student_id)")
        db.execSQL("CREATE INDEX local_lessons_instructor ON lessons(instructor_id)")
        db.execSQL("CREATE INDEX local_lessons_car ON lessons(car_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Не указана миграция локальной базы $oldVersion → $newVersion")
    }

    // Дети удаляются первыми, а вставляются последними. При ошибке остаётся прежняя копия.
    fun replace(snapshot: Snapshot, source: String) {
        require(snapshot.capturedAt.isNotBlank() && snapshot.tables.keys == Catalog.entries.map { it.table }.toSet())
        val db = writableDatabase
        db.beginTransaction()
        try {
            Catalog.entries.reversed().forEach { db.delete(it.table, null, null) }
            Catalog.entries.forEach { catalog ->
                snapshot[catalog].forEach { record ->
                    require(record.id > 0 && record.version > 0 && record.fields.keys == catalog.columns.toSet())
                    val values = ContentValues().apply {
                        put("id", record.id)
                        put("version", record.version)
                        catalog.columns.forEach { put(it, record.fields.getValue(it)) }
                    }
                    db.insertOrThrow(catalog.table, null, values)
                }
            }
            db.replaceOrThrow("snapshot_meta", null, ContentValues().apply {
                put("id", 1)
                put("captured_at", snapshot.capturedAt)
                put("source", source)
            })
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun load(source: String): Snapshot? {
        val db = readableDatabase
        val capturedAt = db.rawQuery(
            "SELECT captured_at FROM snapshot_meta WHERE id = 1 AND source = ?", arrayOf(source)
        ).use { if (it.moveToFirst()) it.getString(0) else null } ?: return null
        val tables = Catalog.entries.associate { catalog ->
            catalog.table to db.query(catalog.table, (listOf("id", "version") + catalog.columns).toTypedArray(),
                null, null, null, null, "id").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(SchoolRecord(cursor.getLong(0), cursor.getInt(1), catalog.columns.withIndex()
                            .associate { (index, column) -> column to cursor.getString(index + 2) }))
                    }
                }
            }
        }
        return Snapshot(capturedAt, tables)
    }
}
