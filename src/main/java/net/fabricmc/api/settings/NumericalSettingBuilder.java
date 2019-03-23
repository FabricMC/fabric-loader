package net.fabricmc.api.settings;

import net.fabricmc.api.settings.schema.Constraint;
import net.fabricmc.api.settings.schema.NumberConstraint;
import net.fabricmc.api.settings.schema.Restrictions;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class NumericalSettingBuilder<S, T extends Number> extends SettingBuilder<S, T> {

	private T max;
	private T min;

	public NumericalSettingBuilder(Settings registry, Class<T> type) {
		super(registry, type);
	}

	private <A extends Number> NumericalSettingBuilder(NumericalSettingBuilder<S, A> copy, Class<T> type) {
		super((SettingBuilder) copy, type);
		this.max = this.min = null;
		if (copy.max != null) {
			try {
				this.max = type.getDeclaredConstructor(String.class).newInstance(copy.max.toString());
			} catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
				e.printStackTrace();
			}
		}
		if (copy.min != null) {
			try {
				this.min = type.getDeclaredConstructor(String.class).newInstance(copy.min.toString());
			} catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public <A> SettingBuilder type(Class<? extends A> clazz) {
		return new NumericalSettingBuilder(this, clazz);
	}

	public NumericalSettingBuilder<S, T> min(T min) {
		this.min = min;
		return this;
	}

	public NumericalSettingBuilder<S, T> max(T max) {
		this.max = max;
		return this;
	}

	@Override
	public SettingBuilder<S, T> comment(String comment) {
		return super.comment(comment);
	}

	@Override
	protected Predicate<T> restriction() {
		return t -> super.restriction().test(t) || boundRestriction().test(t);
	}

	private Predicate<T> boundRestriction() {
		return t -> {
			BigDecimal decimal = new BigDecimal(t.toString());
			if (min != null) {
				if (new BigDecimal(min.toString()).compareTo(decimal) > 0) return true;
			}
			if (max != null) {
				return new BigDecimal(max.toString()).compareTo(decimal) < 0;
			}
			return false;
		};
	}

	@Override
	protected List<Constraint> constraints() {
		List<Constraint> constraints = new ArrayList<>();
		if (min != null) {
			constraints.add(new NumberConstraint(Restrictions.NUMERICAL_LOWER_BOUND, min));
		}
		if (max != null) {
			constraints.add(new NumberConstraint(Restrictions.NUMERICAL_UPPER_BOUND, max));
		}
		return constraints;
	}
}
