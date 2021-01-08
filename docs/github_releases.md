# Release new versions on GitHub

The following process requires at least two contributors, as there is no CI or review process for verification.

1. Merge all commits to master that should be part of the next release
2. Check out origin master
3. Execute the release task

    ```console
    ./gradlew release -PnextVersion=<nextVersion>
    ```

    This updates the version in the build.gradle and updates the CHANGELOG. It generates a commit with the changed files and creates a tag `nextVersion`.

4. Push to master `git push -u origin HEAD:master --tags` (Note: pull request workflow not possible because this would generate a new commit, which would not be referenced by the tag)
5. Push to the related release branch `git push -u origin HEAD:0.x`, where 0.x refers to the related major version