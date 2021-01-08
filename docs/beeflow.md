# BeeFlow

## Content

- [BeeFlow](#BeeFlow)
  - [Content](#Content)
  - [What is BeeFlow](#What-is-BeeFlow)
  - [BeeFlow in a nutshell](#BeeFlow-in-a-nutshell)
  - [Develop a new feature](#Develop-a-new-feature)
    - [Merge feature into master](#Merge-feature-into-master)
    - [Rebase feature if required](#Rebase-feature-if-required)
  - [Release new features](#Release-new-features)
  - [Release a patch (bugfix)](#Release-a-patch-bugfix)
  - [Maintaining multiple versions](#Maintaining-multiple-versions)

## What is BeeFlow

BeeFlow is a streamlined git branching model with a clear focus on readability of the history and completely avoids merge commits. Using BeeFlow is most useful in projects with the following requirements:

- Human readable history
- Maintaining multiple versions in parallel

## BeeFlow in a nutshell

This section gives an overview of BeeFlow and explains the fundamentals on a very high level. More detailed explanations can be found in the subsequent sections. Working with BeeFlow only requires three kind of branches:

- **Topic branches:** A topic branch contains commits related to a feature or a bug fix, which are not yet completed or reviewed.
- **Master branch:** The master branch contains completed features which are not yet released.
- **Release branches:** A release branch exists for every major version of project.

The example below shows how these branches interact with each other. In general every `topic` branch branches off from `HEAD` of the `master` branch. The `master` branch always branches off from the `HEAD` of the latest release branch. Every commit that represents a released version is tagged.

```asciidoc
                         A---B---C    topic-a
                        /
               D---E---F              master
              /     \
             /       `G---H           topic-b
            /
---I---J---K                          1.x
   :       :
v1.1.0  v1.2.0                        Tags
```

Notice, the `topic-b` branch in above example is not branched off from `F`, because when it was created `E` was `HEAD` of `master`. A rebase is only required before it is merged into the master branch.

## Develop a new feature

Each new feature should reside in its own `topic` branch. A new `topic` branch that branches off from `master` branch can be created with the following command:

```sh
git fetch
git checkout -b my-feature origin/master
```

On this branch, the new feature can be build up. In theory, it is possible to construct the new feature from several commits. However, in order to ensure the reviewability of a feature, it is **strongly** recommended that the features are kept as small as possible so that they - ideally - fit into one commit. This not only improves the readability of the project history, but also makes it a lot easier to rollback a feature. In case that it is not possible to construct the feature within one commit it is recommended to prefix the subject of the commit message with a feature related tag. This makes it very easy to identify related commits.

An example history would now look like this:

```asciidoc
                       A    my-feature
                      /
             B---C---D      master
            /
---E---F---G                1.x
   :       :
v1.1.0  v1.2.0              Tags
```

### Merge feature into master

When the feature is considered as ready by its owner, the `topic` branch can be merged (**fast-forward only**) into `master`. If the `topic` branch is called `my-feature`, this can be done with the following commands:

```sh
git fetch
git checkout -b master origin/master
git merge my-feature --ff-only
```

After applying these commands the history should look like this:

```asciidoc
             B---C---D---A    master / my-feature
            /
---E---F---G                  1.x
   :       :
v1.1.0  v1.2.0                Tags
```

Now the new `master` branch can be pushed and the `topic` branch `my-feature` can be deleted.

### Rebase feature if required

The fast-forward merge will fail, if the `topic` branch (`my-feature`) is not [rebased](https://git-scm.com/docs/git-rebase) onto the `master` branch and the history looks for instance like this:

```asciidoc
                   A       my-feature
                  /
             B---C---D     master
            /
---E---F---G               1.x
   :       :
v1.1.0  v1.2.0             Tags
```

In this case it is required to rebase the `topic` branch onto the `master` branch with the following command:

```sh
git checkout my-feature
git rebase -i --onto origin/master
```

When the rebase was successful, retry to merge the feature into master.

## Release new features

When a new release is due, either because of a predetermined release date or because `master` branch has acquired enough features, the following steps apply:

1. Identify the commits on the `master` branch which should be part of the release (`A` and `D` in below example).

   ```asciidoc
                A---B---C---D   master
               /
   ---E---F---G                 1.x
      :       :
   v1.1.0  v1.2.0               Tags
   ```

2. Re-order releasable commits on `master` and push to new branch `master-rel-<new-release-version>`. Verify that all tests pass on the new branch after the rebase. If all tests pass this branch can be force pushed to the upstream `master` branch and deleted afterwards.

   ```asciidoc
                A---D---B---C   master
               /
   ---E---F---G                 1.x
      :       :
   v1.1.0  v1.2.0               Tags
   ```

3. Now the commits to release have the desired order and it is possible to merge them (**with fast-forward only**) into the release branch `1.x`. This can be achieved by the following commands:

   ```console
   git checkout -b v1 origin/v1
   git merge <commit 'D'> --ff-only
   ```

   After applying these commands the history should look like this:

   ```asciidoc
                        B---C   master
                       /
   ---E---F---G---A---D         1.x
      :       :
   v1.1.0  v1.2.0               Tags
   ```

   Push the updated `HEAD` of `1.x` branch to the upstream `1.x` branch.

   ```console
   git push origin HEAD:refs/heads/v1
   ```

4. Trigger the build process which tags the `HEAD` of the release branch `1.x`. After a successful build the history should look like this:

   ```asciidoc
                        B---C   master
                       /
   ---E---F---G---A---D         1.x
      :       :       :
   v1.1.0  v1.2.0  v1.3.0       Tags
   ```

## Release a patch (bugfix)

First it is required that the commit representing the patch that fixes the bug is [merged into master](#Merge-feature-into-master). In the example below the commit `A` contains the patch.

```asciidoc
             A   master
            /
---B---C---D     1.x
   :       :
v1.1.0  v1.2.0   Tags
```

Maybe it is possible to simply release a new version 1.3.0 that contains the fix and don't fix the already released versions. In this case have a look into the section [Release new features](#Release-new-features).

But in most cases it is also required to down port the patch to already released versions. This can be achieved by the following steps:

1. Checkout the version to be fixed and create a new bugfix branch.

   ```sh
   git fetch
   git checkout -b bugfix-<name>-<new_version> origin/<version_to_be_fixed>
   ```

   Example: The patch represented by commit `A` fixes a `NullPointerException` during the bootstrap phase and should be fixed in version 1.1.0.

   ```sh
   git fetch
   git checkout -b bugfix-bootstrap-NPE-v1.1.1 v1.1.0
   ```

2. Cherry-pick and push the commit representing the bug fix.

   ```sh
   git cherry-pick <commitID>
   git push HEAD:refs/heads/<bugfix_branch>
   ```

   Example: Commit `A` with the id `4f1a16e6` contains the bug fix

   ```sh
   git cherry-pick 4f1a16e6
   git push HEAD:refs/heads/bugfix-bootstrap-NPE-v1.1.1
   ```

   Now the history should look like this:


   ```asciidoc
                A           master
               /
   ---B---C---D             1.x
      :\      :
      : `-----:-------A'    bugfix-bootstrap-NPE-v1.2.1
      :       :
   v1.1.0  v1.2.0           Tags
   ```

3. Trigger the build process which tags the `HEAD` of the bugfix branch with a new patch version and delete the temporary created bugfix branch. This should result in a history like this:

   ```asciidoc
                A           master
               /
   ---B---C---D             1.x
      :\      :
      : `-----:-------A'
      :       :       :
   v1.1.0  v1.2.0  v1.1.1   Tags
   ```

## Maintaining multiple versions

BeeFlow recommends to use the Semantic Versioning Specification [(SemVer)](https://semver.org/). In BeeFlow every major version has its own branch. It is recommended to name this branches `1.x`, `2.x`, ... The `HEAD` of these branches should always point to the commit representing the latest minor version of the related major version. Every commit that represents the release of a version must be tagged. It is recommended to use the version identifier as name for this tag e.g.: `v1.0.0`, `v1.1.1`, ... . In case an already released minor version must be patched, because of a bug fix, the commit representing the patched version is only referenced by its mandatory tag. Applying these rules make the history looking like this:

```asciidoc
----A-----B----C----D                    1.x
    :\              :\
    : `-----C'      : \
    :       :       :  `----E-------F    2.x
    :       :       :       :       :
    :       :       :       :       :
 v1.1.0  v1.1.1  v1.2.0  v2.0.0  v2.1.0  Tags
```
