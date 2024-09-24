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
gpr.user=<username>
gpr.token=<token>
```

### As a .env file (more secure)
1. Add .env files to your .gitignore
```
# Project exlude file types
*.env
```
2. Create a private.env within your project folder
```
githubUser=yourUsername
githubToken=yourToken
```
3. Add this function in your build.gradle file above repositories in order to be able to load .env files as Properties 
``` groovy
def envProperties = new Properties()
def envPropertiesFile = file('private.env')
envPropertiesFile.withReader('UTF-8') { reader ->
    envProperties.load(reader)
}
```
4. Add the Repository
``` groovy
    maven {
        name = "github"
        url = "https://maven.pkg.github.com/TerraNova-Devs/TerranovaLib"
        credentials {
            username = project.findProperty(envProperties.getProperty('githubUser'))
            password = project.findProperty(envProperties.getProperty('githubToken'))
        }
    }
```
5. Add the Dependencie
``` groovy
compileOnly 'de.mcterranova:terranova-lib:<version>'
```
Now you are good to go!


