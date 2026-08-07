"""Чтение JSONL датасета и построение временных окон."""

import json
import numpy as np
from pathlib import Path

from model import FEATURE_COUNT, SEQ_LEN, OUTPUT_SIZE

SCHEMA_VERSION = 2


class DatasetError(Exception):
    pass


def read_meta(dataset_path):
    """Читает мету рядом с датасетом. Возвращает dict или None."""
    meta_path = Path(str(dataset_path).replace(".jsonl", "") + ".meta.json")
    if not meta_path.exists():
        return None
    with open(meta_path, "r", encoding="utf-8") as f:
        return json.load(f)


def validate_meta(meta):
    """Проверяет совместимость датасета. Бросает DatasetError при несовместимости."""
    if meta is None:
        raise DatasetError(
            "Мета датасета не найдена. Датасеты v1 несовместимы со схемой v2 — "
            "перезапишите датасет через Ai Record."
        )

    version = meta.get("schemaVersion", 1)
    if version < SCHEMA_VERSION:
        raise DatasetError(
            f"Датасет schemaVersion={version}, требуется {SCHEMA_VERSION}. "
            "Конвертация невозможна: v1 не содержит нужных фич. Перезапишите датасет."
        )

    fc = meta.get("featureCount")
    if fc is not None and fc != FEATURE_COUNT:
        raise DatasetError(f"featureCount={fc}, ожидается {FEATURE_COUNT}")

    source = meta.get("source", "HUMAN")
    if source != "HUMAN":
        raise DatasetError(
            f"Источник датасета: {source}. Обучение только на человеческих записях — "
            "источник «Ротация» удалён. Перезапишите датасет через Ai Record или .ai dump."
        )

    return meta


def load_samples(dataset_path, quality_filter=("CLEAN",)):
    """Читает JSONL построчно. Возвращает (features, labels, ticks)."""
    features, labels, ticks = [], [], []
    skipped_quality = 0
    skipped_bad = 0
    skipped_source = 0

    with open(dataset_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                row = json.loads(line)
            except json.JSONDecodeError:
                skipped_bad += 1
                continue

            feat = row.get("f")
            label = row.get("y")
            if not feat or not label or len(feat) != FEATURE_COUNT or len(label) != OUTPUT_SIZE:
                skipped_bad += 1
                continue

            # Обучение только на человеческих метках — не-человеческие строки
            # пропускаем даже если мета датасета прошла валидацию.
            source = row.get("s", "HUMAN")
            if source != "HUMAN":
                skipped_source += 1
                continue

            quality = row.get("q", "CLEAN")
            if quality_filter and quality not in quality_filter:
                skipped_quality += 1
                continue

            features.append(feat)
            labels.append(label)
            ticks.append(row.get("t", 0))

    if not features:
        raise DatasetError("После фильтрации не осталось сэмплов")

    return (
        np.asarray(features, dtype=np.float32),
        np.asarray(labels, dtype=np.float32),
        np.asarray(ticks, dtype=np.int64),
        {"skipped_quality": skipped_quality, "skipped_bad": skipped_bad,
         "skipped_source": skipped_source},
    )


def build_windows(features, labels, ticks, seq_len=SEQ_LEN):
    """
    Строит окна длины seq_len. Окно валидно только если тики подряд —
    иначе окно перекрывает разрыв записи или смену цели.
    """
    n = len(features)
    if n < seq_len:
        raise DatasetError(f"Сэмплов {n}, нужно минимум {seq_len} для одного окна")

    windows = []
    targets = []

    for end in range(seq_len - 1, n):
        start = end - seq_len + 1
        # Проверяем непрерывность тиков внутри окна
        span = ticks[end] - ticks[start]
        if span != seq_len - 1:
            continue
        windows.append(features[start:end + 1])
        targets.append(labels[end])

    if not windows:
        raise DatasetError(
            "Не удалось построить ни одного непрерывного окна. "
            "Записывайте более длинные непрерывные отрезки боя."
        )

    return np.stack(windows), np.stack(targets)


def split(x, y, val_fraction=0.2, seed=1337):
    """
    Честный split. Валидация берётся ХВОСТОМ по времени, а не случайно:
    окна пересекаются, случайный split протёк бы между train и val.
    """
    n = len(x)
    n_val = max(1, int(n * val_fraction))
    n_train = n - n_val
    if n_train < 1:
        raise DatasetError(f"Слишком мало окон для split: {n}")
    return x[:n_train], y[:n_train], x[n_train:], y[n_train:]


def compute_norm(x_train):
    """
    mean/std считаются ТОЛЬКО по train split.
    Форма x_train: (n, seq, feat) -> статистика по фичам.
    """
    flat = x_train.reshape(-1, x_train.shape[-1])
    mean = flat.mean(axis=0)
    std = flat.std(axis=0)
    std[std < 1e-6] = 1.0
    return mean.astype(np.float32), std.astype(np.float32)


def normalize(x, mean, std):
    return (x - mean) / std
