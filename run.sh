#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

if ! command -v javac >/dev/null 2>&1; then
  echo "Erro: Java JDK (javac) não encontrado. Instale o JDK 8 ou superior (ex.: https://adoptium.net)."
  exit 1
fi

if [ ! -f out/Main.class ] || [ -n "$(find src -name '*.java' -newer out/Main.class)" ]; then
  echo "Compilando..."
  mkdir -p out
  javac -encoding UTF-8 -d out src/*.java
  touch out/Main.class
fi

exec java -Djava.net.preferIPv4Stack=true -cp out Main "$@"
