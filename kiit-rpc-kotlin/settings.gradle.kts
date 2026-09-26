pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Lets Gradle auto-provision a JDK toolchain for compiling/testing when only an older JDK is
    // installed locally.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "kiit-rpc-kotlin"

// kiit-inputs and kiit-identity are both published to Maven Central (see build.gradle.kts), no
// composite build needed for either anymore.

// Composite build: kiit-requests isn't published to Maven Central yet, so build it from the
// local checkout instead. Explicit substitution rather than relying on group/version matching,
// since the included subproject doesn't set `group`/`version` as real Gradle project properties
// (only inside its mavenPublishing { coordinates(...) } block). Remove this block once
// kiit-requests is actually published.
includeBuild("../../kiit-requests/kiit-requests-kotlin") {
    dependencySubstitution {
        substitute(module("dev.kiit:kiit-requests")).using(project(":kiit-requests"))
    }
}

include(":kiit-rpc")
include(":sample-kotlin")

// sample-kotlin stays in the shared ./samples/ folder alongside sample-java/sample-swift, one
// level up from this settings file, matching kiit-codes' layout. sample-java/sample-swift are
// still empty placeholders (see _prd/kiit-httprpc/260921-setup-plan.md, Step 7), not included yet.
project(":sample-kotlin").projectDir = file("../samples/sample-kotlin")
