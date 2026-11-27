/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.github.gradle.node.pnpm.task.PnpmTask
import com.github.gradle.node.task.NodeTask
import hu.bme.mit.semantifyr.gradle.AppendFurtherCases
import hu.bme.mit.semantifyr.gradle.corpusModels
import hu.bme.mit.semantifyr.gradle.oxstsFileExtension
import hu.bme.mit.semantifyr.gradle.semantifyrArtifact
import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    id("hu.bme.mit.semantifyr.nodejs")
    id("hu.bme.mit.semantifyr.suites")
    kotlin("jvm")
}

dependencies {
    implementation(project(":utils"))
    implementation(project(":logging"))
}

testing {
    suites {
        named<JvmTestSuite>("integrationTest") {
            dependencies {
                implementation(testFixtures(project(":oxsts.lang")))
            }
        }
    }
}

val sysmlCommit = "569fe43143fe23f96eb668dc9e87af2d5b9cf8c7" // feat: a transition whose first names the entry action of its state is initial
val sysmlUrl = "https://github.com/arminzavada/sysml-2ls.git"
val sysmlDirectory = layout.projectDirectory.dir("sysml-2ls")

node {
    nodeProjectDir = sysmlDirectory
    // pnpm switches itself to the fork's packageManager, so the installed one must match it
    pnpmVersion = providers.fileContents(sysmlDirectory.file("package.json")).asText.map {
        Regex("\"packageManager\"\\s*:\\s*\"pnpm@([^\"+]+)").find(it)?.groupValues?.get(1)
    }.orElse(libs.versions.pnpm)
}

val checkoutSysml = tasks.register<Exec>("checkoutSysml") {
    inputs.property("sysmlUrl", sysmlUrl)
    inputs.property("sysmlCommit", sysmlCommit)
    inputs.dir("scripts").withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(sysmlDirectory.dir(".git"))

    if (Os.isFamily(Os.FAMILY_WINDOWS)) {
        commandLine("cmd.exe", "/d", "/c", "scripts\\checkout.cmd", sysmlDirectory.asFile.absolutePath, sysmlUrl, sysmlCommit)
    } else {
        commandLine("scripts/checkout.sh", sysmlDirectory.asFile.absolutePath, sysmlUrl, sysmlCommit)
    }
}

tasks.pnpmInstall {
    // the checkout's working tree is not a declared output of checkoutSysml
    dependsOn(checkoutSysml)
    // every pnpm run records its run state here
    nodeModulesOutputFilter {
        exclude(".pnpm-task-run-state-v1")
    }
}

val sysmlSourceFiles = fileTree(sysmlDirectory.dir("packages")) {
    include("**/src/**/*.ts")
    include("**/gen/**/*.ts")
    include("**/tsconfig.json")
    include("**/package.json")
    include("**/esbuild.mjs")
    include("**/scripts/*.*")
    exclude("**/node_modules/**")
    exclude("**/lib/**")
    exclude("**/dist/**")
}

val buildCli = tasks.register<PnpmTask>("buildCli") {
    // node_modules links back into the workspace packages, so it changes whenever they are built
    dependsOn(tasks.pnpmInstall)
    inputs.file(sysmlDirectory.file("package.json")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(sysmlDirectory.file("tsconfig.json")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(sysmlSourceFiles).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(sysmlDirectory.file("packages/syside-cli/dist/cli.cjs"))

    pnpmCommand = listOf("run", "cli")
}

val bundleExtension = tasks.register<PnpmTask>("bundleExtension") {
    dependsOn(tasks.pnpmInstall)
    mustRunAfter(buildCli)
    inputs.file(sysmlDirectory.file("package.json")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(sysmlDirectory.file("tsconfig.json")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(sysmlSourceFiles).withPathSensitivity(PathSensitivity.RELATIVE)
    val vscodePackageDirectory = sysmlDirectory.dir("packages/syside-vscode")
    outputs.files(
        fileTree(vscodePackageDirectory) {
            include("*.vsix")
        },
    )

    // the vsix name carries the fork's version, and collectExtension must find only the current one
    doFirst {
        vscodePackageDirectory.asFile.listFiles { file ->
            file.extension == "vsix"
        }?.forEach {
            it.delete()
        }
    }

    pnpmCommand = listOf("run", "vscode:package")
}

val sysideVsixDirectory = layout.buildDirectory.dir("vscode-extension")

val collectExtension = tasks.register<Sync>("collectExtension") {
    from(bundleExtension)
    rename(".*\\.vsix", "syside-vscode.vsix")
    into(sysideVsixDirectory)
}

val cliBundleDirectory = layout.buildDirectory.dir("cli-bundle")

val bundleCli = tasks.register<Sync>("bundleCli") {
    from(buildCli)
    from(sysmlDirectory.dir("SysML-v2-Release/sysml.library")) {
        include("**/*.sysml")
        include("**/*.kerml")
        into("sysml.library")
    }
    into(cliBundleDirectory)
}

val assembleFork = tasks.register<PnpmTask>("assembleFork") {
    dependsOn(tasks.pnpmInstall)
    mustRunAfter(buildCli, bundleExtension)
    inputs.file(sysmlDirectory.file("package.json")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(sysmlDirectory.file("tsconfig.json")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(sysmlSourceFiles).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(sysmlDirectory.dir("packages/syside-languageserver/lib"))

    pnpmCommand = listOf("-r", "assemble")
}

val languageServerDirectory = sysmlDirectory.dir("packages/syside-languageserver")

val bundleWorker = tasks.register<PnpmTask>("bundleWorker") {
    dependsOn(tasks.pnpmInstall)
    inputs.files(assembleFork).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(sysmlSourceFiles).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(languageServerDirectory.file("library/Verification.sysml")).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(languageServerDirectory.dir("dist/worker"))

    pnpmCommand = listOf("-F", "syside-languageserver", "bundle:worker")
}

val sysmlWorkerDirectory = layout.buildDirectory.dir("sysml-worker")

val collectWorker = tasks.register<Sync>("collectWorker") {
    from(bundleWorker) {
        exclude(
            "library/.project",
            "library/.settings/**",
        )
    }
    from(languageServerDirectory.file("syntaxes/sysml.tmLanguage.json"))
    into(sysmlWorkerDirectory)
}

configurations.consumable("sysmlWorkerElements") {
    attributes(semantifyrArtifact("sysml-worker"))
    outgoing.artifact(sysmlWorkerDirectory) {
        builtBy(collectWorker)
    }
}

configurations.consumable("vscodeExtensionElements") {
    attributes(semantifyrArtifact("vscode-extension"))
    outgoing.artifact(
        sysideVsixDirectory.map {
            it.file("syside-vscode.vsix")
        },
    ) {
        builtBy(collectExtension)
    }
}

tasks.processResources {
    from(bundleCli) {
        into("sysml-cli")
    }
}

val compiledExamplesDirectory = layout.buildDirectory.dir("compiled-examples")
val examplesSourceDirectory = layout.projectDirectory.dir("../models/examples")
val appendedCorpusDirectory = layout.buildDirectory.dir("corpus")

val appendFurtherCases = tasks.register<AppendFurtherCases>("appendFurtherCases") {
    corpusDirectory = examplesSourceDirectory
    outputDirectory = appendedCorpusDirectory
}

configurations.consumable("sysmlCorpusElements") {
    attributes(semantifyrArtifact("sysml-corpus"))
    outgoing.artifact(appendedCorpusDirectory) {
        builtBy(appendFurtherCases)
    }
}

val compiledExamplesElements = configurations.consumable("compiledExamplesElements") {
    attributes(semantifyrArtifact("compiled-examples"))
}

// the CLI builds every SysML and KerML file under the model's directory as its workspace
fun registerSysmlCompile(
    taskName: String,
    sourceDirectory: Provider<Directory>,
    workspace: FileCollection,
    name: String,
    targetDirectory: Provider<Directory>,
    elements: NamedDomainObjectProvider<ConsumableConfiguration>,
) {
    val sourceFile = sourceDirectory.map {
        it.file("$name.sysml")
    }
    val targetFile = targetDirectory.map {
        it.file("$name.$oxstsFileExtension")
    }
    val namesFile = targetDirectory.map {
        it.file("$name.names.json")
    }
    val compile = tasks.register<NodeTask>(taskName) {
        inputs.files(bundleCli).withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.files(workspace).withPathSensitivity(PathSensitivity.RELATIVE)
        outputs.file(targetFile)
        outputs.file(namesFile)
        outputs.cacheIf {
            true
        }
        script = cliBundleDirectory.map {
            it.file("cli.cjs")
        }
        workingDir = layout.projectDirectory
        args = provider {
            listOf("compile", sourceFile.get().asFile.relativeTo(projectDir).invariantSeparatorsPath, cliBundleDirectory.get().dir("sysml.library").asFile.relativeTo(projectDir).invariantSeparatorsPath, "-o", targetFile.get().asFile.relativeTo(projectDir).invariantSeparatorsPath)
        }
    }
    elements.configure {
        outgoing.artifact(targetFile) {
            builtBy(compile)
        }
        outgoing.artifact(namesFile) {
            builtBy(compile)
        }
    }
}

val examplesWorkspace = fileTree(examplesSourceDirectory) {
    include(
        "**/*.sysml",
        "**/*.kerml",
    )
}

// every model on the default library, as live serves it: live shows the compiled served models, and the live backend's tests take others
corpusModels(examplesSourceDirectory).filter {
    !it.conformance
}.forEach {
    registerSysmlCompile(
        "compileSysmlExample_${it.name}",
        provider {
            examplesSourceDirectory
        },
        examplesWorkspace,
        it.name,
        compiledExamplesDirectory,
        compiledExamplesElements,
    )
}

val smokeSourceDirectory = layout.projectDirectory.dir("../models/smoke")
val compiledSmokeDirectory = layout.buildDirectory.dir("compiled-smoke")

val compiledSmokeElements = configurations.consumable("compiledSmokeElements") {
    attributes(semantifyrArtifact("compiled-smoke"))
}

registerSysmlCompile(
    "compileSysmlSmoke",
    provider {
        smokeSourceDirectory
    },
    fileTree(smokeSourceDirectory) {
        include(
            "**/*.sysml",
            "**/*.kerml",
        )
    },
    "smoke",
    compiledSmokeDirectory,
    compiledSmokeElements,
)

tasks.named("integrationTest") {
    inputs.files(appendFurtherCases).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(
        layout.projectDirectory.file("../models/etc/crossroads-green.trace.sysml"),
        layout.projectDirectory.file("../models/etc/crossroads-green.report.json"),
        layout.projectDirectory.file("../models/etc/crossroads-green.witness.semantifyr"),
        layout.projectDirectory.file("../models/etc/room-lamp-lit.trace.sysml"),
        layout.projectDirectory.file("../models/tutorial/room.sysml"),
    ).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(layout.projectDirectory.dir("../models/libraries/default")).withPathSensitivity(PathSensitivity.RELATIVE)
}
