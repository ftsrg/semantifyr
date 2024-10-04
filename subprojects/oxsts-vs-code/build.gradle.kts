import com.github.gradle.node.npm.task.NpmTask
import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    base
    alias(libs.plugins.gradle.node)
}

val distribution by configurations.creating

dependencies {
    distribution(project(":oxsts.lang.ide", configuration = "distributionOutput"))
}

val cloneIde by tasks.registering(Sync::class) {
    dependsOn(distribution)
    inputs.file(distribution.singleFile)

    from(tarTree(distribution.singleFile))
    into("bin")
}

tasks.clean {
    delete("dist")
}

tasks {
    val compile by registering(NpmTask::class) {
        dependsOn(npmInstall)

        npmCommand.set(
            listOf(
                "run",
                "package",
            )
        )

        outputs.dir("dist")
    }

    val packageExtension by registering(Exec::class) {
        inputs.files(cloneIde.get().outputs)
        inputs.files(compile.get().outputs)
        inputs.file("node_modules/.bin/vsce.cmd")

        outputs.dir(project.layout.buildDirectory.dir("vscode"))

        if (Os.isFamily(Os.FAMILY_WINDOWS)) {
            commandLine(
                "cmd",
                "/c",
                "node_modules\\.bin\\vsce.cmd",
                "package",
                "--out", project.layout.buildDirectory.dir("vscode").get().asFile.absolutePath,
            )
        } else {
            commandLine(
                "sh",
                "node_modules/.bin/vsce",
                "package",
                "--out", project.layout.buildDirectory.dir("vscode").get().asFile.absolutePath,
            )
        }
    }

    assemble {
        dependsOn(packageExtension)
    }
}
