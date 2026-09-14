// Файл сборки верхнего уровня, где можно добавить параметры конфигурации, общие для всех подпроектов/модулей.

// AGP 9.0+ поставляется со встроенной поддержкой Kotlin с минимальной
// зависимостью времени выполнения от Kotlin Gradle Plugin 2.2.10. Плагины
// компилятора Compose и serialization этого проекта закреплены на версии
// Kotlin 2.3.20 (см. gradle/libs.versions.toml), поэтому встроенный
// компилятор повышается до соответствующей версии — иначе возникнет
// несовпадение версий между компилятором AGP по умолчанию 2.2.10 и
// артефактами плагинов компилятора 2.3.20.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    // Здесь нет org.jetbrains.kotlin.android: начиная с AGP 9.0, поддержка
    // Kotlin встроена в сам Android Gradle plugin, и применение
    // kotlin-android поверх него ломает сборку ("no longer required ...
    // since AGP 9.0").
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
