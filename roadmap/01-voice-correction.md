# 01 — Correct deterministic voice selection

## Agent prompt

Implement this task using the shared instructions in [README.md](README.md).

Inspect `config/AvailableAI.java`. Correct `Archid` to `Achird` in both model lists
without changing list order, length, or UUID selection behavior. Confirm the selected
models' voice support against official documentation. Keep this fix independent of
the broader recovery work in task 10.

## Acceptance

- Both misspelled entries are corrected.
- A representative UUID previously selecting the entry now selects `Achird`.
- UUIDs selecting other entries retain their original voices in both model choices.
- Both loaders build. Record any model support uncertainty separately from the typo fix.
