package net.fabricmc.api.settings;

public interface Converter<F, T> {

	F serialise(T data);
	T deserialise(F object);

}
