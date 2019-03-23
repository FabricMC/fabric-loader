package net.fabricmc.api.settings;

public interface Converter<F, T> {

	F serialize(T data);
	T deserialize(F object);

}
