import json
import os

import numpy as np
import torch
import torch.nn as nn

WINDOW = 40
STRIDE = 10
SEQ = 64
EPS = 1e-6
RHO_LIMIT = 0.95
PATIENCE = 100
AUTO_CAP = 1200
BATCH = 256
LR = 1e-2
MIX = 3


def wrap(a):
    return (a + 180.0) % 360.0 - 180.0


def quantize(value, gcd):
    return np.round(value / gcd) * gcd if gcd > 0 else value


def squash(u, limit):
    return float(np.clip(np.sinh(np.clip(u, -8.0, 8.0)), -limit, limit))


def unsquash(value):
    return float(np.arcsinh(value))


class Policy(nn.Module):
    def __init__(self, features, hidden, mix=MIX):
        super().__init__()
        self.hidden = hidden
        self.mix = mix
        self.gru = nn.GRU(features, hidden, batch_first=True)
        self.head = nn.Linear(hidden, 1 + 6 * mix)

    def forward(self, x, state=None):
        out, state = self.gru(x, state)
        head = self.head(out)
        parts = head[..., 1:].unflatten(-1, (self.mix, 6))
        return (parts[..., 1:3], parts[..., 3:5].clamp(-4.0, 1.5), head[..., 0:1],
                torch.tanh(parts[..., 5]) * RHO_LIMIT,
                torch.log_softmax(parts[..., 0], dim=-1), state)


def loss_of(mu, log_sigma, extra, rho, logw, y):
    z = (y[..., 0:4:2].unsqueeze(-2) - mu) / log_sigma.exp()
    d = (1.0 - rho ** 2).clamp(min=1e-4)
    per = ((z[..., 0] ** 2 - 2 * rho * z[..., 0] * z[..., 1] + z[..., 1] ** 2) / (2 * d)
           + log_sigma.sum(-1) + 0.5 * torch.log(d))
    alive = y[..., 1]
    live = (-torch.logsumexp(logw - per, dim=-1) * alive).sum() / alive.sum().clamp(min=1.0)
    valid = y[..., 5]
    bce = nn.functional.binary_cross_entropy_with_logits(
        extra.squeeze(-1), y[..., 4], reduction="none")
    return live + (bce * valid).sum() / valid.sum().clamp(min=1.0)


def chunk(sequences):
    xs, ys = [], []
    for x, y in sequences:
        for i in range(0, len(x) - 16, SEQ):
            piece = x[i:i + SEQ]
            if len(piece) < 16:
                continue
            pad = SEQ - len(piece)
            xs.append(np.pad(piece, ((0, pad), (0, 0))))
            ys.append(np.pad(y[i:i + SEQ], ((0, pad), (0, 0))))
    return torch.tensor(np.array(xs)), torch.tensor(np.array(ys))


def train_policy(sequences, features, hidden, epochs, seed, quiet=False, mix=MIX):
    torch.manual_seed(seed)
    device = "cuda" if torch.cuda.is_available() else "cpu"
    x, y = chunk(sequences)
    x, y = x.to(device), y.to(device)
    order = torch.randperm(len(x), generator=torch.Generator().manual_seed(seed)).to(device)
    split = max(1, int(len(x) * 0.15))
    val, tr = order[:split], order[split:]

    model = Policy(features, hidden, mix).to(device)
    opt = torch.optim.Adam(model.parameters(), lr=LR, weight_decay=1e-5)
    auto = epochs <= 0
    limit = AUTO_CAP if auto else epochs
    every = 40 if auto else max(1, epochs // 10)
    best, best_state, stall = float("inf"), None, 0
    for epoch in range(limit):
        model.train()
        perm = tr[torch.randperm(len(tr), device=device)]
        for i in range(0, len(perm), BATCH):
            idx = perm[i:i + BATCH]
            opt.zero_grad()
            loss_of(*model(x[idx])[:-1], y[idx]).backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 5.0)
            opt.step()
        model.eval()
        with torch.no_grad():
            v = loss_of(*model(x[val])[:-1], y[val]).item()
        stall = 0 if v < best - 1e-3 else stall + 1
        if v < best:
            best, best_state = v, {k: t.clone() for k, t in model.state_dict().items()}
        done = auto and stall >= PATIENCE
        if not quiet and ((epoch + 1) % every == 0 or done):
            mark = f"застой {stall}/{PATIENCE}" if auto else f"{100 * (epoch + 1) // epochs}%"
            print(f"прогресс {mark} эпоха {epoch + 1} val {v:.4f} лучший {best:.4f}")
        if done:
            break
    model.load_state_dict(best_state)
    return model.to("cpu"), best


def step(model, feats, state):
    with torch.no_grad():
        mu, log_sigma, extra, rho, logw, state = model(
            torch.tensor([[feats]], dtype=torch.float32), state)
    return (mu[0, 0].numpy(), log_sigma[0, 0].exp().numpy(),
            float(torch.sigmoid(extra[0, 0, 0])), rho[0, 0].numpy(),
            logw[0, 0].exp().numpy(), state)


def windows(dyaw, dpitch, repeat_frozen=True):
    dy, dp = np.asarray(dyaw, dtype=np.float64).copy(), np.asarray(dpitch, dtype=np.float64).copy()
    if repeat_frozen:
        for i in range(1, len(dy)):
            if abs(dy[i]) < 1e-9 and abs(dp[i]) < 1e-9:
                dy[i], dp[i] = dy[i - 1], dp[i - 1]
    out = []
    for i in range(0, max(0, len(dy) - WINDOW + 1), STRIDE):
        out.append(np.concatenate([dy[i:i + WINDOW], dp[i:i + WINDOW]]))
    if not out:
        return np.zeros((0, 2 * WINDOW), dtype=np.float32)
    return np.array(out, dtype=np.float32)


def auc(labels, score):
    order = np.argsort(score, kind="stable")
    ranks = np.empty(len(score), dtype=np.float64)
    sorted_score = np.asarray(score)[order]
    i = 0
    while i < len(order):
        j = i
        while j + 1 < len(order) and sorted_score[j + 1] == sorted_score[i]:
            j += 1
        ranks[order[i:j + 1]] = (i + j) / 2.0 + 1.0
        i = j + 1
    pos, neg = labels.sum(), (1 - labels).sum()
    if pos == 0 or neg == 0:
        return 0.5
    return (ranks[labels == 1].sum() - pos * (pos + 1) / 2) / (pos * neg)


def detector_auc(real, fake, seed=0, epochs=400):
    if len(real) < 16 or len(fake) < 16:
        return float("nan")
    torch.manual_seed(seed)
    x = np.concatenate([real, fake])
    y = np.concatenate([np.zeros(len(real)), np.ones(len(fake))])
    x = (x - x.mean(0, keepdims=True)) / (x.std(0, keepdims=True) + 1e-6)
    order = np.random.default_rng(seed).permutation(len(x))
    x, y = x[order], y[order]
    split = int(len(x) * 0.3)
    xv, yv = torch.tensor(x[:split]), y[:split]
    xt, yt = torch.tensor(x[split:]), torch.tensor(y[split:], dtype=torch.float32)

    net = nn.Sequential(nn.Linear(x.shape[1], 64), nn.ReLU(), nn.Linear(64, 32), nn.ReLU(), nn.Linear(32, 1))
    opt = torch.optim.Adam(net.parameters(), lr=1e-3, weight_decay=1e-3)
    lossf = nn.BCEWithLogitsLoss()
    best = 0.5
    for _ in range(epochs):
        net.train()
        opt.zero_grad()
        lossf(net(xt).squeeze(1), yt).backward()
        opt.step()
        net.eval()
        with torch.no_grad():
            best = max(best, auc(yv, net(xv).squeeze(1).numpy()))
    return best


def runs_of(mask):
    out, count = [], 0
    for value in mask:
        if value:
            count += 1
        elif count:
            out.append(count)
            count = 0
    if count:
        out.append(count)
    return np.array(out) if out else np.array([0])


def stream_stats(dyaw, dpitch):
    dy, dp = np.asarray(dyaw), np.asarray(dpitch)
    if len(dy) < 8:
        return {}
    a = np.abs(dy)
    frozen = (a < 1e-9) & (np.abs(dp) < 1e-9)
    return dict(
        med=float(np.median(a)),
        p90=float(np.percentile(a, 90)),
        max=float(a.max()),
        pitch=float(np.median(np.abs(dp))),
        frozen=float(frozen.mean()),
        run=float(runs_of(frozen).mean()),
        autocorr=float(np.corrcoef(a[1:], a[:-1])[0, 1]),
        flip=float(np.mean(np.sign(dy[1:]) * np.sign(dy[:-1]) < 0)),
    )


def compare(name_a, stats_a, name_b, stats_b):
    print(f"{'метрика':<10}{name_a:>10}{name_b:>10}")
    for key in ("med", "p90", "max", "pitch", "frozen", "run", "autocorr", "flip"):
        if key in stats_a and key in stats_b:
            print(f"{key:<10}{stats_a[key]:>10.3f}{stats_b[key]:>10.3f}")


def verify(model, path, feats):
    payload = json.load(open(path, encoding="utf-8"))
    h, mix = payload["hidden"], payload["mix"]
    wi, wh = np.array(payload["gru"]["wi"]), np.array(payload["gru"]["wh"])
    bi, bh = np.array(payload["gru"]["bi"]), np.array(payload["gru"]["bh"])
    head_w, head_b = np.array(payload["out"]["w"]), np.array(payload["out"]["b"])
    state, mine = np.zeros(h), []
    for row in feats:
        gi, gh = wi @ np.asarray(row, dtype=np.float64) + bi, wh @ state + bh
        r = 1.0 / (1.0 + np.exp(-(gi[:h] + gh[:h])))
        z = 1.0 / (1.0 + np.exp(-(gi[h:2 * h] + gh[h:2 * h])))
        n = np.tanh(gi[2 * h:] + r * gh[2 * h:])
        state = (1.0 - z) * n + z * state
        mine.append(head_w @ state + head_b)
    mine = np.array(mine)

    with torch.no_grad():
        mu, log_sigma, extra, rho, logw, _ = model(
            torch.from_numpy(np.asarray(feats, dtype=np.float32)).unsqueeze(0))
    slots = [1 + 6 * m for m in range(mix)]
    logits = mine[:, slots]
    gap = max(
        np.abs(mine[:, 0] - extra[0, :, 0].numpy()).max(),
        np.abs(logits - logits.max(1, keepdims=True)
               - np.log(np.exp(logits - logits.max(1, keepdims=True)).sum(1, keepdims=True))
               - logw[0].numpy()).max(),
        np.abs(mine[:, [s + 1 for s in slots]] - mu[0, :, :, 0].numpy()).max(),
        np.abs(mine[:, [s + 2 for s in slots]] - mu[0, :, :, 1].numpy()).max(),
        np.abs(np.clip(mine[:, [s + 3 for s in slots]], -4.0, 1.5) - log_sigma[0, :, :, 0].numpy()).max(),
        np.abs(np.clip(mine[:, [s + 4 for s in slots]], -4.0, 1.5) - log_sigma[0, :, :, 1].numpy()).max(),
        np.abs(np.tanh(mine[:, [s + 5 for s in slots]]) * RHO_LIMIT - rho[0].numpy()).max(),
    )
    assert gap < 1e-4, f"раскладка головы разошлась с torch: {gap:.2e}"
    return gap


def export(model, path, features, limits, knob, freeze_cut, error=None, units="deg"):
    m = lambda t: [[round(v, 6) for v in row] for row in t.detach().numpy().tolist()]
    v = lambda t: [round(x, 6) for x in t.detach().numpy().tolist()]
    payload = dict(
        kind="gru", features=features, hidden=model.hidden, mix=model.mix,
        squash="sinh", units=units, limits=list(limits),
        knob=[list(k) for k in knob], freezeCut=freeze_cut,
        error=[] if error is None else [round(float(x), 6) for x in error],
        gru=dict(wi=m(model.gru.weight_ih_l0), wh=m(model.gru.weight_hh_l0),
                 bi=v(model.gru.bias_ih_l0), bh=v(model.gru.bias_hh_l0)),
        out=dict(w=m(model.head.weight), b=v(model.head.bias)))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(payload, fh)
    return sum(p.numel() for p in model.parameters())
