package tech.onetap.util.player.other;

import lombok.experimental.UtilityClass;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import tech.onetap.util.IMinecraft;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@UtilityClass
public class SlownessManager implements IMinecraft {
    private final KeyBinding[] keybindsList = new KeyBinding[]{mc.options.forwardKey, mc.options.leftKey, mc.options.rightKey, mc.options.backKey, mc.options.jumpKey, mc.options.sprintKey, mc.options.sneakKey};

    private final List<SlownessManager.SlowTask> tasks = new ArrayList<>();
    private final List<SlownessManager.TimeTask> timeTasks = new ArrayList<>();

    private SlownessManager.SlowTask lastTask = null;

    public void addTask(SlownessManager.SlowTask task) {
        for (SlowTask existing : tasks) {
            if (existing.runnable != null && task.runnable != null) {
                if (existing.runnable.equals(task.runnable)) {
                    return;
                }
            }
        }
        lastTask = task;
        tasks.add(task);
    }

    public void addTimeTask(SlownessManager.TimeTask task) {
        timeTasks.add(task);
    }

    public void updateSlowTasks() {
        long now = System.currentTimeMillis();

        if (lastTask != null) {
            if (now - lastTask.time < lastTask.duration) {
                for (KeyBinding key : new KeyBinding[]{mc.options.forwardKey, mc.options.leftKey, mc.options.rightKey, mc.options.backKey, mc.options.jumpKey, mc.options.sprintKey}) {
                    key.setPressed(false);
                }
            } else {
                for (KeyBinding key : keybindsList) {
                    if (mc.currentScreen == null) key.setPressed(InputUtil.isKeyPressed(mc.getWindow().getHandle(), key.getDefaultKey().getCode()));
                }
            }
        }

        List<SlowTask> copy = new ArrayList<>(tasks);
        for (SlowTask task : copy) {
            if (now - task.time > (task.duration - task.reflection) && task.runnable != null) {
                task.runnable.run();
                task.runnable = null;
            }
        }

        Iterator<SlowTask> iterator = tasks.iterator();
        while (iterator.hasNext()) {
            SlowTask task = iterator.next();
            if (now - task.time > task.duration || !task.condition) {
                iterator.remove();
            }
        }
    }

    public void updateTimeTasks(boolean ticksUpdate) {
        long now = System.currentTimeMillis();

        for (TimeTask task : new ArrayList<>(timeTasks)) {
            if (ticksUpdate) task.ticksExisted++;
            if (task.mode == TimeTask.Mode.TICKS ? task.ticksExisted >= task.time : now - task.time > task.duration && task.runnable != null) {
                task.runnable.run();
                task.runnable = null;
            }
        }

        Iterator<TimeTask> iterator = timeTasks.iterator();
        while (iterator.hasNext()) {
            TimeTask task = iterator.next();
            if ((task.mode == TimeTask.Mode.TICKS ? task.ticksExisted >= task.time : now - task.time > task.duration) || !task.condition) {
                iterator.remove();
            }
        }
    }

    public boolean slowTasksIsEmpty() {
        return tasks.isEmpty();
    }

    public boolean timeTasksIsEmpty() {
        return timeTasks.isEmpty();
    }

    public static class SlowTask {
        public long duration;
        public long time;
        public long reflection;
        public Runnable runnable;
        public boolean condition;

        public SlowTask(long duration, long reflection, Runnable runnable) {
            this.duration = duration;
            this.reflection = reflection;
            this.runnable = runnable;
            this.time = System.currentTimeMillis();
            this.condition = true;
        }

        public SlowTask(long duration, long reflection, Runnable runnable, boolean condition) {
            this.duration = duration;
            this.reflection = reflection;
            this.runnable = runnable;
            this.time = System.currentTimeMillis();
            this.condition = condition;
        }

        public SlowTask(long duration, Runnable runnable) {
            this.duration = duration;
            this.reflection = 20;
            this.runnable = runnable;
            this.time = System.currentTimeMillis();
            this.condition = true;
        }

        public SlowTask(long duration, Runnable runnable, boolean condition) {
            this.duration = duration;
            this.reflection = 20;
            this.runnable = runnable;
            this.time = System.currentTimeMillis();
            this.condition = condition;
        }
    }

    public static class TimeTask {
        public long duration;
        public long time;
        public long ticksExisted;
        public Runnable runnable;
        public boolean condition;
        public Mode mode;

        public TimeTask(long duration, Runnable runnable, boolean condition) {
            mode = Mode.TIME;
            this.duration = duration;
            this.runnable = runnable;
            this.time = System.currentTimeMillis();
            this.condition = condition;
        }

        public TimeTask(Mode mode, int ticks, Runnable runnable, boolean condition) {
            this.mode = mode;
            this.runnable = runnable;
            this.time = ticks;
            this.condition = condition;
        }

        public enum Mode {
            TIME,
            TICKS
        }
    }
}