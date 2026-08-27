import java.util.Properties
import javax.inject.Inject
import org.gradle.api.configuration.BuildFeatures

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
    val localValue = localProperties.getProperty(name)?.trim().orEmpty()
    if (localValue.isNotEmpty()) {
        return localValue
    }
    return providers.gradleProperty(name).orNull?.trim().orEmpty()
}

fun localGradleOrEnvironmentProperty(name: String, environmentName: String): String {
    return localOrGradleProperty(name)
        .ifEmpty { providers.environmentVariable(environmentName).orNull?.trim().orEmpty() }
}

fun isReleaseArtifactTaskName(taskName: String): Boolean {
    val leafName = taskName.substringAfterLast(':').lowercase()
    if (
        leafName in setOf(
            "assemble",
            "build",
            "builddependents",
            "buildneeded",
            "bundle",
            "validatereleasesigning"
        )
    ) {
        return true
    }

    if (!leafName.contains("release")) {
        return false
    }

    return listOf(
        "assemble",
        "bundle",
        "install",
        "makeapk",
        "package",
        "publish",
        "sign",
        "signing",
        "upload",
        "validate"
    ).any(leafName::startsWith)
}

data class ReleaseSigningSettings(
    val storeFilePath: String,
    val keyAlias: String,
    val storePassword: String,
    val keyPassword: String
) {
    fun missingInputNames(): List<String> = buildList {
        if (storeFilePath.isEmpty()) {
            add("bodeulReleaseStoreFile 또는 BODEUL_RELEASE_STORE_FILE")
        }
        if (keyAlias.isEmpty()) {
            add("bodeulReleaseKeyAlias 또는 BODEUL_RELEASE_KEY_ALIAS")
        }
        if (storePassword.isEmpty()) {
            add("BODEUL_RELEASE_STORE_PASSWORD")
        }
        if (keyPassword.isEmpty()) {
            add("BODEUL_RELEASE_KEY_PASSWORD")
        }
    }
}

abstract class BuildFeaturesAccessor @Inject constructor(
    val buildFeatures: BuildFeatures
)

val releaseArtifactRequested = gradle.startParameter.taskNames.any(::isReleaseArtifactTaskName)
if (releaseArtifactRequested && gradle.startParameter.excludedTaskNames.isNotEmpty()) {
    throw GradleException("릴리스 산출물 작업에서는 -x 또는 --exclude-task 옵션을 사용할 수 없습니다.")
}
val buildFeatures = objects.newInstance<BuildFeaturesAccessor>().buildFeatures
if (releaseArtifactRequested && buildFeatures.configurationCache.active.get()) {
    throw GradleException(
        "릴리스 서명 비밀값이 Gradle 구성 캐시에 저장되지 않도록 " +
            "릴리스 산출물은 --no-configuration-cache 옵션으로 빌드해야 합니다."
    )
}

// 암호는 추적 파일이나 Gradle 속성으로 받지 않고 릴리스 요청 시 환경변수에서만 읽는다.
val releaseSigningSettings = if (releaseArtifactRequested) {
    ReleaseSigningSettings(
        storeFilePath = localGradleOrEnvironmentProperty(
            "bodeulReleaseStoreFile",
            "BODEUL_RELEASE_STORE_FILE"
        ),
        keyAlias = localGradleOrEnvironmentProperty(
            "bodeulReleaseKeyAlias",
            "BODEUL_RELEASE_KEY_ALIAS"
        ),
        storePassword = providers.environmentVariable("BODEUL_RELEASE_STORE_PASSWORD").orNull.orEmpty(),
        keyPassword = providers.environmentVariable("BODEUL_RELEASE_KEY_PASSWORD").orNull.orEmpty()
    )
} else {
    null
}

val releaseStoreFile = releaseSigningSettings
    ?.takeIf { it.missingInputNames().isEmpty() }
    ?.let { rootProject.file(it.storeFilePath) }
    ?.takeIf { it.isFile }

val kakaoNativeAppKey = localOrGradleProperty("kakaoNativeAppKey")
val bodeulCoreApiBaseUrl = localOrGradleProperty("bodeulCoreApiBaseUrl")
val bodeulCoreApiDebugBaseUrl = localOrGradleProperty("bodeulCoreApiDebugBaseUrl")
val effectiveBodeulCoreApiDebugBaseUrl = bodeulCoreApiBaseUrl.ifEmpty { bodeulCoreApiDebugBaseUrl }
check(effectiveBodeulCoreApiDebugBaseUrl.isNotEmpty()) { "Debug Core API 주소가 비어 있습니다." }
val bodeulSupabaseUrl = localOrGradleProperty("bodeulSupabaseUrl")
val bodeulSupabasePublishableKey = localOrGradleProperty("bodeulSupabasePublishableKey")
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
        resValue("string", "bodeul_supabase_url", bodeulSupabaseUrl)
        resValue("string", "bodeul_supabase_publishable_key", bodeulSupabasePublishableKey)
        resValue("string", "naver_client_id", naverClientId)
        resValue("string", "naver_client_name", naverClientName)
        // 네이버 클라이언트 시크릿은 앱에 포함하지 않고 서버 중계가 준비될 때까지 로그인을 비활성화한다.
        resValue("bool", "naver_login_enabled", "false")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningSettings != null && releaseStoreFile != null) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseSigningSettings.storePassword
                keyAlias = releaseSigningSettings.keyAlias
                keyPassword = releaseSigningSettings.keyPassword
            }
        }
    }

    buildTypes {
        debug {
            // 공개 Preview 주소를 기본으로 사용하되 local.properties로 다른 개발 서버를 선택할 수 있다.
            resValue(
                "string",
                "bodeul_core_api_base_url",
                effectiveBodeulCoreApiDebugBaseUrl
            )
        }
        release {
            // 운영 앱이 개발 서버를 바라보지 않도록 Preview 기본값을 상속하지 않는다.
            resValue("string", "bodeul_core_api_base_url", bodeulCoreApiBaseUrl)
            signingConfig = signingConfigs.findByName("release")
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

val validateReleaseSigning = tasks.register("validateReleaseSigning") {
    group = "verification"
    description = "릴리스 서명 입력값과 키 저장소 파일을 검증합니다."
    notCompatibleWithConfigurationCache("릴리스 서명 비밀값을 구성 캐시에 저장하지 않습니다.")

    doLast {
        val settings = releaseSigningSettings ?: throw GradleException(
            "릴리스 서명 설정을 구성할 수 없습니다. " +
                "validateReleaseSigning 또는 전체 릴리스 작업 이름을 사용하고 " +
                "--no-configuration-cache 옵션을 지정하세요."
        )
        val missingInputNames = settings.missingInputNames()
        if (missingInputNames.isNotEmpty()) {
            throw GradleException(
                "릴리스 서명 입력값이 누락되었습니다: ${missingInputNames.joinToString(", ")}"
            )
        }

        val configuredStoreFile = rootProject.file(settings.storeFilePath)
        if (!configuredStoreFile.isFile) {
            throw GradleException(
                "릴리스 키 저장소 파일을 찾을 수 없습니다. " +
                    "bodeulReleaseStoreFile 또는 BODEUL_RELEASE_STORE_FILE 값을 확인하세요."
            )
        }
        if (android.signingConfigs.findByName("release") == null) {
            throw GradleException("릴리스 서명 구성이 적용되지 않았습니다. 전체 릴리스 작업 이름으로 다시 실행하세요.")
        }

        logger.lifecycle("릴리스 서명 입력값과 키 저장소 파일을 확인했습니다.")
    }
}

tasks.matching { it.name == "preReleaseBuild" || isReleaseArtifactTaskName(it.name) }
    .configureEach {
        if (name != validateReleaseSigning.name) {
            dependsOn(validateReleaseSigning)
            doFirst {
                if (android.buildTypes.getByName("release").signingConfig == null) {
                    throw GradleException(
                        "릴리스 서명 구성이 없어 산출물을 만들 수 없습니다. " +
                            "전체 릴리스 작업 이름과 --no-configuration-cache 옵션을 사용하세요."
                    )
                }
            }
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
    implementation(libs.okhttp)
    debugImplementation(libs.firebase.appcheck.debug)
    testImplementation(libs.junit4)
    testImplementation(libs.json.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
}
