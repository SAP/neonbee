#!/bin/bash

SCRIPTPATH="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"

export ORG_GRADLE_PROJECT_signingPassword=EnterSigningPasswordHere
export ORG_GRADLE_PROJECT_signingKey=EnterContentOfKeyArmorFileHere # e.g.:`cat ~/NeonBeePrivate.asc`

export ORG_GRADLE_PROJECT_mavenCentralUsername=ossrh-jira-user
export ORG_GRADLE_PROJECT_mavenCentralPassword=ossrh-jira-password

cd "${SCRIPTPATH}"
./gradlew publishAllPublicationsToMavenCentralRepository

BEARER=$(printf '%s:%s' "$ORG_GRADLE_PROJECT_mavenCentralUsername" "$ORG_GRADLE_PROJECT_mavenCentralPassword" | base64)

curl -v -H "Authorization: Bearer $BEARER" -i -X POST 'https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/io.neonbee'