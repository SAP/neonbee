#!/bin/sh

set -e

NEXT_VERSION="$1"

if [ -z "$NEXT_VERSION" ]; then
  echo "usage: ./release.sh <nextVersion>"
  >&2 echo "\033[1;31merror: must specify next release version"
  exit 1
fi

cd "$( dirname "$0" )/.."

echo "Releasing forme-gev ${NEXT_VERSION}"

git tag "${NEXT_VERSION}"  # hack: creating a temporary local tag which is needed for generating the changelog
./gradlew changelog
git tag -d "${NEXT_VERSION}"

sed -i.bak "s/^version = \'[^\']*\'/version = \'${NEXT_VERSION}\'/g" build.gradle
rm -f build.gradle.bak

git add build.gradle CHANGELOG.*
git --no-pager diff --cached -- build.gradle

git commit -m "release: ${NEXT_VERSION}"
git tag "${NEXT_VERSION}"