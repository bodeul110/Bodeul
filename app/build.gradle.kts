import java.util.Properties

plugins {
    id("com.android.application")
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
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
    compileSdk = 34

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
        // 카카오 앱 키를 리소스로 주입하기 위해 resValue 생성을 켠다.
        resValues = true
    }

    compileOptions {
        // 현재 빌드 JDK와 정합성을 맞춰 Java 컴파일 경고를 제거한다.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.7.0"))

    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.kakao.sdk:v2-user:2.23.3")
    implementation("com.navercorp.nid:oauth:5.11.2")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
