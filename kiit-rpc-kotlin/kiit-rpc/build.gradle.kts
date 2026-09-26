plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover)
    alias(libs.plugins.skie)
    id("signing")
}

// Single source of truth for the published version — mirrors kiit-codes/kiit-result. Left as a
// placeholder: the starting version and first publish target (GitHub Packages pre-release vs.
// Maven Central stable) haven't been decided yet.
val libraryVersion = "0.8.0"

kotlin {
    jvm {
        compilerOptions {
            // JVM 21 so Kotlin emits PermittedSubclasses for the sealed Auth/Body hierarchies,
            // enabling exhaustive Java pattern-matching `switch`, same as kiit-codes/kiit-result.
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    androidTarget {
        publishLibraryVariants("release")
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach {
        it.binaries.framework {
            baseName = "KiitRpc"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
            // Hardcoded coordinates rather than version-catalog entries, matching kiit-result's
            // own dependency on kiit-codes — these are external kiit libraries, not part of this
            // repo's own version catalog.
            //
            // api, not implementation: kiit-rpc's public API directly exposes both (every
            // RpcClient method returns Outcome<RpcResponse>, Policy returns Outcome,
            // StatusConverter returns Status), so consumers need them on their own compile
            // classpath too, same reasoning as kiit-result's own dependency on kiit-codes.
            api("dev.kiit:kiit-codes:1.1.0")
            api("dev.kiit:kiit-result:1.0.2")

            // Inputs/Meta are published to Maven Central, no composite build needed.
            api("dev.kiit:kiit-inputs:0.8.0")

            // RpcSettings exposes Identity directly. Published to Maven Central, no composite
            // build needed.
            api("dev.kiit:kiit-identity:0.8.0")

            // RpcRequest/RpcResponse expose Verb/Version/Trace/Content/ClientRequest directly
            // (kiit-requests, which now owns the shared call-shape vocabulary). Resolved from
            // the local checkout via the composite build in settings.gradle.kts until it's
            // actually published.
            api("dev.kiit:kiit-requests:0.0.0")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

// Disabled: SKIE's default analytics upload sends git/hardware/project data to Touchlab — off
// until that's something we explicitly want, not because it's a default worth silently keeping.
skie {
    analytics {
        enabled.set(false)
    }
}

android {
    namespace = "kiit.rpc"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

/**
 * Store the following in ~/.gradle/gradle.properties
 *
 * signingInMemoryKeyPassword=
 * signingInMemoryKey=
 * signing.gnupg.keyName=
 * signing.gnupg.passphrase=
 *
 * Maven local: ~/.m2/repository/dev/kiit/kiit-rpc/
 */
mavenPublishing {
    publishToMavenCentral(automaticRelease = true)

    coordinates(
        groupId = "dev.kiit",
        artifactId = "kiit-rpc",
        version = libraryVersion,
    )
    pom {
        name = "kiit-rpc"
        description = "Simple, declarative RPC-style HTTP client for Kotlin Multiplatform"
        url = "https://kiit.dev"
        licenses {
            license {
                name = "Apache-2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0"
            }
        }
        developers {
            developer {
                id = "codehelix"
                name = "CodeHelix"
                url = "https://kiit.dev"
            }
        }
        scm {
            url = "https://github.com/kiitdev/kiit-rpc"
            connection = "scm:git:git://github.com/kiitdev/kiit-rpc.git"
            developerConnection = "scm:git:ssh://git@github.com/kiitdev/kiit-rpc.git"
        }
    }
}

detekt {
    config.setFrom("$projectDir/detekt.yml")
    buildUponDefaultConfig = true
    source.setFrom(
        "src/commonMain/kotlin",
        "src/iosMain/kotlin",
    )
}

signing {
    useGpgCmd()
    sign(publishing.publications)
}

// The jvm() target compiles to JVM 21 bytecode (see the jvm{} block above, for exhaustive Java
// switch over sealed Auth/Body later) — run jvmTest on a matching JVM, same as kiit-codes/kiit-result.
tasks.named<Test>("jvmTest") {
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        },
    )
}

// Read by the release workflow (once one exists) to derive the git tag/GitHub release name from
// the same version published to Maven Central, same convention as kiit-codes/kiit-result.
tasks.register("printVersion") {
    doLast { println(libraryVersion) }
}
