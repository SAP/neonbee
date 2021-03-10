# Release new versions on GitHub

1. Checkout a new branch

    ```console
    git checkout -b release-<nextVersion> origin/main
    ```

2. Execute the gradle release task

    ```console
    ./gradlew release -PnextVersion=<nextVersion>
    ```

    This task updates the version in the `build.gradle` to `<nextVersion>` and updates `CHANGELOG.*`.
    It also generates a commit with the changed files.

3. Push the branch

    ```console
    `git push -u origin HEAD:refs/heads/release-<nextVersion>
    ```

4. Open a new pull request against the `main` branch
5. Get approval from at least one committer. Once the pull request has been merged, a GitHub Action workflow will be triggered. The workflow publishes the version to maven central and creates a Github Draft Release with the corresponding changelog.
6. Once the workflow in step 5. has finished fetch and push the newly created release tag to the related release branch. Example:

    ```console
    git fetch
    git push origin <nextVersion>:/refs/heads/0.x
    ```

    where `0.x` refers to the related major version
