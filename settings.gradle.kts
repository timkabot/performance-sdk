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
    }

    versionCatalogs {
        create("commonLibs") {
            from(files("gradle/common.versions.toml"))
        }
    }
}

rootProject.name = "performance-sdk"
include(":performance-sdk")
include(":sample-app")
