# 프로젝트 전용 ProGuard 규칙을 이 파일에 작성한다.
# 적용되는 구성 파일은 build.gradle의 proguardFiles 설정으로 제어할 수 있다.
# 자세한 내용: https://developer.android.com/guide/developing/tools/proguard

# WebView에서 JavaScript 인터페이스를 사용할 경우 아래 규칙을 참고한다.
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# 디버깅용 줄 번호 정보를 유지하려면 아래 규칙을 사용한다.
#-keepattributes SourceFile,LineNumberTable

# 원본 소스 파일명을 숨기려면 아래 규칙을 사용한다.
#-renamesourcefileattribute SourceFile
