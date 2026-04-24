// 프로젝트 전반에서 공통으로 사용할 플러그인 버전을 정의한다.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.google.services) apply false
}
