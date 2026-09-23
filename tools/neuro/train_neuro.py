#!/usr/bin/env python3
"""
Скрипт автономного обучения нейросетевой модели наводки (Rockstar Neuro).
Обучает GRU-модель по боевым логам (.csv) и сохраняет веса в JSON для Rockstar.
"""

import argparse
import os
import sys

# Добавляем модуль trainer из ресурсов мода
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
TRAINER_DIR = os.path.join(SCRIPT_DIR, "src", "main", "resources", "assets", "rockstar", "neuro", "trainer")
sys.path.insert(0, TRAINER_DIR)

try:
    import numpy as np
    import torch
    import train_aura
except ImportError as e:
    print(f"[!] Ошибка импорта: {e}")
    print("[!] Убедитесь, что установлены numpy и torch (python3 -m pip install numpy torch)")
    sys.exit(1)


def main():
    parser = argparse.ArgumentParser(description="Обучение нейро-модели наводки Rockstar")
    parser.add_argument(
        "--data",
        default=os.path.join(SCRIPT_DIR, "run", "Rockstar", "neuro", "data"),
        help="Путь к папке с датасетами .csv (по умолчанию: run/Rockstar/neuro/data)"
    )
    parser.add_argument(
        "--name",
        default="meow",
        help="Имя модели (по умолчанию: meow)"
    )
    parser.add_argument(
        "--out",
        default=None,
        help="Путь к выходящему файлу модели .json (по умолчанию: run/Rockstar/neuro/<name>.json)"
    )
    parser.add_argument(
        "--epochs",
        type=int,
        default=100,
        help="Количество эпох обучения (0 — авто, пока падает ошибка validation, по умолчанию: 100)"
    )
    parser.add_argument(
        "--seeds",
        type=int,
        default=1,
        help="Количество сидов обучения для выбора лучшей модели (по умолчанию: 1)"
    )
    parser.add_argument(
        "--hidden",
        type=int,
        default=32,
        help="Количество скрытых нейронов GRU (по умолчанию: 32)"
    )
    parser.add_argument(
        "--activate",
        action="store_true",
        help="Сделать эту модель активной в active.txt"
    )

    args = parser.parse_args()

    out_file = args.out
    if out_file is None:
        out_file = os.path.join(SCRIPT_DIR, "run", "Rockstar", "neuro", f"{args.name}.json")

    os.makedirs(os.path.dirname(out_file), exist_ok=True)

    print("=" * 60)
    print("   Rockstar Neuro Trainer")
    print("=" * 60)
    print(f"[*] Датасеты: {args.data}")
    print(f"[*] Модель:   {out_file}")
    print(f"[*] Эпохи:    {args.epochs if args.epochs > 0 else 'авто'}")
    print(f"[*] PyTorch:  {torch.__version__} (CUDA: {torch.cuda.is_available()})")
    print("=" * 60)

    # Передаём параметры в train_aura
    sys.argv = [
        "train_aura.py",
        "--data", args.data,
        "--out", out_file,
        "--epochs", str(args.epochs),
        "--seeds", str(args.seeds),
        "--hidden", str(args.hidden)
    ]

    ret = train_aura.main()
    if ret == 0:
        if args.activate:
            active_file = os.path.join(SCRIPT_DIR, "run", "Rockstar", "neuro", "active.txt")
            with open(active_file, "w", encoding="utf-8") as f:
                f.write(args.name)
            print(f"[+] Модель {args.name} назначена активной в active.txt")

        print("\n" + "=" * 60)
        print(f"[✓] Обучение успешно завершено!")
        print(f"[✓] Модель сохранена в: {out_file}")
        print(f"[i] В игре напишите команду: .neuro load {args.name}")
        print(f"[i] Или проверьте статус:   .neuro status")
        print("=" * 60)
    else:
        print(f"\n[!] Обучение завершилось с кодом {ret}")

    return ret


if __name__ == "__main__":
    sys.exit(main() or 0)
