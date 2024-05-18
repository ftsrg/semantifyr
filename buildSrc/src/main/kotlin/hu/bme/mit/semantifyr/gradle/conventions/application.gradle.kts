package hu.bme.mit.semantifyr.gradle.conventions

plugins {
    id("hu.bme.mit.semantifyr.gradle.conventions.jvm")
    application
}

tasks.startScripts {
    classpath = files("%APP_HOME%/lib/*")
}
