package hu.bme.mit.gamma.gradle

import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.kotlin.dsl.creating
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.the

plugins {
	id("hu.bme.mit.gamma.gradle.conventions.jvm")
}

val mwe2: Configuration by configurations.creating {
	isCanBeConsumed = false
	isCanBeResolved = true
	extendsFrom(configurations.implementation.get())
}

val libs = the<LibrariesForLibs>()

dependencies {
	mwe2(libs.mwe2.launch)
}

eclipse.classpath.plusConfigurations += mwe2
