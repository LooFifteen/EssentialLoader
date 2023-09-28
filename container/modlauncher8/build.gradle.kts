import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import essential.configureModLauncherContainerJar

plugins {
    id("base")
    id("essential.build-logic")
}

val stage0 by configurations.creating { isCanBeConsumed = false }

dependencies {
    stage0(project(":stage0:modlauncher8"))
}

val jar by tasks.registering(ShadowJar::class) {
    destinationDirectory.set(project.buildDir.resolve("libs"))
    archiveBaseName.set("container-${project.name}")

    configureModLauncherContainerJar(stage0)

    from("resources")
}

artifacts {
    add("default", jar)
}
