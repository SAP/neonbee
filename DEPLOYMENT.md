## 🐝 NeonBee Core Deployment

**Run NeonBee in cluster mode in local environment**

To run NeonBee-cluster in a local environment, please apply following NeonBee options on command line:

```
java -jar build\neonbee-shadow-0.0.30-SNAPSHOT.jar -cl -cc hazelcast-local.xml
```

In this case, the cluster mode will be enabled with **-cl** switch. For local cluster configuration, the Hazelcast configuration file **hazelcast-local.xml** will be provided as the configuration value for **-cc** option.

Within this configuration, the default Hazelcast multicast discovery will be used to build the cluster.

**Run NeonBee in cluster mode on CloudFoundry**

To run NeonBee-cluster on CloudFoundry, please apply the following NeonBee options on command line:

```
java -jar build\neonbee-shadow-0.0.30-SNAPSHOT.jar -cl -cc hazelcast-cf.xml
```

The command line parameters will be passed in the **manifest.yml**:

```
  env:
     JBP_CONFIG_JAVA_MAIN: '{ arguments: "-cl -clp 50050 -cc hazelcast-cf.xml -cp 55000" }'
     JBP_CONFIG_OPEN_JDK_JRE: '{ jre: { version: 11.+}}'
```

In this case, the cluster mode will be enabled with **-cl** switch. For CloudFoundry cluster configuration, a DNS name-based cluster discovery will be enabled, which is configured in the Hazelcast configuration file **hazelcast-cf.xml**.

Furthermore, the VertX event-bus port will be set with the **-clp** option.

Furthermore, 2 additional administration steps are required to run NeonBee cluster on CloudFoundry.

1. Register the internal route to create a new DNS entry.

```
cf map-route neonbee apps.internal --hostname neonbee
```

2. Enable container-to-container communication between the NeonBee instances.

```
cf add-network-policy neonbee --destination-app neonbee --protocol tcp --port 50000-60000
```

Please make sure, that both the Hazelcast port specified in the **hazelcast-cf.xml** and the Vert.X event-bus port specified with the **-clp** option are both within the port range specified above.
