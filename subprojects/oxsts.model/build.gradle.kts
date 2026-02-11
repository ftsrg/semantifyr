/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
    id("hu.bme.mit.semantifyr.gradle.mwe2")
}

dependencies {
    api(libs.ecore)

    mwe2(libs.ecore.codegen)
    mwe2(libs.mwe.utils)
    mwe2(libs.mwe2.lib)
    mwe2(libs.xtext.core)
    mwe2(libs.xtext.xbase)
}

sourceSets.main {
    java.srcDir("src/main/emf-gen")
}

tasks {
    val generateEPackage by registering(JavaExec::class) {
        mainClass.set("org.eclipse.emf.mwe2.launch.runtime.Mwe2Launcher")

        classpath(configurations.mwe2)

        inputs.file("./GenerateModel.mwe2")
        inputs.file("model/oxsts.ecore")
        inputs.file("model/oxsts.genmodel")

        outputs.dir("src/main/emf-gen")

        args("./GenerateModel.mwe2", "-p", "rootPath=/$projectDir")
    }

    compileJava {
        inputs.files(generateEPackage.get().outputs)
    }

    clean {
        delete("src/main/emf-gen")
    }
}
