# PacketMine DoubleBreak

References: [ThunderHack SpeedMine](https://github.com/Pan4ur/ThunderHack-Recode/blob/main/src/main/java/thunder/hack/features/modules/player/SpeedMine.java), and Minecraft 26.3 ServerPlayerGameMode.handleBlockBreakAction / tick bytecode. ThunderHack uses early STOP to place one target into delayed destruction. Ancient implements explicit channel ownership, not a verbatim source copy.

Vanilla stores one active destroyPos and one delayedDestroyPos. Starting both positions with identical STOP/START/STOP bursts obscures which channel owns each position. STOP for a delayed position is ignored while destroyPos names the second position. ABORT clears active destruction, including a mismatched position, and does not cancel delayed destruction. Both channels calculate damage with the same server-held item.

Expected order: START A; after selecting B, aim at A and early STOP A; A becomes delayed; aim at B and START B; wait for server air confirmation for A; finish B with STOP B after sufficient damage/time. Each start/handoff is committed only after packet handlers accept it. A delayed target never sends a finishing STOP while B is active.

A common hotbar tool is chosen for the slowest of the two blocks. Tool changes update damage estimates without restarting both channels. Rebreak retains the newest confirmed position; the older delayed position is one-shot. Removing a delayed target does not send ABORT that would cancel the remaining active target. Its server slot remains occupied until air is observed. A third request waits for channel availability.

Validation: build and temporary JVM ownership/progress regression checks. Manual checks still required: stone/obsidian with one pickaxe, different block/tool types, reversed finish order, cancelled START/STOP, target removal, third selection, Rebreak replacement, server confirmation timeouts, GUI pause, lag and exact anticheat/server versions.
