# Changelog

## 0.27.0 (2023-07-25)

### Bug Fixes

- prefer using user name / id in caching tuple ([449309a1](https://github.com/SAP/neonbee/commit/449309a15d34914c7b5afe0d792d2044446107ba))


### Features

- add a CodeQL Github Workflow ([614cc1f0](https://github.com/SAP/neonbee/commit/614cc1f0e93c9e4617bcd4c1786e270789d8f5b5))
- extend NeonBeeOptions with trust configuration ([69f37c2b](https://github.com/SAP/neonbee/commit/69f37c2b2d637953c09f2a9602b40a7775646a27))


### Build System

- **deps**: bump io.micrometer:micrometer-registry-prometheus ([a89c35d8](https://github.com/SAP/neonbee/commit/a89c35d8908ba29a2f84230133b833a75df89e6a))
- **deps**: bump org.sonarqube from 4.2.1.3168 to 4.3.0.3225 ([7c65e37e](https://github.com/SAP/neonbee/commit/7c65e37e0974e1f81da87d72d0d5fbc1e6b0234e))
- **deps**: bump com.diffplug.spotless from 6.19.0 to 6.20.0 ([558fb48f](https://github.com/SAP/neonbee/commit/558fb48f2c62fd242571b93b729d9fe1be537268))
- **deps**: bump sapmachine from 11.0.19 to 11.0.20 ([fccf4b58](https://github.com/SAP/neonbee/commit/fccf4b58387b6dd281f744284d04c5ca7f6e4096))


## 0.26.0 (2023-06-29)

### Bug Fixes

- revert SelfCleaningRegistry change, due to conceptual issues ([e22979a5](https://github.com/SAP/neonbee/commit/e22979a566d4c28646795443bf36add3f9bc92c0))


## 0.25.0 (2023-06-27)

### Build System

- **deps**: bump io.micrometer:micrometer-registry-prometheus ([17558c49](https://github.com/SAP/neonbee/commit/17558c498389dc9a6e1164d6fc02fa0be0492e69))
- **deps**: bump ch.qos.logback:logback-classic from 1.4.7 to 1.4.8 ([c9cb23a4](https://github.com/SAP/neonbee/commit/c9cb23a41668404da1193d1c0964277c5a73fa09))
- **deps**: upgrade `vertx` to 4.4.4 ([04dc73c4](https://github.com/SAP/neonbee/commit/04dc73c4cb746fcb1867957cf401f22c0b6fec32))
- **deps**: bump org.sonarqube from 4.2.0.3129 to 4.2.1.3168 ([e194283a](https://github.com/SAP/neonbee/commit/e194283a2991c3626bd454d313add04df9146fe1))


## 0.24.0 (2023-06-19)

### Features

- expose module registration for Databind codec ([7092837c](https://github.com/SAP/neonbee/commit/7092837c4422b3de7f16438b63103c9a1e88b853))


## 0.23.0 (2023-06-14)

### Bug Fixes

- disable unstable JobVerticle test ([136fd762](https://github.com/SAP/neonbee/commit/136fd7627eb4bf5b95f203aefd79f33b591c2006))
- removed Timout annotations ([997e8efa](https://github.com/SAP/neonbee/commit/997e8efad1a0dd52f233547253e31d6c25297236))


### Features

- update logback to 1.4.7 and slf4j to 2.0.7 ([94a21327](https://github.com/SAP/neonbee/commit/94a21327f762c81136c653e6c066d37559271cbb))
- return 201 (not 204) for POST requests to entity verticles and optional Location-header ([5aacd279](https://github.com/SAP/neonbee/commit/5aacd27978a5fd7cc880233293e15b16c0261c4b))
- add BufferingDataVerticle ([9f30e742](https://github.com/SAP/neonbee/commit/9f30e7422b4cc844a8eea8745001a2950032e36f))
- introduce in-memory caching / request coalescing ([5b5b0055](https://github.com/SAP/neonbee/commit/5b5b00555a64d7c5e13fe2bf2fa0cbcd72206ab1))
- add SelfCleaningRegistry and make Registry interface type safe ([5f981326](https://github.com/SAP/neonbee/commit/5f981326ad77546611a1a0e8bdd9f76788d12f17))
- disable tests influenced by available CPU time on GitHub CI ([c2377c23](https://github.com/SAP/neonbee/commit/c2377c2372a33c20410c9bb5cd790bad5d121f13))
- add jsonMaxStringSize configuration ([2d978f26](https://github.com/SAP/neonbee/commit/2d978f269c8a161963757a785f2bd22c43fe6b11))
- remove methods which became deprecated in previous versions ([9f4e2ca7](https://github.com/SAP/neonbee/commit/9f4e2ca7fc87ffe99ba7533e9a8a771c2316169f))
- bump Vert.x to 4.4.3 and deprecate joinComposite and allComposite ([46d5fd53](https://github.com/SAP/neonbee/commit/46d5fd53e91d26996c1d4fbafa0dacdc9e96e456))


### Build System

- **deps**: bump io.micrometer:micrometer-registry-prometheus ([2287460f](https://github.com/SAP/neonbee/commit/2287460f812ed017b772567c34fde879a3745a4e))
- **deps**: bump com.diffplug.spotless from 6.18.0 to 6.19.0 ([97041d80](https://github.com/SAP/neonbee/commit/97041d80a00b7474510144011fa576da9fe67e02))
- **deps**: bump org.infinispan:infinispan-component-annotations ([82de2b2c](https://github.com/SAP/neonbee/commit/82de2b2c311ca17cccdd96a61151d7aabbc76909))
- **deps**: bump com.google.guava:guava from 31.1-jre to 32.0.0-jre ([3fdd5836](https://github.com/SAP/neonbee/commit/3fdd58367adc5f1cbaa472538906259d613bb10a))
- **deps**: bump org.sonarqube from 4.0.0.2929 to 4.1.0.3113 ([53f0d83b](https://github.com/SAP/neonbee/commit/53f0d83be43afd06ccd912d779de94fa9e3c5b1e))
- **deps**: bump se.bjurr.violations.violations-gradle-plugin ([84a72bec](https://github.com/SAP/neonbee/commit/84a72becc374f0fe17dd76403f154765bc9736e9))
- **deps**: bump org.sonarqube from 4.1.0.3113 to 4.2.0.3129 ([4066a12b](https://github.com/SAP/neonbee/commit/4066a12b7b903228d32dd02dba7cda699a3c392c))
- **deps**: bump org.infinispan:infinispan-component-annotations ([7f2358a1](https://github.com/SAP/neonbee/commit/7f2358a16b8227b8bb5a13e96ed7035010993fc3))


## 0.22.0 (2023-05-12)

### Bug Fixes

- dynatrace null or empty dimension warning ([90b0e0de](https://github.com/SAP/neonbee/commit/90b0e0defb9cacddcbe3a013d033836180f3405e))


### Features

- update to Vert.x 4.4.2 ([40268a71](https://github.com/SAP/neonbee/commit/40268a71e649039278bb68731d091b44f709566e))


### Build System

- **deps**: bump com.github.spotbugs from 5.0.13 to 5.0.14 ([c6e37bcd](https://github.com/SAP/neonbee/commit/c6e37bcdb80ed7c2c0245e8420f686e2fd0beebe))
- **deps**: bump org.ow2.asm:asm from 9.4 to 9.5 ([f63cf50d](https://github.com/SAP/neonbee/commit/f63cf50d7388836834a15766d0c2abd36d5a7072))
- **deps**: bump com.sap.cds:cds4j-core from 1.36.0 to 1.37.0 ([abf616a2](https://github.com/SAP/neonbee/commit/abf616a248427b141a4530975dbe6c3baa882fe9))
- **deps**: upgrade `vertx` to 4.4.1 ([e3995df0](https://github.com/SAP/neonbee/commit/e3995df040701f8f0d04438a13f8adb4a1214c02))
- **deps**: bump com.diffplug.spotless from 6.17.0 to 6.18.0 ([e91141fc](https://github.com/SAP/neonbee/commit/e91141fcf854f147cfa0908570b1e3bb86724a51))
- **deps**: bump org.ajoberstar.grgit from 5.0.0 to 5.2.0 ([4ca7dbf2](https://github.com/SAP/neonbee/commit/4ca7dbf26a633ae87a81901d9b79889df2dccbea))
- **deps**: bump com.sap.cds:cds4j-core from 1.37.0 to 1.37.1 ([861409e0](https://github.com/SAP/neonbee/commit/861409e02cbe064020c08c740d7fa770a9d2d190))
- **deps**: bump net.ltgt.errorprone from 3.0.1 to 3.1.0 ([2a8c7e1b](https://github.com/SAP/neonbee/commit/2a8c7e1b813379c7af938eafd44aeab4e7dc4a2e))
- **deps**: bump io.micrometer:micrometer-registry-prometheus ([03af4791](https://github.com/SAP/neonbee/commit/03af4791c28e65887eccc59da91af27c0b7e5b42))
- **deps**: bump sapmachine from 11.0.18 to 11.0.19 ([5a35d2eb](https://github.com/SAP/neonbee/commit/5a35d2eb9f17af7ec420a815fbd8d70985261c7b))
- **deps**: bump com.sap.cds:cds4j-core from 1.37.1 to 1.38.1 ([d8b237ac](https://github.com/SAP/neonbee/commit/d8b237ac22b0af9f93d705d3a44af880b782d6ec))


## 0.21.0 (2023-03-27)

### Features

- allow to pass count value via response hints ([68a52c55](https://github.com/SAP/neonbee/commit/68a52c553ed01ac5de3687ce713ef53954d0f161))
- make HealthCheck collection configurable ([22eb10ee](https://github.com/SAP/neonbee/commit/22eb10ee2d332be41be37c039583f6981befcb12))
- add CorsHandlerFactory ([80700925](https://github.com/SAP/neonbee/commit/8070092549404f1d80116243e294802908f9cf5c))


### Chores

- improve error message when no verticle is registered for an entity type ([2443e4e7](https://github.com/SAP/neonbee/commit/2443e4e72283f43f9310ec62aa5b5a0565189af0))


### Documentation

- add documentation for gradle tasks ([61a974d8](https://github.com/SAP/neonbee/commit/61a974d8121eafa783c7724d24ab6eeaa6f62aae))


### Build System

- **deps**: bump com.diffplug.spotless from 6.16.0 to 6.17.0 ([dc3c5787](https://github.com/SAP/neonbee/commit/dc3c578773a66da570096c6deb6da565a7e931bc))
- **deps**: bump se.bjurr.violations.violations-gradle-plugin ([12f73b8a](https://github.com/SAP/neonbee/commit/12f73b8a1a89d0dedc423d5b54aa6e3e0cd78b9e))
- **deps**: bump io.micrometer:micrometer-registry-prometheus ([e8525823](https://github.com/SAP/neonbee/commit/e85258230f1ccd592c4a275d747290d17d1d85d2))


## 0.20.0 (2023-03-07)

### Bug Fixes

- assertion for test verifyJobExecuted in JobVerticleTest ([3e41a91a](https://github.com/SAP/neonbee/commit/3e41a91a4143b80279b8900b39b0b536350ecb47))


### Chores

- remove unused docker plugin ([f8e7369a](https://github.com/SAP/neonbee/commit/f8e7369a3dc2a85da58fc05926c6623d2e21d711))
- remove webserver name from X-Instance-Info header ([14f29a18](https://github.com/SAP/neonbee/commit/14f29a189a12bebb65803f6821e885077981bcfb)), closes [#268](https://github.com/SAP/neonbee/issues/268)


### Tests

- **odata**: unit tests for OData Batch Request handling ([99d20852](https://github.com/SAP/neonbee/commit/99d208520e736fe50fb46d0305b0b95ba5be7b06))
- **odata**: implement ODataMetadataRequest and deprecate ODataRequest#setMetadata ([c1cdaef5](https://github.com/SAP/neonbee/commit/c1cdaef593d81a9cd7ea921165a349808ab328bd))


### Documentation

- **health**: fix register checks via SPI ([d2f220e3](https://github.com/SAP/neonbee/commit/d2f220e307f12367409e83e23e6ab9fd4c57fe28))


### Build System

- **deps**: bump jackson-databind from 2.13.4.2 to 2.14.2 ([c34dbd34](https://github.com/SAP/neonbee/commit/c34dbd34bcafdd9a9994deaf79e27c8bdc20b0f4))
- **deps**: bump net.ltgt.errorprone from 2.0.2 to 3.0.1 ([dda0c554](https://github.com/SAP/neonbee/commit/dda0c5548b059ce71ec551c1aebdaa2e1937846b))
- **deps**: bump org.sonarqube from 3.3 to 3.5.0.2730 ([d8081a8f](https://github.com/SAP/neonbee/commit/d8081a8f9cf9bd55953e02f266c702d4ea522cac))
- **deps**: bump micrometer-registry-prometheus from 1.9.2 to 1.10.3 ([573e91e6](https://github.com/SAP/neonbee/commit/573e91e634ebb6be6b7f368febd44a5c2be762b9))
- **deps**: bump org.ajoberstar.grgit from 4.1.1 to 5.0.0 ([11f097cb](https://github.com/SAP/neonbee/commit/11f097cb25f3b4cb1441a51320bea190e07a0aa5))
- **deps**: bump com.diffplug.spotless from 6.0.5 to 6.14.1 ([5213a54b](https://github.com/SAP/neonbee/commit/5213a54bf7f4497844ca2aba998fb614b30c1f0f))
- **deps**: bump org.ow2.asm:asm from 9.3 to 9.4 ([48800a6b](https://github.com/SAP/neonbee/commit/48800a6bfd7c8cb700f697f7dff72b18ea47402f))
- **deps**: bump com.github.spotbugs:spotbugs-annotations ([8a000604](https://github.com/SAP/neonbee/commit/8a000604b8e8c078e524e9fcb3065b4f60bbf693))
- **deps**: bump com.github.johnrengelman.shadow from 7.1.0 to 7.1.2 ([73a3fd78](https://github.com/SAP/neonbee/commit/73a3fd782deb78ba677b5e045848d41841283794))
- **deps**: bump com.sap.cds:cds4j-core from 1.30.0 to 1.35.1 ([a5404865](https://github.com/SAP/neonbee/commit/a5404865703fb3af99d4baefc71c0d6683a69a85))
- **deps**: bump org.infinispan:infinispan-component-annotations ([80443bee](https://github.com/SAP/neonbee/commit/80443bee2fb00fc5799a966e2db7e51aa570ecea))
- **deps**: bump sapmachine from 11.0.15.0.1 to 11.0.18 ([8fbacd88](https://github.com/SAP/neonbee/commit/8fbacd885191e0a9bc452bd4691a1cc53e46a6a9))
- **deps**: bump com.github.spotbugs from 5.0.3 to 5.0.13 ([4e4e5ed2](https://github.com/SAP/neonbee/commit/4e4e5ed2d973f0778855f564e08c886400bb5dba))
- **deps**: bump se.bjurr.violations.violations-gradle-plugin ([b7eadcb8](https://github.com/SAP/neonbee/commit/b7eadcb8b58625c397ba0f8850031bdf637958f9))
- **deps**: bump Vert.x to 4.3.8 ([d781e505](https://github.com/SAP/neonbee/commit/d781e5056532d4fda85e60e3808d91eb7f6023f6))
- **deps**: bump a few dependencies ([52cb1bcb](https://github.com/SAP/neonbee/commit/52cb1bcb82c99ce1963058d54d6ec42b9d8aef3d))
- **deps**: bump com.diffplug.spotless from 6.14.1 to 6.15.0 ([083f5d54](https://github.com/SAP/neonbee/commit/083f5d54940a824fdc103b38ec00fcc55f152228))
- **deps**: bump checkstyle from 9.2 to 10.7.0 ([ad9d7974](https://github.com/SAP/neonbee/commit/ad9d7974e26bbfb166a2fa0348257906fadecb1f))
- **deps**: bump org.sonarqube from 3.5.0.2730 to 4.0.0.2929 ([5bc945da](https://github.com/SAP/neonbee/commit/5bc945da6ed46da78fb177a2e086b5ca856d9f25))
- **deps**: bump io.micrometer:micrometer-registry-prometheus ([ccc95583](https://github.com/SAP/neonbee/commit/ccc9558363809dbc98bf297d91371d1471933e0d))
- **deps**: bump com.sap.cds:cds4j-core from 1.35.1 to 1.36.0 ([53d679e7](https://github.com/SAP/neonbee/commit/53d679e77f46521e47819fb2a445b6c7d4e5f85c))
- **deps**: bump se.bjurr.violations.violations-gradle-plugin ([afae401a](https://github.com/SAP/neonbee/commit/afae401a4cb4352e458d4ca06715b7d3f13e5b8d))
- **deps**: bump com.diffplug.spotless from 6.15.0 to 6.16.0 ([a27696c2](https://github.com/SAP/neonbee/commit/a27696c2f884ad2dd07b0b9d30e315ea7d64be93))
- **deps**: bump Vert.x to 4.4.0 and switch to vertx-openapi ([05c8bdf4](https://github.com/SAP/neonbee/commit/05c8bdf4f17886fe9cb12725ea7de5196e3ae97e))


### Continuous Integration

- **dependabot**: configure dependencies to ignore ([4e49086d](https://github.com/SAP/neonbee/commit/4e49086dffe93444256f5551ba3158c6ac60d12d))
- add dependabot configuration ([050454e9](https://github.com/SAP/neonbee/commit/050454e9910ba77e49d0175d1365f875b9535b80))


## 0.19.0 (2023-01-19)

### Bug Fixes

- case-insensitive headers ([a1a1e9c8](https://github.com/SAP/neonbee/commit/a1a1e9c805d70218148beda97f0cec43d420f509))
- getName for dummy EntityVerticle ([10537b00](https://github.com/SAP/neonbee/commit/10537b00878e636bd5e09ed9cc8bd90b5ccde742))
- test execution order leads to failed test ([d86ccd5a](https://github.com/SAP/neonbee/commit/d86ccd5ae95d790818342c5a7ef2bca87ebd4eec))
- address already in use BindException ([79af576a](https://github.com/SAP/neonbee/commit/79af576a7c0bf980fd09744b99f1c39b80101190))
- test fails when tests run slowly ([73f09810](https://github.com/SAP/neonbee/commit/73f09810784e0a520f465b9ff6314687554afeef))
- set await-initial-transfer to false ([21aadd4e](https://github.com/SAP/neonbee/commit/21aadd4e146da5bbc94961bac52b7be7933d5011))
- isolate and run tests in EventLoopHealthCheckTest sequentially ([1e942226](https://github.com/SAP/neonbee/commit/1e94222679f96b2b40d5007673e4001e2d027295))
- neonBee is null NPE ([1488c06d](https://github.com/SAP/neonbee/commit/1488c06db44831f4c78419cd232c32fa7dc26f8c))
- make verifyJobExecuted test more resilient ([42f50320](https://github.com/SAP/neonbee/commit/42f50320e9943c912118873c2333c75279b686af))


### Features

- **health**: add status endpoint & support filtering ([f1154084](https://github.com/SAP/neonbee/commit/f1154084cfa0015568a5e3aaafdbe6d835376400))
- deploy HealthCheckVerticle only if HealthChecks are enabled ([6b80cb20](https://github.com/SAP/neonbee/commit/6b80cb20d42ae6070a5e4aa927a0b02aaf9774bb))
- remove MetricsVerticle ([08ee561a](https://github.com/SAP/neonbee/commit/08ee561a07b1ea48d0a4801f68482bb237571246))
- unregister entities from shared map ([62e437bb](https://github.com/SAP/neonbee/commit/62e437bba03f5cd37943eb232bb2bd123c75e23d))
- add NODE_ADDED, NODE_LEFT hooks ([520256fe](https://github.com/SAP/neonbee/commit/520256fe5bd78f743a47813b4bad09229d95de78))


### Code Refactoring

- extract registry logic ([288d3995](https://github.com/SAP/neonbee/commit/288d399517e33415f3dfcac85a4c9c6955d79fa4))


### Chores

- change spotless code formatter ([f4aa0292](https://github.com/SAP/neonbee/commit/f4aa0292c9847f54d97939ae2cb38e8322a5c2d6))


### Documentation

- add user documentation ([5b7186b3](https://github.com/SAP/neonbee/commit/5b7186b3735fb7e831a8c1fe8614afc9d1b77a2e))
- replace getting started section with link to neonbee-examples and add TOC ([05d2bfba](https://github.com/SAP/neonbee/commit/05d2bfbafeea873a1b5b88c7bdb84788967f3424))


### Build System

- **deps**: upgrade vert.x to 4.3.7 ([02d22528](https://github.com/SAP/neonbee/commit/02d225284f58f3f96d5fca745f8e89e4787f467a))


## 0.18.0 (2022-11-15)

### Bug Fixes

- generated files have changed ([80ef04ec](https://github.com/SAP/neonbee/commit/80ef04ecb21c15db0a56d63409559780815ff629))
- odata query encoding in EntityVerticle ([ae52053a](https://github.com/SAP/neonbee/commit/ae52053a831c4e8c86931529a5f17445c229bda3))
- dataquery cannot correctly process query strings ([4cccf60b](https://github.com/SAP/neonbee/commit/4cccf60bd738557c898f24797ebb5405b1334b39))


### Features

- include a version file inside jar files ([ed244da5](https://github.com/SAP/neonbee/commit/ed244da594a0af5b31218ad6f18de68a7ab0daaa))
- load Hazelcast configuration from file ([f721875a](https://github.com/SAP/neonbee/commit/f721875a5edb9454bc2df1dc420d9ca676a6d82a))


### Build System

- **deps**: upgrade `com.fasterxml.jackson` to 2.13.4.2 ([4f0bfaea](https://github.com/SAP/neonbee/commit/4f0bfaeabeb58f5efc8ba2c0829642b7dd6a99fa))
- **docker**: pin gradle version to 7.2 ([cb1be0ed](https://github.com/SAP/neonbee/commit/cb1be0edff012d809bc39e0812a5c5e7751c5bb0))


## 0.17.0 (2022-10-24)

### Bug Fixes

- multi-release issue w/ Infinispan ([cd6ccd99](https://github.com/SAP/neonbee/commit/cd6ccd993ae1310937f0c85162b191325f825b1f))
- close Infinispan cache container after NeonBee shutdown ([57616a86](https://github.com/SAP/neonbee/commit/57616a86e072af9e04d72d4423ec91cf3223d079))
- #199, add tracing to EntityModelLoader and improve logging ([d6130468](https://github.com/SAP/neonbee/commit/d6130468bf22e32c42c6c467568c786607f59146))
- odata query encoding ([869d6875](https://github.com/SAP/neonbee/commit/869d68750e3af4bffe89fa66da3ffde9d53e434e))


### Features

- introduce a NeonBeeExtension.TestBase ([9608028c](https://github.com/SAP/neonbee/commit/9608028c02d34ed064992295f8ed23f222b93482))


### Continuous Integration

- fix tests ([a058129c](https://github.com/SAP/neonbee/commit/a058129c62eaedc97e3858d84625c27e518d911f))


## 0.16.1 (2022-10-12)

### Bug Fixes

- pass SystemClassLoader to cluster manager configuration ([ab258a7b](https://github.com/SAP/neonbee/commit/ab258a7bbf04da6bd85a5cf2b5dc046a854c8226))


## 0.16.0 (2022-10-11)

### Bug Fixes

- **health**: make health check collection more resilient ([5421607b](https://github.com/SAP/neonbee/commit/5421607b2580d6979603d1d4f53f60e71965a9c5))
- removed docker image tag with sha ([44cd0379](https://github.com/SAP/neonbee/commit/44cd03799231f03e99fe2779e9869db0b6b8eab9))
- register NeonBee mock ([5d3999b2](https://github.com/SAP/neonbee/commit/5d3999b2cd152521e39703d1ffd0b62f164b2af1))
- make SystemClassLoader available to Infinispan ([d8b9b1f2](https://github.com/SAP/neonbee/commit/d8b9b1f2fde33df7127ba03b31124db05f8ddc0f))


### Features

- add option to set metrics registry name ([194cbb2b](https://github.com/SAP/neonbee/commit/194cbb2bdc4148ff6c76636f786bb42d53448a26))


### Code Refactoring

- registerNeonBeeMock should not influence mock ([d392975f](https://github.com/SAP/neonbee/commit/d392975ff867717e59486cfa0da71a002acd2a4b))


## 0.15.0 (2022-10-05)

### Bug Fixes

- failing metrics test on Github ([56eedff2](https://github.com/SAP/neonbee/commit/56eedff2346e020346108e681e25bb97bc32f323))


### Features

- **health**: add event-loop health check ([1a2fa4be](https://github.com/SAP/neonbee/commit/1a2fa4be43a9d71aeb6d7841d14c71052bfd7751))
- use Vert.x FakeClusterManager in NeonBeeExtension ([e522452f](https://github.com/SAP/neonbee/commit/e522452fd9dc647b3d3d36ac25f8968258f08bbb))
- make test cluster manager configurable ([32e5d120](https://github.com/SAP/neonbee/commit/32e5d120aabf42801494f17f2d2d61252d7d9c83))
- add Infinispan ClusterManager ([26bb382e](https://github.com/SAP/neonbee/commit/26bb382e86532383e2ac9ceca19776eae93d09cf))


### Code Refactoring

- remove redundant code ([ce6f3490](https://github.com/SAP/neonbee/commit/ce6f3490d624810e7571273cd8b6af99f2b473fc))


### Documentation

- add docker login step to docs ([bf2707df](https://github.com/SAP/neonbee/commit/bf2707df21c071479e47f50448fab7e538189c26))


### Build System

- **deps**: upgrade `vertx` to 4.3.4 ([fedab717](https://github.com/SAP/neonbee/commit/fedab7173486469348bb5a26191c5d26cad20802))


### Continuous Integration

- **github**: update issue templates ([65f5db6e](https://github.com/SAP/neonbee/commit/65f5db6edefefe3b91b0525a2ec3b75799baf607))
- add workflow step to publish docker image to ghcr ([7152dba0](https://github.com/SAP/neonbee/commit/7152dba0bfb8cd93b8aea5aa6d74e1e3c0f87fad))


## 0.14.0 (2022-08-30)

### Bug Fixes

- same NeonBee nodeId for multiple instances ([3b48202c](https://github.com/SAP/neonbee/commit/3b48202cb0e4ff7d3433562589f28c53c322acb0))
- pass vertx instance to NeonBee for the metrics config ([05559de7](https://github.com/SAP/neonbee/commit/05559de7245b1828487465eee93b3f8641e84bd5))


### Features

- support response hint ([88cb66f5](https://github.com/SAP/neonbee/commit/88cb66f59449542a16bd17a8afed4b4b2cb23305))
- add failureDetail to DataException ([c7fa2bdb](https://github.com/SAP/neonbee/commit/c7fa2bdb0b80fea421d95175a5af88bcde0c4cf7))


## 0.13.0 (2022-08-10)

### Bug Fixes

- speed up the test execution ([c59f6d86](https://github.com/SAP/neonbee/commit/c59f6d8668a1c94df78eb88080e8ad655b291b94))
- disable unstable test on github ([1e29e130](https://github.com/SAP/neonbee/commit/1e29e130dde52f14f55e9a3498e843baf9ab2b34))


### Features

- make ServerVerticle handler configurable ([deff579c](https://github.com/SAP/neonbee/commit/deff579ccf34f5e55512a2fc4c4141ab813e9bfe))


### Documentation

- **health**: add documentation for the health feature ([99be11b7](https://github.com/SAP/neonbee/commit/99be11b7657f6589aa23552294055de5fd221aa2))


### Build System

- **deps**: upgrade `vertx` to 4.3.3 ([e0f366ca](https://github.com/SAP/neonbee/commit/e0f366ca1ce86e0e516f853b2997f6fc0e1b5489))


## 0.12.1 (2022-07-14)

### Chores

- log the NeonBee Id ([40203a3c](https://github.com/SAP/neonbee/commit/40203a3c0cafd5daf835a57c7440dd2242915672))
- remove physical memory from health check data ([367eef5b](https://github.com/SAP/neonbee/commit/367eef5b690666dc91b08939d940073b172a6599))


### Build System

- **deps**: upgrade `vertx` to 4.3.2 ([edae6e0b](https://github.com/SAP/neonbee/commit/edae6e0bf80460a2d186093dec49c5924b5d1fcc))
- **deps**: upgrade org.ow2.asm to version 9.3 ([88d89b1e](https://github.com/SAP/neonbee/commit/88d89b1e45bebec5d60bf754f8e98920d0f696ec))
- **deps**: upgrade dependencies ([479456f9](https://github.com/SAP/neonbee/commit/479456f99ae643f4dcc0f68c3186911d5fd3ec42))


## 0.12.0 (2022-07-13)

### Bug Fixes

- **health**: succeed if health check config not found ([ae371b28](https://github.com/SAP/neonbee/commit/ae371b285e1b1e41c454d4517fe356b50f8a4c4c))
- do not mock NeonBee logger in NeonBeeMockHelper ([8238ec8d](https://github.com/SAP/neonbee/commit/8238ec8d1aebca301e0b17333c83c17dd5f31100)), closes [#130](https://github.com/SAP/neonbee/issues/130)
- avoid printing warnings when using NeonBeeMockHelper ([03d9e535](https://github.com/SAP/neonbee/commit/03d9e5350fd3fc7dbd5dcf12dacec9f1220e0009))


### Features

- **health**: add health check verticle ([7fbd10e4](https://github.com/SAP/neonbee/commit/7fbd10e4d30878f9c7534bb0c53caf9cffccfe7e))
- **health**: provide a `/health` endpoint ([151cf6e2](https://github.com/SAP/neonbee/commit/151cf6e211aed7d8de50fe76eb90ab5c3ba3145c))
- add metrics to DataVerticle ([1afca66c](https://github.com/SAP/neonbee/commit/1afca66cda6a0eff520035a6ddf177bf4a4ce5a7))
- make health checks addable via SPI ([71e754e1](https://github.com/SAP/neonbee/commit/71e754e1278fd48da21820cf0eb4cef46114d88e))


## 0.11.1 (2022-06-06)

### Bug Fixes

- broken ChainAuthHandler ([a9d770ca](https://github.com/SAP/neonbee/commit/a9d770cad9b73a2260ff16a6f34577b0d5de1b27))


## 0.11.0 (2022-06-03)

### BREAKING CHANGES

- remove LauncherPreProcessor ([b4a014d8](https://github.com/SAP/neonbee/commit/b4a014d803a956192cdb2d5257f9d5f07f91ca3c))


### Bug Fixes

- assertDataFailure should return a succeeded future ([0f73202b](https://github.com/SAP/neonbee/commit/0f73202b8823276e5c6983badb0d01c27cf85356))
- enable tests to run in intellij idea with coverage ([f263eac5](https://github.com/SAP/neonbee/commit/f263eac5c35b15549daf4a410641a844b7edeb08))
- loading the NeonBee configuration ([649b54c9](https://github.com/SAP/neonbee/commit/649b54c9a181196d0812bca9d922fdd4a030c12c))


### Features

- add temporary vertx instance ([f622f17d](https://github.com/SAP/neonbee/commit/f622f17d31bf2225bafdc833c10deb591006ebd8))
- load endpoints asynchronously ([89036cca](https://github.com/SAP/neonbee/commit/89036cca64103677197b16759641ddc3c56bf140))
- add abstract OpenAPI endpoint ([4032df4b](https://github.com/SAP/neonbee/commit/4032df4bd2701afbfc2f41e7c237a2b70f009f80))
- add health checks ([e4e93552](https://github.com/SAP/neonbee/commit/e4e93552479589d0b321855efd2d7dc6a3812553)), closes [#117](https://github.com/SAP/neonbee/issues/117)
- ensure compatibility with Java 17 ([6adeaad7](https://github.com/SAP/neonbee/commit/6adeaad73022757dd8517b312fda2484b986f603))


### Code Refactoring

- created methods to reuse code ([de48d135](https://github.com/SAP/neonbee/commit/de48d135bda5385928b8c18b42c1c4acad9708a0))


### Build System

- **deps**: upgrade `vertx` to 4.2.5 ([0723ac2b](https://github.com/SAP/neonbee/commit/0723ac2b88755f6f1f86945ea78cebf8b479fcc2))
- **deps**: upgrade `vertx` to 4.2.6 ([624de7de](https://github.com/SAP/neonbee/commit/624de7dee090f563280bc09a1b4659054e8c6466))
- **deps**: upgrade `vertx` to 4.2.7 ([368451f1](https://github.com/SAP/neonbee/commit/368451f10540bcd72f5b8f4382796e68c8cbf030))


## 0.10.0 (2022-03-07)

### Bug Fixes

- allow no active profile, make ALL the default ([e8461712](https://github.com/SAP/neonbee/commit/e8461712fb5de4baefac21a49eadbbff096ce681))
- prefer loading Vert.x/NeonBee from system class loader ([9d01198b](https://github.com/SAP/neonbee/commit/9d01198bb66ee32c1afedcdba4b52cdfeb145e28))


### Features

- add NeonBee vs. Vert.x to README.md ([bf00a23a](https://github.com/SAP/neonbee/commit/bf00a23a763fdf2d51a401660e97369325253edf))
- make NeonBee boot logging a little more verbose ([ca5d8be2](https://github.com/SAP/neonbee/commit/ca5d8be2e6c4d93e534bb3dc38ee873413a5832c))


### Code Refactoring

- change to futurized interfaces ([e2659537](https://github.com/SAP/neonbee/commit/e2659537bd18a79c5405c7d1a7a8889ae7403a3e))
- improve wildcard handling in SelfFirstClassLoader ([e98615bb](https://github.com/SAP/neonbee/commit/e98615bbb62797ba28cd974576c7af8bc4a90d44))


### Continuous Integration

- automate dependency upgrade (of vertx) ([1ab6d1a8](https://github.com/SAP/neonbee/commit/1ab6d1a84817c115240c6dc1385afe0481ddf854))


## 0.9.1 (2022-02-17)

### Bug Fixes

- NPE ImmutableJsonObject/Array for null values ([0a791364](https://github.com/SAP/neonbee/commit/0a791364835c96364571c509a505bd7058bcf6b9))


## 0.9.0 (2022-02-15)

### Bug Fixes

- null values queried with contains ([0b06e92b](https://github.com/SAP/neonbee/commit/0b06e92bd99f60e8e2f73bc2f38cff926ae2b61d))


### Features

- made `EntityModelManager` a non-static class ([d5e57d9b](https://github.com/SAP/neonbee/commit/d5e57d9b61edc12a0fd0a996316d4b4a906ce940))
- improve `Launcher`, `Deployable`, `EntityModelManager` and more ([b578bace](https://github.com/SAP/neonbee/commit/b578bace738e82646da062b2723f61909da33b62))


## 0.8.0 (2022-01-27)

### Features

- add custom micrometer registries via NeonBeeConfig ([6a11d795](https://github.com/SAP/neonbee/commit/6a11d795ae624b16e2a2bb09d2a30f1b3d17434a))


### Documentation

- fix typo in github release guide ([974a7cf8](https://github.com/SAP/neonbee/commit/974a7cf8a022351bdffa8b3902a00e2ed0a85e31))


### Build System

- **deps**: upgrade `vertx` to 4.2.3 ([ea793306](https://github.com/SAP/neonbee/commit/ea7933067a0c81795932122062c3b72f4ae2bd4c))
- **deps**: upgrade `micrometer-registry-prometheus` to 1.8.1 ([c1b4168c](https://github.com/SAP/neonbee/commit/c1b4168c320e5db780ee5a7f521eddc8cbd63c90))
- **deps**: upgrade `cds4j-core` to 1.25.0 ([c1408cbd](https://github.com/SAP/neonbee/commit/c1408cbd1fcd3b72f75fcfcb4d40aae67e51a4b8))
- **deps**: upgrade `mockito` to 4.2.0 ([b78c3448](https://github.com/SAP/neonbee/commit/b78c3448722553fa19e2c080d27a940b17663d76))
- **deps**: upgrade `junit` to 5.8.2 ([ac8d9c36](https://github.com/SAP/neonbee/commit/ac8d9c366f1f7823f6bd27b34520d47ad6976c38))
- **deps**: upgrade `guava` to 31.0.1-jre ([9911f356](https://github.com/SAP/neonbee/commit/9911f35654f68e1a689594e35a1b9046d8ad529b))
- **deps**: upgrade gradle plugin dependencies ([7e7bdcec](https://github.com/SAP/neonbee/commit/7e7bdcec890f600ec1a96f920eac6fbfe6e30410))
- **deps**: upgrade `logback-classic` to 1.2.9 ([67fff831](https://github.com/SAP/neonbee/commit/67fff8318103dad71a00bdb0e597bcbdc772bea1))
- **deps**: upgrade `junit-platform` to 1.8.2 ([18feb6d7](https://github.com/SAP/neonbee/commit/18feb6d7a43794d482473d63ca2df60ff98b84c3))
- **deps**: upgrade `slf4j-api` to 1.7.32 ([0953e401](https://github.com/SAP/neonbee/commit/0953e4018e7d9d0fca60f83197c5180a8defc4bf))


### Continuous Integration

- update commitlint github action to `@v4`, fixes #91 ([#91](https://github.com/SAP/neonbee/issues/91)) ([7803220e](https://github.com/SAP/neonbee/commit/7803220ea87737ce13d87ad2cbe6ebb683586745))


## 0.7.0 (2021-12-15)

### Features

- move MetricOptions to NeonBeeOptions ([6286fd1a](https://github.com/SAP/neonbee/commit/6286fd1a8ba2da4f428f3a0146f5451c7a05aad3))
- make usage of createQualifiedName more resilient ([56a16013](https://github.com/SAP/neonbee/commit/56a160131e71bb87bda8ad2e948a0e4e57706767))


### Code Refactoring

- remove dependency to CompilingClassLoader of Vert.x ([48e17700](https://github.com/SAP/neonbee/commit/48e17700a222aaedaffb6e4327e71bbb76c4bfad))


### Build System

- **deps**: bump Vert.x from 4.1.0 to 4.2.1 ([c498f707](https://github.com/SAP/neonbee/commit/c498f707f4d53d05b5c5daafcbe300d52f92d6a3))
- **deps**: bump Vert.x from 4.2.1 to 4.2.2 ([db177905](https://github.com/SAP/neonbee/commit/db17790568652b9b9dafb85dcefe2e6006b4bc8c))


## 0.6.2 (2021-11-23)

### Bug Fixes

- solve static code issues ([e3be538d](https://github.com/SAP/neonbee/commit/e3be538d8e1a8f1070e475870f1c157e738337ad))


### Features

- offer a new ErrorHandler which can be initialized asynchronously ([597f2575](https://github.com/SAP/neonbee/commit/597f257532583d66672ce8b69b1937eca4fb319e))


## 0.6.1 (2021-10-05)

### Bug Fixes

- make `ImmutableJsonArray/Object.equals` behave better ([7b4338cc](https://github.com/SAP/neonbee/commit/7b4338ccd5f4a09be02f54b18372bf4fc574e8e7))


## 0.6.0 (2021-10-04)

### Bug Fixes

- rephrase error handler properties in config ([e9495778](https://github.com/SAP/neonbee/commit/e94957783a8566b123a806670def011577fb00c4))
- switch to Vert.x-owned Hazelcast instance creation ([67b6b977](https://github.com/SAP/neonbee/commit/67b6b9774a8b9bda2325938ffa56256cde28c36a))


### Features

- add JobSchedule validation ([17199b20](https://github.com/SAP/neonbee/commit/17199b20ad7e2331522029588d17c0f93560253c))
- add session id to DataContext ([8fce1828](https://github.com/SAP/neonbee/commit/8fce18285048a1c560af60aa053f766353ec775e))
- add a `ImmutableBuffer` class ([d6145454](https://github.com/SAP/neonbee/commit/d614545492090cf89d5a0a048810eccbc0beca4b))
- add NO_WEB profile ([a2c3a5d5](https://github.com/SAP/neonbee/commit/a2c3a5d5d7ab3c76269ba59544c89b4861a588ef))
- add a `CompositeBuffer` class ([946049be](https://github.com/SAP/neonbee/commit/946049be3ad91302352a6fab44448124535141b3))
- add/removeActiveProfile to/from NeonBeeOptions.Mutable ([69771636](https://github.com/SAP/neonbee/commit/69771636d46a42dc88afae66b1c9abc5efd307d3))
- add doNotWatchFiles option to not watch for file changes ([2e4c7934](https://github.com/SAP/neonbee/commit/2e4c79347768618c9579f3adcf38c12b90733ea3))


### Code Refactoring

- do not fail start of `WatchVerticle` if doNotWatchFiles ([f551fb1a](https://github.com/SAP/neonbee/commit/f551fb1a9505329b3fae840e2e3ae890d8fb6506))


### Chores

- don't start gradle as a daemon ([9f8fa949](https://github.com/SAP/neonbee/commit/9f8fa949bf25f657205964efeb545f83f23e2618))


### Documentation

- add how-to debug tests ([3646016f](https://github.com/SAP/neonbee/commit/3646016fd80ad40b001fd838c13ccc19f303ccc2))


### Build System

- **deps**: bump com.sap.cds:cds4j-core from 1.19.0 to 1.22.1 ([7f043171](https://github.com/SAP/neonbee/commit/7f0431718441c4fbba3c42cdc5699b9a7ff602b7))
- upgrade to Gradle 7.2 and bump all test tooling ([c035ebf4](https://github.com/SAP/neonbee/commit/c035ebf4fb1f289f58e0807cee1be231d1298b82))


### Continuous Integration

- **gh-actions**: add maven central section in release body ([ea938f7d](https://github.com/SAP/neonbee/commit/ea938f7d447b7c1fa89765bb544838910d9d3e48))
- add NO_WEB profile to all tests not using the server verticle ([dab61cdf](https://github.com/SAP/neonbee/commit/dab61cdf110f1ae4aa05727d5d6cd59f7d178f46))
- add a RunningTest- and a AliveThreadReporter ([d97bdce0](https://github.com/SAP/neonbee/commit/d97bdce0c458889a9d58322fe4028ed8330c3e26))
- add NeonBeeTestExecutionListener to check for stale threads ([cf17fa40](https://github.com/SAP/neonbee/commit/cf17fa404f96ddecff3c255547d290cfc559ec0d))
- fix NeonBeeTestBaseTest on GitHub ([cbd53b5c](https://github.com/SAP/neonbee/commit/cbd53b5ce0355a9c605edca27f0b28091005db76))
- add better getFreePort implementation ([bb443d52](https://github.com/SAP/neonbee/commit/bb443d52a8b6b2b0e59b44c7dd406ab7c2eb7e7a))
- add cluster termination for tests ([88df6657](https://github.com/SAP/neonbee/commit/88df6657a918c29bc01c1ae689d5c38c5abc149d))
- add some additional logging ([77ab4b1c](https://github.com/SAP/neonbee/commit/77ab4b1cd3d94841018b331642fd7c9963c9257d))
- add and adapt test timeouts ([6c8fea84](https://github.com/SAP/neonbee/commit/6c8fea84a0cc729b3c63ccf725d7f9515a870c93))
- add do not watch files test for WatchVerticle ([48f6d572](https://github.com/SAP/neonbee/commit/48f6d5729ab98881281a88b4a98b987857ffbeef))
- switch to setup-java@v2 cache for Gradle ([3b6bb5df](https://github.com/SAP/neonbee/commit/3b6bb5df61ee7e31f9cbbc1e6d66e261d7c95e60))
- isolate tests modifying global resources ([336db173](https://github.com/SAP/neonbee/commit/336db173b2c188cc45e8ac5789a585a25be18d2f))
- always close Vert.x if tests create own instances ([28f03861](https://github.com/SAP/neonbee/commit/28f03861d59c5af3539c0f9500d2b1dddd22ee42))
- add vertx-parameters for tests ([20cb77c1](https://github.com/SAP/neonbee/commit/20cb77c15ed59c385acc684beec88e382bca42c9))
- refactor NeonBeeTestExecutionListener into a StaleVertx/ThreadChecker ([1b386199](https://github.com/SAP/neonbee/commit/1b386199f3af1c65692a60b23b2de375ba82589d))
- use better default options for NeonBee in tests ([14128512](https://github.com/SAP/neonbee/commit/14128512404f16284a0abe228253174a169e38f2))
- fix typos, provide explanation why `@BeforeEach` ignores testContext ([007b8ce6](https://github.com/SAP/neonbee/commit/007b8ce664a11bbb6c6ce776303a4a6b33057d2d))
- fix publish ([b5f27df2](https://github.com/SAP/neonbee/commit/b5f27df27ba8bc860ef76032cd363ffc6f1b5dbf))
- add changelog again ([a6b256ad](https://github.com/SAP/neonbee/commit/a6b256ad298944d93e6578e6570594b9d1677307))


## 0.5.1 (2021-07-27)

### Bug Fixes

- decode query parameters in odata requests ([38e36e2f](https://github.com/SAP/neonbee/commit/38e36e2fe6ddc1b1463d3fc8857a5456e6061862))
- prepend uri paths of entity requests with a slash ([ae31b863](https://github.com/SAP/neonbee/commit/ae31b8638dc66b9c4d272b23eeb463760267fe1b))


### Features

- allow multiple MANIFST.MF files when parsing NeonBee-Module ([dec4a91f](https://github.com/SAP/neonbee/commit/dec4a91fcb0ab646486dbeb9a9e332a967eee3f6))


### Code Refactoring

- **processor**: extract common methods into helper ([608ec395](https://github.com/SAP/neonbee/commit/608ec395c195f0197f8210e21a1d272ebbd5ef55))
- made the ClassPathScanner non-blocking ([dc190210](https://github.com/SAP/neonbee/commit/dc1902100297100511ad59a584a92a4ee60c35a0))
- move error handler configuration to ServerConfig class ([71e328ff](https://github.com/SAP/neonbee/commit/71e328ff32dfa400ff52d4a29a0e864135f18a64))


### Documentation

- **readme**: add example repository ([2a861f7f](https://github.com/SAP/neonbee/commit/2a861f7f2c2cfc9721b081a04083b6a4ac78ce2b))


## 0.5.0 (2021-06-07)

### Bug Fixes

- use gradle task for setting new version ([9295c74a](https://github.com/SAP/neonbee/commit/9295c74a04e1bde51859f03b381a73900be054ab))
- correct typos ([d340192e](https://github.com/SAP/neonbee/commit/d340192ea4f837d7c8186c47a8bb8b921f230981))
- switch to async. log appender ([880b338b](https://github.com/SAP/neonbee/commit/880b338b04bbaee03fc7aede7cf6fe823f3d85d8))
- add fake URI path to navigation properties requests ([3d2af584](https://github.com/SAP/neonbee/commit/3d2af584f49f6272d06c0c8f82c6aaae3cd197c9))
- enable parseUriInfo for OData URIs containing properties ([fea16bde](https://github.com/SAP/neonbee/commit/fea16bdece36b846559cf6353f638345240c1b63))
- re-enable system query count ([d90c40f8](https://github.com/SAP/neonbee/commit/d90c40f80537d71fdb2df5abd4c5d7671f674391))
- calculation of system query count ([35bd1dce](https://github.com/SAP/neonbee/commit/35bd1dcea21a95f464e8b0ff14b26476b760d7ac))
- respect HTTP server port configuration in NeonBee options ([4cdbd150](https://github.com/SAP/neonbee/commit/4cdbd1506cc28ed5709ce385a42e7817d12d2144))
- example for ServerVerticle (ServerConfig) ([2509bfcc](https://github.com/SAP/neonbee/commit/2509bfcc27f8aa71bc60b0146ec6546b9c0875c5))


### Features

- add limited support for OData navigation properties ([d520f703](https://github.com/SAP/neonbee/commit/d520f703574b10ae631e6cac9187b8e71984cf46))
- make launcher options configurable via env ([f51886d0](https://github.com/SAP/neonbee/commit/f51886d00d6125fe90ea821300cb32943e685475))
- add Vert.x code generation and made NeonBeeConfig a DataObject ([e026ab19](https://github.com/SAP/neonbee/commit/e026ab194618cb77f4ebe1a191e86b882531c540))
- add missing tests for NeonBeeOptions ([9608bcca](https://github.com/SAP/neonbee/commit/9608bcca7b1045747fc8b66421c549fe7bbacf7f))
- add missing tests for NeonBeeConfig ([40b66db0](https://github.com/SAP/neonbee/commit/40b66db0ac8ea592ffa560b370d3e52a6a42edcc))
- make the ServerVerticle and its endpoints fully configurable ([7871036d](https://github.com/SAP/neonbee/commit/7871036dedbef47d84116dee0cdcd092b2bb7354))
- add support for custom error handlers ([0b34d82c](https://github.com/SAP/neonbee/commit/0b34d82cf0682ad9698011446576e3152cbe7973))
- add test for JWT AuthHandler creation ([ac544cfa](https://github.com/SAP/neonbee/commit/ac544cfa75ac64a5f46d26730601be0305b4da55))
- introduce exposedEntities/Verticles allow/block lists ([2c4e8356](https://github.com/SAP/neonbee/commit/2c4e8356bbd37705fefe36b0fe2cf87be9691696))


### Code Refactoring

- remove annotation provider leftovers ([1b9e0059](https://github.com/SAP/neonbee/commit/1b9e00599052568e5d4412586cae49bd5c08c21d))
- split up god-class "Helper" ([aacf9ff8](https://github.com/SAP/neonbee/commit/aacf9ff802db23d80929030610630b3e4d78bb48))
- refactor NeonBee class ([61800401](https://github.com/SAP/neonbee/commit/6180040137d717b8d2be66acd236e5867a397f82))
- move files to prepare upcoming NeonBeeConfig change ([0f47cec2](https://github.com/SAP/neonbee/commit/0f47cec27a0963e16d8acbd911637926db95745d))
- serverPort + timeZone in options / config and Launcher ([9499d2f3](https://github.com/SAP/neonbee/commit/9499d2f3a84f69fc863e62fa3527991867e73cbc))
- move files to prepare endpoints change ([ddb9c29d](https://github.com/SAP/neonbee/commit/ddb9c29d562377f0ae19584f27008ee35cfe7359))


### Chores

- add PMD rules to avoid unnecessary modifiers in tests ([2e1c95dd](https://github.com/SAP/neonbee/commit/2e1c95dd52545396de8dd3a42d7b08f3580a6b18))
- add java doc build for test sources to the voter ([decc350d](https://github.com/SAP/neonbee/commit/decc350d5384972d3f66f28b81d7e5ae7f027571))
- bump static code dependencies ([59a1ba87](https://github.com/SAP/neonbee/commit/59a1ba87042348785908ad7735f165f4d7d4231b))


### Documentation

- update roadmap ([e98466eb](https://github.com/SAP/neonbee/commit/e98466eb5b0c09b3d90ce3df5e98da8218070b80))
- mark configurable endpoints milestone as done ([e2003852](https://github.com/SAP/neonbee/commit/e200385258894225ff83cd88c24475d6d7060b7a))


### Build System

- **ci**: set Github Actions OS to ubuntu-18.04 ([a7c20b1b](https://github.com/SAP/neonbee/commit/a7c20b1b1c166ef855c14fc627dce18e4c4d3bc6))
- **commitlint**: allow pascal-case scopes ([1a80e51d](https://github.com/SAP/neonbee/commit/1a80e51d4a1edd02f385b6bab5353b8afaaf997b))
- **deps**: replace cds-services-impl with cds4j-core ([a72d2d0e](https://github.com/SAP/neonbee/commit/a72d2d0e95511610cc219b8c3dc6e867a8fab4ae))
- **deps**: bump `vertx` from 4.0.3 to 4.1.0 ([55753cbd](https://github.com/SAP/neonbee/commit/55753cbd9ae50544be857453c66e5e89fc29536e))


### Continuous Integration

- **actions**: disable sonarqube on forks ([dc73c8ef](https://github.com/SAP/neonbee/commit/dc73c8ef1566084a404b4478fc385e8a844afc39))
- **actions**: disable sonarqube on pull requests ([0f1135a7](https://github.com/SAP/neonbee/commit/0f1135a792efadece349464ba423ebe472f903f2))


## 0.4.0 (2021-03-18)

### Bug Fixes

- flaky WatchVerticleTest ([df677239](https://github.com/SAP/neonbee/commit/df67723915edcdade47b04e38490ffbb64e73985))
- echo the correct project name ([eddbd5b2](https://github.com/SAP/neonbee/commit/eddbd5b27c2f01cb5c893a7ef6469a4a70a138b7))


### Features

- add $expand support for entities ([6c5d0790](https://github.com/SAP/neonbee/commit/6c5d07909d4695bde4bce1ba37a12aeaa5d16888))


### Code Refactoring

- simplify EntityProcessor ([6bd0a214](https://github.com/SAP/neonbee/commit/6bd0a214305e4432b4a3e7a3afe5c7e402cd0e8f))


### Documentation

- add coverage badge ([c0df69f7](https://github.com/SAP/neonbee/commit/c0df69f79e96f52ff1cd86c4165c3e87b0e3370d))


### Build System

- **deps**: bump `vertx` from 4.0.0 to 4.0.3 ([111301dd](https://github.com/SAP/neonbee/commit/111301dd60f18e3f30e5e4425396a0bcd1f4fed9))


### Continuous Integration

- use custom GitHub token for protected branches ([710f4274](https://github.com/SAP/neonbee/commit/710f42743f038541e58925818ac7ddf792154430))
- add sonarqube to github voter ([52f43ab2](https://github.com/SAP/neonbee/commit/52f43ab21a57d835f4c79178658bbf5cbb2d6c53))


## 0.3.0 (2021-03-10)

### Bug Fixes

- PubKey extraction for JWT Authhandler and other fixes ([ff018e04](https://github.com/SAP/neonbee/commit/ff018e0411915a453cd5f870c3670497d98fc21e))
- prevent UnsupportedOperationException when deploying NeonBeeModules ([e60a6a3b](https://github.com/SAP/neonbee/commit/e60a6a3b51862aeb37310420570e0688ea37f60d))
- wrong hazelcast configuration version ([1ed24b9f](https://github.com/SAP/neonbee/commit/1ed24b9f5b09609a80ccd4f4178fb7230e1e67a7))
- Usage of wrong verticles folder in WorkingDirectoryBuilder ([ba0c0e98](https://github.com/SAP/neonbee/commit/ba0c0e98dc3ddf864a756670ac1d96c678424ee5))
- Eclipse Formatter rules for VS Code ([4808ea9a](https://github.com/SAP/neonbee/commit/4808ea9a095a166d8685a32d74ff10e18497893e))
- Broken HTTP status code propagation in ODataEndpointHandler ([eabd7742](https://github.com/SAP/neonbee/commit/eabd7742c59ee4b4688544d06eb430a5663e2c5e))
- match correct version pattern when releasing ([b64bbe3c](https://github.com/SAP/neonbee/commit/b64bbe3ca033d0f28fbb783b024bd5856eb3b05c))
- Broken HTTP status code propagation to the client ([9627ea73](https://github.com/SAP/neonbee/commit/9627ea732784955c1591ff9327899f2d2550ebea))
- All static code errors ([8aaf1654](https://github.com/SAP/neonbee/commit/8aaf1654fd31ecb5a9690725eae1b471e338b507))


### Features

- support forward basic HTTP exceptions ([4f4fdf58](https://github.com/SAP/neonbee/commit/4f4fdf58130b8c33c7163f59dd704157aa32493b))
- add $expand support for entity collections ([92dbcde7](https://github.com/SAP/neonbee/commit/92dbcde7f196daac58b2acbe2d7f82a01702431b))


### Code Refactoring

- Improve NeonBee ASCII art logo ([3a52e479](https://github.com/SAP/neonbee/commit/3a52e4797dcaeac35d80b2ec21435e39e143e779))
- Remove broadcasting from DataRequest/simplify ([d183a2ab](https://github.com/SAP/neonbee/commit/d183a2ab9177113a3a14a16f05f2653f2c5454a6))
- simplify the EntityCollectionProcessor ([02b1309f](https://github.com/SAP/neonbee/commit/02b1309f5e692cc135784788f944419267f94463))
- expose SLF4J Logger interface in LoggingFacade ([d299658b](https://github.com/SAP/neonbee/commit/d299658b82c96a76a016ad56d398b1bff27696b1))


### Chores

- Rename repository to just "neonbee" ([c2a6d9f0](https://github.com/SAP/neonbee/commit/c2a6d9f03875f0b2c71100b1cf55a8001a1830e4))
- update issue templates ([5b8aef2d](https://github.com/SAP/neonbee/commit/5b8aef2d6062f462c19327223863ccfe3f24f56c))
- Adding missing license information ([e20fb48c](https://github.com/SAP/neonbee/commit/e20fb48ce6663b3123ddcc3e2db54146aec01512))
- add codeowners ([e41957b6](https://github.com/SAP/neonbee/commit/e41957b604daf26080366bd637653d766ac01c0d))
- Setup voter with github actions ([99b1b7c7](https://github.com/SAP/neonbee/commit/99b1b7c721fccf57b4376cc562fa1b6759af7ee1))
- lint commit messages on push ([726dae05](https://github.com/SAP/neonbee/commit/726dae053569e49633c9e258b3dbbf7f859c0389))
- disable body-max-line-length for commitlint ([de63afe1](https://github.com/SAP/neonbee/commit/de63afe15a3b0a6c12964c5c4daf4ce77f38f746))


### Tests

- Fix all failing unit tests on Windows ([8ce4d746](https://github.com/SAP/neonbee/commit/8ce4d7461c524c1c93b67da03eb668e8f9da2a72))


### Documentation

- Add CONTRIBUTING.md ([14ff9121](https://github.com/SAP/neonbee/commit/14ff9121db41d39f99f6469ef92f85f6c0858b9d))
- Add high-level milestones to docs/roadmap.md ([c14c4ecb](https://github.com/SAP/neonbee/commit/c14c4ecbafb215a49830d8cc831667486b1eead7))
- add licensing and contributing information ([3e3d9a32](https://github.com/SAP/neonbee/commit/3e3d9a320d7ff17cc7129d6d6251ef680ef5f33c))
- change NeonBee description in README.md ([2bd264e8](https://github.com/SAP/neonbee/commit/2bd264e8e0c8b0e15fb774f3f5ad341a969c065e))
- adapt more technical writing in README, add dataflow and roadmap items ([209abcde](https://github.com/SAP/neonbee/commit/209abcde09508b41bc71c79885450bdf16b00f5c))
- adapt docs for GitHub release workflow ([d06c9ba9](https://github.com/SAP/neonbee/commit/d06c9ba969bf0ec7706edda5e42a97d4c9f8f308))


### Build System

- **deps**: downgrade `jackson` from 2.12.0 to 2.11.3 ([06fd9a4b](https://github.com/SAP/neonbee/commit/06fd9a4b8449f92378208943d13e69bd4a03cd2a))
- **gradle**: disable errorprone checks for TypeParameterUnusedInFormals ([87b16311](https://github.com/SAP/neonbee/commit/87b16311f17b231657c6c74bc68279f838c95a4f))
- Move most .gradle files to /gradle and restructure ([2dec63a9](https://github.com/SAP/neonbee/commit/2dec63a933c0e85593f3c2926fc833e6055d4793))
- remove unnecessary repositories from Gradle files and bump Gradle ([8226af83](https://github.com/SAP/neonbee/commit/8226af83a66178e73b0105cfeabe271a80ba8345))
- add required configuration for publishing to maven central ([9cc50e4b](https://github.com/SAP/neonbee/commit/9cc50e4b927c4ae408c94cf7b7475c2edbba5b40))


### Continuous Integration

- **actions**: add reuse compliance workflow ([7867a2f6](https://github.com/SAP/neonbee/commit/7867a2f6121e031ed5f062b6bdad569b0e099310))
- Add commitlint GitHub workflow ([8ee0bdb6](https://github.com/SAP/neonbee/commit/8ee0bdb6a6e1e8653f5e2afd7c05179e288056a6))
- add publishing workflow ([12e5b4fc](https://github.com/SAP/neonbee/commit/12e5b4fc9d8cd5109837db257fe600a660972145))


### Others

- **deps**: Mockito to 3.7.7 ([736f880f](https://github.com/SAP/neonbee/commit/736f880f1ee04dd2015ea2d15182d0c23ee3d8e5))

