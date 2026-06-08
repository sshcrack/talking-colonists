#!/usr/bin/env bash

# Checks that every class in the mixin package directory is listed in mc_talking.mixins.json

MIXIN_DIR="src/main/java/me/sshcrack/mc_talking/mixin"
MIXINS_JSON="src/main/resources/mc_talking.mixins.json"
HAS_ERROR=0

if [ ! -d "$MIXIN_DIR" ]; then
  echo "Mixin directory not found: $MIXIN_DIR"
  exit 1
fi

if [ ! -f "$MIXINS_JSON" ]; then
  echo "Mixins JSON not found: $MIXINS_JSON"
  exit 1
fi

for file in "$MIXIN_DIR"/*.java; do
  class=$(basename "$file" .java)

  if ! grep -q "\"$class\"" "$MIXINS_JSON"; then
    echo "NOT REGISTERED: $class (in $MIXIN_DIR/$class.java but missing from $MIXINS_JSON)"
    HAS_ERROR=1
  fi
done

if [ "$HAS_ERROR" -eq 1 ]; then
  exit 1
fi

echo "OK — all mixin classes are registered in $MIXINS_JSON"
exit 0

