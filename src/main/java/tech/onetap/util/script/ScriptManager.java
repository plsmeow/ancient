package tech.onetap.util.script;

import com.google.common.eventbus.Subscribe;
import tech.onetap.Onetap;
import tech.onetap.event.list.EventPlayerSync;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.event.list.EventTick;

import java.util.LinkedList;
import java.util.Queue;

public class ScriptManager {

    private final Queue<ScriptTask> tasks = new LinkedList<>();

    public ScriptManager() {
        Onetap.getInstance().getEventBus().register(this);
    }
    
    public void tick(Object event) {
        ScriptTask currentTask = tasks.peek();
        if (currentTask == null) return;

        
        boolean finished = currentTask.tryTick(event);
        if (finished) {
            tasks.poll();
        }
    }

    @Subscribe
    public void updatePlayerTick(EventPlayerUpdate tickUpdate) {
        tick(tickUpdate);
    }

    @Subscribe
    public void updateTick(EventTick tickUpdate) {
        tick(tickUpdate);
    }

    @Subscribe
    public void onSync(EventPlayerSync tickUpdate) {
        tick(tickUpdate);
    }

    public void addTask(ScriptTask task) {
        tasks.add(task);
    }

    public boolean isFinished() {
        return tasks.isEmpty();
    }

    public static class ScriptTask {
        private final Queue<Step<?>> steps = new LinkedList<>();

        
        private int idleTicks = 0;

        
        private int maxIdleTicks = 400; 

        
        public ScriptTask withMaxIdleTicks(int maxIdleTicks) {
            this.maxIdleTicks = Math.max(1, maxIdleTicks);
            return this;
        }

        public <E> ScriptTask schedule(Class<E> eventClass, StepTask<E> action) {
            steps.add(new Step<>(eventClass, action));
            return this;
        }
        
        public boolean tryTick(Object event) {
            Step<?> nextStep = steps.peek();

            
            if (nextStep == null) {
                return true;
            }

            boolean progressed = false;

            
            if (nextStep.eventClass.isInstance(event)) {
                boolean stepDone = nextStep.execute(event);
                if (stepDone) {
                    steps.poll();       
                    progressed = true;  
                }
            }

            if (progressed) {
                idleTicks = 0;                 
                return steps.isEmpty();        
            } else {
                idleTicks++;
                
                if (idleTicks > maxIdleTicks) {
                    
                    steps.clear();
                    return true;
                }
                return false;
            }
        }

        public boolean isCompleted() {
            return steps.isEmpty();
        }

        
        private static class Step<E> {
            private final Class<E> eventClass;
            private final StepTask<E> action;

            Step(Class<E> eventClass, StepTask<E> action) {
                this.eventClass = eventClass;
                this.action = action;
            }

            boolean execute(Object event) {
                return action.accept(eventClass.cast(event));
            }
        }

        @FunctionalInterface
        public interface StepTask<E> {
            
            boolean accept(E t);
        }
    }
}
