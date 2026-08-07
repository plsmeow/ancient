"""Архитектуры моделей для Neuro Rotation."""

import torch
import torch.nn as nn

FEATURE_COUNT = 33
SEQ_LEN = 8
OUTPUT_SIZE = 2


class FlatMlp(nn.Module):
    """Плоское окно seq*features -> MLP. Временной сигнал сохраняется в окне."""

    def __init__(self, feature_count=FEATURE_COUNT, seq_len=SEQ_LEN, output_size=OUTPUT_SIZE):
        super().__init__()
        flat = feature_count * seq_len
        self.net = nn.Sequential(
            nn.Linear(flat, 128),
            nn.ReLU(),
            nn.Linear(128, 128),
            nn.ReLU(),
            nn.Linear(128, 64),
            nn.ReLU(),
            nn.Linear(64, 32),
            nn.ReLU(),
            nn.Linear(32, output_size),
        )

    def forward(self, x):
        # x: (batch, seq, feat) -> (batch, seq*feat)
        return self.net(x.flatten(start_dim=1))


class GruModel(nn.Module):
    """GRU(64) по §13. Берём последний timestep и подаём в Linear-голову."""

    def __init__(self, feature_count=FEATURE_COUNT, hidden=64, output_size=OUTPUT_SIZE):
        super().__init__()
        self.gru = nn.GRU(feature_count, hidden, num_layers=1, batch_first=True)
        self.head = nn.Sequential(
            nn.Linear(hidden, 64),
            nn.ReLU(),
            nn.Linear(64, 32),
            nn.ReLU(),
            nn.Linear(32, output_size),
        )

    def forward(self, x):
        # x: (batch, seq, feat)
        out, _ = self.gru(x)
        return self.head(out[:, -1, :])


def build(arch, feature_count=FEATURE_COUNT, seq_len=SEQ_LEN, output_size=OUTPUT_SIZE):
    if arch == "mlp":
        return FlatMlp(feature_count, seq_len, output_size)
    if arch == "gru":
        return GruModel(feature_count, 64, output_size)
    raise ValueError(f"unknown arch: {arch}")
