---
name: project-manager
summary: Claim, hand off and review work in the My Health repo so several AI tools can co-operate safely.
---

# Skill: project-manager

Wraps the house rules in `AGENTS.md` for repos that use `AGENTS.md` + `TASKS.md` + `HANDOFF.md`.

## Commands

### what's pending
```bash
awk '/^## Pending/,/^## Done/' TASKS.md | grep '^- \[ \]'
```
Prints open work with ids, titles and priorities.

### claim next task
1. Pick the highest-priority `[ ]` task (or the id you were given).
2. Edit `TASKS.md`: move the line under `## In progress` and rewrite it as
   `- [~] T-0xx Title (claimed by: <agent>, <current ISO-8601 UTC>)`.
3. `git add TASKS.md && git commit -m "T-0xx: claim"` — **before** writing code.
4. Create your branch/worktree: `git worktree add ../myhealth-<agent> -b feat/<agent>-T-0xx`.

Safety: if the task is already `[~]` and the claim is younger than 24 h, choose another task.
If the claim is older than 24 h, re-claim it and record that in `HANDOFF.md`.

### hand off
1. Run the definition of done: `./gradlew :app:assembleDebug :app:testDebugUnitTest` (plus
   `:app:assembleDebugAndroidTest` when the change touches UI).
2. Set the task `[x]` (or `[!]` with a reason) in `TASKS.md`; add newly discovered work to `Pending`.
3. Append a dated section to `HANDOFF.md`: what changed, what's left, gotchas, files touched.
4. `git commit -m "T-0xx: …" && git push` — push the branch even when the task is only partly done,
   as long as the branch builds.

### review the last agent's work
```bash
git log --oneline -10
git diff HEAD~1 --stat
```
1. Confirm the claimed task exists in `TASKS.md` as `[~]`/`[x]` and matches the diff.
2. Verify the definition of done yourself (build + tests) — do not trust the note alone.
3. Check the invariants: no new hue in the palette, no hardcoded UI strings, all three language
   files updated, `.domain` stays pure Kotlin, no new annotation processors.
4. If something is wrong, open a `Pending` task describing the fix instead of silently patching.

## Non-negotiables (from AGENTS.md)
- One agent per checkout; never two builds at once.
- Never commit to the default branch; never commit secrets or signing material.
- Health data only ever leaves the device through an explicit user-initiated share/export.
