plugins {
    application
}

application {
    apply(plugin = "maven-publish")
    mainClass.set("com.elvarg.Server")
}

val lib = rootProject.project.libs
dependencies {
    with(lib) {
        implementation(commons)
        implementation(commons.lang)
        implementation(commons.compress)
        implementation(classgraph)
        implementation(slf4j.api)
        implementation(okhttp3)
        implementation(password4J)
        implementation(dynamodb)
        implementation(joda)
        implementation(dynamodb.enhanced)
        implementation(netty.all)
    }
    runtimeOnly(project(":plugin"))
}

tasks.named<Jar>("jar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks {

    build {
        this.finalizedBy(project(":plugin").tasks.getByName("build"))
    }

    buildNeeded {
        this.finalizedBy(project(":plugin").tasks.getByName("buildNeeded"))
    }

    compileKotlin {
        this.finalizedBy(project(":plugin").tasks.getByName("build"))
    }

}


// ---------------------------------------------------------------------------
// EverGielinor host tools
//
// World generation runs ahead of the server, not during boot, so a broken seed
// is caught before players are let in. Each task runs from the module directory
// because the server's data paths (GameConstants.CLIPPING_DIRECTORY) are
// relative to it.
//
//   ./gradlew :game:generateWorld -Pseed=847293
//   ./gradlew :game:generateWorld -Pseed=847293 -PdryRun=true
//   ./gradlew :game:inspectWorld
//   ./gradlew :game:previewIsland -Pseed=847293
//   ./gradlew :game:verifyCodec
// ---------------------------------------------------------------------------

fun Project.toolArgsFor(vararg extra: String): List<String> {
    val list = mutableListOf<String>()
    if (project.hasProperty("seed")) {
        list += listOf("--seed", project.property("seed").toString())
    }
    if (project.hasProperty("dryRun") && project.property("dryRun") == "true") {
        list += "--dry-run"
    }
    list += extra
    return list
}

tasks.register<JavaExec>("generateWorld") {
    group = "evergielinor"
    description = "Generates, validates and installs a procedural island from a seed."
    mainClass.set("com.elvarg.game.world.tool.GenerateWorld")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = projectDir
    doFirst { args = project.toolArgsFor() }
}

tasks.register<JavaExec>("inspectWorld") {
    group = "evergielinor"
    description = "Prints the installed world: seed, localities, dungeons and boss assignments."
    mainClass.set("com.elvarg.game.world.tool.InspectWorld")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = projectDir
}

tasks.register<JavaExec>("previewIsland") {
    group = "evergielinor"
    description = "Renders a seed's geography to island.png without writing any game data."
    mainClass.set("com.elvarg.game.world.tool.IslandPreview")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = projectDir
    doFirst {
        val list = mutableListOf(
            project.findProperty("seed")?.toString() ?: "847293",
            project.findProperty("out")?.toString() ?: "island.png"
        )
        // -Pcrop=<halfWidth> -Pzoom=<scale> renders a close-up of the start village,
        // which is the only way to judge town layout rather than island shape.
        project.findProperty("crop")?.let { list += listOf("--crop", it.toString()) }
        project.findProperty("zoom")?.let { list += listOf("--zoom", it.toString()) }
        args = list
    }
}

tasks.register<JavaExec>("verifyCodec") {
    group = "evergielinor"
    description = "Round-trips every shipped map file through the landscape codec."
    mainClass.set("com.elvarg.game.world.tool.CodecSelfTest")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = projectDir
    args = listOf("../data/clipping")
}

tasks.register<JavaExec>("verifyInstall") {
    group = "evergielinor"
    description = "Loads the installed island through the server's own RegionManager and checks it."
    mainClass.set("com.elvarg.game.world.tool.VerifyInstall")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = projectDir
}

tasks.register<JavaExec>("verifyDeterminism") {
    group = "evergielinor"
    description = "Generates one seed twice and proves the results are byte-identical."
    mainClass.set("com.elvarg.game.world.tool.DeterminismTest")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = projectDir
    doFirst { args = listOf(project.findProperty("seed")?.toString() ?: "847293") }
}

tasks.register<JavaExec>("objectSearch") {
    group = "evergielinor"
    description = "Finds object ids by name: -Pq=<substring> [-Pn=<max>]"
    mainClass.set("com.elvarg.game.world.tool.ObjectSearch")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = projectDir
    doFirst {
        args = listOf(
            project.findProperty("q")?.toString() ?: "bank",
            project.findProperty("n")?.toString() ?: "20"
        )
    }
}

tasks.register<JavaExec>("auditResources") {
    group = "evergielinor"
    description = "Prints the real object definition behind every resource id the generator places."
    mainClass.set("com.elvarg.game.world.tool.AuditResources")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = projectDir
}

tasks.register<JavaExec>("inspectDungeon") {
    group = "evergielinor"
    description = "Reports walkable area per dungeon floor: -Pid=<dungeonId> for one."
    mainClass.set("com.elvarg.game.world.tool.InspectDungeon")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = projectDir
    doFirst { args = listOfNotNull(project.findProperty("id")?.toString()) }
}

tasks.register<JavaExec>("verifyPalette") {
    group = "evergielinor"
    description = "Checks every floor value on the island against the client cache's own flo.dat. -Plist dumps the palette."
    mainClass.set("com.elvarg.game.world.tool.VerifyPalette")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = projectDir
    doFirst {
        args = if (project.hasProperty("list")) {
            listOf("../../ElvargClient/Cache", "list")
        } else {
            listOf<String>()
        }
    }
}

tasks.register<JavaExec>("extractBuildingParts") {
    group = "evergielinor"
    description = "Harvests wall, door, window, roof and furniture sets from the original map."
    mainClass.set("com.elvarg.game.world.tool.ExtractBuildingParts")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = projectDir
}
