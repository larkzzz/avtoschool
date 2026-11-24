package ru.avtoschool.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.google.gson.Gson
import ru.avtoschool.app.DataState
import ru.avtoschool.app.SchoolState
import ru.avtoschool.app.data.Connection
import ru.avtoschool.shared.Snapshot
import java.time.LocalDateTime

@Composable
private fun Example(page: Page, online: Boolean = false, editor: Boolean = false, query: String = "") {
    val context = LocalContext.current
    val snapshot = remember {
        context.assets.open("demo.json").bufferedReader(Charsets.UTF_8).use { Gson().fromJson(it, Snapshot::class.java) }
    }
    val state = SchoolState(snapshot, if (online) Connection() else null, if (online) DataState.ONLINE else DataState.DEMO)
    SchoolTheme { SchoolScreen(state, page, editor, query, formTime = LocalDateTime.of(2026, 11, 24, 9, 0)) }
}

@PreviewTest
@Preview(widthDp = 410, heightDp = 865, showBackground = true, locale = "ru")
@Composable
fun DashboardExample() = Example(Page.HOME)

@PreviewTest
@Preview(widthDp = 410, heightDp = 865, showBackground = true, locale = "ru")
@Composable
fun SearchExample() = Example(Page.STUDENTS, query = "Соловьева")

@PreviewTest
@Preview(widthDp = 410, heightDp = 865, showBackground = true, locale = "ru")
@Composable
fun StudentFormExample() = Example(Page.STUDENTS, online = true, editor = true)

@PreviewTest
@Preview(widthDp = 410, heightDp = 865, showBackground = true, locale = "ru")
@Composable
fun ScheduleExample() = Example(Page.LESSONS, online = true)
