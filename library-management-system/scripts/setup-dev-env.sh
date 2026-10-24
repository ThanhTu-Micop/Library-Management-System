#!/usr/bin/env bash
# Repeatable development-environment bootstrap for the Library Management System.
# Installs JDK 21, Maven, and PostgreSQL 17 portable binaries into ~/tools (no root required),
# creates the application database, and starts PostgreSQL on port 5432.
# Used to produce the verified environment documented in docs/phase1-plan.md.
set -euo pipefail

TOOLS="$HOME/tools"
mkdir -p "$TOOLS"

echo "== JDK 21 =="
if [ ! -x "$TOOLS/jdk/bin/java" ]; then
  curl -sSL --max-time 240 "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse?project=jdk" \
    -o "$TOOLS/jdk21.tar.gz"
  mkdir -p "$TOOLS/jdk"
  tar xzf "$TOOLS/jdk21.tar.gz" -C "$TOOLS/jdk" --strip-components=1
fi
"$TOOLS/jdk/bin/java" -version

echo "== Maven =="
if [ ! -x "$TOOLS/maven/bin/mvn" ]; then
  curl -sSL --max-time 120 "https://dlcdn.apache.org/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.tar.gz" \
    -o "$TOOLS/maven.tar.gz"
  tar xzf "$TOOLS/maven.tar.gz" -C "$TOOLS"
  mv "$TOOLS"/apache-maven-* "$TOOLS/maven"
fi
JAVA_HOME="$TOOLS/jdk" PATH="$TOOLS/jdk/bin:$PATH" "$TOOLS/maven/bin/mvn" -version | head -1

echo "== PostgreSQL 17 portable binaries (from Maven Central, io.zonky.test.postgres) =="
if [ ! -x "$TOOLS/pg17/bin/initdb" ]; then
  curl -sSL --max-time 180 \
    "https://repo.maven.apache.org/maven2/io/zonky/test/postgres/embedded-postgres-binaries-linux-amd64/17.11.0/embedded-postgres-binaries-linux-amd64-17.11.0.jar" \
    -o "$TOOLS/pg17.jar"
  mkdir -p "$TOOLS/pg17"
  (cd "$TOOLS/pg17" && unzip -o -q "$TOOLS/pg17.jar" postgres-linux-x86_64.txz \
    && tar xJf postgres-linux-x86_64.txz && rm postgres-linux-x86_64.txz)
fi

echo "== Start PostgreSQL cluster =="
if ! "$TOOLS/pg17/bin/pg_ctl" -D "$TOOLS/pgdata" status >/dev/null 2>&1; then
  if [ ! -f "$TOOLS/pgdata/PG_VERSION" ]; then
    "$TOOLS/pg17/bin/initdb" -D "$TOOLS/pgdata" -U postgres --auth=trust -E UTF8
  fi
  "$TOOLS/pg17/bin/pg_ctl" -D "$TOOLS/pgdata" -l /tmp/pg.log -o "-p 5432 -k /tmp" start
  sleep 2
fi
"$TOOLS/pg17/bin/pg_ctl" -D "$TOOLS/pgdata" status

echo "== Create application database (idempotent) =="
cat > /tmp/LibDbCreate.java <<'EOF'
import java.sql.*;
public class LibDbCreate {
  public static void main(String[] a) throws Exception {
    try (Connection c = DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres","postgres","");
         Statement s = c.createStatement()) {
      try { s.execute("CREATE DATABASE library_db"); System.out.println("library_db created"); }
      catch (SQLException e) { System.out.println("library_db: " + e.getMessage()); }
    }
  }
}
EOF
JAVA_HOME="$TOOLS/jdk" "$TOOLS/jdk/bin/javac" -d /tmp /tmp/LibDbCreate.java
JAVA_HOME="$TOOLS/jdk" "$TOOLS/jdk/bin/java" -cp /tmp "$(find "$HOME/.m2" -name 'postgresql-*.jar' 2>/dev/null | head -1 || true)" LibDbCreate 2>/dev/null || { mkdir -p "$TOOLS/drivers"; curl -sSL --max-time 30 "https://repo.maven.apache.org/maven2/org/postgresql/postgresql/42.7.6/postgresql-42.7.6.jar" -o "$TOOLS/drivers/pgjdbc.jar"; "$TOOLS/jdk/bin/java" -cp "/tmp:$TOOLS/drivers/pgjdbc.jar" LibDbCreate; }

cat <<'NOTE'

Environment ready. For shells:
  export JAVA_HOME=$HOME/tools/jdk
  export PATH=$JAVA_HOME/bin:$HOME/tools/maven/bin:$HOME/tools/pg17/bin:$PATH

Database (matches scripts in docs):
  DB_URL=jdbc:postgresql://localhost:5432/library_db
  DB_USER=postgres
  DB_PASSWORD=
NOTE
