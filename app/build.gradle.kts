import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

// 저장소에 남기지 않을 로컬 설정은 local.properties를 우선 읽는다.
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
val kakaoRestApiKey = localOrGradleProperty("kakaoRestApiKey")
val bodeulCoreApiBaseUrl = localOrGradleProperty("bodeulCoreApiBaseUrl")
val naverClientId = localOrGradleProperty("naverClientId")
val naverClientName = localOrGradleProperty("naverClientName")
    .ifEmpty { "보들" }

android {
    namespace = "com.example.bodeul"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.bodeul"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        manifestPlaceholders["kakaoScheme"] = "kakao$kakaoNativeAppKey"
        resValue("string", "kakao_native_app_key", kakaoNativeAppKey)
        resValue("string", "kakao_rest_api_key", kakaoRestApiKey)
        resValue("string", "bodeul_core_api_base_url", bodeulCoreApiBaseUrl)
        resValue("string", "naver_client_id", naverClientId)
        resValue("string", "naver_client_name", naverClientName)
        // 네이버 클라이언트 시크릿은 앱에 포함하지 않고 서버 중계가 준비될 때까지 로그인을 비활성화한다.
        resValue("bool", "naver_login_enabled", "false")

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
        // defaultConfig의 resValue를 사용하므로 명시적으로 활성화한다.
        resValues = true
    }

    compileOptions {
        // 현재 빌드 JDK와 맞춰 Java 컴파일 경고를 줄인다.
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
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.storage)
    implementation(libs.googleid)
    implementation(libs.kakao.user)
    implementation(libs.kakao.map)
    implementation(libs.naver.oauth)
    debugImplementation(libs.firebase.appcheck.debug)
    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
}
