# Phase 1 build recipe and boundary

Use the checked-in wrapper; it pins Gradle **9.7.0** and verifies its distribution
SHA-256. Loom is pinned to stable **1.18.4**, replacing the moving snapshot.
Minecraft **26.1.2**, Fabric Loader **0.19.5** and Fabric API
**0.155.3+26.1.2** remain unchanged. Resolved production/test classpaths are
recorded in `gradle.lockfile`; `dependencyManifest` records artifact SHA-256s.
JUnit Jupiter **5.13.4** / platform launcher **1.13.4** are test-only dependencies.

The complete verified recipe uses Linux and Temurin **25+36** (Java 25). CI pins
the same JDK as `25.0.0+36`. Windows Temurin **25.0.4.1+1-LTS** also compiles
main, client and test source sets. Both target non-preview Java 25 bytecode.

```sh
export JAVA_HOME=/absolute/path/to/jdk-25
export PATH="$JAVA_HOME/bin:$PATH"
bash ./gradlew --no-daemon --max-workers=2 clean build dependencyManifest --stacktrace
bash scripts/check_jar.sh
bash run_offline_checks.sh
python3 scripts/phase4_mutation_probe.py
```

No generated Minecraft API stubs are used by this recipe. `check_jar.sh` verifies
both entrypoints, all 30 production source classes, Java 25 class versions,
metadata, absence of bundled Minecraft/Fabric classes and absence of test
classes. Eight packaging tests include seven injected artifact defects. The
standard Gradle `test` task now discovers ten JUnit adapters for the existing
standalone suites; all independent JavaExec check gates remain in place.

For a fresh rebuild, copy only source/configuration/wrapper/lock files to a new
directory, excluding `.gradle`, `build`, `run`, saves and logs, then run the same
recipe. Archive ordering/timestamps are fixed so both verified Linux builds
produce byte-identical main and sources JARs.

`scripts/smoke_server.sh` is for a disposable CI tree only. It creates a new test
world under `run/`, accepts the EULA there, disables GASLIGHT automatic operation
and remote generation, and supplies loopback/ephemeral-port server properties
when that test directory has none. It requires both the GASLIGHT entrypoint
marker and Minecraft READY. This is a development-classpath server smoke,
not a graphical or packaged-production-launcher gameplay test.

On Windows, `Phase3FinalTests` and `Phase4ValidationTests` fail at durable journal
writes because Java's directory fsync path throws `AccessDeniedException`.
That runtime durability requirement was not weakened or repaired in Phase 1.
Windows compile success is not a passing Windows regression gate.

Only Phase 1 build/configuration and appropriate verification were authorized.
The GPU renderer, remote streaming, camera/frame authority, atomic publication,
collision/interaction and crash reconciliation remain outside this phase.
Do not start Phase 2 until the user explicitly authorizes it. Do not call this
checkpoint playable or release-ready. Existing release gates and defaults stay
unchanged. `SHA256SUMS.txt` is the original handoff baseline manifest; the Phase 1
delivery includes a separate updated source inventory, diff and artifact hashes.
