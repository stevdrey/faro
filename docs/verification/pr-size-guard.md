# Pull Request Size Guard

Faro favors small, independently reviewable pull requests. The
[Pull Request Size Guard](../../.github/workflows/pr-size-guard.yml) workflow
checks every pull request targeting `main`, including drafts and documentation-only
changes, without checking out or executing the proposed source code.

## Policy

| Changed files in the PR | Result |
| --- | --- |
| 0–30 | Pass |
| 31–40 | Pass with a warning |
| 41 or more | Fail: consult the owner and split before merging |

GitHub's `pulls.get().data.changed_files` metadata is authoritative. It counts
added, modified, deleted, renamed, test, documentation, configuration and generated
files. Exactly **40** is allowed; **41** is not. The workflow uses a read-only
`pull-requests: read` token with no source checkout.

**Before implementation**, estimate scope. If work is likely to require more
than 40 files, **stop and consult the repository owner**. Propose two or more
logically coherent PRs with their intended file scopes, ordering/dependencies
and independently verifiable acceptance criteria. Agree on the split first.

Do not remove essential tests or documentation, weaken security/privacy controls,
or leave an unbuildable intermediate state to meet the limit.

## Agent preflight behavior

The local Node.js script at `.agents/scripts/pr-size-preflight.mjs` checks
the union of committed, staged, unstaged, and non-ignored untracked paths
against the merge base of `origin/main`; it counts each path once. Run:

```bash
git fetch origin main
node .agents/scripts/pr-size-preflight.mjs
node --test .agents/scripts/pr-size-preflight.test.mjs
```

For an intentional stacked PR, fetch its target branch and use
`PR_PREFLIGHT_BASE=origin/target-branch` or `--base origin/target-branch`.
Missing base refs or malformed hook payloads fail closed. The local count
includes uncommitted work and is a pre-publication estimate, **not** a
substitute for the GitHub PR count.

Claude Code uses `.claude/settings.json` and Codex uses `.codex/hooks.json`
to run the shared script as a `PreToolUse` hook on the Bash tool.
Recognized `git push` and `gh pr create` commands trigger the check.
Other shell commands do not automatically run it: agents should invoke
the manual preflight throughout implementation. Hook execution depends on
the installed client version, project trust, and hook enablement; see the
root README.

## Enable required enforcement on `main`

**Repository administrator action required:** a failing workflow does not
block merging until its job is configured as a required status check.

1. Merge the workflow PR and confirm that `Pull Request Size Guard / PR file count` has run on at least one PR.
2. Open **Settings → Rules → Rulesets → New ruleset → New branch ruleset**.
3. Name it **Require PR size guard**, set enforcement to **Active**, and target `main`.
4. Require a pull request before merging, unless another active rule already does.
5. Require status checks to pass, selecting **PR file count** from **GitHub Actions**.
6. Save, and avoid routine bypass actors if the rule must apply to all collaborators.
7. Verify that 40-file PRs can merge with respect to this check, but 41-file PRs cannot.

If the check is not listed, let it run on a PR targeting `main` and refresh
the ruleset editor. Use the **job name** `PR file count` (not only the
workflow name). Do not rename it after making it required.

GitHub Actions evaluates PRs after opening/updating them. The rule blocks
**merging**, not creating the PR. Warnings at 31–40 files are non-blocking.
Without an Active ruleset, this guard reports failure but is not a hard merge gate.

## Troubleshooting

- **Missing check:** ensure the PR targets `main`, Actions are enabled,
  and the workflow has run.
- **Unexpected count:** compare with the PR **Files changed** tab;
  non-source files also count.
- **Cannot compute count:** the check fails closed.
- **Local hook not running:** verify client trust and hook support and run
  the manual preflight; CI remains authoritative.
