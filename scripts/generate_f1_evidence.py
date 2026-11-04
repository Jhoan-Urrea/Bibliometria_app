#!/usr/bin/env python3
"""Generate deterministic preprocessing golden files and corpus statistics."""

from __future__ import annotations

import argparse
import collections
import csv
import hashlib
import json
import os
import re
import sys
import tempfile
import unicodedata
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
CORPUS = ROOT / "data" / "corpus.json"
API_CORPUS = ROOT / "api" / "src" / "main" / "resources" / "data" / "corpus.json"
SCHEMA = ROOT / "docs" / "corpus.schema.json"
STOPWORDS = ROOT / "core" / "src" / "main" / "resources" / "stopwords.txt"
GOLDEN_DIR = ROOT / "core" / "src" / "test" / "resources" / "golden"
EVIDENCE_DIR = ROOT / "docs" / "evidence" / "f1"
CHECKLISTS = EVIDENCE_DIR / "checklists.csv"
CHECKPOINT = "sentence-transformers/all-MiniLM-L6-v2"
DEFAULT_MAX_TOKENS = 256
REVIEW_FIELDS = ("title", "authors", "abstract", "page", "language", "sha256")


def normalize(text: str) -> str:
    text = unicodedata.normalize("NFKC", text)
    text = re.sub(r"(?<=[^\W\d_])-\r?\n(?=[^\W\d_])", "", text, flags=re.UNICODE)
    return " ".join(text.split())


def tokenize(text: str) -> list[str]:
    tokens: list[str] = []
    current: list[str] = []
    characters = list(text.lower())
    for index, character in enumerate(characters):
        category = unicodedata.category(character)
        mark = category in {"Mn", "Mc", "Me"}
        if character.isalnum() or (mark and current):
            current.append(character)
        elif (
            character == "-"
            and current
            and (current[-1].isalnum() or unicodedata.category(current[-1]) in {"Mn", "Mc", "Me"})
            and index + 1 < len(characters)
            and characters[index + 1].isalnum()
        ):
            current.append(character)
        elif current:
            tokens.append("".join(current))
            current.clear()
    if current:
        tokens.append("".join(current))
    return tokens


def validate_corpus(records: Any, schema: dict[str, Any]) -> list[dict[str, Any]]:
    if not isinstance(records, list) or len(records) != 20:
        raise ValueError("The corpus must contain exactly 20 records.")
    required = set(schema["items"]["required"])
    expected_ids = [f"A{index:02d}" for index in range(1, 21)]
    if [record.get("id") for record in records] != expected_ids:
        raise ValueError("Corpus IDs must be contiguous and ordered A01-A20.")
    for record in records:
        missing = required - record.keys()
        if missing:
            raise ValueError(f"{record.get('id')}: missing required fields {sorted(missing)}")
        if not re.fullmatch(r"A\d{2}", record["id"]):
            raise ValueError(f"{record['id']}: invalid ID")
        if not re.fullmatch(r"[a-fA-F0-9]{64}", record["sha256"]):
            raise ValueError(f"{record['id']}: invalid SHA-256")
        if len(record["abstractText"]) < 100:
            raise ValueError(f"{record['id']}: abstractText is shorter than 100 characters")
        if (
            not isinstance(record["title"], str)
            or not record["title"].strip()
            or not isinstance(record["authors"], str)
            or not record["authors"].strip()
        ):
            raise ValueError(f"{record['id']}: title/authors must be nonempty")
        if (
            not isinstance(record["filename"], str)
            or not record["filename"].strip()
            or not isinstance(record["language"], str)
            or not record["language"].strip()
            or not isinstance(record["verificadoPor"], str)
            or not record["verificadoPor"].strip()
            or record["paginaAbstract"] is not None
            and (not isinstance(record["paginaAbstract"], int) or record["paginaAbstract"] < 1)
            or not isinstance(record["verified"], bool)
            or not isinstance(record["notas"], list)
            or any(not isinstance(note, str) or not note.strip() for note in record["notas"])
        ):
            raise ValueError(f"{record['id']}: invalid field type or value")
        if record["verified"] and (
            record["verificadoPor"] == "PENDIENTE_REVISION_HUMANA"
            or "AWAITING_MANUAL_DOUBLE_REVIEW" in record["notas"]
        ):
            raise ValueError(f"{record['id']}: cannot be verified while review is pending")
    return records


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    encoded = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="w", encoding="utf-8", newline="\n", dir=path.parent, delete=False
        ) as output:
            temporary = Path(output.name)
            output.write(encoded)
        os.replace(temporary, path)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def exact_model_token_counts(texts: list[str]) -> tuple[list[int], int]:
    try:
        from sentence_transformers import SentenceTransformer
    except ImportError as error:
        raise RuntimeError(
            "Exact model tokenization requires sentence-transformers; install the approved "
            "embedding dependencies, then rerun with --exact-tokenizer."
        ) from error
    model = SentenceTransformer(CHECKPOINT)
    transformer = model._first_module()
    tokenizer = getattr(transformer, "tokenizer", None)
    if tokenizer is None:
        raise RuntimeError(f"{CHECKPOINT} did not expose a tokenizer.")
    tokenizer.truncation_side = "right"
    max_tokens = int(model.max_seq_length)
    counts = []
    for text in texts:
        tokenized = model.tokenize([text])
        counts.append(int(tokenized["attention_mask"].sum().item()))
    return counts, max_tokens


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--exact-tokenizer",
        action="store_true",
        help="Load all-MiniLM-L6-v2 to count its actual subword tokens.",
    )
    arguments = parser.parse_args()

    try:
        raw = CORPUS.read_bytes()
        api_raw = API_CORPUS.read_bytes()
        if raw != api_raw:
            raise ValueError("data/corpus.json and the API resource copy differ.")
        corpus_sha256 = hashlib.sha256(raw).hexdigest()
        frozen_hash = (EVIDENCE_DIR / "corpus.sha256").read_text(encoding="utf-8").split()[0]
        if corpus_sha256 != frozen_hash:
            raise ValueError("docs/evidence/f1/corpus.sha256 does not match data/corpus.json.")
        records = validate_corpus(
            json.loads(raw), json.loads(SCHEMA.read_text(encoding="utf-8"))
        )
        with (ROOT / "data" / "manifest.csv").open(
            encoding="utf-8", newline=""
        ) as manifest_file:
            manifest = {
                row["id"]: row
                for row in csv.DictReader(manifest_file)
            }
        if len(manifest) != 20:
            raise ValueError("data/manifest.csv must contain 20 unique records.")
        for record in records:
            manifest_row = manifest.get(record["id"])
            if (
                manifest_row is None
                or manifest_row["filename"] != record["filename"]
                or manifest_row["sha256"].lower() != record["sha256"].lower()
            ):
                raise ValueError(f"{record['id']}: source PDF does not match the manifest.")
            pdf_path = ROOT / "data" / "raw" / record["filename"]
            if not pdf_path.is_file():
                raise ValueError(f"{record['id']}: missing source PDF {pdf_path.name}.")
            if hashlib.sha256(pdf_path.read_bytes()).hexdigest() != record["sha256"].lower():
                raise ValueError(f"{record['id']}: PDF checksum mismatch.")
        with CHECKLISTS.open(encoding="utf-8", newline="") as checklists_file:
            checklists = {
                row["id"]: row
                for row in csv.DictReader(checklists_file)
            }
        if len(checklists) != 20:
            raise ValueError("docs/evidence/f1/checklists.csv must contain 20 unique records.")
        for record in records:
            if not record["verified"]:
                continue
            checklist = checklists.get(record["id"])
            if (
                checklist is None
                or checklist.get("status") != "APPROVED"
                or any(
                    checklist.get(f"reviewer{reviewer}_{field}") != "PASS"
                    for reviewer in (1, 2)
                    for field in REVIEW_FIELDS
                )
                or not checklist.get("reviewer1")
                or not checklist.get("reviewer2")
                or not checklist.get("reviewer1_date")
                or not checklist.get("reviewer2_date")
            ):
                raise ValueError(
                    f"{record['id']}: verified record lacks two complete approved checklists."
                )
        stopwords = {
            unicodedata.normalize("NFKC", line).lower().strip()
            for line in STOPWORDS.read_text(encoding="utf-8").splitlines()
            if line.strip()
        }

        per_document: list[dict[str, Any]] = []
        document_frequencies: collections.Counter[str] = collections.Counter()
        texts: list[str] = []
        for record in records:
            normalized = normalize(record["title"] + " " + record["abstractText"])
            tokens = [token for token in tokenize(normalized) if token not in stopwords]
            counts: collections.Counter[str] = collections.Counter(tokens)
            ordered_frequencies = dict(counts)
            document_frequencies.update(counts.keys())
            canonical = " ".join(tokens)
            per_document.append(
                {
                    "id": record["id"],
                    "textoNormalizado": normalized,
                    "tokens": tokens,
                    "conjunto": list(dict.fromkeys(tokens)),
                    "frecuencias": ordered_frequencies,
                    "cadenaCanonica": canonical,
                }
            )
            texts.append(normalized)
        vocabulary = set(document_frequencies)

        for golden in per_document:
            write_json(GOLDEN_DIR / f"{golden['id']}.json", golden)
        write_json(GOLDEN_DIR / "corpus.json", records)

        token_source = "P-TOKENS proxy (+2 special tokens); not an exact model tokenizer count"
        maximum = DEFAULT_MAX_TOKENS
        if arguments.exact_tokenizer:
            token_counts, maximum = exact_model_token_counts(texts)
            token_source = f"{CHECKPOINT} tokenizer"
        else:
            token_counts = [len(item["tokens"]) + 2 for item in per_document]

        singleton_terms = sorted(term for term, df in document_frequencies.items() if df == 1)
        ubiquitous_terms = sorted(
            term for term, df in document_frequencies.items() if df == len(records)
        )
        truncated = [
            (record["id"], count)
            for record, count in zip(records, token_counts, strict=True)
            if count > maximum
        ]
        report = [
            "# Estadísticas del corpus — Fase 1",
            "",
            f"- Registros: **{len(records)}**.",
            f"- SHA-256 congelado: `{corpus_sha256}`.",
            f"- Vocabulario `V` (términos únicos tras NFKC, tokens, stopwords y stemming identidad): **{len(vocabulary)}**.",
            f"- Términos con `df=1`: **{len(singleton_terms)}**.",
            f"- Términos con `df=20`: **{len(ubiquitous_terms)}**.",
            f"- Límite IA-2: **{maximum} tokens**; tokenización medida mediante: **{token_source}**.",
            f"- Abstracts por encima del límite: **{len(truncated)}**.",
            "",
            "## Abstracts que exceden el límite",
            "",
        ]
        report.extend(
            [f"- `{article_id}`: {count} tokens de entrada." for article_id, count in truncated]
            or ["- Ninguno."]
        )
        report.extend(["", "## Términos con `df=1`", ""])
        report.extend([f"- `{term}`" for term in singleton_terms] or ["- Ninguno."])
        report.extend(["", "## Términos con `df=20`", ""])
        report.extend([f"- `{term}`" for term in ubiquitous_terms] or ["- Ninguno."])
        report.extend(
            [
                "",
                "## Interpretación y limitaciones",
                "",
                "El conteo exacto de tokens del modelo solo se declara cuando se ejecuta con "
                "`--exact-tokenizer`. Sin esa opción, el conteo usa los tokens del preprocesador "
                "más dos posiciones especiales como estimación y no certifica truncamiento real "
                "de WordPiece. Los registros con `verified: false` siguen pendientes de revisión "
                "manual doble; estas estadísticas no los convierten en verificados.",
                "",
            ]
        )
        report_path = EVIDENCE_DIR / "estadisticas.md"
        report_path.write_text("\n".join(report), encoding="utf-8")
        print(
            f"Generated 20 goldens and {report_path}; V={len(vocabulary)}, "
            f"df=1={len(singleton_terms)}, df=20={len(ubiquitous_terms)}."
        )
        return 0
    except (OSError, ValueError, KeyError, TypeError, RuntimeError) as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
