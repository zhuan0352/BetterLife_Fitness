#!/bin/bash
# Compiles and starts the Fitness Center Manager.
#   ./run.sh              start on http://localhost:8080
#   PORT=9000 ./run.sh    use another port
set -euo pipefail
cd "$(dirname "$0")"

DRIVER_VERSION=3.54.1.0
DRIVER="lib/sqlite-jdbc-$DRIVER_VERSION.jar"

# The SQLite JDBC driver is the only dependency; fetch it from Maven Central if it's missing.
if [ ! -f "$DRIVER" ]; then
    echo "Downloading SQLite JDBC driver $DRIVER_VERSION..."
    mkdir -p lib
    curl -fsSL -o "$DRIVER" \
        "https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/$DRIVER_VERSION/sqlite-jdbc-$DRIVER_VERSION.jar"
fi

mkdir -p out
javac -d out -cp "$DRIVER" src/gym/*.java
exec java --enable-native-access=ALL-UNNAMED -cp "out:$DRIVER" gym.Main
