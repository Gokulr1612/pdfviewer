#!/usr/bin/env bash
#
# Creates an app signing key and uploads it to GitHub Actions as secrets.
#
# Run this on your own machine, not in CI. The keystore it creates is the
# credential that proves this app's identity: anyone holding it can sign
# updates that Android will accept as genuine, and losing it means no future
# build can ever upgrade an existing install.
#
#     ./tools/setup-signing.sh
#
set -euo pipefail

KEYSTORE="${KEYSTORE:-release.jks}"
ALIAS="${ALIAS:-docviewer}"
VALIDITY_DAYS="${VALIDITY_DAYS:-10000}"

fail() { printf '\nError: %s\n' "$1" >&2; exit 1; }
step() { printf '\n\033[1m%s\033[0m\n' "$1"; }

# ---- checks -----------------------------------------------------------------

command -v keytool >/dev/null 2>&1 ||
  fail "keytool not found. It ships with the JDK — install one, or add it to PATH."

command -v gh >/dev/null 2>&1 ||
  fail "The GitHub CLI (gh) is not installed. See https://cli.github.com, or set the four secrets by hand as described in the README."

gh auth status >/dev/null 2>&1 ||
  fail "The GitHub CLI is not signed in. Run: gh auth login"

git rev-parse --is-inside-work-tree >/dev/null 2>&1 ||
  fail "Run this from inside the repository."

repo=$(gh repo view --json nameWithOwner --jq .nameWithOwner)

if [ -e "$KEYSTORE" ]; then
  fail "$KEYSTORE already exists. Refusing to overwrite it — if you replace a keystore, existing installs can no longer be upgraded. Move it aside deliberately if that is what you want."
fi

# ---- generate ---------------------------------------------------------------

step "Creating $KEYSTORE for $repo"

# Generated locally and never printed. It goes straight into the GitHub secret
# and into the keystore itself; nothing else needs to know it.
if command -v openssl >/dev/null 2>&1; then
  password="$(openssl rand -base64 30)"
else
  password="$(head -c 30 /dev/urandom | base64)"
fi

keytool -genkeypair -noprompt \
  -keystore "$KEYSTORE" \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 4096 \
  -validity "$VALIDITY_DAYS" \
  -storepass "$password" \
  -keypass "$password" \
  -dname "CN=Doc Viewer, OU=Doc Viewer, O=Doc Viewer, C=GB"

printf '  created a 4096-bit RSA key valid for %s days\n' "$VALIDITY_DAYS"

# ---- upload -----------------------------------------------------------------

step "Uploading secrets to $repo"

if base64 --help 2>&1 | grep -q -- '-w'; then
  encoded="$(base64 -w0 "$KEYSTORE")"     # GNU
else
  encoded="$(base64 < "$KEYSTORE" | tr -d '\n')"  # BSD/macOS
fi

printf '%s' "$encoded"   | gh secret set SIGNING_KEYSTORE_BASE64 --repo "$repo"
printf '%s' "$password"  | gh secret set SIGNING_STORE_PASSWORD  --repo "$repo"
printf '%s' "$ALIAS"     | gh secret set SIGNING_KEY_ALIAS       --repo "$repo"
printf '%s' "$password"  | gh secret set SIGNING_KEY_PASSWORD    --repo "$repo"

unset encoded

# ---- back up ----------------------------------------------------------------

backup="${KEYSTORE%.jks}-credentials.txt"
umask 077
cat > "$backup" <<EOF
Doc Viewer app signing credentials
Created: $(date -u '+%Y-%m-%d %H:%M UTC')
Repository: $repo

Keystore file: $KEYSTORE
Key alias:     $ALIAS
Password:      $password
  (the same value is used for both the keystore and the key)

Keep this file and $KEYSTORE together, somewhere durable — a password manager
or an encrypted backup. They are not recoverable. Without them no future build
can upgrade an app already installed from this key, and if you ever publish to
Google Play you would be unable to update the listing at all.

Both files are already excluded by .gitignore. Do not commit them.
EOF

unset password

step "Done"
cat <<EOF

  Secrets set on $repo:
    SIGNING_KEYSTORE_BASE64, SIGNING_STORE_PASSWORD,
    SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD

  Written locally, and git-ignored:
    $KEYSTORE
    $backup   <- contains the password

  Next: move both files into a password manager or an encrypted backup, then
  delete them from this directory. The next merge into master will publish a
  signed, R8-shrunk APK that installs over previous builds.

EOF
