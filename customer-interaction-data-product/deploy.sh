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
#   meta/ generated *.avsc    -> dp-kafka-data-mesh/scripts/jikkou/schemas/ (referenced via $ref in schema-specs.yml)
#   scripts/redpanda-connect/streams/ -> dp-kafka-data-mesh/custom-conf/redpanda-connect/streams/ (streams: input/pipeline/output only)
#   scripts/redpanda-connect/*.yaml   -> dp-kafka-data-mesh/custom-conf/redpanda-connect/         (connect.yaml via -o, resources.yaml via -r; OUTSIDE streams/)
#
# Usage:
#   ./copy-scripts.sh            copy the files
#   ./copy-scripts.sh --dry-run  show what would be copied, change nothing

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_ROOT="${ROOT_DIR}/scripts"
DEST_ROOT="${ROOT_DIR}/../platys"

# source-subdir : destination-subdir (relative to DEST_ROOT)
MAPPINGS=(
  "vector:conf/vector"
  "postgresql:init/postgresql"
  "jikkou:scripts/jikkou"
  "redpanda-connect/streams:custom-conf/redpanda-connect/streams"
  "redpanda-connect:custom-conf/redpanda-connect"
  "shadowtraffic:scripts/shadowtraffic"
  "opa/rego:security/opa/policies"
  "docker:."
)

# Avro schemas generated from the .avdl files in ./meta (avdl2avsc plugin),
# copied next to the jikkou specs so the container can reference them
# (schema.$ref: file:///jikkou/schemas/<name>.avsc).
# generated-schema-path (relative to meta/target/generated-sources/avro/schema)
SCHEMAS=(
  "aristech/event/CallEvent.avsc"
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

# Generate the .avsc files from the .avdl sources and copy them to the jikkou schemas folder.
META_SCHEMA_DIR="${ROOT_DIR}/meta/target/generated-sources/avro/schema"
JIKKOU_SCHEMA_DEST="${DEST_ROOT}/scripts/jikkou/schemas"
if ! $DRY_RUN; then
  echo "Generating Avro schemas (mvn generate-sources) ..."
  mvn -q -f "${ROOT_DIR}/meta/pom.xml" generate-sources
fi
copied=0
for schema in "${SCHEMAS[@]}"; do
  f="${META_SCHEMA_DIR}/${schema}"
  if $DRY_RUN; then
    echo "WOULD COPY  meta/.../${schema}  ->  ${JIKKOU_SCHEMA_DEST#"$ROOT_DIR"/}/$(basename "$schema")"
  else
    [[ -f "$f" ]] || { echo "ERROR: generated schema not found: $f" >&2; exit 1; }
    mkdir -p "$JIKKOU_SCHEMA_DEST"
    cp -p "$f" "$JIKKOU_SCHEMA_DEST/"
    echo "COPIED      meta/.../${schema}  ->  ${JIKKOU_SCHEMA_DEST#"$ROOT_DIR"/}/$(basename "$schema")"
  fi
  copied=$((copied + 1))
done

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
