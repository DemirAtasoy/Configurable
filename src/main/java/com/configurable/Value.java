package com.configurable;

import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public class Value<A> {

    private A value;

    protected Value(final A value) {
        this.value = value;
    }

    public A get() {
        return this.getOrDefault(null);
    }

    public A getOrDefault(final A value) {
        return (this.value != null) ? this.value : value;
    }

    public A set(final A value) {
        final A oldValue = this.value;
        this.value = value;
        return oldValue;
    }

    public Value<A> map(final UnaryOperator<A> function) {
        this.value = function.apply(this.value);
        return this;
    }

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
