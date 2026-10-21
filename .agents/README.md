# Canonical Agent Guidance

This file is the common source of project-level guidance for Codex, Claude Code, and other coding agents. Use the existing specialized skills in `.agents/skills/` and `.claude/skills/` when applicable, without duplicating them.

## Task Preparation

1. Inspect the current `main` branch and the smallest relevant source/tests.
2. Read the root `README.md`, relevant `docs/` and accepted ADRs.
3. Identify a narrowly scoped, testable implementation and its acceptance criteria.
4. Preserve Faro's privacy, LOCAL_ONLY/provider-independence architecture, and supply-chain security constraints.

## Pull Request Scope Guard

Plan for at most **30 changed files** per PR. The **absolute maximum is 40**. Count all files, including source code, tests, docs, configuration, renamed/deleted paths, generated outputs, and migrations. The `PR file count` GitHub Actions check is the authoritative enforcement boundary.

- **0–30:** proceed.
- **31–40:** proceed with a warning; recheck scope before publishing.
- **41+:** stop. **Consult the repository owner** and propose splitting the work into two or more coherent, ordered PRs with explicit dependencies and acceptance criteria. Do not implement or publish an over-limit PR as a workaround.
- Keep each split independently reviewable, buildable/testable, and secure. Never drop required tests/documentation, weaken privacy/security checks, or publish broken intermediate states merely to fit the count.
- Estimate the number of affected paths before implementing. Re-run the manual preflight during development and before publishing. Repository-scoped `PreToolUse` hooks also check recognized shell publication commands; they do **not** replace CI.
- Reference: [PR size policy and setup](../docs/verification/pr-size-guard.md).

## Verification and Publication

Run the narrowest meaningful checks for the touched scope, report actual results, and avoid unnecessary duplicate runs. For this repository, preserve Node/npm installation safety, Java/Gradle build verification, and data-minimization constraints. A failed or unavailable check must be reported, not treated as a pass.

Before any `git push` or `gh pr create`, run `node .agents/scripts/pr-size-preflight.mjs` if hook support is unavailable. If the local `origin/main` reference is missing, fetch it rather than overriding the guard. A failing PR check cannot be waived from agent configuration.

When creating a PR, link the corresponding Issue, summarize scope, verification and risks, and do not imply branch rulesets have been enabled unless they were verified.
