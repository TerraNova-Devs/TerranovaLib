# TerranovaLib

## Usage

### build.gradle

```groovy
repositories {
    maven {
        name = "github"
        url = "https://maven.pkg.github.com/TerraNova-Devs/TerranovaLib"
        credentials {
            username = project.findProperty("gpr.user")
            password = project.findProperty("gpr.token")
        }
    }
}
```

```groovy
dependencies {
    implementation ('de.mcterranova:terranova-lib:0.0.3')
}
```

### Generate a token

1. Click on your profile picture
2. Settings
3. Developer Settings
4. Personal Access Tokens
5. Tokens(classic)
6. Make sure to check package:write and read permission

### gradle.properties

```
gpr.user="<username>"
gpr.token="<token>"
```
