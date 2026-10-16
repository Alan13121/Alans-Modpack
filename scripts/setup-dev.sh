#!/bin/sh
# Sets up the private dev files for a collaborator: clones Alans-Modpack-dev into dev/ (if missing) and links
# CLAUDE.md, docs and art from it to the checkout root. Needs read access to the private repository.
set -e
cd "$(dirname "$0")/.."

if [ ! -d dev/.git ]; then
    if command -v gh >/dev/null 2>&1; then
        # HTTPS with gh's login, so a broken or missing SSH setup does not matter.
        git -c credential.helper='!gh auth git-credential' clone https://github.com/Alan13121/Alans-Modpack-dev.git dev
        git -C dev config credential.helper '!gh auth git-credential'
    else
        git clone git@github.com:Alan13121/Alans-Modpack-dev.git dev
    fi
fi

for name in CLAUDE.md docs art; do
    if [ -L "$name" ]; then
        continue
    elif [ -e "$name" ]; then
        echo "skip $name: a real file or folder is already here; move it into dev/ first" >&2
    else
        ln -s "dev/$name" "$name"
        echo "linked $name -> dev/$name"
    fi
done
