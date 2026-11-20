#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${TELENO_ANDROID_NDK:?Set TELENO_ANDROID_NDK to the r27d directory}"
: "${TELENO_ECJ:?Set TELENO_ECJ to the ECJ 3.40.0 jar}"
: "${TELENO_JSON_JAR:?Set TELENO_JSON_JAR to org.json json-20240303.jar (host tests only)}"
python3 - "$TELENO_JSON_JAR" <<'PY'
import hashlib,sys
assert hashlib.sha256(open(sys.argv[1],'rb').read()).hexdigest() == '3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed', 'Wrong host JSON dependency'
PY
[[ "$(uname -s)" == Linux ]] || { echo 'These supervisor tests require Linux.' >&2; exit 1; }
TEST_DIR="$ROOT/build/host-tests"
mkdir -p "$TEST_DIR/include" "$TEST_DIR/classes"
cp "$TELENO_ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include/jni.h" "$TEST_DIR/include/"
cc -Wall -Wextra -Werror -shared -fPIC -I"$TEST_DIR/include" "$ROOT/native/bridge/keys.c" "$ROOT/native/bridge/files.c" -lcrypto -o "$TEST_DIR/libteleno_keys.so"
cc -Wall -Wextra -Werror "$ROOT/native/bridge/runner.c" -o "$TEST_DIR/teleno_runner"
java -jar "$TELENO_ECJ" -17 -nowarn -cp "$TELENO_JSON_JAR" -d "$TEST_DIR/classes" \
  "$ROOT/app/src/main/java/com/koinos/teleno/Readiness.java" \
  "$ROOT/app/src/main/java/com/koinos/teleno/Base58.java" \
  "$ROOT/app/src/main/java/com/koinos/teleno/Proto.java" \
  "$ROOT/app/src/main/java/com/koinos/teleno/NativeKeys.java" \
  "$ROOT/app/src/main/java/com/koinos/teleno/NativeFiles.java" \
  "$ROOT/app/src/main/java/com/koinos/teleno/StorageFiles.java" \
  "$ROOT/app/src/main/java/com/koinos/teleno/StorageLinks.java" \
  "$ROOT/app/src/main/java/com/koinos/teleno/VaultClient.java" \
  "$ROOT/app/src/main/java/com/koinos/teleno/ProductionPermission.java" \
  "$ROOT/app/src/main/java/com/koinos/teleno/BurnKoin.java" \
  "$ROOT/app/src/main/java/com/koinos/teleno/RecoveryPolicy.java" \
  "$ROOT/app/src/main/java/com/koinos/teleno/SnapshotPlan.java" \
  "$ROOT/app/src/main/java/com/koinos/teleno/SnapshotDownload.java" \
  "$ROOT/app/src/main/java/com/koinos/teleno/SnapshotSource.java" \
  "$ROOT/app/src/main/java/com/koinos/teleno/SnapshotSetup.java" \
  "$ROOT/app/src/main/java/com/koinos/teleno/ProductionReview.java" \
  "$ROOT/app/src/main/java/com/koinos/teleno/ProductionStatus.java" \
  "$ROOT/app/src/main/java/com/koinos/teleno/EngineLog.java" \
  "$ROOT/app/src/main/java/com/koinos/teleno/WalletReader.java" \
  "$ROOT/app/src/main/java/com/koinos/teleno/ProductionSetup.java" \
  "$ROOT/app/src/main/java/com/koinos/teleno/StartupPorts.java" \
  "$ROOT/tests/java/com/koinos/teleno/CoreTests.java" \
  "$ROOT/tests/java/com/koinos/teleno/StorageTests.java" \
  "$ROOT/tests/java/com/koinos/teleno/VaultTests.java" \
  "$ROOT/tests/java/com/koinos/teleno/PermissionTests.java" \
  "$ROOT/tests/java/com/koinos/teleno/SnapshotTests.java" \
  "$ROOT/tests/java/com/koinos/teleno/SnapshotRetryTests.java" \
  "$ROOT/tests/java/com/koinos/teleno/SnapshotSetupTests.java" \
  "$ROOT/tests/java/com/koinos/teleno/ProductionTests.java" \
  "$ROOT/tests/java/com/koinos/teleno/EngineLogTests.java" \
  "$ROOT/tests/java/com/koinos/teleno/DashboardTests.java" \
  "$ROOT/tests/java/com/koinos/teleno/ProductionSetupTests.java" \
  "$ROOT/tests/java/com/koinos/teleno/StartupPortsTests.java" \
  "$ROOT/tests/java/com/koinos/teleno/BurnRecoveryTests.java"
java -Djava.library.path="$TEST_DIR" -cp "$TEST_DIR/classes" com.koinos.teleno.CoreTests
java -Djava.library.path="$TEST_DIR" -cp "$TEST_DIR/classes" com.koinos.teleno.StorageTests "$TEST_DIR/teleno_runner"
java -cp "$TEST_DIR/classes:$TELENO_JSON_JAR" com.koinos.teleno.VaultTests
java -cp "$TEST_DIR/classes:$TELENO_JSON_JAR" com.koinos.teleno.PermissionTests "$TEST_DIR/permission-vectors.json"
java -cp "$TEST_DIR/classes:$TELENO_JSON_JAR" com.koinos.teleno.SnapshotTests "$ROOT/tests/fixtures"
java -cp "$TEST_DIR/classes:$TELENO_JSON_JAR" com.koinos.teleno.SnapshotRetryTests
java -cp "$TEST_DIR/classes" com.koinos.teleno.SnapshotSetupTests
java -cp "$TEST_DIR/classes" com.koinos.teleno.ProductionTests
java -cp "$TEST_DIR/classes" com.koinos.teleno.EngineLogTests
java -cp "$TEST_DIR/classes:$TELENO_JSON_JAR" com.koinos.teleno.DashboardTests
java -cp "$TEST_DIR/classes" com.koinos.teleno.ProductionSetupTests
java -cp "$TEST_DIR/classes:$TELENO_JSON_JAR" com.koinos.teleno.BurnRecoveryTests "$TEST_DIR/burn-vectors.json"
java -cp "$TEST_DIR/classes" com.koinos.teleno.StartupPortsTests
c++ -std=c++17 -Wall -Wextra -Werror "$ROOT/tests/pipe_key_reader.cpp" -o "$TEST_DIR/pipe_key_reader"
python3 "$ROOT/tests/test_runner.py" "$TEST_DIR/teleno_runner" "$TEST_DIR/pipe_key_reader"
