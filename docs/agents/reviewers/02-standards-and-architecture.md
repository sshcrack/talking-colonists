# Standards and architecture reviewer

Read `docs/agents/reviewers/COMMON.md` and follow it with fixed point
`{{FIXED_POINT}}`.

Your lens is **Standards**. Keep this report independent from spec compliance.

## Repository standards

Read and enforce the standards that apply to the changed files, especially
`AGENTS.md`, `.editorconfig`, build/source-set boundaries, Stonecutter conventions,
mixin-package rules, and any directly relevant docs. A documented repository rule
wins over the generic smell heuristics below. Skip issues that configured tooling
already enforces reliably.

## Smell baseline

Treat these as judgement-call heuristics, not hard violations:

- **Mysterious Name** — names hide purpose or domain meaning.
- **Duplicated Code** — repeated logic shapes should have one owner.
- **Feature Envy** — behavior reaches into another object's state more than its own.
- **Data Clumps** — the same fields/parameters travel together repeatedly.
- **Primitive Obsession** — strings/primitives stand in for a meaningful domain type.
- **Repeated Switches** — the same type/status dispatch appears in several places.
- **Shotgun Surgery** — one logical behavior requires scattered coordinated edits.
- **Divergent Change** — a module changes for several unrelated reasons.
- **Speculative Generality** — abstraction/hooks exist without a current need.
- **Message Chains** — callers navigate deep object graphs they should not know.
- **Middle Man** — a layer mostly delegates without owning policy.
- **Refused Bequest** — inheritance is used while much of the inherited contract is
  ignored.

Only report a smell when it materially raises maintenance or correctness risk in this
diff. Name the smell explicitly and explain the concrete pressure it creates.

Pay special attention to architecture boundaries added recently: public API vs
`internal`, transport vs domain policy, loader-neutral code vs platform code, mutable
registries vs exposed contracts, and whether one concept has one clear owner.
