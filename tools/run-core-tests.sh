#!/usr/bin/env bash
# Compiles and runs the pure-JVM core modules' unit tests WITHOUT Gradle or network access,
# using the Kotlin compiler, kotlinx.serialization and JUnit jars bundled in a Gradle install.
# The normal path is `./gradlew :core:model:test :core:storage:test`; this exists for
# sandboxed CI/agents that cannot reach Maven repositories.
#
# Usage: tools/run-core-tests.sh [GRADLE_HOME]   (default: /opt/gradle-8.14.3)
set -euo pipefail
cd "$(dirname "$0")/.."
G="${1:-/opt/gradle-8.14.3}/lib"
jar() { { ls "$G"/$1 "$G"/plugins/$1 2>/dev/null || true; } | head -1; }

KOTLINC_CP="$(jar 'kotlin-compiler-embeddable-*.jar'):$(jar 'kotlin-stdlib-2*.jar'):$(jar 'kotlin-script-runtime-*.jar'):$(jar 'kotlin-reflect-*.jar'):$(jar 'kotlin-daemon-embeddable-*.jar'):$(jar 'kotlinx-coroutines-core-jvm-*.jar'):$(jar 'trove4j-*.jar'):$(jar 'annotations-*.jar')"
RT="$(jar 'kotlin-stdlib-2*.jar'):$(jar 'kotlinx-serialization-core-jvm-*.jar'):$(jar 'kotlinx-serialization-json-jvm-*.jar')"
TEST_RT="$RT:$(jar 'junit-4*.jar'):$(jar 'hamcrest-core-*.jar')"

OUT=build/core-tests
rm -rf "$OUT" && mkdir -p "$OUT"/{main,test}
kc() {
  set +e
  java -cp "$KOTLINC_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 "$@" 2>&1 | grep -v '^Picked up'
  local rc=${PIPESTATUS[0]}; set -e
  [ "$rc" -eq 0 ] || { echo "Compilation failed"; exit "$rc"; }
}

echo "== compiling main sources"
kc -cp "$RT" -d "$OUT/main" $(find core/*/src/main -name '*.kt')
echo "== compiling test sources"
kc -cp "$TEST_RT:$OUT/main" -d "$OUT/test" $(find core/*/src/test -name '*.kt')

CLASSES=$(cd "$OUT/test" && find . -name '*Test.class' | sed 's|^\./||; s|\.class$||; s|/|.|g')
echo "== running: $CLASSES"
set +e
java -cp "$TEST_RT:$OUT/main:$OUT/test" org.junit.runner.JUnitCore $CLASSES 2>&1 | grep -v '^Picked up'
exit "${PIPESTATUS[0]}"
