package net.fabricmc.api;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.CLASS)
public @interface EnvironmentInterfaces {
	EnvironmentInterface[] value();
}
