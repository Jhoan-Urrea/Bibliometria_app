#!/usr/bin/env python3
"""Validate the raw PDF corpus and write its deterministic SHA-256 manifest."""

from __future__ import annotations

import argparse
import csv
import hashlib
import os
import sys
import tempfile
from pathlib import Path


EXPECTED_PDF_COUNT = 20
CSV_FIELDS = ("id", "filename", "sha256")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as pdf_file:
        for chunk in iter(lambda: pdf_file.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    repository_root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--raw-dir",
        type=Path,
        default=repository_root / "data" / "raw",
        help="Directory containing the 20 source PDFs.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=repository_root / "data" / "manifest.csv",
        help="Destination CSV manifest.",
    )
    arguments = parser.parse_args()

    if not arguments.raw_dir.is_dir():
        print(f"Error: raw PDF directory does not exist: {arguments.raw_dir}", file=sys.stderr)
        return 1

    pdf_paths = sorted(
        (
            path
            for path in arguments.raw_dir.iterdir()
            if path.is_file() and path.suffix.casefold() == ".pdf"
        ),
        key=lambda path: (path.name.casefold(), path.name),
    )
    if len(pdf_paths) != EXPECTED_PDF_COUNT:
        print(
            f"Error: expected {EXPECTED_PDF_COUNT} PDF files, found {len(pdf_paths)}.",
            file=sys.stderr,
        )
        return 1

    records = []
    for index, pdf_path in enumerate(pdf_paths, start=1):
        try:
            with pdf_path.open("rb") as pdf_file:
                header = pdf_file.read(1024)
            if b"%PDF-" not in header:
                raise ValueError("missing PDF header in the first 1024 bytes")
            records.append(
                {
                    "id": f"A{index:02d}",
                    "filename": pdf_path.name,
                    "sha256": sha256_file(pdf_path),
                }
            )
        except (OSError, ValueError) as error:
            print(f"Error: invalid PDF {pdf_path.name}: {error}", file=sys.stderr)
            return 1

    output_path = arguments.output.resolve()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    temporary_path = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="w",
            encoding="utf-8",
            newline="",
            dir=output_path.parent,
            prefix=f"{output_path.name}.",
            suffix=".tmp",
            delete=False,
        ) as temporary_file:
            temporary_path = Path(temporary_file.name)
            writer = csv.DictWriter(
                temporary_file, fieldnames=CSV_FIELDS, lineterminator="\n"
            )
            writer.writeheader()
            writer.writerows(records)
        os.replace(temporary_path, output_path)
    except OSError as error:
        print(f"Error: could not write manifest {output_path}: {error}", file=sys.stderr)
        return 1
    finally:
        if temporary_path is not None:
            temporary_path.unlink(missing_ok=True)

    print(f"Manifest generated: {output_path} ({len(records)} PDFs)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())