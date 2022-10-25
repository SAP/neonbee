# Development Concept

## Content

- [Development Concept](#Development-Concept)
  - [Content](#Content)
  - [Glossary](#Glossary)
  - [General](#General)
  - [Review](#Review)
    - [Focus and Scope](#Focus-and-Scope)
    - [Consensus Preferred](#Consensus-Preferred)
  - [Releases](#Releases)
    - [Changelog and Artifacts](#Changelog-and-Artifacts)
    - [Breaking changes](#Breaking-changes)

## Glossary

- <a name="neonbee_council"></a>**NeonBee Council:** This council is composed of an uneven number of committers to ensure that the council is always able to act and never run into a standoff. The council is responsible for the overall project strategy. In case there are project related topics where no consensus can be achieved, e.g. in a review or naming, the council will decide. A decision by the council must always be justified.
- <a name="committer"></a>**Committer:** Committers are developers from the core team who have the ability to directly push (i.e., they have write access) their own contributions to the project’s Git repositories, and have the responsibility to review and merge the contributions of others.
- <a name="contributor"></a>**Contributor:** Anybody can be a contributor.

## General

Anybody can be a [contributor](#contributor) and contribute to NeonBee. There are only a certain conventions / processes every contributor must stick to.

- NeonBee uses the branching model [BeeFlow](beeflow.md).
- NeonBee strictly follows the [Semantic Versioning](https://semver.org/) specification.
- Any contribution must fulfill the NeonBee community conventions for [commit messages](commit_msg.md).
- Any contribution must fulfill the NeonBee community conventions for [quality](code_quality.md).
- Confirm these [checks](#Review) before requesting a review.

## Review

When the owner of a `topic` branch wants to get feedback on a change the following guidelines apply:

- A topic branch has been created based on `master`. If the [contributor](#contributor) has no write access, a fork must be created.
- Each commit is considered a separate logical unit of change to make rollbacks easier.
- A pull request has been created with the respective issue(s) linked in the commit message (e.g. `Fixes #15`), if those exists.
- Each commit must be conform to the NeonBee community conventions for [commit messages](commit_msg.md) and [quality](code_quality.md).
- Each commit **SHOULD** add necessary tests for the changes made and existing tests **MUST NOT** break.

### Focus and Scope

In general there is no limitation what reviewer can comment and what not, everything else would be censorship, but there are a clear rules what prevents a contribution to be merged into the `master` branch. These rules are defined by the NeonBee community conventions for [commit messages](commit_msg.md) and [quality](code_quality.md).

If a **reviewer want changes** that go **beyond the NeonBee conventions**, they have to **make** these **themselves**, provided the contributor agrees.

### Consensus Preferred

In case of multiple proposed solutions consensus is always preferable, but if consensus on an approach cannot be reached, the [NeonBee Council](#neonbee_council) should decide on which approach to take.

## Releases

### Changelog and Artifacts

Releases are generally published in the [releases section](https://github.com/SAP/neonbee/releases) of the project. Each new release contains a stable version of the source code, a [description of the changes](../../CHANGELOG.md) and offers [compiled artifacts](release_artifacts.md) of NeonBee.

### Breaking changes

Breaking changes are any changes that might require action from users of NeonBee's API. Although the project strives to provide a stable API by keeping the number of breaking changes as small as possible, sometimes it is inevitable to break existing APIs.

In order to make breaking changes as transparent as possible, they will be indicated by a major version increment and announced via the [changelog](CHANGELOG.md) with a brief description and upgrade instructions.

**Notice:** Before the first major version is released, minor versions can contain breaking changes.
