package ru.avtoschool.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import ru.avtoschool.app.ui.SchoolTheme
import ru.avtoschool.app.ui.SchoolUi

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val model = ViewModelProvider(this)[SchoolViewModel::class.java]
        setContent { SchoolTheme { SchoolUi(model) } }
    }
}
