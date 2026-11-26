#!/usr/bin/env bash
# Install piconnect-tmux.sh into ~/.local/bin and add (or replace) its section in ~/.bashrc.
# Uses the files next to this script, or downloads them from GitHub when run as:
#   curl -fsSL https://raw.githubusercontent.com/r4d01/piconnect-app/main/extras/piconnect-tmux-install.sh | bash

set -euo pipefail

REPO_RAW="https://raw.githubusercontent.com/r4d01/piconnect-app/main/extras"
BIN="${HOME}/.local/bin/piconnect-tmux.sh"
RC="${HOME}/.bashrc"
BACKUP="${HOME}/.bashrc.piconnect-tmux.bak"
START="# piconnect section start"
END="# piconnect section end"

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

# download URL FILE
download() {
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL "$1" -o "$2"
    elif command -v wget >/dev/null 2>&1; then
        wget -qO "$2" "$1"
    else
        echo "Neither curl nor wget is installed." >&2
        exit 1
    fi
}

# Find the source files: next to this script, or from GitHub
src_dir=""
if [ -n "${BASH_SOURCE[0]:-}" ] && [ -f "${BASH_SOURCE[0]}" ]; then
    src_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
fi
if [ -z "${src_dir}" ] || [ ! -f "${src_dir}/piconnect-tmux.sh" ] || [ ! -f "${src_dir}/piconnect-tmux.bashrc" ]; then
    echo "Downloading from ${REPO_RAW}"
    src_dir="${tmp_dir}/src"
    mkdir -p "${src_dir}"
    download "${REPO_RAW}/piconnect-tmux.sh" "${src_dir}/piconnect-tmux.sh"
    download "${REPO_RAW}/piconnect-tmux.bashrc" "${src_dir}/piconnect-tmux.bashrc"
fi
snippet="${src_dir}/piconnect-tmux.bashrc"
[ -s "${snippet}" ] || { echo "${snippet} is empty." >&2; exit 1; }

# Install the script
install -D -m 755 "${src_dir}/piconnect-tmux.sh" "${BIN}"
echo "Installed ${BIN}"

# Add or replace the section in .bashrc
touch "${RC}"
cp "${RC}" "${BACKUP}"
new_rc="${tmp_dir}/bashrc"

if grep -qxF "${START}" "${RC}"; then
    # Replace every marked section's body with the snippet (keeping only the first section)
    if ! awk -v start="${START}" -v end="${END}" '
        NR == FNR { body = body $0 "\n"; next }
        $0 == start {
            if (!done) printf "%s\n%s%s\n", start, body, end
            done = 1; skip = 1; next
        }
        skip && $0 == end { skip = 0; next }
        skip { next }
        { print }
        END { if (skip) exit 2 }
    ' "${snippet}" "${RC}" >"${new_rc}"; then
        echo "${RC} has '${START}' without a matching '${END}'; fix it by hand. Nothing changed." >&2
        exit 1
    fi
    action="Replaced"
else
    { cat "${RC}"; printf '\n%s\n' "${START}"; awk 1 "${snippet}"; printf '%s\n' "${END}"; } >"${new_rc}"
    action="Added"
fi

# Overwrite in place to keep the file's permissions and any symlink
cat "${new_rc}" >"${RC}"
echo "${action} the piconnect section in ${RC} (backup: ${BACKUP})"
echo "It takes effect in new Raspberry Pi Connect remote shells."