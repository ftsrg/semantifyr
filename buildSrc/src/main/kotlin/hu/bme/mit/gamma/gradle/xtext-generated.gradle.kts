package hu.bme.mit.gamma.gradle

import org.gradle.kotlin.dsl.creating

plugins {
	id("hu.bme.mit.gamma.gradle.conventions.jvm")
}

val xtextGenPath = "src/main/xtext-gen"

val xtextGenerated: Configuration by configurations.creating {
	isCanBeConsumed = false
	isCanBeResolved = true
}

sourceSets.main {
	java.srcDir(xtextGenPath)
	resources.srcDir(xtextGenPath)
}

tasks {
	// Based on the idea from https://stackoverflow.com/a/57788355 to safely consume generated sources in sibling
	// projects.
	val syncXtextGeneratedSources by tasks.creating(Sync::class) {
		from(xtextGenerated)
		into(xtextGenPath)
	}

	for (taskName in listOf("compileJava", "processResources", "generateEclipseSourceFolders")) {
		tasks.named(taskName) {
			dependsOn(syncXtextGeneratedSources)
		}
	}

	clean {
		delete(xtextGenPath)
	}
}
