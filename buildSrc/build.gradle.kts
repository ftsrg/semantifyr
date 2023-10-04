plugins {
	`kotlin-dsl`
	alias(libs.plugins.versions)
}

repositories {
	gradlePluginPortal()
	mavenCentral()
}

dependencies {
	// https://github.com/gradle/gradle/issues/15383
	implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
