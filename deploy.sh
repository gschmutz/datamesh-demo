#!/usr/bin/env bash
#
# copy-scripts.sh
#
# Copies the config/scripts kept under ./scripts into the matching
# dp-kafka-data-mesh folders (the locations the docker-compose volume mounts
# expect). Run from anywhere; paths are resolved relative to this script.
#
# Mapping (source -> destination), derived from dp-kafka-data-mesh/docker-compose.yml:
#   scripts/vector/           -> dp-kafka-data-mesh/conf/vector/      (mounted as /etc/vector/vector.yaml)
#   scripts/postgresql/       -> dp-kafka-data-mesh/init/postgresql/  (mounted as /docker-entrypoint-initdb.d/)
#   scripts/jikkou/           -> dp-kafka-data-mesh/scripts/jikkou/   (mounted as /jikkou)
#   scripts/redpanda-connect/streams/ -> dp-kafka-data-mesh/custom-conf/redpanda-connect/streams/ (streams: input/pipeline/output only)
#   scripts/redpanda-connect/*.yaml   -> dp-kafka-data-mesh/custom-conf/redpanda-connect/         (connect.yaml via -o, resources.yaml via -r; OUTSIDE streams/)
#
# Usage:
#   ./copy-scripts.sh            copy the files
#   ./copy-scripts.sh --dry-run  show what would be copied, change nothing

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_ROOT="${ROOT_DIR}/scripts"
DEST_ROOT="${ROOT_DIR}/dp-kafka-data-mesh"

# source-subdir : destination-subdir (relative to DEST_ROOT)
MAPPINGS=(
  "vector:conf/vector"
  "postgresql:init/postgresql"
  "jikkou:scripts/jikkou"
  "redpanda-connect/streams:custom-conf/redpanda-connect/streams"
  "redpanda-connect:custom-conf/redpanda-connect"
  "shadowtraffic:scripts/shadowtraffic"
)

DRY_RUN=false
[[ "${1:-}" == "--dry-run" || "${1:-}" == "-n" ]] && DRY_RUN=true

if [[ ! -d "$SRC_ROOT" ]]; then
  echo "ERROR: source folder not found: $SRC_ROOT" >&2
  exit 1
fi
if [[ ! -d "$DEST_ROOT" ]]; then
  echo "ERROR: target folder not found: $DEST_ROOT" >&2
  exit 1
fi

copied=0
for map in "${MAPPINGS[@]}"; do
  src="${SRC_ROOT}/${map%%:*}"
  dest="${DEST_ROOT}/${map##*:}"

  if [[ ! -d "$src" ]]; then
    echo "SKIP  (no source dir): ${src#"$ROOT_DIR"/}"
    continue
  fi

  # Copy every regular file from the source subfolder into the destination.
  shopt -s nullglob dotglob
  files=("$src"/*)
  shopt -u nullglob dotglob
  if [[ ${#files[@]} -eq 0 ]]; then
    echo "SKIP  (empty source dir): ${src#"$ROOT_DIR"/}"
    continue
  fi

  for f in "${files[@]}"; do
    [[ -f "$f" ]] || continue
    rel_src="${f#"$ROOT_DIR"/}"
    rel_dest="${dest#"$ROOT_DIR"/}/$(basename "$f")"
    if $DRY_RUN; then
      echo "WOULD COPY  $rel_src  ->  $rel_dest"
    else
      mkdir -p "$dest"
      cp -p "$f" "$dest/"
      echo "COPIED      $rel_src  ->  $rel_dest"
    fi
    copied=$((copied + 1))
  done
done

echo ""
if $DRY_RUN; then
  echo "Dry run: $copied file(s) would be copied. Re-run without --dry-run to apply."
else
  echo "Done: $copied file(s) copied."
fi
