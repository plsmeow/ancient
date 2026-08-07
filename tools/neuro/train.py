#!/usr/bin/env python3
"""Обучение Neuro Rotation модели. Поддерживает MLP и GRU, экспорт в ONNX."""

import argparse
import json
import sys
from pathlib import Path

# Консоль Windows по умолчанию cp1251 — принудительно переводим вывод в UTF-8,
# иначе torch.onnx.export падает на своих же unicode-символах,
# и .ai train не сможет читать stdout процесса.
for _stream in ("stdout", "stderr"):
    _s = getattr(sys, _stream, None)
    if _s is not None and hasattr(_s, "reconfigure"):
        _s.reconfigure(encoding="utf-8", errors="replace")

import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import DataLoader, TensorDataset

import dataset
import model as model_arch


def parse_args():
    parser = argparse.ArgumentParser(description="Train Neuro Rotation model")
    parser.add_argument("--dataset", required=True, help="Path to .jsonl dataset")
    parser.add_argument("--out", required=True, help="Output model name (directory created)")
    parser.add_argument("--epochs", type=int, default=100, help="Training epochs")
    parser.add_argument("--arch", choices=["mlp", "gru"], default="mlp", help="Architecture")
    parser.add_argument("--base", help="Base model for fine-tuning (.ai improve)")
    parser.add_argument("--lr", type=float, default=1e-3, help="Learning rate")
    parser.add_argument("--batch-size", type=int, default=32, help="Batch size")
    parser.add_argument("--patience", type=int, default=10,
                        help="Early stopping patience (0 = выключен, обучение до конца)")
    parser.add_argument("--quality", default="CLEAN", help="Comma-separated quality filter")
    return parser.parse_args()


def train_epoch(model, loader, criterion, optimizer, device):
    model.train()
    total_loss = 0.0
    for x_batch, y_batch in loader:
        x_batch, y_batch = x_batch.to(device), y_batch.to(device)
        optimizer.zero_grad()
        pred = model(x_batch)
        loss = criterion(pred, y_batch)
        loss.backward()
        optimizer.step()
        total_loss += loss.item() * len(x_batch)
    return total_loss / len(loader.dataset)


@torch.no_grad()
def validate(model, loader, criterion, device):
    model.eval()
    total_loss = 0.0
    yaw_err, pitch_err = 0.0, 0.0
    count = 0
    for x_batch, y_batch in loader:
        x_batch, y_batch = x_batch.to(device), y_batch.to(device)
        pred = model(x_batch)
        loss = criterion(pred, y_batch)
        total_loss += loss.item() * len(x_batch)

        err = torch.abs(pred - y_batch)
        yaw_err += err[:, 0].sum().item()
        pitch_err += err[:, 1].sum().item()
        count += len(x_batch)

    return total_loss / len(loader.dataset), yaw_err / count, pitch_err / count


def export_onnx(model, out_dir, arch, feature_count, seq_len):
    """
    Экспорт в ONNX.

    opset 18, а не 13 из спеки: torch экспортирует граф сразу в 18, а попытка
    сконвертировать вниз падает (нет adapter для Shape с 15). ONNX Runtime 1.28
    поддерживает 18 нативно, так что понижать версию незачем.
    """
    model.eval()
    dummy_input = torch.randn(1, seq_len, feature_count)
    onnx_path = out_dir / "model.onnx"

    torch.onnx.export(
        model,
        dummy_input,
        onnx_path,
        input_names=["input"],
        output_names=["output"],
        opset_version=18,
        dynamic_axes={"input": {0: "batch"}, "output": {0: "batch"}},
    )
    return onnx_path


def golden_test(onnx_path, torch_model, feature_count, seq_len):
    """Golden test: PyTorch vs ONNX Runtime совпадают до 1e-5."""
    import onnxruntime as ort

    torch_model.eval()
    session = ort.InferenceSession(str(onnx_path))

    test_input = torch.randn(3, seq_len, feature_count)
    with torch.no_grad():
        torch_out = torch_model(test_input).cpu().numpy()

    ort_out = session.run(None, {"input": test_input.numpy()})[0]

    max_diff = np.abs(torch_out - ort_out).max()
    if max_diff > 1e-5:
        raise RuntimeError(f"Golden test failed: max diff {max_diff:.2e} > 1e-5")

    print(f"✓ Golden test passed: max diff {max_diff:.2e}")


def main():
    args = parse_args()

    dataset_path = Path(args.dataset)
    if not dataset_path.exists():
        print(f"Датасет не найден: {dataset_path}", file=sys.stderr)
        return 1

    # Читаем мету и проверяем совместимость
    meta = dataset.read_meta(dataset_path)
    try:
        meta = dataset.validate_meta(meta)
    except dataset.DatasetError as e:
        print(f"Ошибка датасета: {e}", file=sys.stderr)
        return 1

    print(f"Датасет: {meta['name']}, сэмплов {meta['samples']}, источник {meta['source']}")

    # Загружаем сэмплы
    quality_filter = tuple(q.strip() for q in args.quality.split(","))
    try:
        features, labels, ticks, stats = dataset.load_samples(dataset_path, quality_filter)
        print(f"Загружено {len(features)} сэмплов после фильтрации "
              f"(пропущено quality={stats['skipped_quality']}, bad={stats['skipped_bad']}, "
              f"нечеловек={stats['skipped_source']})")

        # Строим окна
        x, y = dataset.build_windows(features, labels, ticks, dataset.SEQ_LEN)
        print(f"Построено {len(x)} окон (seq={dataset.SEQ_LEN})")

        # Split
        x_train, y_train, x_val, y_val = dataset.split(x, y, val_fraction=0.2)
    except dataset.DatasetError as e:
        print(f"Ошибка датасета: {e}", file=sys.stderr)
        return 1
    print(f"Train {len(x_train)}, val {len(x_val)}")

    # Нормализация — считается ТОЛЬКО по train
    mean, std = dataset.compute_norm(x_train)
    x_train = dataset.normalize(x_train, mean, std)
    x_val = dataset.normalize(x_val, mean, std)

    # DataLoader
    train_ds = TensorDataset(torch.from_numpy(x_train), torch.from_numpy(y_train))
    val_ds = TensorDataset(torch.from_numpy(x_val), torch.from_numpy(y_val))
    train_loader = DataLoader(train_ds, batch_size=args.batch_size, shuffle=True)
    val_loader = DataLoader(val_ds, batch_size=args.batch_size, shuffle=False)

    # Модель
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    net = model_arch.build(args.arch, dataset.FEATURE_COUNT, dataset.SEQ_LEN, dataset.OUTPUT_SIZE)

    # Загружаем base model для improve
    if args.base:
        base_path = Path(".options/ai/models") / args.base / "model.pt"
        if not base_path.exists():
            print(f"Base модель не найдена: {base_path}", file=sys.stderr)
            return 1
        net.load_state_dict(torch.load(base_path, map_location="cpu"))
        print(f"Загружена base модель: {args.base}")

    net.to(device)

    # L1 loss — лучше для угловых дельт, чем L2
    criterion = nn.L1Loss()
    optimizer = torch.optim.Adam(net.parameters(), lr=args.lr)

    # Early stopping. patience=0 — выключен: обучение идёт все эпохи до конца,
    # сохраняются веса ПОСЛЕДНЕЙ эпохи (для .ai train из игры).
    early_stopping = args.patience > 0
    best_val_loss = float("inf")
    best_epoch = 0
    patience_counter = 0
    best_state = None

    print(f"Обучение {args.arch} на {device}, {args.epochs} эпох, lr={args.lr}"
          + ("" if early_stopping else ", early stopping выключен"))

    for epoch in range(1, args.epochs + 1):
        train_loss = train_epoch(net, train_loader, criterion, optimizer, device)
        val_loss, yaw_mae, pitch_mae = validate(net, val_loader, criterion, device)

        print(f"Epoch {epoch:3d}/{args.epochs}  "
              f"train {train_loss:.4f}  val {val_loss:.4f}  "
              f"yaw_mae {yaw_mae:.3f}°  pitch_mae {pitch_mae:.3f}°")

        if val_loss < best_val_loss:
            best_val_loss = val_loss
            best_epoch = epoch
            best_state = {k: v.cpu().clone() for k, v in net.state_dict().items()}
            patience_counter = 0
        else:
            patience_counter += 1
            if early_stopping and patience_counter >= args.patience:
                print(f"Early stopping на эпохе {epoch}: val не улучшался {args.patience} эпох подряд. "
                      f"Лучшая эпоха {best_epoch} (val {best_val_loss:.4f}) — восстановлены её веса. "
                      f"Это штатная защита от переобучения, а не обрыв: изменить можно через --patience N")
                break

    # При включённом early stopping восстанавливаем лучшую эпоху;
    # при выключенном оставляем последнюю — обучение прошло полностью.
    if early_stopping and best_state:
        net.load_state_dict(best_state)
        net.to(device)

    # Финальная валидация
    final_loss, final_yaw, final_pitch = validate(net, val_loader, criterion, device)
    if early_stopping:
        print(f"\nFinal: val_loss {final_loss:.4f} (лучшая эпоха {best_epoch}), "
              f"yaw_mae {final_yaw:.3f}°, pitch_mae {final_pitch:.3f}°")
    else:
        print(f"\nFinal: val_loss {final_loss:.4f} (эпоха {epoch}/{args.epochs}, обучено полностью), "
              f"yaw_mae {final_yaw:.3f}°, pitch_mae {final_pitch:.3f}°")
        if best_epoch != epoch:
            print(f"Минимум val был на эпохе {best_epoch} ({best_val_loss:.4f}) — "
                  f"если последняя хуже, обучите заново с --patience 10")

    # Сохраняем
    out_dir = Path(".options/ai/models") / args.out
    out_dir.mkdir(parents=True, exist_ok=True)

    # PyTorch checkpoint (для improve)
    torch.save(net.state_dict(), out_dir / "model.pt")

    # ONNX
    onnx_path = export_onnx(net, out_dir, args.arch, dataset.FEATURE_COUNT, dataset.SEQ_LEN)
    print(f"Экспортировано в ONNX: {onnx_path}")

    # Golden test
    try:
        golden_test(onnx_path, net, dataset.FEATURE_COUNT, dataset.SEQ_LEN)
    except Exception as e:
        print(f"Golden test FAILED: {e}", file=sys.stderr)
        return 1

    # Мета
    meta_out = {
        "schemaVersion": dataset.SCHEMA_VERSION,
        "featureCount": dataset.FEATURE_COUNT,
        "seqLen": dataset.SEQ_LEN,
        "arch": args.arch,
        "outputSize": dataset.OUTPUT_SIZE,
        "mean": mean.tolist(),
        "std": std.tolist(),
        "labelScale": [1.0, 1.0],
        "trainSamples": len(x_train),
        "valLoss": float(final_loss),
        "yawMae": float(final_yaw),
        "pitchMae": float(final_pitch),
        "source": meta.get("source", "UNKNOWN"),
        "createdAt": meta.get("createdAt", ""),
        "baseModel": args.base if args.base else None,
    }

    with open(out_dir / "meta.json", "w", encoding="utf-8") as f:
        json.dump(meta_out, f, indent=2)

    print(f"\n✓ Модель сохранена: {out_dir}")
    print(f"  Используйте: .ai load {args.out}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
