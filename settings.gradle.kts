rootProject.name = "user"

include("service", "model")

dependencyResolutionManagement {
    versionCatalogs {
        repositories {
            mavenLocal()
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/bible-game/version")
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }

        create("libs") {
            from("game.bible:version:0.1.0")
        }
    }
}