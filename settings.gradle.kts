pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "LightTube"
include(":app")
include(":core:common", ":core:ui", ":core:data", ":core:network")
include(
    ":feature:explore", ":feature:download", ":feature:player", ":feature:search",
    ":feature:vault", ":feature:clean", ":feature:boost", ":feature:whatsapp",
    ":feature:web", ":feature:taskmanager", ":feature:settings", ":feature:updater",
    ":feature:ads", ":feature:push", ":feature:analytics", ":feature:oem",
)
