package hu.bme.mit.gamma.gradle.conventions

plugins {
    id("hu.bme.mit.gamma.gradle.conventions.jvm")
}

sourceSets.main {
    java.srcDir("src")
    java.srcDir("src-gen")
    java.srcDir("xtend-gen")
}
