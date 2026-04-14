plugins {
    id("com.android.application")
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

val kakaoNativeAppKey = providers.gradleProperty("kakaoNativeAppKey").orElse("").get()
val naverClientId = providers.gradleProperty("naverClientId").orElse("").get()
val naverClientSecret = providers.gradleProperty("naverClientSecret").orElse("").get()
val naverClientName = providers.gradleProperty("naverClientName").orElse("").get()

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
