## 🐝 NeonBee Core Build

**Create the neonbee-shadow.jar Shadow JAR**

Use the following command to create a neonbee-shadow.jar in the **build** directory which contains both, classes and dependencies needed to run NeonBee.:

```
gradlew shadowJar
```

**Create the neonbee-core.jar Core JAR**

Use the following command to create a neonbee-core.jar in the **build** directory which contains only the Vulp.s classes:

```
gradlew coreJar
```

**Create the neonbee-dist.tar.gz Distribution Archive**

Use the following command to create a neonbee-dist.tar.gz distribution archive in the **dist** folder that contains also start scripts:

```
gradlew distTar
```

**Create the neonbee-uber.jar Uber and neonbee-core.jar Core JARs as well as the neonbee-dist.zip and the neonbee-dist.tar.gz Distribution Archives**

Use the following command to create a neonbee-dist.tar.gz distribution archive in the **dist** folder that contains also start scripts:

```
gradlew build
```
