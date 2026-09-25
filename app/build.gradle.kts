plugins {
    alias(libs.plugins.android.application)
    // Плагин kotlin-android не подключаем: начиная с AGP 9.0 поддержка
    // Kotlin встроена в сам Android Gradle Plugin.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.agentsapp"
    // AAR-метаданные Compose UI 1.12.0 требуют компиляции против API 37+.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.agentsapp"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Блока kotlinOptions { jvmTarget = ... } нет: это DSL плагина
    // kotlin-android, который здесь не применяется. Встроенный компилятор
    // Kotlin берёт jvmTarget из compileOptions.targetCompatibility выше.

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // LocalLifecycleOwner — экран чата отмечает сообщения прочитанными, только пока виден.
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Ни Room, ни прямых обращений к LLM: AgentsCore (отдельный процесс) —
    // единственный владелец персистентности и оркестрации LLM. Это
    // приложение работает только с HTTP API AgentsCore.
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Рендеринг markdown-сообщений чата (Доработка 2). JSON-сообщения и
    // факты Sticky Facts используют собственный composable-viewer — надёжной
    // опубликованной библиотеки "JsonViewer-Compose" не существует.
    implementation(libs.markdown.renderer.m3)
}
