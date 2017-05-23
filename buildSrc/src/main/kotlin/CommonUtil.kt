@file:Suppress("unused") // usages in build scripts are not tracked properly

import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.script.lang.kotlin.*
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.internal.AbstractTask
import org.gradle.jvm.tasks.Jar
import java.io.File

inline fun <reified T : Task> Project.task(noinline configuration: T.() -> Unit) = tasks.creating(T::class, configuration)


fun AbstractTask.dependsOnTaskIfExist(task: String) {
    project.tasks.firstOrNull { it.name == task }?.let { dependsOn(it) }
}

fun AbstractTask.dependsOnTaskIfExistRec(task: String, project: Project? = null) {
    dependsOnTaskIfExist(task)
    (project ?: this.project).subprojects.forEach {
        dependsOnTaskIfExistRec(task, it)
    }
}

fun Project.dist(body: Copy.() -> Unit) {
    task<Copy>("dist") {
        dependsOn("assemble")
        body()
        into(rootProject.extra["distLibDir"].toString())
    }
}

fun Project.ideaPlugin(subdir: String = "lib", body: Copy.() -> Unit) {
    task<Copy>("idea-plugin") {
        dependsOnTaskIfExist("assemble")
        body()
        into(File(rootProject.extra["ideaPluginDir"].toString(), subdir).path)
    }
}

fun Project.fixKotlinTaskDependencies(): Unit {
    the<JavaPluginConvention>().sourceSets.all { sourceset ->
        val taskName = if (sourceset.name == "main") "classes" else (sourceset.name + "Classes")
        tasks.withType<Task> {
            if (name == taskName) {
                dependsOn("copy${sourceset.name.capitalize()}KotlinClasses")
            }
        }
    }
}

fun Jar.setupRuntimeJar(implementationTitle: String): Unit {
    dependsOn(":prepare:build.version:prepare")
    manifest.attributes.apply {
        put("Built-By", project.rootProject.extra["manifest.impl.vendor"])
        put("Implementation-Vendor", project.rootProject.extra["manifest.impl.vendor"])
        put("Implementation-Title", implementationTitle)
        put("Implementation-Version", project.rootProject.extra["build.number"])
    }
//    from(project.configurations.getByName("build-version").files, action = { into("META-INF/") })
}

fun Jar.setupSourceJar(implementationTitle: String): Unit {
    dependsOn("classes")
    setupRuntimeJar(implementationTitle + " Sources")
    project.pluginManager.withPlugin("java") {
        from(project.the<JavaPluginConvention>().sourceSets["main"].allSource)
    }
    classifier = "sources"
    project.artifacts.add("archives", this)
}

fun Project.buildVersion(): Dependency {
    val cfg = configurations.create("build-version")
    return dependencies.add(cfg.name, dependencies.project(":prepare:build.version", configuration = "default"))
}

fun Project.commonDep(coord: String): String {
    val parts = coord.split(':')
    return when (parts.size) {
        1 -> "$coord:$coord:${rootProject.extra["versions.$coord"]}"
        2 -> "${parts[0]}:${parts[1]}:${rootProject.extra["versions.${parts[1]}"]}"
        3 -> coord
        else -> throw IllegalArgumentException("Illegal maven coordinates: $coord")
    }
}

fun Project.commonDep(group: String, artifact: String): String = "$group:$artifact:${rootProject.extra["versions.$artifact"]}"

fun Project.preloadedDeps(vararg artifactBaseNames: String, baseDir: File = File(rootDir, "dependencies"), subdir: String? = null): ConfigurableFileCollection {
    val dir = if (subdir != null) File(baseDir, subdir) else baseDir
    if (!dir.exists() || !dir.isDirectory) throw GradleException("Invalid base directory $dir")
    val matchingFiles = dir.listFiles { file -> artifactBaseNames.any { file.matchMaybeVersionedArtifact(it) } }
    if (matchingFiles == null || matchingFiles.size < artifactBaseNames.size)
        throw GradleException("Not all matching artifacts '${artifactBaseNames.joinToString()}' found in the '$dir' (found: ${matchingFiles?.joinToString { it.name }})")
    return files(*matchingFiles.map { it.canonicalPath }.toTypedArray())
}

fun Project.ideaSdkDeps(vararg artifactBaseNames: String, subdir: String = "lib"): ConfigurableFileCollection =
        preloadedDeps(*artifactBaseNames, baseDir = File(rootDir, "ideaSdk"), subdir = subdir)

fun Project.ideaSdkCoreDeps(vararg artifactBaseNames: String): ConfigurableFileCollection = ideaSdkDeps(*artifactBaseNames, subdir = "core")

fun Project.kotlinDep(artifactBaseName: String): String = "org.jetbrains.kotlin:kotlin-$artifactBaseName:${rootProject.extra["kotlinVersion"]}"

fun DependencyHandler.projectDep(name: String): Dependency = project(name, configuration = "default")
fun DependencyHandler.projectDepIntransitive(name: String): Dependency =
        project(name, configuration = "default").apply { isTransitive = false }

val protobufLiteProject = ":custom-dependencies:protobuf-lite"
fun KotlinDependencyHandler.protobufLite(): ProjectDependency =
        project(protobufLiteProject, configuration = "default").apply { isTransitive = false }
val protobufLiteTask = "$protobufLiteProject:prepare"

fun KotlinDependencyHandler.protobufFull(): ProjectDependency =
        project(protobufLiteProject, configuration = "relocated").apply { isTransitive = false }
val protobufFullTask = "$protobufLiteProject:prepare-relocated-protobuf"

fun Project.getCompiledClasses(): SourceSetOutput? = the<JavaPluginConvention>().sourceSets.getByName("main").output
fun Project.getSources(): SourceDirectorySet? = the<JavaPluginConvention>().sourceSets.getByName("main").allSource
fun Project.getResourceFiles(): SourceDirectorySet? = the<JavaPluginConvention>().sourceSets.getByName("main").resources


private fun Project.configureKotlinProjectSourceSet(srcs: Iterable<File>, sourceSetName: String, getSources: SourceSet.() -> SourceDirectorySet) =
        configure<JavaPluginConvention> {
//            if (srcs.none()) {
//                sourceSets.removeIf { it.name == sourceSetName }
//            }
//            else {
                sourceSets.matching { it.name == sourceSetName }.forEach { it.getSources().setSrcDirs(srcs) }
//            }
        }

private fun Project.configureKotlinProjectSourceSet(vararg srcs: String, sourceSetName: String, getSources: SourceSet.() -> SourceDirectorySet, sourcesBaseDir: File? = null) =
        configureKotlinProjectSourceSet(srcs.map { File(sourcesBaseDir ?: projectDir, it) }, sourceSetName, getSources)

fun Project.configureKotlinProjectSources(vararg srcs: String, sourcesBaseDir: File? = null) =
        configureKotlinProjectSourceSet(*srcs, sourceSetName = "main", getSources = { this.java }, sourcesBaseDir = sourcesBaseDir)

fun Project.configureKotlinProjectSources(srcs: Iterable<File>) =
        configureKotlinProjectSourceSet(srcs, sourceSetName = "main", getSources = { this.java })

fun Project.configureKotlinProjectSourcesDefault(sourcesBaseDir: File? = null) = configureKotlinProjectSources("src", sourcesBaseDir = sourcesBaseDir)

fun Project.configureKotlinProjectResources(vararg srcs: String, sourcesBaseDir: File? = null) =
        configureKotlinProjectSourceSet(*srcs, sourceSetName = "main", getSources = { this.resources }, sourcesBaseDir = sourcesBaseDir)

fun Project.configureKotlinProjectResources(srcs: Iterable<File>) =
        configureKotlinProjectSourceSet(srcs, sourceSetName = "main", getSources = { this.resources })

fun Project.configureKotlinProjectNoTests() {
    configureKotlinProjectSourceSet(sourceSetName = "test", getSources = { this.java })
    configureKotlinProjectSourceSet(sourceSetName = "test", getSources = { this.resources })
}

fun Project.configureKotlinProjectTests(vararg srcs: String, sourcesBaseDir: File? = null) =
        configureKotlinProjectSourceSet(*srcs, sourceSetName = "test", getSources = { this.java }, sourcesBaseDir = sourcesBaseDir)

fun Project.configureKotlinProjectTestsDefault(sourcesBaseDir: File? = null) = configureKotlinProjectTests("tests", sourcesBaseDir = sourcesBaseDir)

fun Project.configureKotlinProjectTestResources(vararg srcs: String, sourcesBaseDir: File? = null) =
        configureKotlinProjectSourceSet(*srcs, sourceSetName = "test", getSources = { this.resources }, sourcesBaseDir = sourcesBaseDir)

private fun File.matchMaybeVersionedArtifact(baseName: String) =
        name == baseName ||
        name.removeSuffix(".jar") == baseName ||
        name.startsWith(baseName + "-")

