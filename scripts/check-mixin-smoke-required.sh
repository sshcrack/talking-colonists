#!/bin/bash
STAGED=$(git diff --cached --name-only -- 'src/main/java/me/sshcrack/mc_talking/mixin/' 'src/main/resources/**/*.mixins.json')
if [ -z "$STAGED" ]; then
    exit 0
fi
echo "" >&2
echo "!!! Mixin files changed — don't forget to run the mixin smoke test !!!" >&2
echo "  bash scripts/test-mixin-smoke.sh" >&2
echo "  Then commit the generated .mixin-smoke-verified file." >&2
echo "" >&2
