package net.fabricmc.loader.api.config.data;

public interface DataCollector {
	<T> void add(DataType<T> type, T data);
}
