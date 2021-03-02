#!/bin/bash

SCRIPTPATH="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"

export ORG_GRADLE_PROJECT_signingPassword=EnterSigningPasswordHere
export ORG_GRADLE_PROJECT_signingKey=EnterContentOfKeyArmorFileHere # e.g.:`cat ~/NeonBeePrivate.asc`

export ORG_GRADLE_PROJECT_mavenCentralUsername=ossrh-jira-user
export ORG_GRADLE_PROJECT_mavenCentralPassword=ossrh-jira-password

cd "${SCRIPTPATH}"
./gradlew publishAllPublicationsToMavenCentralRepository
