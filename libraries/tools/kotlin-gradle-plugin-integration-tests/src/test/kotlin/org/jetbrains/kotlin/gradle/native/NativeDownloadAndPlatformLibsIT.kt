/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.native

import org.gradle.api.logging.LogLevel
import org.gradle.testkit.runner.BuildResult
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.testbase.*
import org.jetbrains.kotlin.gradle.util.replaceFirst
import org.jetbrains.kotlin.gradle.utils.NativeCompilerDownloader
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.target.presetName
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.appendText
import kotlin.io.path.deleteRecursively

// We temporarily disable it for windows until a proper fix is found for this issue:
// https://youtrack.jetbrains.com/issue/KT-60138/NativeDownloadAndPlatformLibsIT-fails-on-Windows-OS
@OsCondition(
    supportedOn = [OS.MAC, OS.LINUX], enabledOnCI = [OS.MAC, OS.LINUX]
)
@DisplayName("Tests for K/N builds with native downloading and platform libs")
@NativeGradlePluginTests
class NativeDownloadAndPlatformLibsIT : KGPBaseTest() {

    companion object {
        private const val KOTLIN_SPACE_DEV = "https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/dev"
        private const val MAVEN_CENTRAL = "https://cache-redirector.jetbrains.com/maven-central"
    }

    private val platformName: String = HostManager.platformName()
    private val currentCompilerVersion = NativeCompilerDownloader.DEFAULT_KONAN_VERSION

    override val defaultBuildOptions: BuildOptions
        get() = super.defaultBuildOptions.copy(
            // For each test in this class, we need to provide an isolated .konan directory,
            // so we create it within each test project folder
            konanDataDir = workingDir.resolve(".konan")
                .toFile()
                .apply { mkdirs() }.toPath()
        )

    @AfterEach
    fun checkThatUserKonanDirIsEmptyAfterTest() {
        val userHomeDir = System.getProperty("user.home")
        assertFileNotExists(Paths.get("$userHomeDir/.konan/kotlin-native-prebuilt-$platformName-$currentCompilerVersion"))
    }

    @DisplayName("Downloading K/N distribution in default .konan dir")
    @GradleTest
    fun testLibrariesGenerationInDefaultKonanDir(gradleVersion: GradleVersion) {

        checkThatUserKonanDirIsEmptyAfterTest()

        val userHomeDir = System.getProperty("user.home")
        platformLibrariesProject("linuxX64", gradleVersion = gradleVersion) {
            build("assemble", buildOptions = defaultBuildOptions.copy(konanDataDir = null)) {
                assertOutputContains("Kotlin/Native distribution: .*kotlin-native-prebuilt-$platformName".toRegex())
                assertOutputDoesNotContain("Generate platform libraries for ")

                // checking that konan was downloaded and native dependencies were not downloaded into ~/.konan dir
                assertDirectoryExists(Paths.get("$userHomeDir/.konan/dependencies"))
                assertDirectoryExists(Paths.get("$userHomeDir/.konan/kotlin-native-prebuilt-$platformName-$currentCompilerVersion"))
            }
        }

        // clean ~/.konan after test it should not be with all inheritors of KGPBaseTest
        Paths.get("$userHomeDir/.konan/dependencies").deleteRecursively()
        Paths.get("$userHomeDir/.konan/kotlin-native-prebuilt-$platformName-$currentCompilerVersion").deleteRecursively()

    }

    @OptIn(EnvironmentalVariablesOverride::class)
    @DisplayName("K/N Gradle project build (on Linux or Mac) with a dependency from a Maven")
    @GradleTest
    fun testSetupCommonOptionsForCaches(gradleVersion: GradleVersion, @TempDir tempDir: Path) {
        val anotherKonanDataDir = tempDir.resolve(".konan2")
        nativeProject(
            "native-with-maven-dependencies",
            gradleVersion = gradleVersion,
            environmentVariables = EnvironmentalVariables(Pair("KONAN_DATA_DIR", anotherKonanDataDir.absolutePathString()))
        ) {
            build(
                "linkDebugExecutableNative",
                buildOptions = defaultBuildOptions.copy(
                    nativeOptions = defaultBuildOptions.nativeOptions.copy(
                        cacheKind = null
                    )
                )
            ) {
                assertOutputDoesNotContain("w: Failed to build cache")
                assertTasksExecuted(":linkDebugExecutableNative")
                assertDirectoryDoesNotExist(anotherKonanDataDir)
            }
        }
    }

    @DisplayName("Downloading K/N with custom konanDataDir property")
    @GradleTest
    fun testLibrariesGenerationInCustomKonanDir(gradleVersion: GradleVersion) {
        platformLibrariesProject("linuxX64", gradleVersion = gradleVersion) {
            build("assemble", buildOptions = defaultBuildOptions.copy(konanDataDir = workingDir.resolve(".konan"))) {
                assertOutputContains("Kotlin/Native distribution: .*kotlin-native-prebuilt-$platformName".toRegex())
                assertOutputDoesNotContain("Generate platform libraries for ")

                // checking that konan was downloaded and native dependencies were not downloaded into ~/.konan dir
                assertDirectoryExists(workingDir.resolve(".konan/dependencies"))
                assertDirectoryExists(workingDir.resolve(".konan/kotlin-native-prebuilt-$platformName-$currentCompilerVersion"))
            }
        }
    }

    @DisplayName("K/N distribution without platform libraries generation")
    @GradleTest
    fun testNoGenerationByDefault(gradleVersion: GradleVersion) {
        platformLibrariesProject("linuxX64", gradleVersion = gradleVersion) {
            build("assemble") {
                assertOutputContains("Kotlin/Native distribution: .*kotlin-native-prebuilt-$platformName".toRegex())
                assertOutputDoesNotContain("Generate platform libraries for ")
            }
        }
    }

    @DisplayName("K/N distribution with platform libraries generation")
    @GradleTest
    fun testLibrariesGeneration(gradleVersion: GradleVersion) {
        nativeProject("native-platform-libraries", gradleVersion = gradleVersion) {

            includeOtherProjectAsSubmodule("native-platform-libraries", "", "subproject", true)

            buildGradleKts.appendText("\nkotlin.linuxX64()\n")
            subProject("subproject").buildGradleKts.appendText("\nkotlin.linuxArm64()\n")

            // Check that platform libraries are correctly generated for both root project and a subproject.
            buildWithLightDist("assemble") {
                assertOutputContains("Kotlin/Native distribution: .*kotlin-native-$platformName".toRegex())
                assertOutputContains("Generate platform libraries for linux_x64")
                assertOutputContains("Generate platform libraries for linux_arm64")
            }

            // Check that we don't generate libraries during a second run. Don't clean to reduce execution time.
            buildWithLightDist("assemble") {
                assertOutputDoesNotContain("Generate platform libraries for ")
            }
        }
    }

    @DisplayName("Link with args via gradle properties")
    @GradleTest
    fun testLinkerArgsViaGradleProperties(gradleVersion: GradleVersion) {
        nativeProject("native-platform-libraries", gradleVersion = gradleVersion) {

            addPropertyToGradleProperties(
                "kotlin.native.linkArgs",
                mapOf(
                    "-Xfoo" to "-Xfoo=bar",
                    "-Xbaz" to "-Xbaz=qux"
                )
            )

            buildGradleKts.appendText(
                """
                |
                |kotlin.linuxX64() {
                |    binaries.sharedLib {
                |        freeCompilerArgs += "-Xmen=pool"
                |    }
                |}
                """.trimMargin()
            )

            build("linkDebugSharedLinuxX64", buildOptions = defaultBuildOptions.copy(logLevel = LogLevel.DEBUG)) {
                assertTasksExecuted(
                    ":compileKotlinLinuxX64",
                    ":linkDebugSharedLinuxX64"
                )
                extractNativeTasksCommandLineArgumentsFromOutput(":linkDebugSharedLinuxX64") {
                    assertCommandLineArgumentsContain("-Xfoo=bar", "-Xbaz=qux", "-Xmen=pool")
                }
                assertFileInProjectExists("build/bin/linuxX64/debugShared/libnative_platform_libraries.so")
                assertFileInProjectExists("build/bin/linuxX64/debugShared/libnative_platform_libraries_api.h")
            }
        }
    }

    @OsCondition(supportedOn = [OS.LINUX], enabledOnCI = [OS.LINUX])
    @DisplayName("Assembling project generates no platform libraries for unsupported host")
    @GradleTest
    fun testNoGenerationForUnsupportedHost(gradleVersion: GradleVersion) {
        platformLibrariesProject(KonanTarget.IOS_X64.presetName, gradleVersion = gradleVersion) {
            buildWithLightDist("assemble") {
                assertOutputDoesNotContain("Generate platform libraries for ")
            }
        }
    }

    @DisplayName("Build K/N project with prebuild type")
    @GradleTest
    fun testCanUsePrebuiltDistribution(gradleVersion: GradleVersion) {
        platformLibrariesProject("linuxX64", gradleVersion = gradleVersion) {
            build(
                "assemble", buildOptions = defaultBuildOptions.copy(
                    nativeOptions = defaultBuildOptions.nativeOptions.copy(
                        distributionType = "prebuilt"
                    )
                )
            ) {
                assertOutputContains("Kotlin/Native distribution: .*kotlin-native-prebuilt-$platformName".toRegex())
                assertOutputDoesNotContain("Generate platform libraries for ")
            }
        }
    }

    @DisplayName("Build K/N project with compiler reinstallation")
    @GradleTest
    fun testCompilerReinstallation(gradleVersion: GradleVersion) {
        platformLibrariesProject("linuxX64", gradleVersion = gradleVersion) {
            // Install the compiler at the first time. Don't build to reduce execution time.
            buildWithLightDist("tasks") {
                assertOutputContains("Generate platform libraries for linux_x64")
            }

            // Reinstall the compiler.
            buildWithLightDist(
                "tasks",
                buildOptions = defaultBuildOptions.copy(nativeOptions = defaultBuildOptions.nativeOptions.copy(reinstall = true))
            ) {
                assertOutputContains("Unpack Kotlin/Native compiler to ")
                assertOutputContains("Generate platform libraries for linux_x64")
            }
        }
    }

    @DisplayName("Download prebuilt Native bundle with maven")
    @GradleTest
    fun shouldDownloadPrebuiltNativeBundleWithMaven(gradleVersion: GradleVersion) {
        val maven = mavenUrl()
        Assumptions.assumeTrue(
            maven != MAVEN_CENTRAL,
            "Don't run this test for build that are not yet published to central.\n" +
                    " We won't public K/N into Maven central until this task is completed: KTI-1067"
        )

        nativeProject("native-download-maven", gradleVersion = gradleVersion) {

            buildGradleKts.replaceFirst("// <MavenPlaceholder>", "maven(\"${maven}\")")

            build(
                "assemble",
                buildOptions = defaultBuildOptions.copy(nativeOptions = defaultBuildOptions.nativeOptions.copy(distributionDownloadFromMaven = true))
            ) {
                assertOutputContains("Unpack Kotlin/Native compiler to ")
                assertOutputDoesNotContain("Generate platform libraries for ")
            }
        }
    }

    @DisplayName("Download light Native bundle with maven")
    @RequiredXCodeVersion(minSupportedMajor = 14, minSupportedMinor = 1)
    @GradleTest
    fun shouldDownloadLightNativeBundleWithMaven(gradleVersion: GradleVersion) {
        val maven = mavenUrl()
        Assumptions.assumeTrue(
            maven != MAVEN_CENTRAL,
            "Don't run this test for build that are not yet published to central.\n" +
                    " We won't public K/N into Maven central until this task is completed: KTI-1067"
        )

        nativeProject("native-download-maven", gradleVersion = gradleVersion) {
            buildGradleKts.replaceFirst("// <MavenPlaceholder>", "maven(\"${maven}\")")
            val nativeOptions = defaultBuildOptions.nativeOptions.copy(
                distributionType = "light",
                distributionDownloadFromMaven = true
            )
            build(
                "assemble",
                buildOptions = defaultBuildOptions.copy(nativeOptions = nativeOptions)
            ) {
                assertOutputContains("Unpack Kotlin/Native compiler to ")
                assertOutputContains("Generate platform libraries for ")
            }
        }
    }

    @DisplayName("Download from maven should fail if there is no such build in the default repos")
    @GradleTest
    fun shouldFailDownloadWithNoBuildInDefaultRepos(gradleVersion: GradleVersion) {
        nativeProject("native-download-maven", gradleVersion = gradleVersion) {
            val nativeOptions = BuildOptions.NativeOptions(
                version = "1.8.0-dev-1234",
                distributionDownloadFromMaven = true
            )
            buildAndFail(
                "assemble",
                buildOptions = defaultBuildOptions.copy(nativeOptions = nativeOptions)
            ) {
                assertOutputContains("Could not find org.jetbrains.kotlin:kotlin-native")
            }
        }
    }

    private fun platformLibrariesProject(
        vararg targets: String,
        gradleVersion: GradleVersion,
        test: TestProject.() -> Unit = {},
    ) {
        nativeProject("native-platform-libraries", gradleVersion) {
            buildGradleKts.appendText(
                targets.joinToString(prefix = "\n", separator = "\n") {
                    "kotlin.$it()"
                }
            )
            test()
        }
    }

    private fun TestProject.buildWithLightDist(
        vararg tasks: String,
        buildOptions: BuildOptions = defaultBuildOptions.copy(),
        assertions: BuildResult.() -> Unit,
    ) =
        build(
            *tasks,
            buildOptions = buildOptions.copy(
                nativeOptions = buildOptions.nativeOptions.copy(distributionType = "light")
            ),
            assertions = assertions
        )

    private fun mavenUrl(): String {
        val versionPattern = "(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-(\\p{Alpha}*\\p{Alnum}|[\\p{Alpha}-]*))?(?:-(\\d+))?".toRegex()
        val (_, _, _, metaString, build) = versionPattern.matchEntire(currentCompilerVersion)?.destructured
            ?: error("Unable to parse version $currentCompilerVersion")
        return when {
            metaString == "dev" || build.isNotEmpty() -> KOTLIN_SPACE_DEV
            metaString in listOf("RC", "RC2", "Beta") || metaString.isEmpty() -> MAVEN_CENTRAL
            else -> throw IllegalStateException("Not a published version $currentCompilerVersion")
        }
    }

}
