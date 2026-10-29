# Release new versions on GitHub

Releases are created by the [Publish](../../.github/workflows/publish.yml) workflow. The version is maintained in
`gradle.properties` and is always a `-SNAPSHOT` version on `main`.

## Create a release

1. Open the [Publish workflow](https://github.com/SAP/neonbee/actions/workflows/publish.yml) and click *Run workflow*.
2. Select the `main` branch and optionally enter the version to release (e.g. `0.38.0`). Without a version, the
   current version from `gradle.properties` without `-SNAPSHOT` is released, e.g. `0.37.38` for `0.37.38-SNAPSHOT`.

The workflow then:

1. Checks that the version is valid and neither tagged nor published on Maven Central yet.
2. Builds and tests the release version.
3. Builds the Docker image (without pushing it yet).
4. Creates the tag `<version>` on the commit that was built and the GitHub release with the changes since the previous
   release.
5. Publishes the release to Maven Central with the `publishAndReleaseToMavenCentral` task of the
   [vanniktech/gradle-maven-publish-plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/central/), which
   uploads all artifacts as one deployment and waits until Maven Central validated and published it.
6. Pushes the Docker image as `<version>` and `latest`.
7. Opens a pull request that bumps the version in `gradle.properties` to the next patch `-SNAPSHOT` version, e.g.
   `0.37.39-SNAPSHOT`.

A release never pushes to `main`, which is protected. The release version is passed to Gradle with `-Pversion`, so the
tagged commit still contains the `-SNAPSHOT` version in `gradle.properties`. To build a release from its tag, pass the
version as well, e.g. `./gradlew build -Pversion=0.37.38`.

Merge the version bump pull request after the release. Until it is merged, `main` still contains the released
`-SNAPSHOT` version and no snapshots are published. Pull requests created by the workflow do not trigger other
workflows, so no checks run on it. If the repository does not allow GitHub Actions to create pull requests, the workflow
pushes the branch `release/bump-<nextVersion>` and links to it in the summary of the workflow run, where the pull
request can be created manually.

To release a new minor or major version, enter it explicitly, e.g. `0.38.0`. The version bump pull request then sets
`0.38.1-SNAPSHOT`.

The changes of each release are listed in its [GitHub release](https://github.com/SAP/neonbee/releases). The changelog is
generated from the commit messages with `./gradlew changelog` into `build/changelog/CHANGELOG.md`.

## Publishing on Maven Central

The release is published on Maven Central without any manual approval on the
[Central Portal](https://central.sonatype.com/publishing/deployments): starting the workflow is the only approval.
Unlike the former OSSRH process, there is no staging repository that has to be closed and released:

1. At the end of a successful Gradle build, the `publishAndReleaseToMavenCentral` task uploads all artifacts as one
   deployment with the publishing type `AUTOMATIC`.
2. Maven Central validates the deployment (signatures, POMs, checksums and the permission to publish to the
   `io.neonbee` namespace) and publishes it automatically if the validation passes.
3. The task waits until the deployment is either `PUBLISHED` or `FAILED` (for at most one hour). The workflow pushes the
   Docker image and opens the version bump pull request only after the deployment was published.

The `publishToMavenCentral` task of the plugin would upload the deployment as `USER_MANAGED` instead, so that it has to
be published manually on the Central Portal. The workflow does not use it, because it could then not tell when the
release was published, and pushing the Docker image and opening the version bump pull request would have to be done
manually, too.

## Failures

Nothing is uploaded to Maven Central if the Gradle or the Docker build fails. If anything fails before Maven Central
published the release, the tag and the GitHub release are deleted and no Docker image is pushed. A deployment that
failed the validation of Maven Central is never published and stays visible as *failed* on the
[Central Portal](https://central.sonatype.com/publishing/deployments), where it can be dropped. Fix the problem and run
the workflow again.

If step 5 fails because it timed out (after one hour), check the deployment on the Central Portal before running the
workflow again: Maven Central may still publish it.

If the workflow fails after step 5, the release is already public and cannot be rolled back. Finish the remaining steps
manually, e.g. by bumping the version in `gradle.properties` to the next `-SNAPSHOT` version in a pull request.

## Snapshots

Every push to `main` publishes the `-SNAPSHOT` version from `gradle.properties` to the
[Maven Central snapshot repository](https://central.sonatype.org/publish/publish-portal-snapshots/) and pushes the
Docker image as `main`. Snapshots are not validated by Maven Central and are deleted after 90 days. No credentials are
needed to use them:

```groovy
repositories {
    maven {
        url = 'https://central.sonatype.com/repository/maven-snapshots/'
    }
}
```

## Prerequisites

The workflow requires the following repository secrets:

| Secret                                         | Description                                                         |
|------------------------------------------------|---------------------------------------------------------------------|
| `SIGNING_KEY_ARMOR`, `SIGNING_PASSWORD`        | ASCII-armored GPG key and its password, used to sign the artifacts  |
| `MAVEN_CENTRAL_USER`, `MAVEN_CENTRAL_PASSWORD` | Central Portal user token with access to the `io.neonbee` namespace |

All other steps use the `GITHUB_TOKEN` of the workflow run, no GitHub App or personal access token is needed.
