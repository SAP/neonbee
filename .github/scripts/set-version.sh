#!/usr/bin/env bash
#
# Sets the project version in gradle.properties.
#
# Usage: set-version.sh <version>

set -euo pipefail

version="${1:?usage: set-version.sh <version>}"
properties="$(dirname "$0")/../../gradle.properties"

sed -i "s/^version=.*/version=${version}/" "${properties}"
if ! grep -qx "version=${version}" "${properties}"; then
    echo "Failed to set version ${version} in gradle.properties" >&2
    exit 1
fi
echo "Set version to ${version}"
