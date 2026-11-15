pluginManagement {
    repositories {
        // repo1 first: in Claude Code cloud sessions repo.maven.apache.org often answers 429.
        maven("https://repo1.maven.org/maven2")
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://repo1.maven.org/maven2")
        mavenCentral()
        google()
    }
}
rootProject.name = "RiftboundTable"
