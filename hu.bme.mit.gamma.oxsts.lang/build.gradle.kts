plugins {
    id("hu.bme.mit.gamma.gradle.conventions.jvm")
    id("hu.bme.mit.gamma.gradle.mwe2")
    id("hu.bme.mit.gamma.gradle.xtext-generated")
}

val generatedIdeSources: Configuration by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

dependencies {
    api(platform(libs.xtext.bom))
    api(libs.ecore)
    api(libs.xtext.core)
    api(libs.xtext.xbase)
    api(project(":hu.bme.mit.gamma.oxsts.model"))
    mwe2(libs.xtext.generator)
    mwe2(libs.xtext.generator.antlr)
}

val syncModel by tasks.registering(Sync::class) {
    from("../hu.bme.mit.gamma.oxsts.model/model")
    into("model")
}

val generateXtextLanguage by tasks.registering(JavaExec::class) {
    dependsOn(syncModel)

    mainClass.set("org.eclipse.emf.mwe2.launch.runtime.Mwe2Launcher")
    classpath(configurations.mwe2)
    inputs.file("src/main/java/hu/bme/mit/gamma/oxsts/lang/GenerateOxsts.mwe2")
    inputs.file("src/main/java/hu/bme/mit/gamma/oxsts/lang/Oxsts.xtext")
    inputs.file("../hu.bme.mit.gamma.oxsts.model/model/oxsts.ecore")
    inputs.file("../hu.bme.mit.gamma.oxsts.model/model/oxsts.genmodel")
    outputs.dir("src/main/xtext-gen")
    outputs.dir(layout.buildDirectory.dir("generated/sources/xtext/ide"))
    args("src/main/java/hu/bme/mit/gamma/oxsts/lang/GenerateOxsts.mwe2", "-p", "rootPath=/$projectDir/..")
}

tasks {
    jar {
        from(sourceSets.main.map { it.allSource }) {
            include("**/*.xtext")
        }
    }

    syncXtextGeneratedSources {
        // We generate Xtext runtime sources directly to {@code src/main/xtext-gen}, so there is no need to copy them
        // from an artifact. We expose the {@code generatedIdeSources} and {@code generatedWebSources} artifacts to
        // sibling IDE and web projects which can use this task to consume them and copy the appropriate sources to
        // their own {@code src/main/xtext-gen} directory.
        enabled = false
    }

    for (taskName in listOf("compileJava", "processResources", "generateEclipseSourceFolders")) {
        named(taskName) {
            dependsOn(generateXtextLanguage)
        }
    }

    clean {
        delete("src/main/xtext-gen")
    }
}

artifacts {
    add(generatedIdeSources.name, layout.buildDirectory.dir("generated/sources/xtext/ide")) {
        builtBy(generateXtextLanguage)
    }
}

eclipse.project.natures.plusAssign("org.eclipse.xtext.ui.shared.xtextNature")
