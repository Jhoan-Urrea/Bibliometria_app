#!/usr/bin/env python3
"""Verify corpus provenance and generate FastText and normalized MiniLM embeddings."""

from __future__ import annotations

import argparse
import hashlib
import importlib.metadata
import json
import math
import os
import sys
import tempfile
import unicodedata
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CORPUS = ROOT / "data" / "corpus.json"
DEFAULT_INPUT_DIR = ROOT / "data" / "embeddings"
DEFAULT_HASH_FILE = ROOT / "docs" / "evidence" / "f1" / "corpus.sha256"
MINILM_MODEL = "sentence-transformers/all-MiniLM-L6-v2"
FASTTEXT_DIMENSIONS = 300


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def sha256_path(path: Path) -> str | None:
    if path.is_file():
        return sha256_file(path)
    if not path.is_dir():
        return None

    digest = hashlib.sha256()
    for file_path in sorted(item for item in path.rglob("*") if item.is_file()):
        relative_path = file_path.relative_to(path).as_posix().encode("utf-8")
        digest.update(len(relative_path).to_bytes(8, "big"))
        digest.update(relative_path)
        digest.update(bytes.fromhex(sha256_file(file_path)))
    return digest.hexdigest()


def load_expected_hash(hash_file: Path, override: str | None) -> str:
    if override is not None:
        expected = override.strip().lower()
    else:
        if not hash_file.is_file():
            raise ValueError(
                f"Expected corpus hash file not found: {hash_file}; "
                "provide --corpus-sha256 or generate the approved baseline."
            )
        tokens = hash_file.read_text(encoding="utf-8").split()
        if not tokens:
            raise ValueError(f"Expected corpus hash file is empty: {hash_file}")
        expected = tokens[0].lower()
    if len(expected) != 64 or any(character not in "0123456789abcdef" for character in expected):
        raise ValueError("Expected corpus SHA-256 must contain exactly 64 hexadecimal characters.")
    return expected


def verify_corpus_hash(corpus: Path, hash_file: Path, override: str | None) -> str:
    if not corpus.is_file():
        raise FileNotFoundError(f"Corpus JSON does not exist: {corpus}")
    expected = load_expected_hash(hash_file, override)
    actual = sha256_file(corpus)
    if actual != expected:
        raise ValueError(f"Corpus SHA-256 mismatch: expected {expected}, found {actual}")
    return actual


def tokenize_p_text(text: str) -> list[str]:
    tokens: list[str] = []
    current: list[str] = []
    for character in text.lower():
        category = unicodedata.category(character)
        is_mark = category in {"Mn", "Mc", "Me"}
        if character.isalnum() or (is_mark and current):
            current.append(character)
        elif current:
            tokens.append("".join(current))
            current.clear()
    if current:
        tokens.append("".join(current))
    return tokens


def load_exported_texts(input_dir: Path, corpus_sha256: str) -> tuple[list[dict[str, str]], set[str]]:
    texts_path = input_dir / "textos_p_texto.json"
    vocabulary_path = input_dir / "vocabulario.txt"
    payload = json.loads(texts_path.read_text(encoding="utf-8"))
    if payload.get("corpusSha256") != corpus_sha256:
        raise ValueError("textos_p_texto.json was not exported from the verified corpus.")
    documents = payload.get("documents")
    if not isinstance(documents, list):
        raise ValueError("textos_p_texto.json must contain a documents array.")

    vocabulary: set[str] = set()
    seen_ids: set[str] = set()
    for document in documents:
        if not isinstance(document, dict):
            raise ValueError("Every exported document must be a JSON object.")
        article_id = document.get("id")
        text = document.get("textoPTexto")
        if not isinstance(article_id, str) or not article_id:
            raise ValueError("Every exported document must have a non-empty id.")
        if article_id in seen_ids:
            raise ValueError(f"Duplicate article id in exported texts: {article_id}")
        if not isinstance(text, str):
            raise ValueError(f"Document {article_id} has no textoPTexto string.")
        seen_ids.add(article_id)
        vocabulary.update(tokenize_p_text(text))

    exported_vocabulary = {
        line.strip()
        for line in vocabulary_path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    }
    if exported_vocabulary != vocabulary:
        raise ValueError("vocabulario.txt does not match tokens extracted from P-TEXTO.")
    return documents, vocabulary


def load_fasttext_model(model_path: Path) -> tuple[Any, str]:
    try:
        from gensim.models import FastText, KeyedVectors
        from gensim.models.fasttext import load_facebook_vectors
    except ImportError as error:
        raise RuntimeError(
            "FastText generation requires gensim. Install the project's approved Python "
            "dependencies before running this script."
        ) from error

    suffix = model_path.suffix.lower()
    if suffix == ".bin":
        model = load_facebook_vectors(str(model_path))
        vectors = model
        model_format = "facebook-fasttext-bin"
    elif suffix == ".vec":
        vectors = KeyedVectors.load_word2vec_format(str(model_path), binary=False)
        model_format = "word2vec-text-vectors"
    elif suffix == ".model":
        model = FastText.load(str(model_path))
        vectors = model.wv
        model_format = "gensim-fasttext-model"
    else:
        raise ValueError("FastText model must be a .bin, .vec, or Gensim .model file.")

    if int(vectors.vector_size) != FASTTEXT_DIMENSIONS:
        raise ValueError(
            f"FastText model must have {FASTTEXT_DIMENSIONS} dimensions, "
            f"found {vectors.vector_size}."
        )
    return vectors, model_format


def fasttext_vector(vectors: Any, token: str) -> tuple[Any | None, bool]:
    known = token in vectors.key_to_index
    supports_subwords = bool(getattr(vectors, "bucket", 0))
    if not known and not supports_subwords:
        return None, False
    return vectors.get_vector(token), known


def create_fasttext_embeddings(
    documents: list[dict[str, str]], vocabulary: set[str], vectors: Any
) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    import numpy as np

    known_vocabulary = sum(token in vectors.key_to_index for token in vocabulary)
    result: list[dict[str, Any]] = []
    total_tokens = 0
    known_tokens = 0
    subword_tokens = 0

    for document in documents:
        tokens = tokenize_p_text(document["textoPTexto"])
        total_tokens += len(tokens)
        usable_vectors = []
        document_known = 0
        document_subword = 0
        for token in tokens:
            vector, known = fasttext_vector(vectors, token)
            if vector is None:
                continue
            usable_vectors.append(vector)
            if known:
                document_known += 1
            else:
                document_subword += 1
        if usable_vectors:
            mean_vector = np.mean(np.asarray(usable_vectors, dtype=np.float64), axis=0)
        else:
            mean_vector = np.zeros(FASTTEXT_DIMENSIONS, dtype=np.float64)
        if mean_vector.shape != (FASTTEXT_DIMENSIONS,) or not np.isfinite(mean_vector).all():
            raise ValueError(f"Invalid FastText vector generated for {document['id']}.")
        known_tokens += document_known
        subword_tokens += document_subword
        result.append(
            {
                "id": document["id"],
                "vector": mean_vector.tolist(),
                "token_count": len(tokens),
                "known_vocabulary_tokens": document_known,
                "subword_tokens": document_subword,
            }
        )

    vocabulary_coverage = known_vocabulary / len(vocabulary) if vocabulary else 1.0
    token_coverage = known_tokens / total_tokens if total_tokens else 1.0
    return result, {
        "unique_vocabulary_tokens": len(vocabulary),
        "known_unique_vocabulary_tokens": known_vocabulary,
        "unique_vocabulary_coverage": vocabulary_coverage,
        "total_document_tokens": total_tokens,
        "known_document_tokens": known_tokens,
        "document_token_coverage": token_coverage,
        "subword_oov_tokens_vectorized": subword_tokens,
    }


def create_minilm_embeddings(
    documents: list[dict[str, str]], model_name: str
) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    try:
        from sentence_transformers import SentenceTransformer
    except ImportError as error:
        raise RuntimeError(
            "MiniLM generation requires sentence-transformers and its approved dependencies."
        ) from error

    model = SentenceTransformer(model_name)
    transformer = model._first_module()
    tokenizer = getattr(transformer, "tokenizer", None)
    if tokenizer is not None:
        tokenizer.truncation_side = "right"
    max_tokens = int(model.max_seq_length)
    texts = [document["textoPTexto"] for document in documents]
    vectors = model.encode(
        texts,
        batch_size=32,
        show_progress_bar=False,
        convert_to_numpy=True,
        normalize_embeddings=True,
    )
    if vectors.ndim != 2 or vectors.shape[0] != len(documents):
        raise ValueError("MiniLM returned an invalid embedding matrix.")
    norms = (float((row**2).sum() ** 0.5) for row in vectors)
    if not all(math.isclose(norm, 1.0, rel_tol=1e-5, abs_tol=1e-5) for norm in norms):
        raise ValueError("MiniLM returned an embedding that is not normalized to unit norm.")

    embeddings = [
        {"id": document["id"], "vector": vector.tolist()}
        for document, vector in zip(documents, vectors, strict=True)
    ]
    config = getattr(transformer.auto_model, "config", None)
    model_digest = hashlib.sha256()
    for name, parameter in sorted(transformer.auto_model.state_dict().items()):
        array = parameter.detach().cpu().contiguous().numpy()
        model_digest.update(name.encode("utf-8"))
        model_digest.update(str(array.dtype).encode("ascii"))
        model_digest.update(json.dumps(array.shape).encode("ascii"))
        model_digest.update(array.tobytes())
    return embeddings, {
        "model_id": model_name,
        "model_revision": getattr(config, "_commit_hash", None),
        "model_state_sha256": model_digest.hexdigest(),
        "dimensions": int(vectors.shape[1]),
        "max_input_tokens": max_tokens,
        "truncation": "right; tokenizer truncation preserves the beginning of each P-TEXTO",
        "normalized": True,
    }


def package_version(package: str) -> str:
    try:
        return importlib.metadata.version(package)
    except importlib.metadata.PackageNotFoundError:
        return "not-installed"


def write_json_atomically(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary_path: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="w",
            encoding="utf-8",
            newline="\n",
            dir=path.parent,
            prefix=f"{path.name}.",
            suffix=".tmp",
            delete=False,
        ) as temporary_file:
            temporary_path = Path(temporary_file.name)
            json.dump(payload, temporary_file, ensure_ascii=False, allow_nan=False, separators=(",", ":"))
            temporary_file.write("\n")
        os.replace(temporary_path, path)
    finally:
        if temporary_path is not None:
            temporary_path.unlink(missing_ok=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--corpus", type=Path, default=DEFAULT_CORPUS)
    parser.add_argument("--hash-file", type=Path, default=DEFAULT_HASH_FILE)
    parser.add_argument("--corpus-sha256", help="Approved corpus hash; overrides --hash-file.")
    parser.add_argument("--input-dir", type=Path, default=DEFAULT_INPUT_DIR)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_INPUT_DIR)
    parser.add_argument("--fasttext-model", type=Path, required=True)
    parser.add_argument("--minilm-model", default=MINILM_MODEL)
    arguments = parser.parse_args()

    try:
        corpus_sha256 = verify_corpus_hash(
            arguments.corpus, arguments.hash_file, arguments.corpus_sha256
        )
        documents, vocabulary = load_exported_texts(arguments.input_dir, corpus_sha256)
        if not arguments.fasttext_model.is_file():
            raise FileNotFoundError(f"FastText model does not exist: {arguments.fasttext_model}")

        fasttext, fasttext_format = load_fasttext_model(arguments.fasttext_model)
        fasttext_results, coverage = create_fasttext_embeddings(documents, vocabulary, fasttext)
        minilm_results, minilm_metadata = create_minilm_embeddings(
            documents, arguments.minilm_model
        )

        fasttext_output = arguments.output_dir / "vectors_ia1.json"
        minilm_output = arguments.output_dir / "embeddings_ia2.json"
        write_json_atomically(fasttext_output, fasttext_results)
        write_json_atomically(minilm_output, minilm_results)

        metadata = {
            "corpus": {
                "path": str(arguments.corpus.resolve()),
                "sha256": corpus_sha256,
                "documents": len(documents),
            },
            "inputs": {
                "textos_p_texto_sha256": sha256_file(
                    arguments.input_dir / "textos_p_texto.json"
                ),
                "vocabulario_sha256": sha256_file(arguments.input_dir / "vocabulario.txt"),
            },
            "fasttext": {
                "model_path": str(arguments.fasttext_model.resolve()),
                "model_format": fasttext_format,
                "model_sha256": sha256_path(arguments.fasttext_model),
                "dimensions": FASTTEXT_DIMENSIONS,
                "gensim_version": package_version("gensim"),
                "coverage": coverage,
            },
            "minilm": {
                **minilm_metadata,
                "sentence_transformers_version": package_version("sentence-transformers"),
                "transformers_version": package_version("transformers"),
                "torch_version": package_version("torch"),
            },
            "outputs": {
                fasttext_output.name: sha256_file(fasttext_output),
                minilm_output.name: sha256_file(minilm_output),
            },
        }
        write_json_atomically(arguments.output_dir / "embeddings.meta.json", metadata)
        print(f"Generated embeddings for {len(documents)} documents in {arguments.output_dir}")
        print(f"FastText unique vocabulary coverage: {coverage['unique_vocabulary_coverage']:.2%}")
        return 0
    except (OSError, ValueError, RuntimeError, json.JSONDecodeError) as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
