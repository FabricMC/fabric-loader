/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class Stage {
    public static StageBuilder newBuilder(String stageName) {
        return new StageBuilder(stageName);
    }

    public static class StageTrigger { }

    public static class StageBuilder {
        private StageBuilder(String name) {
            this.name = name;
        }

        public StageBuilder after(Stage state) {
            stageDependencies.add(state);

            return this;
        }

        public StageBuilder after(Event<?> event) {
            eventDependencies.add(event);

            return this;
        }

        public StageBuilder after(StageTrigger trigger) {
            triggerDependencies.add(trigger);

            return this;
        }

        public Stage build() {
            return new Stage(name, stageDependencies, eventDependencies, triggerDependencies);
        }

        private final String name;
        private final Set<Stage> stageDependencies = new LinkedHashSet<>();
        private final Set<Event<?>> eventDependencies = new LinkedHashSet<>();
        private final Set<StageTrigger> triggerDependencies = new LinkedHashSet<>();
    }

    private Stage(String name, Set<Stage> stageDeps, Set<Event<?>> eventDeps, Set<StageTrigger> triggerDeps) {
        this.name = name;
        this.stageDependencies = stageDeps;
        this.eventDependencies = eventDeps;
        this.triggerDependencies = triggerDeps;
        this.missingStages = stageDeps.isEmpty() ? Collections.emptySet() : Collections.newSetFromMap(new IdentityHashMap<>(stageDeps.size()));
        this.missingEvents = eventDeps.isEmpty() ? Collections.emptySet() : Collections.newSetFromMap(new IdentityHashMap<>(eventDeps.size()));
        this.missingTriggers = triggerDeps.isEmpty() ? Collections.emptySet() : Collections.newSetFromMap(new IdentityHashMap<>(triggerDeps.size()));

        subscribeAll();
        reset();
    }

    public synchronized void subscribe(Runnable handler) {
        if (handlers.contains(handler)) return;

        ArrayList<Runnable> newHandlers = new ArrayList<>(handlers.size() + 1);
        newHandlers.addAll(handlers);
        newHandlers.add(handler);

        handlers = newHandlers;
    }

    public synchronized boolean isDone() {
        return isDone0();
    }

    private boolean isDone0() {
        return missingStages.isEmpty() && missingEvents.isEmpty() && missingTriggers.isEmpty();
    }

    private void subscribeAll() {
        for (Stage stage : stageDependencies) {
            stage.subscribe(() -> onStage(stage));
        }

        for (Event<?> event : eventDependencies) {
            if (event instanceof Event.Event1) {
                ((Event.Event1<?>) event).subscribeStage((a1) -> onEvent(event));
            } else if (event instanceof Event.Event2) {
                ((Event.Event2<?, ?>) event).subscribeStage((a1, a2) -> onEvent(event));
            } else if (event instanceof Event.Event3) {
                ((Event.Event3<?, ?, ?>) event).subscribeStage((a1, a2, a3) -> onEvent(event));
            } else if (event instanceof Event.Event4) {
                ((Event.Event4<?, ?, ?, ?>) event).subscribeStage((a1, a2, a3, a4) -> onEvent(event));
            } else if (event instanceof Event.Event5) {
                ((Event.Event5<?, ?, ?, ?, ?>) event).subscribeStage((a1, a2, a3, a4, a5) -> onEvent(event));
            } else if (event instanceof Event.Event6) {
                ((Event.Event6<?, ?, ?, ?, ?, ?>) event).subscribeStage((a1, a2, a3, a4, a5, a6) -> onEvent(event));
            } else if (event instanceof Event.CancellableEvent1) {
                ((Event.CancellableEvent1<?>) event).subscribeStage((a1) -> onCancellableEvent(event));
            } else if (event instanceof Event.CancellableEvent2) {
                ((Event.CancellableEvent2<?, ?>) event).subscribeStage((a1, a2) -> onCancellableEvent(event));
            } else if (event instanceof Event.CancellableEvent3) {
                ((Event.CancellableEvent3<?, ?, ?>) event).subscribeStage((a1, a2, a3) -> onCancellableEvent(event));
            } else if (event instanceof Event.CancellableEvent4) {
                ((Event.CancellableEvent4<?, ?, ?, ?>) event).subscribeStage((a1, a2, a3, a4) -> onCancellableEvent(event));
            } else if (event instanceof Event.CancellableEvent5) {
                ((Event.CancellableEvent5<?, ?, ?, ?, ?>) event).subscribeStage((a1, a2, a3, a4, a5) -> onCancellableEvent(event));
            } else if (event instanceof Event.CancellableEvent6) {
                ((Event.CancellableEvent6<?, ?, ?, ?, ?, ?>) event).subscribeStage((a1, a2, a3, a4, a5, a6) -> onCancellableEvent(event));
            } else {
                throw new IllegalArgumentException("unknown event class: ");
            }
        }
    }

    private void onStage(Stage stage) {
        handleDepCompletion(missingStages, stageDependencies, stage);
    }

    private void onEvent(Event<?> event) {
        handleDepCompletion(missingEvents, eventDependencies, event);
    }

    private boolean onCancellableEvent(Event<?> event) {
        handleDepCompletion(missingEvents, eventDependencies, event);
        return true;
    }

    public void trigger(StageTrigger trigger) {
        handleDepCompletion(missingTriggers, triggerDependencies, trigger);
    }

    private synchronized <T> void handleDepCompletion(Set<T> set, Set<T> deps, T obj) {
        if (set.remove(obj)) {
            if (isDone0()) { // the last dependency was just met
                for (Runnable handler : handlers) {
                    handler.run();
                }
            }
        } else if (isDone0() && deps.contains(obj)) { // event after completion, triggers a stage reset
            reset();
            set.remove(obj);
        }
    }

    private void reset() {
        missingStages.addAll(stageDependencies);
        missingEvents.addAll(eventDependencies);
        missingTriggers.addAll(triggerDependencies);
    }

    @Override
    public String toString() {
        return "Stage "+name;
    }

    private final String name;
    private final Set<Stage> stageDependencies;
    private final Set<Event<?>> eventDependencies;
    private final Set<StageTrigger> triggerDependencies;
    private final Set<Stage> missingStages;
    private final Set<Event<?>> missingEvents;
    private final Set<StageTrigger> missingTriggers;
    private List<Runnable> handlers = Collections.emptyList();
}