#!/usr/bin/env bash
#
# push-to-github.sh
# ------------------
# Initialises a git repo (if missing), commits everything, creates a remote GitHub repo
# using `gh`, and pushes the first commit. Re-runnable: on subsequent invocations it just
# commits + pushes the diff.
#
# Usage:
#   ./scripts/push-to-github.sh <owner/repo> [commit message]
#
# Examples:
#   ./scripts/push-to-github.sh myuser/lumo-llm
#   ./scripts/push-to-github.sh myuser/lumo-llm "Add streaming notifications"
#
# Prerequisites:
#   - `gh` CLI installed:    https://cli.github.com/
#   - Authenticated once:   gh auth login
#   - `git` installed.
#
# Env:
#   GH_REPO_VISIBILITY  = "public" | "private"   (default: private)
#
set -euo pipefail

# ─── Args ────────────────────────────────────────────────────────────────────
REPO_FULL="${1:-}"
COMMIT_MSG="${2:-Update Lumo fork (LLM-agnostic, no WebView, no Proton login)}"

if [[ -z "$REPO_FULL" ]]; then
    echo "Usage: $0 <owner/repo> [commit message]" >&2
    exit 1
fi

OWNER="${REPO_FULL%%/*}"
REPO_NAME="${REPO_FULL#*/}"

if [[ -z "$OWNER" || -z "$REPO_NAME" || "$OWNER" == "$REPO_NAME" ]]; then
    echo "Invalid repo spec: '$REPO_FULL'. Expected 'owner/repo'." >&2
    exit 1
fi

VISIBILITY="${GH_REPO_VISIBILITY:-private}"

# ─── Pre-flight ─────────────────────────────────────────────────────────────
command -v git >/dev/null 2>&1 || { echo "git not installed"; exit 1; }

# Auto-install gh if missing on common distros.
if ! command -v gh >/dev/null 2>&1; then
    echo "gh CLI not found. Attempting to install…"
    if command -v apt-get >/dev/null 2>&1; then
        # Debian/Ubuntu (and derivatives)
        sudo mkdir -p -m 755 /etc/apt/keyrings \
            && wget -qO- https://cli.github.com/packages/githubcli-archive-keyring.gpg \
            | sudo tee /etc/apt/keyrings/githubcli-archive-keyring.gpg > /dev/null \
            && sudo chmod go+r /etc/apt/keyrings/githubcli-archive-keyring.gpg \
            && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" \
            | sudo tee /etc/apt/sources.list.d/github-cli.list > /dev/null \
            && sudo apt-get update -qq \
            && sudo apt-get install -y gh
    elif command -v dnf >/dev/null 2>&1; then
        sudo dnf install -y gh
    elif command -v brew >/dev/null 2>&1; then
        brew install gh
    else
        echo "Could not auto-install gh. Install manually: https://cli.github.com/" >&2
        exit 1
    fi
fi

command -v gh >/dev/null 2>&1 || {
    echo "gh CLI installation failed. Install manually from https://cli.github.com/" >&2
    exit 1
}

# ─── gh auth check ──────────────────────────────────────────────────────────
if ! gh auth status >/dev/null 2>&1; then
    echo "gh is not authenticated. Starting login flow…"
    echo "  → Browser will open, paste the one-time code."
    gh auth login --git-protocol https --web
fi

# Re-check after attempting login.
if ! gh auth status >/dev/null 2>&1; then
    echo "Still not authenticated. Aborting." >&2
    exit 1
fi

echo "✓ gh authenticated as: $(gh api user --jq '.login')"

# ─── Init / verify git repo ─────────────────────────────────────────────────
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ ! -d .git ]]; then
    git init -q -b main
    echo "✓ Initialised git repo at $ROOT"
fi

# Ensure user identity is set (otherwise commit fails).
if ! git config user.email >/dev/null; then
    git config user.email "$(gh api user --jq '.login' || echo dev)@users.noreply.github.com"
    git config user.name  "$(gh api user --jq '.name // .login' || echo dev)"
    echo "✓ Configured git identity: $(git config user.name) <$(git config user.email)>"
fi

# Make sure gradlew is executable (preserved across zip downloads etc).
chmod +x gradlew

# ─── .gitignore sanity ──────────────────────────────────────────────────────
if ! grep -q "^\.gradle$" .gitignore 2>/dev/null; then
    cat >> .gitignore <<'EOF'

# Build outputs / caches
.gradle/
build/
**/build/
local.properties
*.iml
.idea/
captures/
.cxx/
EOF
    echo "✓ Appended build-outputs entries to .gitignore"
fi

# ─── Stage and commit ───────────────────────────────────────────────────────
git add -A

if git diff --cached --quiet; then
    echo "Nothing to commit — working tree clean."
else
    git commit -q -m "$COMMIT_MSG"
    echo "✓ Committed: $COMMIT_MSG"
    git --no-pager log --oneline -1
fi

# ─── Remote / repo creation ─────────────────────────────────────────────────
REMOTE_URL="https://github.com/$REPO_FULL.git"

if ! git remote get-url origin >/dev/null 2>&1; then
    echo "Creating GitHub repo '$REPO_FULL' ($VISIBILITY)…"
    gh repo create "$REPO_FULL" "--$VISIBILITY" \
        --description "Lumo (Proton fork) — decoupled from Proton, native Compose chat, configurable LLM, persistent chats" \
        --source=. --remote=origin --push=false
    echo "✓ Created: https://github.com/$REPO_FULL"
else
    git remote set-url origin "$REMOTE_URL"
    echo "✓ Remote 'origin' set to $REMOTE_URL"
fi

# ─── Push ───────────────────────────────────────────────────────────────────
BRANCH="$(git symbolic-ref --short HEAD 2>/dev/null || echo main)"
echo "Pushing '$BRANCH' to origin…"
if git ls-remote --exit-code --heads origin "$BRANCH" >/dev/null 2>&1; then
    git pull --rebase --autostash origin "$BRANCH" || true
fi
git push -u origin "$BRANCH"

echo ""
echo "✅ Done."
echo "   Repo:     https://github.com/$REPO_FULL"
echo "   Branch:   $BRANCH"
echo "   Actions:  https://github.com/$REPO_FULL/actions"
echo ""
echo "The 'Build APK' workflow will start automatically and produce a debug APK you can download"
echo "from the Actions → run → Artifacts section."
