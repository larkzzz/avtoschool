package ru.avtoschool.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.avtoschool.app.DataState
import ru.avtoschool.app.RecordText
import ru.avtoschool.app.SchoolState
import ru.avtoschool.app.SchoolViewModel
import ru.avtoschool.app.data.Connection
import ru.avtoschool.app.describe
import ru.avtoschool.app.search
import ru.avtoschool.shared.Catalog
import ru.avtoschool.shared.Change
import ru.avtoschool.shared.SchoolRecord
import ru.avtoschool.shared.Snapshot
import java.time.LocalDateTime

internal val Ink = Color(0xFF162043)
internal val Muted = Color(0xFF53607B)
internal val Accent = Color(0xFF5045CF)
internal val Soft = Color(0xFFECEAFF)
internal val PageColor = Color(0xFFF5F7FC)
internal val Line = Color(0xFFDFE4EF)
private val White = Color.White
private val Green = Color(0xFF14664A)
private val Amber = Color(0xFF77500F)

@Composable
internal fun SchoolTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = lightColorScheme(
        primary = Accent, onPrimary = White, primaryContainer = Soft, onPrimaryContainer = Ink,
        background = PageColor, onBackground = Ink, surface = White, onSurface = Ink,
        surfaceVariant = PageColor, onSurfaceVariant = Muted, outline = Color(0xFF77839C), outlineVariant = Line
    ), content = content
)

internal enum class Page(val title: String, val mark: Mark, val catalog: Catalog? = null) {
    HOME("Обзор", Mark.HOME),
    STUDENTS("Ученики", Mark.PEOPLE, Catalog.STUDENTS),
    GROUPS("Группы", Mark.GROUP, Catalog.GROUPS),
    LESSONS("Вождение", Mark.CAR, Catalog.LESSONS),
    INSTRUCTORS("Инструкторы", Mark.PERSON, Catalog.INSTRUCTORS),
    CARS("Автопарк", Mark.CAR, Catalog.CARS),
    CATEGORIES("Категории", Mark.CATEGORY, Catalog.CATEGORIES),
    SETTINGS("Подключение", Mark.SETTINGS)
}

private val primaryPages = listOf(Page.HOME, Page.STUDENTS, Page.GROUPS, Page.LESSONS)

@Composable
internal fun SchoolUi(model: SchoolViewModel) {
    SchoolScreen(model.state, onRefresh = model::refresh, onClear = model::clearNotices,
        onConnect = model::connect, onDemo = model::demo, onSave = model::save)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SchoolScreen(
    state: SchoolState, initialPage: Page = Page.HOME, initialEditor: Boolean = false, initialSearch: String = "",
    formTime: LocalDateTime? = null,
    onRefresh: () -> Unit = {}, onClear: () -> Unit = {}, onConnect: (Connection, () -> Unit) -> Unit = { _, _ -> },
    onDemo: (() -> Unit) -> Unit = {}, onSave: (Catalog, Change, () -> Unit) -> Unit = { _, _, _ -> }
) {
    var page by rememberSaveable { mutableStateOf(initialPage) }
    var previous by rememberSaveable { mutableStateOf(Page.HOME) }
    var editor by rememberSaveable { mutableStateOf(initialEditor) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var search by rememberSaveable { mutableStateOf(initialSearch) }
    val catalog = page.catalog
    val snapshot = state.snapshot
    val online = state.mode == DataState.ONLINE
    fun open(target: Page) {
        if (target != page) {
            previous = if (page in primaryPages) page else Page.HOME
            page = target
            search = ""
        }
        editor = false
        selectedId = null
        onClear()
    }
    fun closeEditor() {
        editor = false
        selectedId = null
    }
    fun back() {
        if (editor) {
            onClear()
            closeEditor()
        } else if (page !in primaryPages) open(previous) else open(Page.HOME)
    }
    BackHandler(state.busy || editor || page != Page.HOME) { if (!state.busy) back() }

    Scaffold(
        containerColor = PageColor,
        topBar = {
            TopAppBar(
                title = {
                    if (page == Page.HOME) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            IconTile(Mark.ROAD, Accent, White, 38)
                            Text("Автошкола", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        }
                    } else Text(if (editor) catalog.singleTitle(selectedId != null) else page.title, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    if (editor || page !in primaryPages) ActionIcon(Mark.BACK, "Назад", !state.busy, ::back)
                },
                actions = {
                    if (!editor && page != Page.SETTINGS) {
                        if (state.connection != null) ActionIcon(Mark.SYNC, "Обновить", !state.busy, onRefresh)
                        ActionIcon(Mark.SETTINGS, "Подключение", !state.busy) { open(Page.SETTINGS) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PageColor)
            )
        },
        bottomBar = {
            if (page in primaryPages && !editor) {
                HorizontalDivider(color = Line)
                NavigationBar(containerColor = White, tonalElevation = 0.dp) {
                    primaryPages.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == page, enabled = !state.busy, onClick = { open(tab) },
                            icon = { MarkIcon(tab.mark) }, label = { Text(tab.title, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = Soft, selectedIconColor = Accent,
                                selectedTextColor = Ink, unselectedIconColor = Muted, unselectedTextColor = Muted)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Notice(it, true, onClear) }
            state.message?.let { Notice(it, false, onClear) }
            when {
                page == Page.SETTINGS -> ConnectionForm(state, onConnect, onDemo) { open(previous) }
                snapshot == null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(if (state.busy) "Загружаем данные…" else "Нет локальной копии. Проверьте подключение в настройках.", color = Muted)
                }
                page == Page.HOME -> Dashboard(snapshot, state.mode) { if (!state.busy) open(it) }
                catalog != null && editor -> {
                    val record = selectedId?.let { snapshot.find(catalog, it.toString()) }
                    EditForm(catalog, record, snapshot, state.busy, online, ::back, formTime) { change ->
                        onSave(catalog, change, ::closeEditor)
                    }
                }
                catalog != null -> CatalogPage(catalog, snapshot, search, { search = it }, online, state.busy,
                    connect = { open(Page.SETTINGS) },
                    add = { editor = true; selectedId = null; onClear() },
                    select = { editor = true; selectedId = it.id; onClear() })
            }
        }
    }
}

private fun Catalog?.singleTitle(edit: Boolean): String = when (this) {
    Catalog.STUDENTS -> if (edit) "Карточка ученика" else "Новый ученик"
    Catalog.GROUPS -> if (edit) "Карточка группы" else "Новая группа"
    Catalog.LESSONS -> if (edit) "Карточка занятия" else "Новое занятие"
    else -> "Карточка"
}

@Composable
private fun Notice(text: String, error: Boolean, dismiss: () -> Unit) {
    Surface(
        color = if (error) MaterialTheme.colorScheme.errorContainer else Color(0xFFDBF3E8),
        contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else Green,
        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp)
    ) {
        Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f).padding(vertical = 10.dp), style = MaterialTheme.typography.bodySmall)
            ActionIcon(Mark.CLOSE, "Закрыть сообщение", action = dismiss)
        }
    }
}

@Composable
internal fun ActionIcon(mark: Mark, label: String, enabled: Boolean = true, action: () -> Unit) {
    IconButton(onClick = action, enabled = enabled, modifier = Modifier.semantics { contentDescription = label }) {
        MarkIcon(mark, color = if (enabled) Ink else Muted)
    }
}

@Composable
private fun Dashboard(snapshot: Snapshot, mode: DataState, open: (Page) -> Unit) {
    val planned = snapshot[Catalog.LESSONS].filter { it["status"] == "PLANNED" }.sortedBy { it["starts_at"] }
    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item { Hero(mode) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("Ученики", snapshot[Catalog.STUDENTS].size, Mark.PEOPLE, Modifier.weight(1f)) { open(Page.STUDENTS) }
                Metric("Группы", snapshot[Catalog.GROUPS].size, Mark.GROUP, Modifier.weight(1f)) { open(Page.GROUPS) }
                Metric("В плане", planned.size, Mark.CAR, Modifier.weight(1f)) { open(Page.LESSONS) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Вождение", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = { open(Page.LESSONS) }) { Text("Расписание"); Spacer(Modifier.width(4.dp)); MarkIcon(Mark.ARROW, Modifier.size(16.dp)) }
            }
        }
        if (planned.isEmpty()) item { Text("Запланированных занятий нет.", color = Muted) }
        items(planned.take(2), key = { it.id }) {
            RecordCard(snapshot.describe(Catalog.LESSONS, it), Mark.CAR, it["status"]) { open(Page.LESSONS) }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Справочники", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                listOf(Page.INSTRUCTORS, Page.CARS, Page.CATEGORIES).forEach { page ->
                    OutlinedCard(onClick = { open(page) }, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(15.dp), colors = CardDefaults.outlinedCardColors(containerColor = White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                        Row(Modifier.padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconTile(page.mark, Soft, Accent, 34)
                            Text(page.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text(snapshot[checkNotNull(page.catalog)].size.toString(), color = Muted)
                            MarkIcon(Mark.ARROW, Modifier.size(16.dp), Muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hero(mode: DataState) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1B2452), Color(0xFF403794))))
    ) {
        Canvas(Modifier.matchParentSize()) {
            for (i in 0..6) {
                val distance = i * size.width * .065f
                drawLine(Color.White.copy(alpha = .07f), Offset(size.width * .66f + distance, size.height),
                    Offset(size.width * .98f + distance, 0f), strokeWidth = 2.dp.toPx())
            }
        }
        Column(Modifier.padding(21.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("ПАНЕЛЬ АДМИНИСТРАТОРА", color = Color(0xFFDAD7FF),
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text("Учебный центр", color = White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Text("Ученики, группы и занятия", color = Color(0xFFE2E4F6), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(3.dp))
            Surface(shape = CircleShape, color = White.copy(alpha = .14f), contentColor = White) {
                Row(Modifier.padding(horizontal = 11.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.size(7.dp).background(if (mode == DataState.ONLINE) Color(0xFF71EBB6) else Color(0xFFF4CC85), CircleShape))
                    Text(mode.label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, count: Int, mark: Mark, modifier: Modifier, action: () -> Unit) {
    OutlinedCard(onClick = action, modifier = modifier, shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = White), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            IconTile(mark, Soft, Accent, 32)
            Text("$count", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, color = Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CatalogPage(
    catalog: Catalog, snapshot: Snapshot, query: String, setQuery: (String) -> Unit, online: Boolean, busy: Boolean,
    connect: () -> Unit, add: () -> Unit, select: (SchoolRecord) -> Unit
) {
    val records = remember(snapshot, catalog, query) { snapshot.search(catalog, query) }
    val mark = Page.entries.first { it.catalog == catalog }.mark
    val hint = when (catalog) {
        Catalog.STUDENTS -> "ФИО, телефон или группа"
        Catalog.GROUPS -> "Номер группы или инструктор"
        Catalog.LESSONS -> "Ученик, дата или автомобиль"
        else -> "Поиск по справочнику"
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        if (catalog.editable) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Всего: ${snapshot[catalog].size}", color = Muted, style = MaterialTheme.typography.titleMedium)
                if (online) Button(add, enabled = !busy) {
                    MarkIcon(Mark.ADD, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Добавить")
                } else TextButton(connect, enabled = !busy) { Text("Подключить сервер") }
            }
        }
        OutlinedTextField(
            value = query, onValueChange = setQuery, modifier = Modifier.fillMaxWidth(),
            label = { Text("Поиск") }, placeholder = { Text(hint) },
            leadingIcon = { MarkIcon(Mark.SEARCH, Modifier.size(20.dp), Muted) },
            trailingIcon = if (query.isNotEmpty()) ({ ActionIcon(Mark.CLOSE, "Очистить поиск") { setQuery("") } }) else null,
            singleLine = true, shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = White, focusedContainerColor = White)
        )
        Text("Найдено: ${records.size}", Modifier.padding(top = 13.dp, bottom = 10.dp), color = Muted,
            style = MaterialTheme.typography.labelLarge)
        if (records.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 35.dp), contentAlignment = Alignment.Center) {
                Text(if (query.isBlank()) "В этом разделе пока нет записей." else "Ничего не найдено. Измените запрос.", color = Muted)
            }
        } else LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(records, key = { it.id }) { record ->
                val edit = catalog.editable && online && !busy && (catalog != Catalog.LESSONS || record["status"] == "PLANNED")
                RecordCard(snapshot.describe(catalog, record), mark, record["status"], if (edit) ({ select(record) }) else null)
            }
        }
    }
}

@Composable
private fun RecordCard(text: RecordText, mark: Mark, status: String, open: (() -> Unit)? = null) {
    val content: @Composable ColumnScope.() -> Unit = {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconTile(mark, Soft, Accent, 38)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(text.subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
                if (text.detail.isNotBlank()) Text(text.detail, style = MaterialTheme.typography.bodySmall, color = Muted)
                if (text.badge.isNotBlank()) StatusBadge(text.badge, status)
            }
            if (open != null) MarkIcon(Mark.ARROW, Modifier.size(19.dp).align(Alignment.CenterVertically), Muted)
        }
    }
    val colors = CardDefaults.outlinedCardColors(containerColor = White)
    val shape = RoundedCornerShape(16.dp)
    val border = androidx.compose.foundation.BorderStroke(1.dp, Line)
    if (open == null) OutlinedCard(Modifier.fillMaxWidth(), shape = shape, colors = colors, border = border, content = content)
    else OutlinedCard(open, Modifier.fillMaxWidth(), shape = shape, colors = colors, border = border, content = content)
}

@Composable
private fun StatusBadge(label: String, status: String) {
    val (bg, fg) = when (status) {
        "STUDYING", "DONE" -> Color(0xFFE0F3E8) to Green
        "PAUSED", "CANCELED" -> Color(0xFFFFEFCE) to Amber
        else -> Soft to Color(0xFF4337A6)
    }
    Surface(color = bg, contentColor = fg, shape = CircleShape) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun IconTile(mark: Mark, background: Color, tint: Color, size: Int) {
    Box(Modifier.size(size.dp).background(background, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        MarkIcon(mark, Modifier.size((size * .56).dp), tint)
    }
}
