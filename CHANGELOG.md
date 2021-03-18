# Changelog

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

