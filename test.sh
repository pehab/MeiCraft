#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/test-classes
bash scripts/qr-dependency.sh
QR_JAR=build/dependencies/zxing-core-3.5.4.jar
java -m jdk.compiler/com.sun.tools.javac.Main -classpath "$QR_JAR" -d build/test-classes app/src/main/java/de/meiapps/meicraft/WorldTransfer.java app/src/main/java/de/meiapps/meicraft/QrCodec.java app/src/main/java/de/meiapps/meicraft/QrCameraFrame.java app/src/main/java/de/meiapps/meicraft/World.java app/src/main/java/de/meiapps/meicraft/Story.java app/src/main/java/de/meiapps/meicraft/Survival.java app/src/main/java/de/meiapps/meicraft/Nature.java app/src/main/java/de/meiapps/meicraft/Forge.java app/src/main/java/de/meiapps/meicraft/UpdateInfo.java app/src/main/java/de/meiapps/meicraft/SavedWorld.java app/src/main/java/de/meiapps/meicraft/OnlineWorld.java app/src/main/java/de/meiapps/meicraft/OnlineInventory.java tests/OnlineWorldTests.java tests/OnlineResourceTests.java tests/WorldTests.java tests/UpdateTests.java tests/StoryTests.java tests/SavedWorldTests.java tests/SurvivalTests.java tests/NatureTests.java tests/ForgeTests.java tests/WorldTransferTests.java tests/QrCameraFrameTests.java
java -cp "build/test-classes:$QR_JAR" de.meiapps.meicraft.WorldTests
java -cp "build/test-classes:$QR_JAR" de.meiapps.meicraft.UpdateTests
java -cp "build/test-classes:$QR_JAR" de.meiapps.meicraft.StoryTests
java -cp "build/test-classes:$QR_JAR" de.meiapps.meicraft.SavedWorldTests

java -cp "build/test-classes:$QR_JAR" de.meiapps.meicraft.SurvivalTests


java -cp "build/test-classes:$QR_JAR" de.meiapps.meicraft.NatureTests

java -cp "build/test-classes:$QR_JAR" de.meiapps.meicraft.WorldTransferTests

java -cp "build/test-classes:$QR_JAR" de.meiapps.meicraft.QrCameraFrameTests

java -cp "build/test-classes:$QR_JAR" de.meiapps.meicraft.ForgeTests

java -cp "build/test-classes:$QR_JAR" de.meiapps.meicraft.OnlineWorldTests

java -cp "build/test-classes:$QR_JAR" de.meiapps.meicraft.OnlineResourceTests
