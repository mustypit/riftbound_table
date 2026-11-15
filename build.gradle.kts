import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
    id("org.jetbrains.compose") version "1.8.2"
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    testImplementation(kotlin("test"))
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation("junit:junit:4.13.2")
}

compose.desktop {
    application {
        mainClass = "io.github.mustypit.riftboundtable.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Riftbound Table"
            packageVersion = (System.getenv("GITHUB_RUN_NUMBER")?.let { "1.0.$it" } ?: "1.0.0")
            description = "Personal virtual table for the Riftbound TCG"
            vendor = "mustypit"
            windows { menuGroup = "Riftbound Table"; upgradeUuid = "6f0c2b52-3f7e-4d63-9a59-2e7c4a1b8d11" }
            macOS { bundleID = "io.github.mustypit.riftboundtable" }
            linux { packageName = "riftbound-table" }
        }
    }
}

tasks.test {
    // Screenshots land in build/screenshots (looked at after every UI change).
    systemProperty("screenshots.dir", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
    maxHeapSize = "2g"
}
