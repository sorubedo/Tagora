pluginManagement {
    repositories {
        maven { url = uri("https://mirrors.cloud.tencent.com/gradle/") }  // google() 镜像
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }  // mavenCentral() 镜像
        maven { url = uri("https://mirrors.cloud.tencent.com/gradle-plugins/") }  // gradlePluginPortal() 镜像
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://mirrors.cloud.tencent.com/gradle/") }  // google() 镜像
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }  // mavenCentral() 镜像
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Tagora"
include(":app")
