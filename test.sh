#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/test-classes
java -m jdk.compiler/com.sun.tools.javac.Main -d build/test-classes app/src/main/java/de/meiapps/meicraft/World.java app/src/main/java/de/meiapps/meicraft/Story.java app/src/main/java/de/meiapps/meicraft/Survival.java app/src/main/java/de/meiapps/meicraft/UpdateInfo.java app/src/main/java/de/meiapps/meicraft/SavedWorld.java tests/WorldTests.java tests/UpdateTests.java tests/StoryTests.java tests/SavedWorldTests.java tests/SurvivalTests.java
java -cp build/test-classes de.meiapps.meicraft.WorldTests
java -cp build/test-classes de.meiapps.meicraft.UpdateTests
java -cp build/test-classes de.meiapps.meicraft.StoryTests
java -cp build/test-classes de.meiapps.meicraft.SavedWorldTests

java -cp build/test-classes de.meiapps.meicraft.SurvivalTests
