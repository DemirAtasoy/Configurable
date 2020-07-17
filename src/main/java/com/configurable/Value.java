package com.configurable;

import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 *  A mutable container class for Configurable to populate.
 *  An instance of this class is coupled with its associated {@link Configuration} instance and as such changes to this
 *  value will be reflected in the Configuration object.
 *
 * @param <A> The Type of the Configuration value
 */
public class Value<A> {

    private A value;

    protected Value(final A value) {
        this.value = value;
    }

    /**
     *  Returns the backing value for the Configuration value. This is backed by the associated Configuration
     *  objects and as such if the value of this property is changed through the associated Configuration the change
     *  will be reflected in the return value of this method.
     *
     * @return The backing value.
     */
    public A get() {
        return this.getOrDefault(null);
    }

    /**
     *  Returns the backing value, or the specified default value if it is null
     *
     * @param value The Default Value
     * @return The backing value, or the specified default value if it is null
     */
    public A getOrDefault(final A value) {
        return (this.value != null) ? this.value : value;
    }

    /**
     *  Sets the value for this Configuration property.
     *  This change will propagate to the backing Configuration object.
     *
     * @param value The new value
     * @return THe old value
     */
    public A set(final A value) {
        final A oldValue = this.value;
        this.value = value;
        return oldValue;
    }

    /**
     *  Sets the value of this property to the output of the specified function when called with the current value
     *
     * @param function The mapping function
     * @return this
     */
    public Value<A> map(final UnaryOperator<A> function) {
        this.value = function.apply(this.value);
        return this;
    }

    /**
     *  Runs the current value of this property through the specified predicate. If the predicate returns true, no
     *  changes are made. If the predicate returns false, the current value is set to null.
     *
     * @param predicate The predicate to use
     * @return this
     */
    public Value<A> filter(final Predicate<A> predicate) {
        this.value = predicate.test(this.value) ? this.value : null;
        return this;
    }

    @Override
    public String toString() {
        return this.getClass().getName() + "{value=" + this.value + "}";
    }

    public static <A> Value<A> to(final A value) {
        return new Value<>(value);
    }
}
