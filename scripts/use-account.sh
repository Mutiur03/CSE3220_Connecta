# Source this (do not run it) to act as one collaborator in the current shell:
#   source scripts/use-account.sh <login>
# It sets the git author AND committer plus the gh token together, so every git command in this
# shell (commit, amend, rebase, cherry-pick) and every gh call uses the same identity.

_login="$1"
case "$_login" in
  Mutiur03) _email="mutiur5bb@gmail.com" ;;
  bipro96)  _email="bb123biprobiswas@gmail.com" ;;
  Aavas-99) _email="roy.aavas@gmail.com" ;;
  *) echo "usage: source scripts/use-account.sh <Mutiur03|bipro96|Aavas-99>" >&2; return 1 2>/dev/null || exit 1 ;;
esac

export GIT_AUTHOR_NAME="$_login" GIT_AUTHOR_EMAIL="$_email"
export GIT_COMMITTER_NAME="$_login" GIT_COMMITTER_EMAIL="$_email"
export GH_TOKEN
GH_TOKEN="$(gh auth token -u "$_login")" || { echo "gh is not logged in as $_login" >&2; return 1 2>/dev/null || exit 1; }

echo "acting as $_login <$_email>"
unset _login _email
