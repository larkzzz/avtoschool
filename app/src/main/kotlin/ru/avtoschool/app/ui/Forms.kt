package ru.avtoschool.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ru.avtoschool.app.DataState
import ru.avtoschool.app.SchoolState
import ru.avtoschool.app.data.Connection
import ru.avtoschool.app.defaultFields
import ru.avtoschool.app.referenceLabel
import ru.avtoschool.app.validate
import ru.avtoschool.shared.*
import java.time.LocalDateTime

private data class Field(val key: String, val title: String, val placeholder: String = "", val catalog: Catalog? = null,
    val options: Map<String, String>? = null, val keyboard: KeyboardType = KeyboardType.Text)

private fun fields(catalog: Catalog): List<Field> = when (catalog) {
    Catalog.STUDENTS -> listOf(
        Field("name", "ФИО", "Например, Анна Белова"), Field("phone", "Телефон", "+79990000000", keyboard = KeyboardType.Phone),
        Field("group_id", "Учебная группа", catalog = Catalog.GROUPS),
        Field("status", "Статус", options = studentStatuses), Field("enrolled_on", "Дата зачисления", "ГГГГ-ММ-ДД")
    )
    Catalog.GROUPS -> listOf(
        Field("title", "Название группы", "Например, Б-26/03"), Field("category_id", "Категория", catalog = Catalog.CATEGORIES),
        Field("instructor_id", "Ответственный инструктор", catalog = Catalog.INSTRUCTORS),
        Field("start_date", "Дата начала", "ГГГГ-ММ-ДД"), Field("end_date", "Дата окончания", "ГГГГ-ММ-ДД"),
        Field("capacity", "Вместимость, чел.", "1–40", keyboard = KeyboardType.Number)
    )
    Catalog.LESSONS -> listOf(
        Field("student_id", "Ученик", catalog = Catalog.STUDENTS), Field("instructor_id", "Инструктор", catalog = Catalog.INSTRUCTORS),
        Field("car_id", "Учебный автомобиль", catalog = Catalog.CARS), Field("starts_at", "Дата и время начала", "ГГГГ-ММ-ДД ЧЧ:ММ"),
        Field("duration_minutes", "Длительность, мин", "30–180", keyboard = KeyboardType.Number),
        Field("status", "Статус", options = lessonStatuses), Field("note", "Комментарий (необязательно)")
    )
    else -> emptyList()
}

@Composable
internal fun EditForm(
    catalog: Catalog, record: SchoolRecord?, snapshot: Snapshot, busy: Boolean, online: Boolean,
    cancel: () -> Unit, formTime: LocalDateTime? = null, submit: (Change) -> Unit
) {
    val definitions = remember(catalog) { fields(catalog) }
    var values by rememberSaveable(catalog, record?.id) {
        val initial = record?.fields ?: defaultFields(catalog, formTime ?: LocalDateTime.now())
        mutableStateOf(ArrayList(definitions.map { field ->
            val value = initial[field.key].orEmpty()
            if (field.key == "starts_at") value.replace('T', ' ').removeSuffix(":00").let {
                // ISO timestamps from the server include seconds; retain HH:mm for the form.
                if (it.length == 13) "$it:00" else it
            } else value
        }))
    }
    val version by rememberSaveable(catalog, record?.id) { mutableStateOf(record?.version) }
    var error by rememberSaveable(catalog, record?.id) { mutableStateOf<String?>(null) }
    val locked = !online || (catalog == Catalog.LESSONS && record != null && record["status"] != "PLANNED")
    val versionChanged = record != null && record.version != version
    val enabled = !busy && !locked && !versionChanged

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(when {
            versionChanged -> "Эта запись уже обновилась. Закройте форму и откройте её снова."
            locked -> "Эта карточка сейчас доступна только для просмотра."
            catalog == Catalog.LESSONS -> "Время указывается по местному времени автошколы."
            else -> "Поля со справочными значениями выбираются из списка."
        }, color = Muted, style = MaterialTheme.typography.bodyMedium)
        definitions.forEachIndexed { index, field ->
            val chosenStudent = definitions.indexOfFirst { it.key == "student_id" }.takeIf { it >= 0 }
                ?.let { snapshot.find(Catalog.STUDENTS, values[it]) }
            val categoryId = chosenStudent?.let { snapshot.find(Catalog.GROUPS, it["group_id"])?.get("category_id") }
            val options = field.options ?: field.catalog?.let { ref ->
                snapshot[ref].filter { ref != Catalog.CARS || categoryId == null || it["category_id"] == categoryId }
                    .associate { it.id.toString() to referenceLabel(ref, it) }
            }
            val fieldEnabled = enabled && !(catalog == Catalog.GROUPS && record != null && field.key == "category_id") &&
                !(catalog == Catalog.LESSONS && record == null && field.key == "status")
            fun update(value: String) {
                val next = ArrayList(values)
                next[index] = value
                if (catalog == Catalog.LESSONS && field.key == "student_id" && value != values[index]) {
                    val carIndex = definitions.indexOfFirst { it.key == "car_id" }
                    if (carIndex >= 0) next[carIndex] = ""
                }
                values = next
                error = null
            }
            if (options != null) {
                ChoiceField(field.title, values[index], options, fieldEnabled, ::update)
            } else OutlinedTextField(
                value = values[index], onValueChange = ::update, label = { Text(field.title) },
                placeholder = { if (field.placeholder.isNotBlank()) Text(field.placeholder) },
                modifier = Modifier.fillMaxWidth(), enabled = fieldEnabled, singleLine = field.key != "note",
                minLines = if (field.key == "note") 2 else 1, shape = RoundedCornerShape(13.dp),
                keyboardOptions = KeyboardOptions(keyboardType = field.keyboard),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White)
            )
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(cancel, modifier = Modifier.weight(1f), enabled = !busy) { Text("Закрыть") }
            Button(onClick = {
                val result = validate(catalog, definitions.mapIndexed { index, field -> field.key to values[index] }.toMap())
                error = result.error
                if (result.error == null) submit(Change(record?.id, version, result.fields))
            }, modifier = Modifier.weight(1f), enabled = enabled) { Text("Сохранить") }
        }
    }
}

@Composable
private fun ChoiceField(title: String, value: String, options: Map<String, String>, enabled: Boolean, select: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedCard(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, if (enabled) MaterialTheme.colorScheme.outline else Line),
            colors = CardDefaults.outlinedCardColors(containerColor = Color.White, disabledContainerColor = PageColor)) {
            Row(Modifier.padding(horizontal = 15.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, style = MaterialTheme.typography.labelSmall, color = Muted)
                    Text(options[value] ?: "Выберите из списка", style = MaterialTheme.typography.bodyLarge, color = if (value in options) Ink else Muted)
                }
                if (enabled) MarkIcon(Mark.ARROW, Modifier.size(18.dp), Muted)
            }
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 300.dp).widthIn(min = 255.dp)) {
            if (options.isEmpty()) DropdownMenuItem(text = { Text("Нет доступных значений") }, onClick = {}, enabled = false)
            else options.forEach { (key, label) ->
                DropdownMenuItem(text = { Text(label, fontWeight = if (value == key) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { select(key); expanded = false })
            }
        }
    }
}

@Composable
internal fun ConnectionForm(state: SchoolState, onConnect: (Connection, () -> Unit) -> Unit,
    onDemo: (() -> Unit) -> Unit, done: () -> Unit) {
    val fallback = state.connection ?: Connection()
    var url by rememberSaveable { mutableStateOf(fallback.url) }
    var key by rememberSaveable { mutableStateOf(fallback.token) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(color = Soft, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(state.mode.label, color = Accent, fontWeight = FontWeight.Bold)
                Text("Для записи подключитесь к серверу автошколы. Без сети остаются доступными просмотр и поиск в локальной копии.",
                    color = Ink, style = MaterialTheme.typography.bodyMedium)
            }
        }
        OutlinedTextField(url, { url = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Адрес сервера") },
            supportingText = { Text("На эмуляторе: http://10.0.2.2:8080") }, singleLine = true, enabled = !state.busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), shape = RoundedCornerShape(13.dp))
        OutlinedTextField(key, { key = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Ключ доступа") },
            singleLine = true, enabled = !state.busy, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), shape = RoundedCornerShape(13.dp))
        Button(onClick = { onConnect(Connection(url, key), done) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text("Подключиться")
        }
        if (state.mode != DataState.DEMO) TextButton(onClick = { onDemo(done) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text("Вернуться к демонстрационным данным")
        }
        Text("Демонстрационные данные вымышлены. Не передавайте реальные данные по HTTP.", color = Muted,
            style = MaterialTheme.typography.bodySmall)
    }
}
