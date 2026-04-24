import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

// 저장소에 남기지 않을 로그인 키는 local.properties를 우선으로 읽는다.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

fun localOrGradleProperty(name: String): String {
    return (localProperties.getProperty(name)
        ?: providers.gradleProperty(name).orNull
        ?: "").trim()
}

val kakaoNativeAppKey = localOrGradleProperty("kakaoNativeAppKey")
val naverClientId = localOrGradleProperty("naverClientId")
val naverClientSecret = localOrGradleProperty("naverClientSecret")
val naverClientName = localOrGradleProperty("naverClientName")
    .ifEmpty { "보들" }

android {
    namespace = "com.example.bodeul"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.bodeul"
        minSdk = 24
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
        manifestPlaceholders["kakaoScheme"] = "kakao$kakaoNativeAppKey"
        resValue("string", "kakao_native_app_key", kakaoNativeAppKey)
        resValue("string", "naver_client_id", naverClientId)
        resValue("string", "naver_client_secret", naverClientSecret)
        resValue("string", "naver_client_name", naverClientName)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        // 기본값으로 꺼져 있어 defaultConfig의 resValue를 사용하려면 명시적으로 켜야 한다.
        resValues = true
    }

    compileOptions {
        // 현재 빌드 JDK와 정합성을 맞춰 Java 컴파일 경고를 제거한다.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.material)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.googleid)
    implementation(libs.kakao.user)
    implementation(libs.naver.oauth)
    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
}
