// Root build file. Per-module configuration lives in app/build.gradle.kts.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

// Every dependency version in gradle/libs.versions.toml is already an exact
// pin, not a range — but that alone doesn't cover transitive dependencies
// pulled in without a version of their own choosing. Locking makes the full
// resolved graph (transitives included) explicit and reproducible: CI
// regenerates app/gradle.lockfile on every push (see build-umbra.yml's
// "Generate dependency locks" + "Commit updated lockfile" steps) and a
// build fails loudly if resolution would otherwise pick something different
// from what's committed.
allprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
}

tasks.register("clean", Delete::class) {
    group = "build"
    description = "Deletes the build directory."
    delete(rootProject.layout.buildDirectory)
}
