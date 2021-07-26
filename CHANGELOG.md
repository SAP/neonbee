# Changelog

## 0.5.1 (2021-07-21)

### Bug Fixes

- decode query parameters in odata requests ([38e36e2f](https://github.com/SAP/neonbee/commit/38e36e2fe6ddc1b1463d3fc8857a5456e6061862))
- prepend uri paths of entity requests with a slash ([ae31b863](https://github.com/SAP/neonbee/commit/ae31b8638dc66b9c4d272b23eeb463760267fe1b))


### Code Refactoring

- **processor**: extract common methods into helper ([608ec395](https://github.com/SAP/neonbee/commit/608ec395c195f0197f8210e21a1d272ebbd5ef55))
- made the ClassPathScanner non-blocking ([dc190210](https://github.com/SAP/neonbee/commit/dc1902100297100511ad59a584a92a4ee60c35a0))
- move error handler configuration to ServerConfig class ([71e328ff](https://github.com/SAP/neonbee/commit/71e328ff32dfa400ff52d4a29a0e864135f18a64))


### Features

- allow multiple MANIFST.MF files when parsing NeonBee-Module ([dec4a91f](https://github.com/SAP/neonbee/commit/dec4a91fcb0ab646486dbeb9a9e332a967eee3f6))


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


### Code Refactoring

- remove annotation provider leftovers ([1b9e0059](https://github.com/SAP/neonbee/commit/1b9e00599052568e5d4412586cae49bd5c08c21d))
- split up god-class "Helper" ([aacf9ff8](https://github.com/SAP/neonbee/commit/aacf9ff802db23d80929030610630b3e4d78bb48))
- refactor NeonBee class ([61800401](https://github.com/SAP/neonbee/commit/6180040137d717b8d2be66acd236e5867a397f82))
- move files to prepare upcoming NeonBeeConfig change ([0f47cec2](https://github.com/SAP/neonbee/commit/0f47cec27a0963e16d8acbd911637926db95745d))
- serverPort + timeZone in options / config and Launcher ([9499d2f3](https://github.com/SAP/neonbee/commit/9499d2f3a84f69fc863e62fa3527991867e73cbc))
- move files to prepare endpoints change ([ddb9c29d](https://github.com/SAP/neonbee/commit/ddb9c29d562377f0ae19584f27008ee35cfe7359))


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


### Documentation

- update roadmap ([e98466eb](https://github.com/SAP/neonbee/commit/e98466eb5b0c09b3d90ce3df5e98da8218070b80))
- mark configurable endpoints milestone as done ([e2003852](https://github.com/SAP/neonbee/commit/e200385258894225ff83cd88c24475d6d7060b7a))


### Chores

- add PMD rules to avoid unnecessary modifiers in tests ([2e1c95dd](https://github.com/SAP/neonbee/commit/2e1c95dd52545396de8dd3a42d7b08f3580a6b18))
- add java doc build for test sources to the voter ([decc350d](https://github.com/SAP/neonbee/commit/decc350d5384972d3f66f28b81d7e5ae7f027571))
- bump static code dependencies ([59a1ba87](https://github.com/SAP/neonbee/commit/59a1ba87042348785908ad7735f165f4d7d4231b))


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


### Code Refactoring

- simplify EntityProcessor ([6bd0a214](https://github.com/SAP/neonbee/commit/6bd0a214305e4432b4a3e7a3afe5c7e402cd0e8f))


### Features

- add $expand support for entities ([6c5d0790](https://github.com/SAP/neonbee/commit/6c5d07909d4695bde4bce1ba37a12aeaa5d16888))


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


### Code Refactoring

- Improve NeonBee ASCII art logo ([3a52e479](https://github.com/SAP/neonbee/commit/3a52e4797dcaeac35d80b2ec21435e39e143e779))
- Remove broadcasting from DataRequest/simplify ([d183a2ab](https://github.com/SAP/neonbee/commit/d183a2ab9177113a3a14a16f05f2653f2c5454a6))
- simplify the EntityCollectionProcessor ([02b1309f](https://github.com/SAP/neonbee/commit/02b1309f5e692cc135784788f944419267f94463))
- expose SLF4J Logger interface in LoggingFacade ([d299658b](https://github.com/SAP/neonbee/commit/d299658b82c96a76a016ad56d398b1bff27696b1))


### Features

- support forward basic HTTP exceptions ([4f4fdf58](https://github.com/SAP/neonbee/commit/4f4fdf58130b8c33c7163f59dd704157aa32493b))
- add $expand support for entity collections ([92dbcde7](https://github.com/SAP/neonbee/commit/92dbcde7f196daac58b2acbe2d7f82a01702431b))


### Documentation

- Add CONTRIBUTING.md ([14ff9121](https://github.com/SAP/neonbee/commit/14ff9121db41d39f99f6469ef92f85f6c0858b9d))
- Add high-level milestones to docs/roadmap.md ([c14c4ecb](https://github.com/SAP/neonbee/commit/c14c4ecbafb215a49830d8cc831667486b1eead7))
- add licensing and contributing information ([3e3d9a32](https://github.com/SAP/neonbee/commit/3e3d9a320d7ff17cc7129d6d6251ef680ef5f33c))
- change NeonBee description in README.md ([2bd264e8](https://github.com/SAP/neonbee/commit/2bd264e8e0c8b0e15fb774f3f5ad341a969c065e))
- adapt more technical writing in README, add dataflow and roadmap items ([209abcde](https://github.com/SAP/neonbee/commit/209abcde09508b41bc71c79885450bdf16b00f5c))
- adapt docs for GitHub release workflow ([d06c9ba9](https://github.com/SAP/neonbee/commit/d06c9ba969bf0ec7706edda5e42a97d4c9f8f308))


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

