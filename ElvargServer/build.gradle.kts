import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm) apply true
    alias(libs.plugins.kotlin.serialization)
    idea
}

allprojects {
    apply(plugin = "idea")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

    group = "com.elvarg"
    version = "0.0.1"

    repositories {
        mavenLocal()
        mavenCentral()
    }

    val lib = rootProject.project.libs
    dependencies {
        with(lib) {
            implementation(gson)
            implementation(guava)
            implementation(progress.bar)
            implementation(inline.loggerr)
            implementation(kotlin.logging)
            implementation(kotlinx.coroutines)
            implementation(kotlin.script.runtime)
            implementation(kotlin.scripting)
            implementation(kotlin.reflect)
            implementation("org.litote.kmongo:kmongo-coroutine-serialization:4.5.0")
        }
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }
    kotlin{
        jvmToolchain{
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    idea {
        module {
            inheritOutputDirs = false
            outputDir = file("${project.buildDir}/classes/kotlin/main")
            testOutputDir = file("${project.buildDir}/classes/kotlin/test")
        }
    }

    tasks.compileJava {
        // Toolchain is 21 (see above); bytecode target stays 17 so it matches
        // Kotlin's jvmTarget below. Kotlin 1.8.10 cannot target 21.
        options.release.set(17)
    }

    tasks.withType<KotlinCompile>().all {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs = listOf(
                "-Xallow-any-scripts-in-source-roots" ,
            )
        }
    }
}


