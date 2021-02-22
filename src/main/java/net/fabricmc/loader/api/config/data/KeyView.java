package net.fabricmc.loader.api.config.data;

import net.fabricmc.loader.api.config.util.ListView;

public interface KeyView<T> {
	ListView<Constraint<T>> getConstraints();
    <D> ListView<D> getData(DataType<D> dataType);
	ListView<DataType<?>> getDataTypes();
}