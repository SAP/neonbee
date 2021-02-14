## 🚀 Milestone: Gradle build tooling
 🌌 Road to Version 1.0

### 📝 Milestone Description

NeonBees module format is a specialized case of Javas default JAR format, extending it with attributes in the manifest file to define deployables, but also it comes with certain rules, which dependencies should be included in the Fat/UeberJar and which should not. This special logic is currently undocumented and hard to reproduce in own built tooling. Thus the NeonBee committers have internal build tooling at SAP, generating valid module JAR archive and generally for testing NeonBee artifacts.

The idea of this milestone item is to generalize this proprietary Gradle build tooling and making it available as a general open source plugin for building NeonBee module artifacts more easily. The plugin should be generally purposed and essentially a drop-in replacement for generating Java Libraries with existing build tooling of Gradle. As with the current plugin, the plugin should look for `@NeonBeeDeployable` annotations in the compiled sources, in order to determine, which verticles should also be marked deployable in the module.

## Tasks / Features

- Generalize the proprietary build tooling of SAP to make it a specific purpose Gradle plugin for building NeonBee modules
- Release the Gradle plugin open source, to the Gradle Plugin library, for everyone to use easily in their Gradle build tooling
- Document the build tooling in the NeonBee documentation
- Consider adding further plugins to the tooling, which help developing / building for NeonBee