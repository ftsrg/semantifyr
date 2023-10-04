plugins {
    id("hu.bme.mit.gamma.gradle.conventions.jvm")
    id("hu.bme.mit.gamma.gradle.mwe2")
    id("hu.bme.mit.gamma.gradle.xtext-generated")
}
dependencies {
    api(libs.ecore)
    api(libs.ecore.xmi)
    mwe2(libs.ecore.codegen)
    mwe2(libs.mwe.utils)
    mwe2(libs.mwe2.lib)
    mwe2(libs.xtext.core)
    mwe2(libs.xtext.xbase)
}

sourceSets {
    main {
        java.srcDir("src/main/emf-gen")
    }
}

tasks {
    val generateEPackage by registering(JavaExec::class) {
        mainClass.set("org.eclipse.emf.mwe2.launch.runtime.Mwe2Launcher")
        classpath(configurations.mwe2)
        inputs.file("./GenerateModel.mwe2")
        inputs.file("model/oxsts.ecore")
        inputs.file("model/oxsts.genmodel")
        outputs.file("build.properties")
        outputs.file("META-INF/MANIFEST.MF")
        outputs.file("plugin.xml")
        outputs.file("plugin.properties")
        outputs.dir("src/main/emf-gen")
        args("./GenerateModel.mwe2", "-p", "rootPath=/$projectDir")
    }

    for (taskName in listOf("compileJava", "processResources", "generateEclipseSourceFolders")) {
        named(taskName) {
            dependsOn(generateEPackage)
        }
    }

    clean {
        delete("src/main/emf-gen")
    }
}

eclipse.project.natures.plusAssign(listOf(
    "org.eclipse.sirius.nature.modelingproject",
    "org.eclipse.pde.PluginNature",
    "org.eclipse.xtext.ui.shared.xtextNature",
))
