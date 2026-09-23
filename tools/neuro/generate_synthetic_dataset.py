#!/usr/bin/env python3
"""
Генератор реалистичных синтетических боевых датасетов для Rockstar Neuro.
Генерирует датасеты в формате CSV для обучения нейросетевых моделей аима (Aura).

Физика и поведение:
- Человеческая модель наводки с микродоводками, овершутом и шумом мыши.
- Квантование углов по Minecraft GCD (Mouse Sensitivity).
- Симуляция движений цели (strafe, jump, distance change).
- Смена таргетов, паузы между ударами, спринт, прыжки, хитбоксы.
- Полная совместимость со структурой колонок Rockstar и валидацией train_aura.py.
"""

import argparse
import math
import os
import random
import sys

HEADER = "t,gcd,clean,yaw,pitch,dyaw,dpitch,has,tid,rx,ry,rz,bw,bh,dist,vis,on,atk,hp,ground,sprint\n"


def wrap_degrees(deg):
    return (deg + 180.0) % 360.0 - 180.0


def quantize_angle(angle, gcd):
    if gcd <= 0:
        return angle
    steps = round(angle / gcd)
    return steps * gcd


class CombatScenarioSimulator:
    """
    Симулирует непрерывные боевые взаимодействия игрока и врагов (PvP).
    """

    def __init__(self, gcd=0.15):
        self.gcd = gcd
        self.player_yaw = random.uniform(-180.0, 180.0)
        self.player_pitch = random.uniform(10.0, 30.0)

        # Скорость поворота мыши игрока
        self.rot_vel_yaw = 0.0
        self.rot_vel_pitch = 0.0

        # Состояние игрока
        self.ground = 1
        self.sprint = 1
        self.attack_cooldown = 0

        # Текущая цель
        self.target_id = random.randint(100, 9999)
        self.target_dist = random.uniform(1.8, 3.8)
        self.target_angle_offset = random.uniform(-25.0, 25.0)
        self.target_pitch_offset = random.uniform(-10.0, 10.0)
        self.target_hp = 20.0
        self.target_rel_vx = random.uniform(-0.15, 0.15)
        self.target_rel_vz = random.uniform(-0.15, 0.15)
        self.target_rel_y = random.uniform(-0.8, -0.2)

        # Параметры прицеливания (человеческие характеристики)
        self.smoothness = random.uniform(0.18, 0.38)
        self.aim_noise = random.uniform(0.12, 0.45)
        self.overshoot = random.uniform(0.02, 0.08)

    def switch_target(self):
        self.target_id = random.randint(100, 9999)
        self.target_dist = random.uniform(2.0, 4.2)
        self.target_angle_offset = random.uniform(-55.0, 55.0)
        self.target_pitch_offset = random.uniform(-20.0, 20.0)
        self.target_hp = 20.0
        self.target_rel_vx = random.uniform(-0.2, 0.2)
        self.target_rel_vz = random.uniform(-0.2, 0.2)
        self.target_rel_y = random.uniform(-0.8, -0.2)

    def step(self, t):
        # 1. Поведение цели (стрейфы, смена направления)
        if random.random() < 0.08:
            self.target_rel_vx = random.uniform(-0.22, 0.22)
        if random.random() < 0.06:
            self.target_rel_vz = random.uniform(-0.25, 0.25)

        # Дистанция до цели варьируется от 1.2 до 4.5 блоков
        self.target_dist = max(1.2, min(4.8, self.target_dist + self.target_rel_vz * 0.4))
        self.target_angle_offset += self.target_rel_vx * 15.0

        # Угол на цель
        target_yaw = self.player_yaw + self.target_angle_offset
        flat_dist = max(0.2, math.sqrt(max(0.01, self.target_dist**2 - self.target_rel_y**2)))
        ideal_pitch = -math.degrees(math.atan2(self.target_rel_y, flat_dist))
        target_pitch = max(-80.0, min(80.0, ideal_pitch + self.target_pitch_offset))

        # Относительные координаты хитбокса (rx, ry, rz)
        rad_yaw = math.radians(target_yaw + 90.0)
        rx = flat_dist * math.cos(rad_yaw)
        rz = flat_dist * math.sin(rad_yaw)
        ry = self.target_rel_y
        bw = 0.6
        bh = 1.8

        # 2. Модель человека (прицеливание)
        diff_yaw = wrap_degrees(target_yaw - self.player_yaw)
        diff_pitch = target_pitch - self.player_pitch

        # Сглаженное ускорение с добавлением человеческого тремора/шума
        accel_yaw = diff_yaw * self.smoothness + random.gauss(0, self.aim_noise)
        accel_pitch = diff_pitch * self.smoothness + random.gauss(0, self.aim_noise * 0.6)

        self.rot_vel_yaw = self.rot_vel_yaw * 0.35 + accel_yaw
        self.rot_vel_pitch = self.rot_vel_pitch * 0.35 + accel_pitch

        # Квантование перемещения мыши по GCD
        raw_dyaw = self.rot_vel_yaw
        raw_dpitch = self.rot_vel_pitch

        dyaw = quantize_angle(raw_dyaw, self.gcd)
        dpitch = quantize_angle(raw_dpitch, self.gcd)

        # Применяем дельту
        self.player_yaw = wrap_degrees(self.player_yaw + dyaw)
        self.player_pitch = max(-90.0, min(90.0, self.player_pitch + dpitch))

        # Оставшаяся ошибка
        curr_diff_yaw = abs(wrap_degrees(target_yaw - self.player_yaw))
        curr_diff_pitch = abs(target_pitch - self.player_pitch)

        # Попадание лучом во врага (on)
        size_yaw = math.degrees(math.atan2(bw / 2.0, flat_dist))
        size_pitch = math.degrees(math.atan2(bh / 2.0, flat_dist))
        on = 1 if (curr_diff_yaw <= size_yaw and curr_diff_pitch <= size_pitch) else 0

        # Атака (клики раз в 9-13 тиков, стандартный 1.9+ cooldown меча / 8-11 cps в 1.8)
        self.attack_cooldown -= 1
        atk = 0
        if on and self.target_dist <= 3.8 and self.attack_cooldown <= 0:
            if random.random() < 0.75:
                atk = 1
                self.attack_cooldown = random.randint(9, 13)
                self.target_hp = max(0.0, self.target_hp - random.uniform(6.0, 9.0))
                if self.target_hp <= 0:
                    self.switch_target()

        # Случайная смена цели раз в несколько сотен тиков
        if random.random() < 0.003:
            self.switch_target()

        # Прыжки/спринт игрока
        if random.random() < 0.05:
            self.ground = 0 if self.ground == 1 else 1
        if random.random() < 0.04:
            self.sprint = 1 if self.sprint == 0 else 1

        clean = 1
        has = 1
        vis = 1

        row = (
            f"{t},"
            f"{self.gcd:.6f},"
            f"{clean},"
            f"{self.player_yaw:.4f},"
            f"{self.player_pitch:.4f},"
            f"{dyaw:.4f},"
            f"{dpitch:.4f},"
            f"{has},"
            f"{self.target_id},"
            f"{rx:.4f},"
            f"{ry:.4f},"
            f"{rz:.4f},"
            f"{bw:.4f},"
            f"{bh:.4f},"
            f"{self.target_dist:.4f},"
            f"{vis},"
            f"{on},"
            f"{atk},"
            f"{self.target_hp:.1f},"
            f"{self.ground},"
            f"{self.sprint}\n"
        )
        return row


def generate_synthetic_dataset(output_path, ticks, gcd_list=None):
    if gcd_list is None:
        gcd_list = [0.15, 0.075, 0.12, 0.08789, 0.14062]

    os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)
    print(f"[*] Генерация {ticks} тиков (~{ticks / 1200:.1f} мин) в {output_path}...")

    episodes_generated = 0
    t = 1

    with open(output_path, "w", encoding="utf-8") as f:
        f.write(HEADER)

        while t <= ticks:
            # Длина одного непрерывного боевого эпизода (от 120 до 800 тиков = 6-40 сек боя)
            episode_len = random.randint(120, 800)
            chosen_gcd = random.choice(gcd_list)
            sim = CombatScenarioSimulator(gcd=chosen_gcd)

            for _ in range(episode_len):
                row = sim.step(t)
                f.write(row)
                t += 1
                if t > ticks:
                    break

            episodes_generated += 1

            # Небольшой перерыв между эпизодами (смена обстановки, 5-15 тиков без цели has=0)
            if t <= ticks and random.random() < 0.5:
                idle_len = random.randint(5, 15)
                for _ in range(idle_len):
                    f.write(
                        f"{t},{chosen_gcd:.6f},1,{sim.player_yaw:.4f},{sim.player_pitch:.4f},"
                        f"0.0000,0.0000,0,-1,0.0000,0.0000,0.0000,0.0000,0.0000,-1.0000,0,0,0,-1.0,1,1\n"
                    )
                    t += 1
                    if t > ticks:
                        break

    file_size_mb = os.path.getsize(output_path) / (1024 * 1024)
    print(f"[✓] Успешно создано: {ticks} тиков ({ticks / 1200:.1f} мин боя), {episodes_generated} эпизодов, размер: {file_size_mb:.2f} МБ")


def main():
    parser = argparse.ArgumentParser(
        description="Генератор большого синтетического датасета для обучения Rockstar Neuro"
    )
    parser.add_argument(
        "--name",
        default="synth_large",
        help="Имя датасета без .csv (по умолчанию: synth_large)"
    )
    parser.add_argument(
        "--ticks",
        type=int,
        default=40000,
        help="Количество боевых тиков (по умолчанию: 40000 = ~33.3 мин боя, идеал для Rockstar Neuro)"
    )
    parser.add_argument(
        "--out",
        default=None,
        help="Путь к результирующему файлу (по умолчанию: run/Rockstar/neuro/data/<name>.csv)"
    )

    args = parser.parse_args()

    project_root = os.path.dirname(os.path.abspath(__file__))
    output_file = args.out
    if output_file is None:
        output_file = os.path.join(project_root, "run", "Rockstar", "neuro", "data", f"{args.name}.csv")

    generate_synthetic_dataset(output_file, args.ticks)


if __name__ == "__main__":
    main()
