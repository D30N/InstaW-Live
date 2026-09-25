pluginManagement {
    repositories {
        maven { url = uri("/home/hatch/workspace/android-toolchain/local-repo") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("/home/hatch/workspace/android-toolchain/local-repo") }
        google()
        mavenCentral()
    }
}
rootProject.name = "follower-widget"
include(":app")
