package hu.bme.mit.gamma.gradle.conventions

plugins {
    id("hu.bme.mit.gamma.gradle.conventions.jvm")
    application
}

tasks.startScripts {
    classpath = files("%APP_HOME%/lib/*")
}
