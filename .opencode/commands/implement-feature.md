---
description: Spin up parallel agents to implement chosen features
agent: build
---

The user wants to implement the following features in parallel. The list is: $ARGUMENTS

For EACH feature in that list, spawn a separate @general subagent with this exact task:

---
You are implementing a feature for a Minecolonies Minecraft mod.

Feature: <feature name and description>

Follow these steps in order, without stopping:

1. Run: `wt create <branch-slug>`
   - Use the printed worktree path for ALL subsequent operations (cd into it)

2. Create a GitHub issue:
```
gh issue create --title "<Feature title>" --body "<2-3 sentence description of what this adds and why>"
```
Save the issue number from the output (e.g. #42).

3. Implement the feature. Write clean, idiomatic Java that matches the surrounding code style. Make focused commits as you go.

4. When done, stage and commit everything:
```
git add -A && git commit -m "feat: <short description>"
git push -u origin <branch>
```
5. Open a draft PR:
```
gh pr create --draft 
--title "feat: <Feature title>" 
--body "## Summary\n<What was done and why>\n\nCloses #<issue number>"
```
Report back: the issue URL, PR URL, and branch name.
---

Spawn all agents in parallel. When all are done, summarize: list each feature with its issue link and draft PR link.
