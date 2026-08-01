#!/bin/sh
# ---------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# Apache Maven Wrapper startup batch script, version 3.3.2
# ---------------------------------------------------------------------------

# Required ENV vars:
# ------------------
#   JAVA_HOME - location of a JDK home dir
#
# Optional ENV vars
# -----------------
#   M2_HOME - location of maven2's installed home dir
#   MAVEN_OPTS - parameters passed to the Java VM when running Maven
#     e.g. to debug Maven itself, use
#       set MAVEN_OPTS=-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000
#   MAVEN_SKIP_RC - flag to disable loading of mavenrc files
# ---------------------------------------------------------------------------

if [ -z "$MAVEN_SKIP_RC" ]; then
  if [ -f "$HOME/.mavenrc" ]; then
    . "$HOME/.mavenrc"
  fi
  if [ -f /etc/mavenrc ]; then
    . /etc/mavenrc
  fi
fi

if [ -z "$JAVA_HOME" ]; then
  echo "Error: JAVA_HOME is not set. Please point it to a JDK 17+ installation." >&2
  exit 1
fi

if [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "Error: JAVA_HOME is set to an invalid directory: $JAVA_HOME" >&2
  echo "Please set the JAVA_HOME variable to match your JDK installation." >&2
  exit 1
fi

# Resolve links
PRG="$0"
while [ -h "$PRG" ]; do
  ls=$(ls -ld "$PRG")
  link=$(expr "$ls" : '.*-> \(.*\)$')
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=$(dirname "$PRG")/"$link"
  fi
done

# Resolve project base directory
SAVED=$(pwd)
cd "$(dirname "$PRG")" || exit
BASEDIR=$(pwd)
cd "$SAVED" || exit

WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

exec "$JAVA_HOME/bin/java" \
  $MAVEN_OPTS \
  $MAVEN_DEBUG_OPTS \
  -classpath "$BASEDIR/.mvn/wrapper/maven-wrapper.jar" \
  "-Dmaven.multiModuleProjectDirectory=$BASEDIR" \
  ${WRAPPER_LAUNCHER} "$@"
