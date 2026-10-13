# Fresh checkout verification, 2026-10-13

Working copy: a new local clone of the published GASLIGHT source.
No saved worlds or pre-existing build output were copied into it.

Commands from native-26.1.2/provider-adapters/fabric-1.21.1 on Windows 11,
with JAVA_HOME set to Eclipse Adoptium JDK 25.0.4.1:

    python setup_deps.py
    .\gradlew.bat clean build --no-daemon --max-workers=2 --console=plain

Both exited 0. See fresh-1.21.1-deps.log and fresh-1.21.1-build.log.
The provider SHA-256 was
07c95e7422c311b53c082a598dd533169d72fb26ee8385e6982cc7078c9f7979.
The wrapper was Gradle 9.7.0, hash pinned in its properties.

The fresh JUnit run contains 16 tests, zero failures/errors/skips:
CameraFenceTest 5, RecoveryAndFootprintTest 3, SqliteIntentStoreTest 8.
The actual production, integration and test source sets compiled. This
does not repeat the previous client/server gameplay run or certify
automatic GASLIGHT behavior.

Fresh normal JAR SHA-256:
0e764df9ea76a3f81a694a1d2b76d5a268430695e978e3cf16efad93ed9d3a8c.
Fresh named development JAR SHA-256:
8ff32c34eb06187f9100d5f76b27cbda59db3648794715f57b0bdb785d1e6111.
The previous packaged development JAR remains the one actually game-tested.
The fresh JARs have identical ZIP entry names and class content to that
artifact; only LICENSE and THIRD_PARTY_NOTICES.md entry contents differ.
No separate game test of the fresh JARs was performed.
