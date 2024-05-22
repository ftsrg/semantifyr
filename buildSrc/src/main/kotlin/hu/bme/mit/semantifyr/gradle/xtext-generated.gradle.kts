package hu.bme.mit.semantifyr.gradle

plugins {
    id("hu.bme.mit.semantifyr.gradle.mwe2")
}

val xtextGenPath = "src/main/xtext-gen"
val xtextTestFixtureGenPath = "src/testFixtures/xtext-gen"

sourceSets {
    main {
        java.srcDir(xtextGenPath)
        resources.srcDir(xtextGenPath)
    }
    testFixtures {
        java.srcDir(xtextTestFixtureGenPath)
        resources.srcDir(xtextTestFixtureGenPath)
    }
}
