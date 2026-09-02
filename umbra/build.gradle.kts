// Root build file. Per-module configuration lives in app/build.gradle.kts.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

// Dependency locking (dependencyLocking { lockAllConfigurations() }) lives in
// app/build.gradle.kts, not here: this root project has zero dependency
// configurations of its own, so declaring it here left a build.gradle.kts
// that could never have a matching gradle.lockfile next to it — see the
// comment there for the full story (a real SonarCloud finding, not just
// tidiness).
tasks.register("clean", Delete::class) {
    group = "build"
    description = "Deletes the build directory."
    delete(rootProject.layout.buildDirectory)
}
