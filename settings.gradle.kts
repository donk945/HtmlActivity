pluginManagement {
    repositories {
        // 阿里云镜像（放在最前面加速）
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")

        // 官方仓库（保留原有的过滤规则）
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 阿里云镜像
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")

        // 官方仓库
        google()
        mavenCentral()
    }
}

rootProject.name = "HtmlActivity"
include(":app")
 