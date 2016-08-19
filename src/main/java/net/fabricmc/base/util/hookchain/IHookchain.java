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

package net.fabricmc.base.util.hookchain;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

public interface IHookchain {
    /**
     * Gets the type of the hookchain's expected hook method.
     *
     * @return The type of the hookchain's expected hook method.
     */
    MethodType getMethodType();

    /**
     * Adds/updates a hook with a callback.
     *
     * @param name     Name of the hook to create or update
     * @param callback Callback for the given hook
     */
    void add(String name, MethodHandle callback);

    /**
     * Adds a P-comes-before-Q constraint.
     *
     * @param nameBefore Name of the dependee ("P")
     * @param nameAfter  Name of the dependent ("Q")
     */
    void addConstraint(String nameBefore, String nameAfter);

    /**
     * Calls all hooks in this chain.
     *
     * @param args Data to be fed to all hooks for processing
     */
    void call(Object... args);
}
