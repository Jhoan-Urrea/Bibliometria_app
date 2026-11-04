#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd -- "$script_dir/.." && pwd)"
manifest="${1:-$repository_root/data/manifest.csv}"
pdf_directory="${2:-$repository_root/data/raw}"

if [[ ! -r "$manifest" ]]; then
    printf 'Error: no se puede leer el manifiesto: %s\n' "$manifest" >&2
    exit 1
fi
if [[ ! -d "$pdf_directory" ]]; then
    printf 'Error: no existe el directorio de PDF: %s\n' "$pdf_directory" >&2
    exit 1
fi

records_file="$(mktemp)"
trap 'rm -f -- "$records_file"' EXIT

if ! python3 - "$manifest" > "$records_file" <<'PY'
import csv
import re
import sys

with open(sys.argv[1], encoding="utf-8", newline="") as manifest_file:
    reader = csv.DictReader(manifest_file)
    if reader.fieldnames != ["id", "filename", "sha256"]:
        raise SystemExit("Error: encabezado requerido: id,filename,sha256")

    records = list(reader)
    if len(records) != 20:
        raise SystemExit(f"Error: se esperaban 20 registros A01-A20; se encontraron {len(records)}.")

    for index, record in enumerate(records, start=1):
        if record["id"] != f"A{index:02d}" or not record["filename"]:
            raise SystemExit(f"Error: registro A{index:02d} inválido.")
        if not re.fullmatch(r"[0-9a-fA-F]{64}", record["sha256"]):
            raise SystemExit(f"Error: SHA-256 inválido para {record['filename']}.")
        for field in ("id", "filename", "sha256"):
            sys.stdout.buffer.write(record[field].encode("utf-8") + b"\0")
PY
then
    exit 1
fi

mapfile -d '' -t records < "$records_file"
for ((index = 0; index < ${#records[@]}; index += 3)); do
    id="${records[index]}"
    filename="${records[index + 1]}"
    expected_hash="${records[index + 2]}"

    pdf_path="$pdf_directory/$filename"
    if [[ ! -f "$pdf_path" ]]; then
        printf 'Error: falta el PDF %s (%s).\n' "$id" "$pdf_path" >&2
        exit 1
    fi
    actual_hash="$(sha256sum -- "$pdf_path")"
    actual_hash="${actual_hash%% *}"
    if [[ "${actual_hash,,}" != "${expected_hash,,}" ]]; then
        printf 'Error: SHA-256 no coincide para %s: esperado %s, obtenido %s.\n' \
            "$filename" "$expected_hash" "$actual_hash" >&2
        exit 1
    fi
done

printf 'Verificación correcta: 20 PDF coinciden con %s.\n' "$manifest"
