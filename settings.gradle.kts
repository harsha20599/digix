pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Google's Home library isn't published yet. It's downloaded by hand, and only it comes from here.
        mavenLocal {
            content {
                includeVersionByRegex("com\\.google\\.android\\.gms", "play-services-home.*", "1[7-9].*")
            }
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "Digix"
include(":app", ":hidden-stubs")
