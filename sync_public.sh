#!/bin/bash
# R47 Robust Public Sync (Surgical Mode)
set -e
echo "--- 🔄 Starting Robust Public Sync ---"

UPSTREAM_URL="https://gitlab.com/rpncalculators/c43.git"

# 1. Setup & Fetch from SwissMicros (Math Core)
if ! git remote | grep -q "upstream"; then
    echo "--- Adding Upstream (SwissMicros C43) ---"
    git remote add upstream "$UPSTREAM_URL"
fi
git fetch upstream

echo "--- Overlaying Math Core ---"
# checkout upstream core into working tree
git checkout upstream/master -- . 2>/dev/null || true

# 2. SURGICAL RESTORE ANDROID PORT (The Winner)
# We restore only repo-owned Android and build files from HEAD
# to avoid 'git checkout dir' deleting files in that directory.
echo "--- Re-applying Repo-Owned Android and Build Files ---"

# Root Infrastructure
git checkout HEAD -- .gitignore README.md Makefile build_android.sh sync_public.sh dist.sh meson.build meson_options.txt 2>/dev/null || true

# Local workflow tree
git checkout HEAD -- .github/ 2>/dev/null || true

# Leave the upstream core authoritative; restore only repo-owned scaffolding.

# Blueprint Preservation
while IFS= read -r meson_file; do
    git checkout HEAD -- "$meson_file"
done < <(git ls-files | grep -E '(^|/)meson\.build$' | grep -Ev '^src/')

# Android Project Folder (This one we can restore safely as it's not in upstream)
git checkout HEAD -- android/ 2>/dev/null || true

echo "--- ✅ SYNC COMPLETE: Core updated, repo-owned files restored. ---"
