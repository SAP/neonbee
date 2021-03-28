## 🚀 Milestone: Change hooks to SPI / remove launcher pre-processors
 🌌 Road to Version 1.0

### 📝 Milestone Description

Currently NeonBee comes with an own, annotation and module-based concept for hooks, where special hook classes can be annotated with the `@Hook` annotation or listed as `NeonBee-Hooks` in the module JAR format when loading verticles. NeonBee attempts to find loaded hook classes on the class path or when loading modules. Hooks can be used to influence certain behaviours of NeonBee, such as a pre-request hook, when the server verticle is receiving a message. Furthermore the Service Provider Interface (SPI) has been used in the NeonBee `Launcher` class, to be able to call a / multiple generic `LauncherPreProcessor`, which will run, before the NeonBee launcher starts.

These are two concepts for a similar purpose. Furthermore it is questionable whether NeonBee should provide the possibility to hook in a `LauncherPreProcessor` in the first place, as this could be achieved with an own `main` method calling the `Launcher` or by simply invoking the `Launcher` e.g. via a shell script. Thus providing a `LauncherPreProcessor` is nothing NeonBee should focus supporting on in future.

This milestone item is to evaluate whether the current, custom / annotation-based hooks concept should be migrated over to Javas Service Provider Interface (SPI) instead of custom a class loading / reflection based mechanism. Furthermore the `LauncherPreProcessor` shall be removed or at least adapted to fit into the new hooking method.

## Tasks / Features

- Determine whether to move the NeonBee hooking support to SPI
- Either adapt or remove the `LauncherPreProcessor` logic