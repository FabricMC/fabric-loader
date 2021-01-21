package net.fabricmc.loader.api.config.data;

public interface DataCollector {
	@SuppressWarnings("unchecked")
	<T> void add(DataType<T> type, T... data);
}
