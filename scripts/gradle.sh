#!/usr/bin/env bash
# Runs Gradle with the JDK/Gradle/SDK toolchain that ships in the Meeting Timer repo.
TC="/d/Code/Mockup Pro/Google calendar timer/.toolchain"
export JAVA_HOME="$TC/jdk-17.0.20.1+1"
export GRADLE_USER_HOME="$TC/gradle-home"
cd "$(dirname "$0")/.." && "$TC/gradle-8.11.1/bin/gradle" --console=plain "$@"
