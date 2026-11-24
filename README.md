# 🚘 Автошкола

**Мобильное рабочее место администратора автошколы.** Ученики, учебные группы и расписание вождения — в одном Android-приложении.

![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?logo=postgresql&logoColor=white)
![SQLite](https://img.shields.io/badge/SQLite-003B57?logo=sqlite&logoColor=white)

**[↓ Скачать APK][apk]** · **[↓ Скачать исходники ZIP][source]** · **[▶ Видеообзор MP4][video]**

## Что умеет приложение

- Вести карточки учеников и групп, планировать занятия и менять их статус.
- Искать по ФИО, телефону, группе и другим реквизитам. Просмотр и поиск работают офлайн.
- Проверять вместимость групп и не допускать пересечений в расписании ученика, инструктора или автомобиля.

При первом запуске доступны демоданные: **12 учеников, 4 группы и 10 занятий**. Чтобы добавлять и редактировать записи, подключите сервер.

## Видеообзор

**[Открыть видеообзор (.mp4, 23 сек.)][video]** — обзор, поиск по фамилии, форма нового ученика и расписание. Видео смонтировано из экранов Compose Preview; это не запись с телефона.

## Стек разработки

| Часть | Технологии |
| --- | --- |
| Android | Kotlin, Jetpack Compose, Material 3, ViewModel |
| Сервер | Kotlin/JVM, HTTP API, JDBC |
| Хранение | PostgreSQL 16 на сервере · SQLite на устройстве |
| Сборка и запуск | Gradle Wrapper, Docker Compose |

## Запуск

Установите [APK][apk] на **Android 8.0 и новее** или откройте корневой `build.gradle.kts` как проект в Android Studio и запустите модуль `app`. Для сборки из исходников нужны Android SDK 37, Build Tools 36.0.0 и JDK 17–25.

```powershell
.\gradlew.bat :app:assembleDebug
```

Собранный APK появится в `app/build/outputs/apk/debug/`. На macOS/Linux используйте `./gradlew` вместо `.\gradlew.bat`.

<details>
<summary><strong>Как запустить сервер и включить редактирование</strong></summary>

Из корня репозитория запустите PostgreSQL и сервер:

```powershell
docker compose -f server/compose.yaml up -d --wait
.\gradlew.bat :server:run --args="--init-demo"
```

Параметр `--init-demo` создаёт схему и заполняет её только при первом запуске. Чтобы проверить API с компьютера, откройте `http://127.0.0.1:8080/health`.

В Android-эмуляторе откройте **Подключение** и введите `http://10.0.2.2:8080` и ключ `school-demo`. Для физического телефона в той же доверенной сети задайте перед запуском сервера `SCHOOL_HOST=0.0.0.0` и собственный `SCHOOL_TOKEN`, а в приложении укажите IP компьютера и этот ключ.

Если PostgreSQL уже установлен, вместо Docker можно создать пустую БД `avtoschool` и задать `SCHOOL_DB_URL`, `SCHOOL_DB_USER`, `SCHOOL_DB_PASSWORD`. При инициализации нужна возможность создать расширение `btree_gist`. Данные в проекте вымышлены; не передавайте реальные контакты по HTTP.

</details>

<details>
<summary><strong>Проверки</strong></summary>

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
py tests/api_smoke.py
```

Проверка API выполняется на запущенном сервере с **отдельной тестовой БД**: скрипт оставляет созданные тестовые записи. Для отрисовки Compose Preview используйте `.\gradlew.bat :app:updateDebugScreenshotTest`.

</details>

**Исходные файлы:** [`app/`](app/) · [`server/`](server/) · [`shared/`](shared/) · [`database/`](database/)

[apk]: https://github.com/larkzzz/avtoschool/releases/download/v1.0.0/avtoschool-1.0.0.apk
[source]: https://github.com/larkzzz/avtoschool/archive/refs/tags/v1.0.0.zip
[video]: https://github.com/larkzzz/avtoschool/releases/download/v1.0.0/avtoschool-demo.mp4
