import argparse
import csv
import glob
import math
import os
import sys

import numpy as np

from common import (MIX, compare, detector_auc, export, quantize, runs_of, squash, step,
                    stream_stats, train_policy, unsquash, verify, windows, wrap)

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DATA_DIR = os.path.join(ROOT, "run", "ancient", "neuro", "data")
MODEL_PATH = os.path.join(ROOT, "run", "ancient", "neuro", "aim.json")

FEATURES = 17
LIMITS = (179.0, 90.0)
MIN_SIZE = 0.5
MIN_LEN = 24
FREEZE_CUT = 40
MAX_RUN = float(FREEZE_CUT)
ERROR_STEPS = 65
DEFAULTS = (0.70, 4, 0.45)
KNOB = (DEFAULTS, (0.70, 1, 0.45), (0.70, 8, 0.45), (0.70, 4, 1.00))

NUMERIC = ("gcd", "yaw", "pitch", "dyaw", "dpitch", "rx", "ry", "rz", "bw", "bh", "dist", "on", "atk")
COLUMNS = ("t", "clean", "has", "tid") + NUMERIC
DATA_PROBLEM = 2


def warn(message):
    print("! " + message)


def fail(message):
    """Понятный отказ вместо трейсбека: клиент красит такие строки красным и молчит про код."""
    print("!! " + message)
    return DATA_PROBLEM


def geometry(rows):
    col = lambda name: np.array([float(r[name]) for r in rows], dtype=np.float64)
    rx, ry, rz = col("rx"), col("ry"), col("rz")
    bw, bh = col("bw"), col("bh")
    flat = np.maximum(np.hypot(rx, rz), 0.05)
    tyaw = np.degrees(np.arctan2(rz, rx)) - 90.0
    tpitch = -np.degrees(np.arctan2(ry, flat))
    yaw, pitch = col("yaw"), col("pitch")
    dyaw, dpitch = col("dyaw"), col("dpitch")

    atk = col("atk")
    since = np.zeros(len(rows))
    run = np.zeros(len(rows))
    count, streak = 99, 0
    for i in range(len(rows)):
        count = 0 if atk[i] > 0.5 else count + 1
        since[i] = min(count, 20)
        streak = streak + 1 if abs(dyaw[i]) < 1e-9 and abs(dpitch[i]) < 1e-9 else 0
        run[i] = min(streak, MAX_RUN)

    return dict(
        dyaw=dyaw, dpitch=dpitch, gcd=col("gcd"), dist=col("dist"), on=col("on"),
        since=since, run=run,
        size_yaw=np.maximum(np.degrees(np.arctan2(bw / 2.0, flat)), MIN_SIZE),
        size_pitch=np.maximum(np.degrees(np.arctan2(bh / 2.0, flat)), MIN_SIZE),
        ey=wrap(tyaw - yaw), ep=tpitch - pitch,
        vyaw=np.concatenate([[0.0], wrap(np.diff(tyaw))]),
        vpitch=np.concatenate([[0.0], np.diff(tpitch)]),
    )


def parse_row(row):
    """Строка датасета в числа или None, если она битая.

    Битая строка — обычное дело, а не редкость: клиент пишет csv на дозапись и сбрасывает буфер раз
    в 256 тиков, поэтому вылет игры обрывает последнюю строку на середине, а следующая сессия
    дописывает свою прямо в этот огрызок. csv.DictReader отдаёт на такой строке None вместо
    значения, и обучение падало трейсбеком `float() argument must be ... not NoneType`.

    Отдельно отсеиваются nan и inf: они парсятся молча, а в обучении дают nan-лосс и модель-мусор.
    """
    if row.get(None) is not None:
        return None  # полей больше, чем в заголовке: две строки слиплись после обрыва записи
    out = {}
    for name in NUMERIC:
        text = row.get(name)
        if text is None:
            return None
        try:
            value = float(text)
        except ValueError:
            return None
        if not math.isfinite(value):
            return None
        out[name] = value
    try:
        out["t"] = int(row["t"])
    except (TypeError, ValueError, KeyError):
        return None
    for name in ("clean", "has", "tid"):
        if row.get(name) is None:
            return None
        out[name] = row[name]
    return out


def load_file(path):
    """Эпизоды одного файла: (эпизоды, строк, битых строк, прочитан ли файл)."""
    name = os.path.basename(path)
    try:
        with open(path, newline="", encoding="utf-8", errors="replace") as fh:
            rows = list(csv.DictReader(fh))
    except (OSError, csv.Error) as e:
        warn(f"{name}: файл не читается ({e}) — пропускаю")
        return [], 0, 0, False
    if not rows:
        warn(f"{name}: пустой файл — пропускаю")
        return [], 0, 0, False
    absent = [c for c in COLUMNS if c not in rows[0]]
    if absent:
        warn(f"{name}: " + ("старый формат без геометрии" if "rx" in absent
                            else "нет колонок " + ", ".join(absent)) + " — пропускаю")
        return [], 0, 0, False

    episodes, broken = [], 0
    current, prev_t, prev_id, streak = [], None, None, 0
    for raw in rows:
        row = parse_row(raw)
        if row is None:
            broken += 1  # битая строка рвёт эпизод: непрерывности тиков в этом месте нет
        still = row is not None and abs(row["dyaw"]) < 1e-9 and abs(row["dpitch"]) < 1e-9
        streak = streak + 1 if still else 0
        live = row is not None and row["clean"] == "1" and row["has"] == "1" and streak <= FREEZE_CUT
        tick = row["t"] if live else None
        tid = row["tid"] if live else None
        if tick is None or prev_t is None or tick != prev_t + 1 or tid != prev_id:
            if len(current) >= MIN_LEN:
                episodes.append(geometry(current))
            current = []
        if live:
            current.append(row)
        prev_t, prev_id = tick, tid
    if len(current) >= MIN_LEN:
        episodes.append(geometry(current))
    if broken:
        share = 100.0 * broken / len(rows)
        warn(f"{name}: битых строк {broken} из {len(rows)}"
             + (f" ({share:.0f}%)" if share >= 1.0 else "") + " — пропущены")
    return episodes, len(rows), broken, True


def load(directory):
    """Все датасеты папки: (сессии, строк всего, битых строк, непрочитанных файлов)."""
    sessions, seen, broken, skipped = {}, 0, 0, 0
    for path in sorted(glob.glob(os.path.join(directory, "*.csv"))):
        episodes, rows, bad, read = load_file(path)
        seen += rows
        broken += bad
        skipped += 0 if read else 1
        if episodes:
            sessions[os.path.basename(path)] = episodes
    return sessions, seen, broken, skipped


def features(ey, ep, dey, dep, vy, vp, dy1, dp1, dy2, dp2,
             dist, size_yaw, size_pitch, on, since, run):
    a = lambda v: float(np.arcsinh(v)) / 3.0
    return [
        a(ey), a(ep), a(dey), a(dep), a(vy), a(vp),
        a(dy1), a(dp1), a(dy2), a(dp2),
        a(ey / size_yaw), a(ep / size_pitch),
        math.log(max(dist, 0.05) + 0.5) / 2.0,
        math.log(size_yaw) / 3.0,
        1.0 if on > 0.5 else 0.0,
        since / 20.0,
        run / MAX_RUN,
    ]


def state_of(ep, i, ey1, ep1, ey2, ep2, dy1, dp1, dy2, dp2, on, run):
    return (ey1, ep1, wrap(ey1 - ey2), ep1 - ep2, ep["vyaw"][i], ep["vpitch"][i],
            dy1, dp1, dy2, dp2,
            ep["dist"][i], ep["size_yaw"][i], ep["size_pitch"][i], on, ep["since"][i - 1], run)


def mirrored(s):
    return (-s[0], s[1], -s[2], s[3], -s[4], s[5], -s[6], s[7], -s[8], s[9],
            s[10], s[11], s[12], s[13], s[14], s[15])


def build_sequences(episodes, mirror=True):
    out = []
    for episode in episodes:
        n = len(episode["ey"])
        if n <= 4:
            continue
        plain, flipped, ys, flipped_ys = [], [], [], []
        for i in range(3, n):
            s = state_of(episode, i, episode["ey"][i - 1], episode["ep"][i - 1],
                         episode["ey"][i - 2], episode["ep"][i - 2],
                         episode["dyaw"][i - 1], episode["dpitch"][i - 1],
                         episode["dyaw"][i - 2], episode["dpitch"][i - 2],
                         episode["on"][i - 1], episode["run"][i - 1])
            frozen = abs(episode["dyaw"][i]) < 1e-9 and abs(episode["dpitch"][i]) < 1e-9
            live = 0.0 if frozen else 1.0
            plain.append(features(*s))
            ys.append([unsquash(episode["dyaw"][i]), live,
                       unsquash(episode["dpitch"][i]), live, 1.0 if frozen else 0.0, 1.0])
            if mirror:
                flipped.append(features(*mirrored(s)))
                flipped_ys.append([unsquash(-episode["dyaw"][i]), live,
                                   unsquash(episode["dpitch"][i]), live,
                                   1.0 if frozen else 0.0, 1.0])
        out.append((np.array(plain, dtype=np.float32), np.array(ys, dtype=np.float32)))
        if mirror:
            out.append((np.array(flipped, dtype=np.float32), np.array(flipped_ys, dtype=np.float32)))
    return out


def residuals(episodes):
    size_yaw = np.concatenate([e["size_yaw"] for e in episodes])
    size_pitch = np.concatenate([e["size_pitch"] for e in episodes])
    r = np.hypot(np.concatenate([e["ey"] for e in episodes]) / size_yaw,
                 np.concatenate([e["ep"] for e in episodes]) / size_pitch)
    return np.quantile(r, np.linspace(0.0, 1.0, ERROR_STEPS))


def quantile(table, u):
    q = min(max(u, 0.0), 1.0) * (len(table) - 1)
    i = int(q)
    if i >= len(table) - 1:
        return float(table[-1])
    return float(table[i] + (table[i + 1] - table[i]) * (q - i))


def rollout(model, episode, rng, temp=1.0, keep=1, scale=0.0, error=None):
    n = len(episode["ey"])
    gcd = episode["gcd"][0]
    ey, ep = np.zeros(n), np.zeros(n)
    dyaw, dpitch = np.zeros(n), np.zeros(n)
    ey[:3], ep[:3] = episode["ey"][:3], episode["ep"][:3]
    dyaw[:3], dpitch[:3] = episode["dyaw"][:3], episode["dpitch"][:3]
    hidden, run = None, 0.0
    for i in range(3, n):
        on = 1.0 if abs(ey[i - 1]) <= episode["size_yaw"][i - 1] \
                    and abs(ep[i - 1]) <= episode["size_pitch"][i - 1] else 0.0
        s = state_of(episode, i, ey[i - 1], ep[i - 1], ey[i - 2], ep[i - 2],
                     dyaw[i - 1], dpitch[i - 1], dyaw[i - 2], dpitch[i - 2], on, run)
        mu, sigma, freeze, rho, weight, hidden = step(model, features(*s), hidden)

        drift_yaw = wrap(ey[i - 1] + episode["vyaw"][i])
        drift_pitch = ep[i - 1] + episode["vpitch"][i]
        forced = run >= MAX_RUN
        if not forced and rng.random() < freeze:
            step_yaw, step_pitch = 0.0, 0.0
        else:
            want = scale * quantile(error, rng.random()) if error is not None else 0.0
            size_yaw = max(episode["size_yaw"][i], 1e-3)
            size_pitch = max(episode["size_pitch"][i], 1e-3)
            edges = np.cumsum(weight)
            best = None
            for _ in range(keep):
                m = min(int(np.searchsorted(edges, rng.random() * edges[-1])), len(weight) - 1)
                blend = math.sqrt(max(0.0, 1.0 - rho[m] * rho[m]))
                zy = rng.standard_normal()
                zp = rho[m] * zy + blend * rng.standard_normal()
                move = (float(quantize(squash(mu[m, 0] + temp * sigma[m, 0] * zy, LIMITS[0]), gcd)),
                        float(quantize(squash(mu[m, 1] + temp * sigma[m, 1] * zp, LIMITS[1]), gcd)))
                left = abs(math.hypot(wrap(drift_yaw - move[0]) / size_yaw,
                                      (drift_pitch - move[1]) / size_pitch) - want)
                if best is None or left < best[0]:
                    best = (left, move)
            step_yaw, step_pitch = best[1]

        if forced and step_yaw == 0.0 and step_pitch == 0.0:
            if abs(drift_yaw) >= abs(drift_pitch):
                step_yaw = math.copysign(gcd, drift_yaw)
            else:
                step_pitch = math.copysign(gcd, drift_pitch)

        run = run + 1 if step_yaw == 0.0 and step_pitch == 0.0 else 0.0
        dyaw[i], dpitch[i] = step_yaw, step_pitch
        ey[i] = wrap(drift_yaw - step_yaw)
        ep[i] = drift_pitch - step_pitch
    return dyaw, dpitch, ey, ep


def measure(model, episodes, error, real):
    print(f"\n{'Доводка':>9}{'Сэмплов':>9}{'Темп':>7}{'на цели':>10}{'AUC':>8}")
    out = []
    for row, (temp, keep, scale) in enumerate(KNOB, start=1):
        rng = np.random.default_rng(row)
        parts = [rollout(model, e, rng, temp=temp, keep=keep, scale=scale, error=error)
                 for e in episodes]
        share = ontarget(episodes, np.concatenate([p[2] for p in parts]),
                         np.concatenate([p[3] for p in parts]))
        fake = np.concatenate([windows(p[0], p[1]) for p in parts])
        score = float(np.mean([detector_auc(real, fake, seed=s) for s in (1, 2, 3)]))
        print(f"{100 * (1 - scale):>8.0f}%{keep:>9}{temp:>7.2f}{100 * share:>9.0f}%{score:>8.3f}")
        out.append(parts)
    return out


def ontarget(episodes, errors_yaw, errors_pitch):
    size_yaw = np.concatenate([e["size_yaw"] for e in episodes])
    size_pitch = np.concatenate([e["size_pitch"] for e in episodes])
    return float(np.mean((np.abs(errors_yaw) <= size_yaw) & (np.abs(errors_pitch) <= size_pitch)))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default=DATA_DIR)
    ap.add_argument("--out", default=MODEL_PATH)
    ap.add_argument("--hidden", type=int, default=32)
    ap.add_argument("--mix", type=int, default=MIX)
    ap.add_argument("--epochs", type=int, default=400, help="0 — авто, пока падает val")
    ap.add_argument("--seeds", type=int, default=2)
    ap.add_argument("--no-mirror", action="store_true")
    ap.add_argument("--freeze-cut", type=int, default=FREEZE_CUT)
    ap.add_argument("--traceback", action="store_true", help="полный трейсбек вместо короткой ошибки")
    args = ap.parse_args()

    globals()["FREEZE_CUT"] = args.freeze_cut
    globals()["MAX_RUN"] = float(args.freeze_cut)
    sessions, seen, broken, skipped = load(args.data)
    episodes = [e for group in sessions.values() for e in group]
    if not episodes:
        return nothing_to_learn(args.data, seen, broken, skipped)
    ticks = sum(len(e["ey"]) for e in episodes)
    sequences = build_sequences(episodes, not args.no_mirror)
    print(f"сессий {len(sessions)}, эпизодов {len(episodes)}, боевых тиков {ticks} "
          f"({ticks / 1200:.1f} мин), последовательностей {len(sequences)}"
          + (f", битых строк {broken}" if broken else ""))
    if not sequences:
        return fail("Из датасета не собралось ни одной обучающей последовательности — данных мало."
                    " Запиши ещё боя: .neuro record <имя>")
    if not all(np.isfinite(x).all() for x, _ in sequences):
        return fail("В датасете нечисловые значения (nan) — на таком обучаться нельзя."
                    " Запиши бой заново, старые файлы лежат в .neuro dir")
    if ticks < 12000:
        print("мало данных: нужно 20-40 тысяч боевых тиков")

    best_model, best_val = None, float("inf")
    for seed in range(args.seeds):
        print(f"\nмодель {seed + 1}/{args.seeds}"
              + (" — авто, пока падает val" if args.epochs <= 0 else f" — {args.epochs} эпох"))
        model, val = train_policy(sequences, FEATURES, args.hidden, args.epochs, seed,
                                  mix=args.mix)
        if val < best_val:
            best_model, best_val = model, val
    if best_model is None or not math.isfinite(best_val):
        return fail("Обучение разошлось: ошибка не считается (nan). Обычно так выглядит битый"
                    " датасет — запиши бой заново, старые файлы лежат в .neuro dir")
    print(f"\nлучшая: val {best_val:.4f}")

    real_dy = np.concatenate([e["dyaw"] for e in episodes])
    real_dp = np.concatenate([e["dpitch"] for e in episodes])
    real_windows = np.concatenate([windows(e["dyaw"], e["dpitch"]) for e in episodes])
    hand_on = float(np.mean(np.concatenate([e["on"] for e in episodes])))
    hand_box = ontarget(episodes, np.concatenate([e["ey"] for e in episodes]),
                        np.concatenate([e["ep"] for e in episodes]))
    print(f"\nрука: луч в хитбоксе {100 * hand_on:.0f}%, |ошибка| <= 1 хитбокса {100 * hand_box:.0f}%")

    error = residuals(episodes)
    picked = measure(best_model, episodes, error, real_windows)[0]
    fake_dy = np.concatenate([p[0] for p in picked])
    fake_dp = np.concatenate([p[1] for p in picked])
    print()
    compare("рука", stream_stats(real_dy, real_dp), "модель", stream_stats(fake_dy, fake_dp))

    count = export(best_model, args.out, FEATURES, LIMITS, KNOB, FREEZE_CUT, error)
    gap = verify(best_model, args.out, sequences[0][0][:12])
    print(f"\nвыгружено {count} весов в {args.out}, сверка раскладки {gap:.2e}")
    return 0


def nothing_to_learn(directory, seen, broken, skipped):
    """Почему учиться не на чем — разными словами: одно «нет данных» игроку ничего не объясняет."""
    if seen == 0 and skipped == 0:
        return fail(f"Датасетов нет: в папке {directory} пусто. Запиши бой: .neuro record <имя>")
    if seen == 0:
        return fail(f"Ни один датасет не подошёл ({skipped} шт., причины выше). Запиши бой заново:"
                    " .neuro record <имя>")
    if broken * 2 >= seen:
        return fail(f"Датасет битый: из {seen} строк повреждено {broken}, а из уцелевших не набралось"
                    " ни одного отрезка боя. Так выглядит оборванная запись — запиши бой заново"
                    " (.neuro record <имя>), а старые файлы удали: .neuro dir")
    return fail(f"Учиться не на чем: строк {seen}, а подряд {MIN_LEN} боевых тиков нет ни разу."
                " Тренер берёт только тики, где рядом цель и целится рука, а не клиент — бей руками,"
                " ауру и аим-ассист выключи.")


if __name__ == "__main__":
    try:
        sys.exit(main() or 0)
    except KeyboardInterrupt:
        sys.exit(130)
    except Exception as e:
        if "--traceback" in sys.argv:
            raise
        # трейсбек уезжает игроку в чат восемью строками, из которых ему не следует ничего
        print(f"!! Обучение сорвалось: {type(e).__name__}: {e}")
        print("!! Если повторяется — запиши бой заново, старые датасеты лежат в .neuro dir")
        sys.exit(1)
