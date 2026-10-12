# GASLIGHT — experimental Java development

Current checkpoint: **NO-GO for playable GASLIGHT**. Java first, shared controller, separate loader adapters.

The new [Fabric 1.21.1 fixed-portal adapter](native-26.1.2/provider-adapters/fabric-1.21.1/README.md)
was genuinely built and tested from its packaged JAR in a graphical client and dedicated
server. See [the execution report](docs/PHASE_A_REPORT.md). This verifies fixed views,
interaction, traversal and cleanup; automatic look-away gameplay, recovery reconciliation
and loader ports remain unfinished.

See [actual status](docs/STATUS.md), [design and compatibility](docs/DESIGN.md), and [existing mathematical specification](docs/MATH.md).

`native-26.1.2/` is the genuine compiled Fabric data/acquisition implementation. This repository contains no saved worlds. `evidence/` contains raw commands/logs and source diffs. No unverified loader is claimed supported.

[Real portal provider proof and screenshots](docs/PROVIDER_PROOF.md): locally built and tested 26.3 Fabric fork. This is provider evidence, not completed GASLIGHT gameplay.
