#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/test-classes
java -m jdk.compiler/com.sun.tools.javac.Main -d build/test-classes app/src/main/java/de/meiapps/meicraft/World.java app/src/main/java/de/meiapps/meicraft/UpdateInfo.java tests/WorldTests.java tests/UpdateTests.java
java -cp build/test-classes de.meiapps.meicraft.WorldTests
java -cp build/test-classes de.meiapps.meicraft.UpdateTests
