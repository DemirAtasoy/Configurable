package com.configurable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 *  Used to denote to Configurable that a field represents a Configuration property and should be populated
 *  when a Configuration is loaded
 *
 *  The Target must be a non-static final field of type {@link Value} on a class extending {@link Configuration} which
 *  is initialized to an instance of {@link Value} with the desired default value for the configuration property
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Property {

    /**
     *  Denotes the name of this property for population by Configurable. The field will be populated with the Json
     *  property of a matching name when Configurable loads a file into the class.
     *
     * @return The Name of this property
     */
    String value();
}
