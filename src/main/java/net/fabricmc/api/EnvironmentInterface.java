package net.fabricmc.api;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.CLASS)
public @interface EnvironmentInterface {
	EnvType value();
	Class<?>[] itf();
}
