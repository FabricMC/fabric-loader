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

import net.fabricmc.api.function.*;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

public abstract class Event<T> {
	public static class Event1<A1> extends Event<Consumer<A1>> {
		public void post(A1 arg1) {
			for (Consumer<A1> handler : handlers) {
				handler.accept(arg1);
			}
		}
	}

	public static class Event2<A1, A2> extends Event<BiConsumer<A1, A2>> {
		public void post(A1 arg1, A2 arg2) {
			for (BiConsumer<A1, A2> handler : handlers) {
				handler.accept(arg1, arg2);
			}
		}
	}

	public static class Event3<A1, A2, A3> extends Event<TriConsumer<A1, A2, A3>> {
		public void post(A1 arg1, A2 arg2, A3 arg3) {
			for (TriConsumer<A1, A2, A3> handler : handlers) {
				handler.accept(arg1, arg2, arg3);
			}
		}
	}

	public static class Event4<A1, A2, A3, A4> extends Event<QuatConsumer<A1, A2, A3, A4>> {
		public void post(A1 arg1, A2 arg2, A3 arg3, A4 arg4) {
			for (QuatConsumer<A1, A2, A3, A4> handler : handlers) {
				handler.accept(arg1, arg2, arg3, arg4);
			}
		}
	}

	public static class Event5<A1, A2, A3, A4, A5> extends Event<QuintConsumer<A1, A2, A3, A4, A5>> {
		public void post(A1 arg1, A2 arg2, A3 arg3, A4 arg4, A5 arg5) {
			for (QuintConsumer<A1, A2, A3, A4, A5> handler : handlers) {
				handler.accept(arg1, arg2, arg3, arg4, arg5);
			}
		}
	}

	public static class Event6<A1, A2, A3, A4, A5, A6> extends Event<SextConsumer<A1, A2, A3, A4, A5, A6>> {
		public void post(A1 arg1, A2 arg2, A3 arg3, A4 arg4, A5 arg5, A6 arg6) {
			for (SextConsumer<A1, A2, A3, A4, A5, A6> handler : handlers) {
				handler.accept(arg1, arg2, arg3, arg4, arg5, arg6);
			}
		}
	}

	public static class CancellableEvent1<A1> extends Event<Predicate<A1>> {
		public boolean post(A1 arg1) {
			for (Predicate<A1> handler : handlers) {
				if (!handler.test(arg1)) {
					return false;
				}
			}

			return true;
		}
	}

	public static class CancellableEvent2<A1, A2> extends Event<BiPredicate<A1, A2>> {
		public boolean post(A1 arg1, A2 arg2) {
			for (BiPredicate<A1, A2> handler : handlers) {
				if (!handler.test(arg1, arg2)) {
					return false;
				}
			}

			return true;
		}
	}

	public static class CancellableEvent3<A1, A2, A3> extends Event<TriPredicate<A1, A2, A3>> {
		public boolean post(A1 arg1, A2 arg2, A3 arg3) {
			for (TriPredicate<A1, A2, A3> handler : handlers) {
				if (!handler.test(arg1, arg2, arg3)) {
					return false;
				}
			}

			return true;
		}
	}

	public static class CancellableEvent4<A1, A2, A3, A4> extends Event<QuatPredicate<A1, A2, A3, A4>> {
		public boolean post(A1 arg1, A2 arg2, A3 arg3, A4 arg4) {
			for (QuatPredicate<A1, A2, A3, A4> handler : handlers) {
				if (!handler.test(arg1, arg2, arg3, arg4)) {
					return false;
				}
			}

			return true;
		}
	}

	public static class CancellableEvent5<A1, A2, A3, A4, A5> extends Event<QuintPredicate<A1, A2, A3, A4, A5>> {
		public boolean post(A1 arg1, A2 arg2, A3 arg3, A4 arg4, A5 arg5) {
			for (QuintPredicate<A1, A2, A3, A4, A5> handler : handlers) {
				if (!handler.test(arg1, arg2, arg3, arg4, arg5)) {
					return false;
				}
			}

			return true;
		}
	}

	public static class CancellableEvent6<A1, A2, A3, A4, A5, A6> extends Event<SextPredicate<A1, A2, A3, A4, A5, A6>> {
		public boolean post(A1 arg1, A2 arg2, A3 arg3, A4 arg4, A5 arg5, A6 arg6) {
			for (SextPredicate<A1, A2, A3, A4, A5, A6> handler : handlers) {
				if (!handler.test(arg1, arg2, arg3, arg4, arg5, arg6)) {
					return false;
				}
			}

			return true;
		}
	}

	private Event() {

	}

	public synchronized boolean subscribe(T handler) {
		if (handlers.contains(handler))
			return false;

		// one could easily extend the handlers assembly to support priorities (2nd subscribe arg)
		ArrayList<T> newHandlers = new ArrayList<>(handlers.size() + 1);
		newHandlers.addAll(handlers);
		newHandlers.add(handler);

		if (stageHandlers != null) {
			newHandlers.sort((first, second) -> {
				int firstPrio = stageHandlers.contains(first) ? 1 : 0;
				int secondPrio = stageHandlers.contains(second) ? 1 : 0;
				return secondPrio - firstPrio;
			});
		}

		handlers = newHandlers;

		return true;
	}

	synchronized boolean subscribeStage(T handler) {
		if (stageHandlers == null) {
			stageHandlers = new HashSet<>();
		}

		stageHandlers.add(handler);
		return subscribe(handler);
	}

	protected List<T> handlers = Collections.emptyList(); // this is actually safe-ish without volatile
	private Set<T> stageHandlers = null;
}
