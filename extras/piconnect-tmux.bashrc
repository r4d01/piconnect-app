if [[ $- == *i* ]] && [ -x "${HOME}/.local/bin/piconnect-tmux.sh" ]; then
  "${HOME}/.local/bin/piconnect-tmux.sh" && exit
fi