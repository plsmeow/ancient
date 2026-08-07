#!/usr/bin/env python3
"""
Генерирует синтетический датасет для проверки пайплайна обучения.
Метка выводится из фич детерминированно, поэтому модель обязана сойтись —
если не сходится, баг в пайплайне, а не в данных.
"""

import argparse
import json
import math
from pathlib import Path

FEATURE_COUNT = 33
TARGET_DELTA_YAW = 21
TARGET_DELTA_PITCH = 22
PREV_DELTA_YAW = 19
PREV_DELTA_PITCH = 20
TARGET_DISTANCE = 15


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default=".options/ai/datasets/synthetic.jsonl")
    parser.add_argument("--samples", type=int, default=2000)
    args = parser.parse_args()

    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)

    rows = []
    prev_dy, prev_dp = 0.0, 0.0

    for i in range(args.samples):
        t = i * 0.05
        feats = [0.0] * FEATURE_COUNT

        # Цель кружит вокруг игрока
        target_dy = 25.0 * math.sin(t)
        target_dp = 8.0 * math.cos(t * 0.7)

        feats[TARGET_DELTA_YAW] = target_dy
        feats[TARGET_DELTA_PITCH] = target_dp
        feats[PREV_DELTA_YAW] = prev_dy
        feats[PREV_DELTA_PITCH] = prev_dp
        feats[TARGET_DISTANCE] = 3.0 + 0.5 * math.sin(t * 0.3)

        # Метка: сглаженное приближение к цели с инерцией.
        # Простая, но нелинейная функция фич.
        label_dy = 0.55 * target_dy + 0.2 * prev_dy
        label_dp = 0.55 * target_dp + 0.2 * prev_dp

        rows.append({
            "f": [round(v, 5) for v in feats],
            "y": [round(label_dy, 5), round(label_dp, 5)],
            "q": "CLEAN",
            "s": "HUMAN",
            "t": i,
        })

        prev_dy, prev_dp = label_dy, label_dp

    with open(out_path, "w", encoding="utf-8") as f:
        for row in rows:
            f.write(json.dumps(row) + "\n")

    name = out_path.stem
    meta = {
        "schemaVersion": 2,
        "name": name,
        "mode": "synthetic",
        "source": "HUMAN",
        "samples": len(rows),
        "featureCount": FEATURE_COUNT,
        "seqLen": 8,
        "outputSize": 2,
        "qualityHistogram": {
            "clean": len(rows), "transition": 0, "targetSwitch": 0,
            "occluded": 0, "invalid": 0,
        },
        "balance": {},
        "createdAt": "synthetic",
    }

    meta_path = out_path.parent / (name + ".meta.json")
    with open(meta_path, "w", encoding="utf-8") as f:
        json.dump(meta, f, indent=2)

    print(f"Wrote {len(rows)} samples to {out_path}")


if __name__ == "__main__":
    main()
