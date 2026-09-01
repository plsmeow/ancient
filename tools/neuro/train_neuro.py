#!/usr/bin/env python3
"""
Neuro Rotation trainer: raw per-tick CSV -> ONNX (TCN + MDN).

CSV columns (must match NeuroFeatureSchema.COLUMNS exactly):
  t,gcd,clean,yaw,pitch,dyaw,dpitch,has,tid,rx,ry,rz,bw,bh,dist,vis,on,atk,hp,ground,sprint

The CSV stores RAW tick state; features are derived here with the same formulas
as NeuroFeatureCollector.computeFeatures on the Java side, so the feature set can
be changed without re-recording datasets.

ONNX contract (normalization baked into the graph):
  input  "input"  : (batch, WINDOW * FEATURES) float32
  output "output" : (batch, 5 + K * (1 + 4 * H)) float32
                    [VERSION, K, H, WINDOW, FEATURES | pi(K) | mu(K*H*2) | log_sigma(K*H*2)]

Usage:
  python train_neuro.py --dataset <data.csv | data_dir> --out <model_name> [--epochs N] [--patience N] [--base <model>]
  python train_neuro.py --selftest <folder with selftest.csv + selftest_java.txt>
"""

from __future__ import annotations

import argparse
import json
import math
import os
import random
import sys
import time
from pathlib import Path

# Консоль Windows по умолчанию cp1251 — принудительно переводим вывод в UTF-8,
# иначе .ai train не сможет читать stdout процесса.
for _stream in ("stdout", "stderr"):
    _s = getattr(sys, _stream, None)
    if _s is not None and hasattr(_s, "reconfigure"):
        _s.reconfigure(encoding="utf-8", errors="replace")

SCHEMA_VERSION = 4

COLUMNS = ["t", "gcd", "clean", "yaw", "pitch", "dyaw", "dpitch", "has", "tid",
           "rx", "ry", "rz", "bw", "bh", "dist", "vis", "on", "atk", "hp", "ground", "sprint"]
COLUMN_COUNT = len(COLUMNS)

C_T, C_GCD, C_CLEAN, C_YAW, C_PITCH, C_DYAW, C_DPITCH, C_HAS, C_TID = range(9)
C_RX, C_RY, C_RZ, C_BW, C_BH, C_DIST, C_VIS, C_ON, C_ATK, C_HP, C_GROUND, C_SPRINT = range(9, 21)

WINDOW = 16
FEATURES = 16
INPUT_DIM = WINDOW * FEATURES
HORIZON = 4

F_DYAW, F_DPITCH, F_ERR_YAW, F_ERR_PITCH = 0, 1, 2, 3
F_HALF_YAW, F_HALF_PITCH, F_DIST, F_PITCH = 4, 5, 6, 7
F_HAS, F_VIS, F_ON, F_ATK, F_GROUND, F_SPRINT, F_HP, F_BH = 8, 9, 10, 11, 12, 13, 14, 15

YAW_SIGNED_FEATURES = (F_DYAW, F_ERR_YAW)

MDN_K = 7
TCN_CHANNELS = 96
TCN_KERNEL = 3
TCN_DILATIONS = (1, 2, 4, 8)
TCN_DROPOUT = 0.10
HEAD_DIM = 128

LOG_SIGMA_MIN = -3.0
LOG_SIGMA_MAX = 2.0

LABEL_CLIP_DEG = 25.0
FEATURE_STD_FLOOR = 0.05
NORM_CLIP = 8.0
LOSS_WEIGHT_SCALE = 4.0
LOSS_WEIGHT_CAP = 8.0

LR_MAX = 3e-3
WEIGHT_DECAY = 3e-4
EMA_DECAY = 0.999
INPUT_JITTER = 0.02
ENTROPY_WEIGHT = 0.02
MEAN_LOSS_WEIGHT = 0.15
MIN_EPOCH_FRACTION = 0.6
VAL_EPISODE_FRACTION = 0.12
SEED = 42

LOG_2PI = float(math.log(2.0 * math.pi))

MODELS_DIR = Path(".options/ai/models")


def log(msg: str) -> None:
    print(msg, flush=True)


def wrap_deg(np, value):
    f = np.fmod(value, 360.0)
    f = np.where(f >= 180.0, f - 360.0, f)
    f = np.where(f < -180.0, f + 360.0, f)
    return f


def compute_features(np, raw):
    """raw: (..., WINDOW, COLUMN_COUNT) -> (..., WINDOW, FEATURES)"""
    shape = raw.shape[:-1] + (FEATURES,)
    out = np.zeros(shape, dtype=np.float64)

    yaw = raw[..., C_YAW]
    pitch = raw[..., C_PITCH]
    rx = raw[..., C_RX]
    ry = raw[..., C_RY]
    rz = raw[..., C_RZ]
    has = raw[..., C_HAS] > 0.5

    hxz = np.sqrt(rx * rx + rz * rz)
    d3 = np.sqrt(rx * rx + ry * ry + rz * rz)
    # NeuroFeatureCollector.computeFeatures (Java) 1:1
    desired_yaw = wrap_deg(np, np.degrees(np.arctan2(rz, rx)) - 90.0)
    desired_pitch = -np.degrees(np.arctan2(ry, np.sqrt(rx * rx + rz * rz)))

    out[..., F_DYAW] = raw[..., C_DYAW]
    out[..., F_DPITCH] = raw[..., C_DPITCH]
    out[..., F_PITCH] = pitch

    out[..., F_ERR_YAW] = np.where(has, wrap_deg(np, desired_yaw - yaw), 0.0)
    out[..., F_ERR_PITCH] = np.where(has, desired_pitch - pitch, 0.0)
    out[..., F_HALF_YAW] = np.where(
        has, np.degrees(np.arctan2(raw[..., C_BW] * 0.5, np.maximum(hxz, 0.05))), 0.0)
    out[..., F_HALF_PITCH] = np.where(
        has, np.degrees(np.arctan2(raw[..., C_BH] * 0.5, np.maximum(d3, 0.05))), 0.0)
    out[..., F_DIST] = np.where(has, np.maximum(raw[..., C_DIST], 0.0), 0.0)
    out[..., F_HP] = np.where(has, np.clip(raw[..., C_HP] / 20.0, 0.0, 2.0), 0.0)
    out[..., F_BH] = np.where(has, raw[..., C_BH], 0.0)

    out[..., F_HAS] = has.astype(np.float64)
    out[..., F_VIS] = (raw[..., C_VIS] > 0.5).astype(np.float64)
    out[..., F_ON] = (raw[..., C_ON] > 0.5).astype(np.float64)
    out[..., F_ATK] = (raw[..., C_ATK] > 0.5).astype(np.float64)
    out[..., F_GROUND] = (raw[..., C_GROUND] > 0.5).astype(np.float64)
    out[..., F_SPRINT] = (raw[..., C_SPRINT] > 0.5).astype(np.float64)
    return out


def read_csv(np, path: Path):
    rows = []
    with path.open("r", encoding="utf-8") as fh:
        for raw_line in fh:
            line = raw_line.strip().lstrip("﻿")
            if not line or line.startswith("#"):
                continue
            if line.startswith("t,"):
                continue
            parts = line.split(",")
            if len(parts) != COLUMN_COUNT:
                continue
            try:
                rows.append([float(v) for v in parts])
            except ValueError:
                continue
    if not rows:
        return np.zeros((0, COLUMN_COUNT), dtype=np.float64)
    return np.asarray(rows, dtype=np.float64)


def gather_csv_files(path: Path):
    if path.is_dir():
        return sorted(p for p in path.glob("*.csv"))
    return [path]


def split_episodes(np, raw):
    """Contiguous runs of t incrementing by exactly 1 with clean == 1."""
    episodes = []
    n = raw.shape[0]
    min_len = WINDOW + HORIZON
    start = None
    for i in range(n):
        if raw[i, C_CLEAN] < 0.5:
            if start is not None and i - start >= min_len:
                episodes.append(raw[start:i])
            start = None
            continue
        if start is None:
            start = i
            continue
        if raw[i, C_T] != raw[i - 1, C_T] + 1.0:
            if i - start >= min_len:
                episodes.append(raw[start:i])
            start = i
    if start is not None and n - start >= min_len:
        episodes.append(raw[start:n])
    return episodes


def build_samples(np, episodes):
    xs = []
    ys = []
    for ep in episodes:
        m = ep.shape[0]
        last = m - HORIZON
        if last <= WINDOW - 1:
            continue
        idx = np.arange(WINDOW - 1, last)
        if idx.size == 0:
            continue
        offsets = np.arange(-(WINDOW - 1), 1)
        win_idx = idx[:, None] + offsets[None, :]
        raw_windows = ep[win_idx]

        label_off = np.arange(1, HORIZON + 1)
        lab_idx = idx[:, None] + label_off[None, :]
        labels = np.stack([ep[lab_idx, C_DYAW], ep[lab_idx, C_DPITCH]], axis=-1)

        keep = ep[idx, C_HAS] > 0.5
        # метка должна описывать движение по ТОЙ ЖЕ живой цели, иначе в обучение
        # попадают хвостовые тики после её потери (человек уводит прицел) и переводы
        # прицела на другого противника
        keep &= np.all(ep[lab_idx, C_HAS] > 0.5, axis=1)
        keep &= np.all(ep[lab_idx, C_TID] == ep[idx, C_TID][:, None], axis=1)
        keep &= np.all(np.abs(labels) <= LABEL_CLIP_DEG, axis=(1, 2))
        keep &= np.all(np.isfinite(labels), axis=(1, 2))
        if not np.any(keep):
            continue

        feats = compute_features(np, raw_windows[keep])
        xs.append(feats.reshape(feats.shape[0], INPUT_DIM))
        ys.append(labels[keep].reshape(-1, HORIZON * 2))
    return xs, ys


def build_net(torch, nn):
    class ChannelNorm(nn.Module):
        def __init__(self, channels):
            super().__init__()
            self.norm = nn.LayerNorm(channels)

        def forward(self, x):
            return self.norm(x.transpose(1, 2)).transpose(1, 2)

    class TemporalBlock(nn.Module):
        def __init__(self, in_ch, out_ch, kernel, dilation, dropout):
            super().__init__()
            pad = (kernel - 1) * dilation
            self.conv1 = nn.Conv1d(in_ch, out_ch, kernel, padding=pad, dilation=dilation)
            self.norm1 = ChannelNorm(out_ch)
            self.conv2 = nn.Conv1d(out_ch, out_ch, kernel, padding=pad, dilation=dilation)
            self.norm2 = ChannelNorm(out_ch)
            self.act = nn.GELU()
            self.drop = nn.Dropout(dropout)
            self.down = nn.Conv1d(in_ch, out_ch, 1) if in_ch != out_ch else None

        def forward(self, x):
            y = self.conv1(x)[:, :, :WINDOW]
            y = self.drop(self.act(self.norm1(y)))
            y = self.conv2(y)[:, :, :WINDOW]
            y = self.drop(self.act(self.norm2(y)))
            res = x if self.down is None else self.down(x)
            return self.act(y + res)

    class HwNet(nn.Module):
        def __init__(self, mean, std):
            super().__init__()
            self.register_buffer("mean", mean.view(1, 1, FEATURES))
            self.register_buffer("std", std.view(1, 1, FEATURES))
            self.register_buffer("header", torch.tensor(
                [float(SCHEMA_VERSION), float(MDN_K), float(HORIZON),
                 float(WINDOW), float(FEATURES)], dtype=torch.float32))

            blocks = []
            in_ch = FEATURES
            for dilation in TCN_DILATIONS:
                blocks.append(TemporalBlock(in_ch, TCN_CHANNELS, TCN_KERNEL, dilation, TCN_DROPOUT))
                in_ch = TCN_CHANNELS
            self.tcn = nn.Sequential(*blocks)

            self.attn = nn.Conv1d(TCN_CHANNELS, 1, 1)
            self.trunk = nn.Sequential(
                nn.Linear(TCN_CHANNELS * 2, HEAD_DIM),
                nn.GELU(),
                nn.Dropout(TCN_DROPOUT),
                nn.Linear(HEAD_DIM, HEAD_DIM),
                nn.GELU(),
            )
            self.fc_pi = nn.Linear(HEAD_DIM, MDN_K)
            self.fc_mu = nn.Linear(HEAD_DIM, MDN_K * HORIZON * 2)
            self.fc_log_sigma = nn.Linear(HEAD_DIM, MDN_K * HORIZON * 2)

            nn.init.zeros_(self.fc_log_sigma.bias)
            nn.init.normal_(self.fc_log_sigma.weight, std=0.01)
            with torch.no_grad():
                bias = torch.zeros(MDN_K, HORIZON, 2)
                denom = max(1, MDN_K - 1)
                for k in range(MDN_K):
                    bias[k, :, 0] = -5.0 + k * (10.0 / denom)
                    bias[k, :, 1] = -2.0 + k * (4.0 / denom)
                self.fc_mu.bias.copy_(bias.flatten())
                nn.init.normal_(self.fc_mu.weight, std=0.05)

        def params(self, inp):
            seq = inp.view(-1, WINDOW, FEATURES)
            seq = ((seq - self.mean) / self.std).clamp(-NORM_CLIP, NORM_CLIP)
            h = self.tcn(seq.transpose(1, 2))
            last = h[:, :, -1]
            pooled = (h * torch.softmax(self.attn(h), dim=2)).sum(dim=2)
            h = self.trunk(torch.cat([last, pooled], dim=1))
            pi_logits = self.fc_pi(h)
            mu = self.fc_mu(h).view(-1, MDN_K, HORIZON * 2)
            log_sigma = self.fc_log_sigma(h).clamp(LOG_SIGMA_MIN, LOG_SIGMA_MAX).view(-1, MDN_K, HORIZON * 2)
            return pi_logits, mu, log_sigma

        def forward(self, inp):
            pi_logits, mu, log_sigma = self.params(inp)
            head = self.header.unsqueeze(0).expand(pi_logits.shape[0], -1)
            return torch.cat([head, pi_logits, mu.flatten(1), log_sigma.flatten(1)], dim=1)

    return HwNet


def run_selftest(folder: Path) -> int:
    try:
        import numpy as np
    except ImportError:
        log("[Neuro] numpy not found")
        return 1
    csv_path = folder / "selftest.csv"
    java_path = folder / "selftest_java.txt"
    if not csv_path.is_file() or not java_path.is_file():
        log(f"[Neuro] selftest files missing in {folder}")
        return 1
    raw = read_csv(np, csv_path)
    if raw.shape[0] != WINDOW:
        log(f"[Neuro] selftest.csv has {raw.shape[0]} rows, expected {WINDOW}")
        return 1
    mine = compute_features(np, raw[None, :, :]).reshape(-1)
    theirs = np.asarray([float(v) for v in java_path.read_text(encoding="utf-8").split()], dtype=np.float64)
    if theirs.size != INPUT_DIM:
        log(f"[Neuro] selftest_java.txt has {theirs.size} values, expected {INPUT_DIM}")
        return 1
    diff = np.abs(mine - theirs)
    worst = int(np.argmax(diff))
    log(f"[Neuro] selftest max|diff|={diff.max():.8f} at tick={worst // FEATURES} feature={worst % FEATURES}")
    if diff.max() > 1e-3:
        log("[Neuro] SELFTEST FAILED — Java and python features disagree")
        return 1
    log("[Neuro] selftest ok")
    return 0


def parse_args():
    parser = argparse.ArgumentParser(description="Train Neuro Rotation model (TCN+MDN)")
    parser.add_argument("--dataset", help="Path to raw CSV or a directory of CSVs")
    parser.add_argument("--out", help="Output model name (directory created)")
    parser.add_argument("--epochs", type=int, default=400, help="Training epochs")
    parser.add_argument("--patience", type=int, default=10,
                        help="Early stopping patience in validations (0 = обучение до конца)")
    parser.add_argument("--base", help="Base model name for fine-tuning (model.pt)")
    parser.add_argument("--selftest", metavar="FOLDER",
                        help="Проверить совпадение фич с Java (selftest.csv + selftest_java.txt)")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.selftest:
        return run_selftest(Path(args.selftest))
    if not args.dataset or not args.out:
        log("Ошибка: нужны --dataset и --out (или --selftest FOLDER)")
        return 2

    data_path = Path(args.dataset)
    if not data_path.exists():
        log(f"Датасет не найден: {data_path}")
        return 1

    try:
        import numpy as np
        import torch
        import torch.nn as nn
    except ImportError as exc:
        log(f"Ошибка: не хватает библиотеки: {exc}")
        return 1

    log(f"[Neuro] python={sys.version.split()[0]} torch={torch.__version__} "
        f"cuda_available={torch.cuda.is_available()}")

    threads = max(1, os.cpu_count() or 4)
    torch.set_num_threads(threads)
    try:
        torch.set_num_interop_threads(max(1, threads // 2))
    except RuntimeError:
        pass
    torch.manual_seed(SEED)
    random.seed(SEED)
    rng = np.random.default_rng(SEED)

    files = gather_csv_files(data_path)
    episodes = []
    total_rows = 0
    for f in files:
        raw = read_csv(np, f)
        total_rows += raw.shape[0]
        eps = split_episodes(np, raw)
        episodes.extend(eps)
        log(f"[Neuro] {f.name}: {raw.shape[0]} строк -> {len(eps)} эпизодов")
    if not episodes:
        log("[Neuro] нет пригодных эпизодов (нужно минимум "
            f"{WINDOW + HORIZON} чистых подряд тиков с целью)")
        return 1

    xs, ys = build_samples(np, episodes)
    if not xs:
        log("[Neuro] ни одно окно не пережило фильтрацию")
        return 1

    ep_sizes = [a.shape[0] for a in xs]
    order = rng.permutation(len(xs))
    val_target = min(len(xs) - 1, max(1, int(round(len(xs) * VAL_EPISODE_FRACTION))))
    val_ids = set(order[:val_target].tolist())

    x_train_np = np.concatenate([xs[i] for i in range(len(xs)) if i not in val_ids], axis=0)
    y_train_np = np.concatenate([ys[i] for i in range(len(ys)) if i not in val_ids], axis=0)
    x_val_np = np.concatenate([xs[i] for i in range(len(xs)) if i in val_ids], axis=0)
    y_val_np = np.concatenate([ys[i] for i in range(len(ys)) if i in val_ids], axis=0)

    log(f"[Neuro] строк={total_rows} эпизодов={len(episodes)} окон={sum(ep_sizes)} "
        f"train={x_train_np.shape[0]} val={x_val_np.shape[0]} (split по эпизодам)")
    if x_train_np.shape[0] < 64 or x_val_np.shape[0] < 8:
        log("[Neuro] слишком мало данных — запиши больше боёв перед обучением")
        return 1

    x_train = torch.tensor(x_train_np, dtype=torch.float32)
    y_train = torch.tensor(y_train_np, dtype=torch.float32)
    x_val = torch.tensor(x_val_np, dtype=torch.float32)
    y_val = torch.tensor(y_val_np, dtype=torch.float32)

    # Зеркалим по знаку yaw: движения мыши симметричны, это удваивает данные
    mirror = torch.ones(INPUT_DIM)
    for t in range(WINDOW):
        for c in YAW_SIGNED_FEATURES:
            mirror[t * FEATURES + c] = -1.0
    label_mirror = torch.tensor([-1.0, 1.0] * HORIZON)
    x_train = torch.cat([x_train, x_train * mirror], dim=0)
    y_train = torch.cat([y_train, y_train * label_mirror], dim=0)
    train_count = x_train.size(0)

    device = torch.device("cpu")
    if torch.cuda.is_available():
        try:
            torch.zeros(8, device="cuda").mul_(2.0)
            device = torch.device("cuda")
            log(f"[Neuro] gpu={torch.cuda.get_device_name(0)}")
        except Exception as exc:
            log(f"[Neuro] CUDA непригодна ({exc}); обучение на CPU")

    seq = x_train.view(train_count, WINDOW, FEATURES)
    feat_mean = seq.mean(dim=(0, 1))
    # пол по масштабу признака, а не 1e-3: иначе константный в датасете признак
    # (например bh, если все цели стояли) на инференсе улетает в сотни сигм
    feat_std = seq.std(dim=(0, 1)).clamp_min(FEATURE_STD_FLOOR)

    HwNet = build_net(torch, nn)
    model = HwNet(feat_mean, feat_std).to(device)
    ema_model = HwNet(feat_mean, feat_std).to(device)

    if args.base:
        base_path = MODELS_DIR / args.base / "model.pt"
        if not base_path.exists():
            log(f"Ошибка: base модель не найдена: {base_path}")
            return 1
        model.load_state_dict(torch.load(base_path, map_location="cpu"))
        log(f"[Neuro] загружена base модель: {args.base}")

    n_params = sum(p.numel() for p in model.parameters())

    x_train, y_train = x_train.to(device), y_train.to(device)
    x_val, y_val = x_val.to(device), y_val.to(device)
    x_std = x_train.std(dim=0).clamp_min(1e-3)

    batch_size = int(min(max(32, train_count // 24), 256))
    steps_per_epoch = max(1, (train_count + batch_size - 1) // batch_size)
    optimizer = torch.optim.AdamW(model.parameters(), lr=LR_MAX, weight_decay=WEIGHT_DECAY)
    scheduler = torch.optim.lr_scheduler.OneCycleLR(
        optimizer, max_lr=LR_MAX, total_steps=args.epochs * steps_per_epoch,
        pct_start=0.15, div_factor=10.0, final_div_factor=50.0)

    ema = {k: v.detach().clone().float() for k, v in model.state_dict().items()}
    ema_keys = [k for k, v in ema.items() if v.dtype.is_floating_point]

    def mdn_terms(net, xb, target):
        pi_logits, mu, log_sigma = net.params(xb)
        sigma = torch.exp(log_sigma)
        z = (target.unsqueeze(1) - mu) / sigma
        comp_logp = (-0.5 * LOG_2PI - log_sigma - 0.5 * z * z).sum(dim=2)
        log_pi = torch.log_softmax(pi_logits, dim=1)
        log_mix = torch.logsumexp(log_pi + comp_logp, dim=1)
        pi = torch.softmax(pi_logits, dim=1)
        mean = (pi.unsqueeze(2) * mu).sum(dim=1)
        entropy = -(pi * log_pi).sum(dim=1)
        return log_mix, mean, entropy

    def training_loss(xb, yb):
        log_mix, mean, entropy = mdn_terms(model, xb, yb)
        w = 1.0 + torch.clamp(LOSS_WEIGHT_SCALE * yb[:, :2].abs().sum(dim=1), max=LOSS_WEIGHT_CAP)
        nll = -(log_mix * w).mean() / w.mean()
        mean_loss = torch.nn.functional.smooth_l1_loss(mean, yb)
        return nll + MEAN_LOSS_WEIGHT * mean_loss - ENTROPY_WEIGHT * entropy.mean()

    def validate(net):
        net.eval()
        with torch.inference_mode():
            log_mix, mean, _ = mdn_terms(net, x_val, y_val)
            nll = float(-log_mix.mean().item())
            mae = float((mean[:, :2] - y_val[:, :2]).abs().mean().item())
        net.train()
        return nll, mae

    log(f"[Neuro] TCN ch={TCN_CHANNELS} dilations={TCN_DILATIONS} K={MDN_K} H={HORIZON} params={n_params}")
    log(f"[Neuro] train={train_count} (mirrored) batch={batch_size} device={device}")

    best_val = float("inf")
    best_mae = float("nan")
    best_state = None
    val_every = max(1, args.epochs // 60)
    patience = max(1, args.patience)
    min_epoch = int(args.epochs * MIN_EPOCH_FRACTION)
    bad = 0

    model.train()
    for epoch in range(1, args.epochs + 1):
        order_t = torch.randperm(train_count, device=device)
        acc = 0.0
        nb = 0
        for start in range(0, train_count, batch_size):
            idx = order_t[start:start + batch_size]
            xb = x_train[idx]
            if INPUT_JITTER > 0.0:
                xb = xb + torch.randn_like(xb) * (x_std * INPUT_JITTER)
            loss = training_loss(xb, y_train[idx])
            optimizer.zero_grad(set_to_none=True)
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            optimizer.step()
            scheduler.step()
            with torch.no_grad():
                sd = model.state_dict()
                torch._foreach_mul_([ema[k] for k in ema_keys], EMA_DECAY)
                torch._foreach_add_([ema[k] for k in ema_keys],
                                    [sd[k].float() for k in ema_keys], alpha=1.0 - EMA_DECAY)
            acc += float(loss.item())
            nb += 1
        train_loss = acc / max(1, nb)

        val_loss, val_mae = best_val, best_mae
        run_val = epoch == 1 or epoch % val_every == 0 or epoch == args.epochs
        if run_val:
            raw_nll, raw_mae = validate(model)
            ema_model.load_state_dict(ema)
            ema_nll, ema_mae = validate(ema_model)
            use_ema = ema_nll <= raw_nll
            val_loss, val_mae = (ema_nll, ema_mae) if use_ema else (raw_nll, raw_mae)
            if val_loss < best_val - 1e-6:
                best_val, best_mae, bad = val_loss, val_mae, 0
                source = ema if use_ema else model.state_dict()
                best_state = {k: v.detach().cpu().clone() for k, v in source.items()}
            else:
                bad += 1

        if epoch == 1 or epoch % max(1, args.epochs // 25) == 0 or epoch == args.epochs:
            log(f"Epoch {epoch}/{args.epochs}  train {train_loss:.4f}  val {val_loss:.4f}  "
                f"yaw_mae {val_mae:.3f}°")

        if run_val and args.patience > 0 and bad >= patience and epoch >= min_epoch:
            log(f"[Neuro] early stop @ epoch {epoch} (val не улучшался {patience} валидаций)")
            break

    if best_state is not None:
        model.load_state_dict(best_state)

    model.eval()
    model_cpu = model.to("cpu")
    dummy = torch.zeros(1, INPUT_DIM, dtype=torch.float32)
    with torch.inference_mode():
        probe = model_cpu(dummy)
    expected = 5 + MDN_K * (1 + 4 * HORIZON)
    if probe.shape[1] != expected:
        log(f"Ошибка: ширина выхода {probe.shape[1]} != {expected}")
        return 1

    final_loss, final_mae = validate(model_cpu)
    model_cpu.eval()

    out_dir = MODELS_DIR / args.out
    out_dir.mkdir(parents=True, exist_ok=True)

    # PyTorch checkpoint (для --base / .ai improve)
    torch.save(model_cpu.state_dict(), out_dir / "model.pt")

    kwargs = {
        "input_names": ["input"],
        "output_names": ["output"],
        "dynamic_axes": {"input": {0: "batch"}, "output": {0: "batch"}},
        "opset_version": 17,
    }
    onnx_path = out_dir / "model.onnx"
    try:
        torch.onnx.export(model_cpu, dummy, str(onnx_path), dynamo=False, **kwargs)
    except TypeError:
        torch.onnx.export(model_cpu, dummy, str(onnx_path), **kwargs)
    log(f"[Neuro] ONNX экспортирован -> {onnx_path} ({onnx_path.stat().st_size // 1024} KB)")

    # Golden test: PyTorch vs ONNX Runtime должны совпадать
    try:
        import onnxruntime as ort
        session = ort.InferenceSession(str(onnx_path))
        test_input = torch.randn(3, INPUT_DIM)
        with torch.inference_mode():
            torch_out = model_cpu(test_input).cpu().numpy()
        ort_out = session.run(None, {"input": test_input.numpy()})[0]
        max_diff = float(np.abs(torch_out - ort_out).max())
        if max_diff > 1e-4:
            log(f"Ошибка: golden test FAILED: max diff {max_diff:.2e} > 1e-4")
            return 1
        log(f"[Neuro] golden test ok: max diff {max_diff:.2e}")
    except ImportError:
        log("[Neuro] onnxruntime не найден — golden test пропущен")

    meta_out = {
        "schemaVersion": SCHEMA_VERSION,
        "featureCount": FEATURES,
        "seqLen": WINDOW,
        "outputSize": expected,
        "arch": "tcn-mdn",
        "horizon": HORIZON,
        "mdnK": MDN_K,
        "mean": feat_mean.tolist(),
        "std": feat_std.tolist(),
        "labelScale": [1.0, 1.0],
        "trainSamples": int(sum(a.shape[0] for a in xs)),
        "valLoss": float(final_loss),
        "yawMae": float(final_mae),
        "pitchMae": float(final_mae),
        "source": "csv",
        "createdAt": time.strftime("%Y-%m-%d %H:%M:%S"),
        "baseModel": args.base if args.base else None,
    }
    with open(out_dir / "meta.json", "w", encoding="utf-8") as fh:
        json.dump(meta_out, fh, indent=2, ensure_ascii=False)

    log(f"Final: val_loss {final_loss:.4f}, yaw_mae {final_mae:.3f}°")
    log(f"✓ Модель сохранена: {out_dir}")
    log(f"  Используйте: .ai load {args.out}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except SystemExit:
        raise
    except Exception as exc:
        log(f"Ошибка: {exc}")
        raise
