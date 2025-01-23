## General Workflow

You have the following options on how to use and/or debug Semantifyr:
- open the project in Intellij IDE and use the existing Kotlin configurations/make your own
- open the `semantifyr-vscode` subproject in VS Code and press *F5* to 
  - _make sure that the `Gradle for Java` and other Java extensions are disabled/set up to not auto-build, so that VS Code will not try to rebuild the project and that the recommended extensions (`.vscode/extensions.json`) are installed_
- install semantifyr as a VS code extension, for example: `code --install-extension ./subprojects/semantifyr-vscode/build/vscode/semantifyr-0.0.1.vsix` 
  - make sure to `gradle build` or `gradle assemble` beforehand and restart VS code after
  - if the extension is installed successfully, just open VS code, open `subprojects/semantifyr/TestModels` or your own models and use the commands popping up over the `Target`
