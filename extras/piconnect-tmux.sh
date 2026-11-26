#!/usr/bin/env bash
# Offer to attach to a tmux session, then offer to log out when it ends.
# Usage: piconnect-tmux.sh [session-name] [seconds]
# Exit status: 0 = caller should log out, 1 = stay in the shell

TMUX_SESSION="${1:-"piconn"}"
WAIT_SECONDS="${2:-3}"

# countdown MESSAGE
# Returns 0 if the countdown finishes, or 1 if a key was pressed to cancel.
countdown() {
    local i
    for ((i = WAIT_SECONDS; i > 0; i--)); do
        printf '\r%s in %d... (press any key to cancel) ' "${1}" "${i}"
        if read -rsn1 -t 1; then
            # Discard leftover bytes from keys like arrows that send several characters
            while read -rsn1 -t 0.01; do :; done
            printf '\r\033[K'
            return 1
        fi
    done
    printf '\r\033[K'
    return 0
}

# Only run in Raspberry Pi Connect remote shells, outside tmux, with tmux installed
[ -n "${CONNECT_TTY}" ] || exit 1
[ -z "${TMUX}" ] || exit 1
command -v tmux >/dev/null 2>&1 || exit 1

if ! countdown "Attaching to tmux session ${TMUX_SESSION}"; then
    printf "\rSkipped tmux. Run 'tmux new -A -s \"%s\"' to attach later." "${TMUX_SESSION}"
    sleep 2
    printf '\r\033[K'
    exit 1
fi

# attach to session or create it
tmux new -A -s "${TMUX_SESSION}" || exit 1

if ! countdown "Logging out"; then
    echo "Logout cancelled."
    exit 1
fi

exit 0