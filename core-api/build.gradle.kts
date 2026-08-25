plugins {
	java
	id("org.springframework.boot") version "3.5.16"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.bodeul"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.security:spring-security-oauth2-jose")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("com.google.firebase:firebase-admin:9.10.0")
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.register<JavaExec>("migrateDatabase") {
	group = "application"
	description = "migration profile로 Flyway를 실행한 뒤 종료합니다."
	classpath = sourceSets["main"].runtimeClasspath
	mainClass.set("com.bodeul.core.DatabaseMigrationApplication")
}

tasks.register<JavaExec>("verifyAccountDeletionInventory") {
	group = "verification"
	description = "계정 삭제 영향도 함수와 runtime 최소 권한을 읽기 전용으로 검증합니다."
	classpath = sourceSets["main"].runtimeClasspath
	mainClass.set("com.bodeul.core.AccountDeletionInventoryVerificationApplication")
}

tasks.register<JavaExec>("applyAppointmentRequestsSeed") {
	group = "application"
	description = "검증된 예약 요청 seed를 migration role로 적용합니다."
	classpath = sourceSets["main"].runtimeClasspath
	mainClass.set("com.bodeul.core.AppointmentRequestsSeedApplication")
}

tasks.register<JavaExec>("applyCompanionSessionSeed") {
	group = "application"
	description = "검증된 동행 세션 seed를 migration role로 적용합니다."
	classpath = sourceSets["main"].runtimeClasspath
	mainClass.set("com.bodeul.core.CompanionSessionSeedApplication")
}

tasks.register<JavaExec>("runRetentionFixture") {
	group = "application"
	description = "preview 자동 파기 리허설용 고정 fixture를 준비, 조회 또는 정리합니다."
	classpath = sourceSets["main"].runtimeClasspath
	mainClass.set("com.bodeul.core.RetentionFixtureApplication")
}

tasks.register<JavaExec>("runGuideDeviceFixture") {
	group = "application"
	description = "preview 가이드 실기기 검증용 고정 fixture를 준비, 조회 또는 정리합니다."
	classpath = sourceSets["main"].runtimeClasspath
	mainClass.set("com.bodeul.core.GuideDeviceFixtureApplication")
}
