plugins {
    kotlin("jvm")
    application
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
application { mainClass.set("ru.avtoschool.server.MainKt") }
dependencies {
    implementation(project(":shared"))
    implementation("com.google.code.gson:gson:2.11.0")
    runtimeOnly("org.postgresql:postgresql:42.7.11")
}
sourceSets.main {
    resources.srcDir(rootProject.projectDir)
    resources.include("database/schema.sql", "database/seed.sql")
}
