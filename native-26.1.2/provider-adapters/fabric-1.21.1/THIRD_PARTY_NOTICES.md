# Third-party notices

GASLIGHT's adapter and reused GASLIGHT geometry, structure and journal classes retain
their existing MIT license. See LICENSE. No WorldEdit code is included.

Immersive Portals 6.0.6 is an unmodified, separately installed Apache-2.0 dependency.
Source: https://github.com/iPortalTeam/ImmersivePortalsMod/tree/1.21
Inspected source commit: 2b1d3546aa60c7d866adfcb7236fba136a8433cb.
This is the inspected branch commit, not a claim that the binary was rebuilt from it.
The published binary and nested dependencies are pinned in provider.lock.json.
Its renderer, world synchronization, traversal, collision and interaction are reused
through its public APIs. Their authors retain their copyright and attribution.

The provider embeds DimLib, Cloth Config and MidnightLib with their original metadata
and license files. Development builds extract these unchanged nested JARs because Loom
does not discover them transitively in this setup. Do not separately add these extracted
development copies to a normal modpack; the provider already embeds them.

Minecraft, Fabric Loader, Fabric API, Gradle and JDK dependencies remain their respective
authors' work. No Minecraft game binary or assets are redistributed in this package.
