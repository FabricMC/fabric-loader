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

import javax.annotation.Nonnull;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for "hook" methods, that is methods which go on a bus.
 *
 * It is good to not put methods belonging to multiple parameter-less buses
 * onto a single class. This might be refactored to have separate annotations
 * for different buses in the future.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Hook {
    /**
     * @return The name of the hook.
     */
    @Nonnull String name();

    /**
     * @return A list of hooks which should run before this hook.
     */
    String[] before();

    /**
     * @return A list of hooks which are supposed to run after this one.
     */
    String[] after();
}
