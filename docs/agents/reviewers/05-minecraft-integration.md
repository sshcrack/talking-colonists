# Minecraft, MineColonies, and loader integration reviewer

Read `docs/agents/reviewers/COMMON.md` and follow it with fixed point
`{{FIXED_POINT}}`.

Review changes that touch Minecraft/MineColonies behavior, mixins/duck interfaces,
platform implementations, registries/resources, networking, or Stonecutter branches.
The supported targets are `1.21.1-neoforge` and `1.20.1-forge`.

## Invariants to stress

- Loader-neutral/common code must not accidentally reference client-only or
  loader-specific classes on the wrong physical side.
- Every Stonecutter conditional has valid code on both supported branches; do not
  review only the active `.sc_active_version` view.
- MineColonies methods, fields, AI states, request/building types, and lifecycle
  assumptions must match the source for each supported version. Use
  `scripts/MINECOLONIES_DOCS.md` and the matching sources jars when uncertain.
- Mixins belong only under the mixin package, target the intended version-specific
  method/field descriptors, and preserve vanilla/MineColonies control flow. Accessors
  should not silently assume nullable/initialized state that target classes do not
  guarantee.
- Duck interfaces and injected state have safe initialization/serialization/lifetime
  semantics and cannot be read before the mixin establishes them.
- Event registration, payload/network registration, config/resources, access
  wideners/transformers, and item/command registration work on both loaders.
- Server code does not depend on a connected client or client-only singleton; client
  code tolerates joining/leaving worlds and server transitions.
- A change to runtime Java, API Java, resources, mixins, or loader/build wiring is
  launch-relevant and therefore requires the repository's client-smoke evidence
  before release; report missing/stale validation as a validation gap rather than
  pretending static review proves launch success.

When a changed helper maps MineColonies state into public prompt/API state, verify the
mapping against both versions instead of accepting similar enum/class names as proof
of semantic equivalence.
