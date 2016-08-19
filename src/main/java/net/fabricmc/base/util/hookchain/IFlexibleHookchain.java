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

public interface IFlexibleHookchain extends IHookchain {
    /**
     * Adds/updates a hook without a callback. It is expected that you mark
     * such hooks as executed via call(name, arg).
     *
     * @param name     Name of the hook to create or update
     */
    void add(String name);

    /**
     * Calls a specific hook in the chain and all hooks with satisfied
     * dependencies which depend on it in a recursive manner.
     *
     * @param name Name of the hook to call.
     * @param args  Data to be fed to all hooks for processing
     */
    void call(String name, Object... args);
}
