# Release Artifacts

This document describes which artifacts are generated for a NeonBee release.

## How does Gradle compose artifact archive names

### Archive

```txt
{baseName}-{archiveAppendix}-{version}-{archiveClassifier}.{archiveExtension}

e.g.:
neonbee-core-0.0.1-shadow.jar
```

## Artifact Structure on Nexus

**Example:**

```bash
.
└── io
    └── neonbee
        ├── neonbee-core
        │   └── 0.0.1
        │       ├── neonbee-core-0.0.1.jar
        │       ├── neonbee-core-0.0.1-javadoc.jar
        │       ├── neonbee-core-0.0.1-shadow.jar
        │       └── neonbee-core-0.0.1-sources.jar
        |
        ├── neonbee-dist
        │   └── 0.0.1
        │       ├── neonbee-dist-0.0.1.tar.gz
        └── neonbee-core-test
            └── 0.0.1
               ├── neonbee-core-test-0.0.1.jar
               └── neonbee-core-test-0.0.1-sources.jar
```
