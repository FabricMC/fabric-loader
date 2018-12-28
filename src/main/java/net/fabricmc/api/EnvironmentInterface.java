package net.fabricmc.api;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.CLASS)
@Repeatable(EnvironmentInterfaces.class)
public @interface EnvironmentInterface {
	EnvType value();
	Class<?> itf();
}
