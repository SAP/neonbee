#!/usr/bin/env bash

# MACOS: Try to set JAVA_HOME to JAVA 11
if [[ "$OSTYPE" == "darwin"* ]]; then
    export JAVA_HOME=`/usr/libexec/java_home -v 11`; java -version
fi

javaCommand="java" # name of the Java launcher without the path

if [ -z "$JAVA_EXE" ]; then
    if [ ! -z "$JAVA_HOME" ]; then # $JAVA_HOME is not null
        javaExe="$JAVA_HOME/bin/$javaCommand"
    elif [ ! -z $(which java) ]; then # $JAVA_HOME is null (zero string)
        javaExe=$(which java) # file name of the Java application launcher executable
    fi
    if [ -z "$javaExe" ]; then
        echo "Unable to locate java executable";
        exit 5;
    fi
else
  javaExe=$JAVA_EXE
fi

# Currently only java 11 is supported
JAVA_VERSION=`$javaExe -version 2>&1 |awk 'NR==1{ gsub(/"/,""); gsub(/_[0-9]*/, ""); print $3 }'`
if [[ ! "$JAVA_VERSION" =~ 11. ]]
then
    echo "JAVA version is not correct. The only supported version is 11. Current java is $JAVA_VERSION";
else
    ljsDir="${BASH_SOURCE[0]}"; # absolute path of the script directory
    if([ -h "${ljsDir}" ]) then
      while([ -h "${ljsDir}" ]) do ljsDir=`readlink "${ljsDir}"`; done
    fi
    pushd . > /dev/null

    tmp_dir=`dirname "${ljsDir}"`
    cd "$tmp_dir" > /dev/null
    ljsDir=`pwd`;
    popd  > /dev/null

    # Start NeonBee
    # Please note it is not possible to use -classpath/-cp option in conjunction with -jar
    echo "NeonBee will now be started on port: 8080";
    java -classpath $ljsDir/libs/*:. io.neonbee.Launcher -working-directory $ljsDir
fi
