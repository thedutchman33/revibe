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
        google()
        mavenCentral()
        // InnerTubeX and its QuackJS cipher runtime are published on JitPack.
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.MetrolistGroup.innertubex")
                includeGroup("com.github.MetrolistGroup.quackjs")
            }
        }
    }
}

rootProject.name = "Revibe"

include(":shared")
include(":desktopApp")
