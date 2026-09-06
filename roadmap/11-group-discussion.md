# 11 — Optional autonomous group discussion

Dependencies: 06, 07, 09.

## Agent prompt

Implement this task using the shared instructions in [README.md](README.md).

Build optional automatic turn selection on top of controlled sessions. Support a
small multi-citizen discussion with shared history and bounded turns, duration,
response length, and provider concurrency. Define fairness and prevent one speaker
from monopolizing turns or participants responding indefinitely to each other.
Use the same interruption and playback-completion semantics as controlled meetings.

Expose automatic selection as a policy a caller can opt into or pause. Meetings
must retain exclusive floor control unless it delegates that control explicitly.
Allow addons to supply family/shop/group context through prompt contributions;
core does not decide addon-specific attendance, schedules, or movement.

## Acceptance

- Three or more participants converse with correct speaker attribution and bounded
  resource use; no connection is required for every silent attendee.
- Tests cover turn limits, unavailable participants, player interruption, policy
  pause/resume, and manual floor ownership with no overlapping speakers.
- Existing two-citizen behavior remains usable and shares the lifecycle implementation.
- Document default limits and the caller's opt-in policy interface.
