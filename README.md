# KPM Kotlin Package Manager

[![License](https://img.shields.io/github/license/lheintzmann1/kpm)](https://opensource.org/licenses/Apache-2.0)
![Lifecycle](https://img.shields.io/badge/lifecycle-Experimental-teal)

## About

A fast, declarative, and extensible package manager for Kotlin. Inspired by NPM, Yarn, and Nix, KPM simplifies dependency management and reproducible builds in Kotlin projects.

## Features

The following commands have been implemented:

- `init`: Initialize a new KPM project using the base template.
- `install`: Apply the `kpm.json` file to the current project, installing dependencies and generating a `kpm-lock.json` file.
- `build`: Build the KPM project defined in the manifest file.
- `gc`: Clean up unused dependencies.
- `version`: Display the current version of KPM, Java, and Kotlin.
- `--help`: Show help information for KPM commands.

## Requirements

Add an environment variable named `KOTLIN_HOME` pointing to the directory where Kotlin is installed.

Examples (Windows):

- `C:\Program Files\Kotlin\bin` is not valid,
- `C:\Program Files\Kotlin\lib` is not valid,
- `C:\Program Files\Kotlin` is valid.

## Dependencies

Dependencies are managed through the `kpm.json` manifest file in your project root. Each dependency is specified using Maven coordinates in the following format:

`"group:artifact:version"`

Example `kpm.json` file:

```json
{
  "name": "my-kotlin-app",
  "version": "1.0",
  "main": "com.example.app.AppKt",
  "dependencies": [
    "org.jetbrains.kotlin:kotlin-stdlib:1.8.0",
    "com.example:my-library:1.0.0"
  ]
}
```

## License

This project is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.

## Contributing

Contributions are welcome! Feel free to open an issue or submit a pull request.