package com.configurable;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Configuration {
    private static final Logger LOG = Logger.getLogger("Configurable");

    private final ReadWriteLock synchronization = new ReentrantReadWriteLock();

    private Set<String> hidden = new HashSet<>();
    private Map<String, Value<Object>> properties = null;

    protected Configuration() {

    }

    public final Object get(final String property) {
        Objects.requireNonNull(property);
        final Lock synchronization = this.synchronization.readLock();

        synchronization.lock();
        final Value<Object> reference = this.properties.get(property);
        synchronization.unlock();

        return (reference != null) ? reference.get() : null;
    }

    public final Object set(final String property, final Object value) {
        Objects.requireNonNull(property);
        if (value == null) {
            final Lock synchronization = this.synchronization.writeLock();

            synchronization.lock();
            final Object oldValue = this.properties.remove(property);
            synchronization.unlock();

            return oldValue;
        }

        final Lock synchronization = this.synchronization.writeLock();
        synchronization.lock();
        final Value<Object> reference = this.properties.computeIfAbsent(property, string -> Value.to(null));
        synchronization.unlock();

        return reference.set(value);
    }

    final Map<String, Value<Object>> getProperties() {
        if (this.properties == null) {
            final List<Field> fields = getDeclaredFields(this.getClass()).stream()
                    .filter(this::isPropertyField)
                    .collect(Collectors.toList())
            ;
            final Map<String, Value<Object>> properties = new HashMap<>();
            for (final Field field : fields) {
                final Value<Object> value = this.getValue(field);
                if (value != null) {
                    getAnnotations(field).stream()
                            .filter(annotation -> annotation.annotationType() == Property.class)
                            .map(Property.class::cast)
                            .map(Property::value)
                            .filter(string -> !properties.containsKey(string))
                            .forEach(string -> properties.put(string, value))
                    ;
                }
            }
            this.properties = properties;
        }
        return this.properties;
    }

    final void setHidden(final String name) {
        this.hidden.add(name);
    }

    final ReadWriteLock getLock() {
        return this.synchronization;
    }

    @Override
    public String toString() {
        return this.getClass().getName() + ((this.properties != null) ? this.properties.toString() : "{}");
    }

    /**
     * Parses the specified Json File into a Configuration instance
     *
     * @param file A Json file to be parsed
     * @return A Configuration instance from the specified File
     */
    public static Configuration read(final File file) {
        return read(new Configuration(), file);
    }

    /**
     * Obtains a Configuration instance from the specified supplier. Then loads and parses the specified file
     * into the supplied Configuration instance, populating any available fields annotated with {@link Property} with
     * values from the parsed file.
     *
     * This method is indented to be called from a subclass which defines one or more fields tracking configuration
     * properties.
     *
     * @param supplier Function that supplies an Configuration object to populate
     * @param file The file to load and parse
     * @return A Configuration objects of the supplied type populated from the specified file
     */
    protected static <A extends Configuration> A read(final Supplier<A> supplier, final File file) {
        Objects.requireNonNull(supplier);
        return Configuration.read(supplier.get(), file);
    }
    private static <A extends Configuration> A read(final A configuration, final File file) {
        Objects.requireNonNull(configuration);

        JsonObject object;
        try {
            object = Configuration.parse(file);
        }
        catch (final FileNotFoundException exception) {
            LOG.info("Unable to parse " + file.getPath() + ": file does not exist");
            object = new JsonObject();
        }
        catch (final IOException exception) {
            LOG.severe("Unable to parse " + file.getPath() + ": " + exception.getMessage());
            object = new JsonObject();
        }

        return Configuration.read(configuration, object);
    }

    private static JsonObject parse(final File file) throws IOException {
        if (file == null) {
            return new JsonObject();
        }
        try (final BufferedReader input = new BufferedReader(new FileReader(file))) {
            final JsonReader jsonreader = new JsonReader(input);
            jsonreader.setLenient(true);
            final JsonElement element = new JsonParser().parse(jsonreader);
            if (!element.isJsonObject()) {
                throw new JsonParseException("Expected JsonObject for top-level element, but was " + element.getClass());
            }
            return element.getAsJsonObject();
        }
    }

    private static <A extends Configuration> A read(final A configuration, final JsonObject object) {
        Objects.requireNonNull(object);

        final List<Field> fields = getDeclaredFields(configuration.getClass()).stream()
                .filter(field -> configuration.isPropertyField(field))
                .collect(Collectors.toList())
        ;
        for (final Field field : fields) {
            final boolean isHidden = (configuration.getStatus(field) == Status.HIDDEN);
            final String name = getAnnotations(field).stream()
                    .filter(annotation -> annotation instanceof Property)
                    .map(annotation -> (Property) annotation)
                    .map(Property::value)
                    .findFirst()
                    .orElse(null)
            ;
            if (name != null && isHidden && !object.has(name)) {
                configuration.setHidden(name);
            }
        }

        final Map<String, Object> map = asMap(object);

        configuration.getLock().writeLock().lock();;
        final Map<String, Value<Object>> properties = configuration.getProperties();
        for (final Map.Entry<String, Object> entry : map.entrySet()) {
            final String name = entry.getKey();
            final Object value = entry.getValue();
            properties.computeIfAbsent(name, string -> Value.to(null)).set(value);
        }
        configuration.getLock().writeLock().unlock();;

        return configuration;
    }

    /**
     *  Writes this Configuration object to the specified file in Json format.
     *
     * @param file The file to write to
     * @return this
     */
    public final <A extends Configuration>  A write(final File file) {
        if (file == null) {
            return (A) this;
        }

        this.synchronization.readLock().lock();
        final JsonObject object = asJsonObject(map(this.properties, Value::get));
        this.synchronization.readLock().unlock();

        final List<String> toRemove = new ArrayList<>();
        for (final Map.Entry<String, JsonElement> entry : object.entrySet()) {
            final String name = entry.getKey();
            if (this.hidden.contains(name)) {
                toRemove.add(name);
            }
        }
        for (final String name : toRemove) {
            object.remove(name);
        }

        final Gson gson = new GsonBuilder()
                .serializeNulls()
                .setLenient()
                .setPrettyPrinting()
                .create()
        ;
        final String string = gson.toJson(object);
        try (final BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file))) {
            bufferedWriter.write(string);
            bufferedWriter.flush();
        }
        catch (final IOException exception) {
            LOG.severe("Unable to write configuration file to " + file.getPath() + ": " + exception.getMessage());
            if (!file.delete()) LOG.severe("Unable to delete configuration file at " + file.getPath());
        }
        return (A) this;
    }

    private static Map<String, Object> asMap(final JsonObject element) {
        if (element == null) {
            return Collections.emptyMap();
        }
        if (element.size() == 0) {
            return Collections.emptyMap();
        }
        final Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : element.entrySet()) {
            final String key = entry.getKey();
            final JsonElement value = entry.getValue();
            if (value.isJsonNull()) {
                map.put(key, null);
            }
            else if (value.isJsonPrimitive()) {
                final JsonPrimitive primitive = value.getAsJsonPrimitive();
                map.put(key, asObject(primitive));
            }
            else if (value.isJsonArray()) {
                final JsonArray array = (JsonArray) value;
                map.put(key, asList(array));
            }
            else if (value.isJsonObject()) {
                final JsonObject object = (JsonObject) value;
                map.put(key, asMap(object));
            }
        }
        return map;
    }

    private static List<Object> asList(final JsonArray arrayElement) {
        if (arrayElement == null) {
            return Collections.emptyList();
        }
        if (arrayElement.size() == 0) {
            return Collections.emptyList();
        }
        final List<Object> list = new ArrayList<>();
        for (JsonElement element : arrayElement) {
            if (element.isJsonNull()) {
                list.add(null);
            }
            else if (element.isJsonPrimitive()) {
                final JsonPrimitive primitive = element.getAsJsonPrimitive();
                list.add(asObject(primitive));
            }
            else if (element.isJsonArray()) {
                final JsonArray array1 = (JsonArray) element;
                list.add(asList(array1));
            }
            else if (element.isJsonObject()) {
                final JsonObject object = (JsonObject) element;
                list.add(asMap(object));
            }
        }
        return list;
    }

    private static Object asObject(final JsonPrimitive primitive) {
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            final Number value = primitive.getAsNumber();
            final int intValue = value.intValue();
            final float floatValue0 = (float) intValue;
            final float floatValue1 = value.floatValue();
            if (floatValue0 != floatValue1) {
                return floatValue1;
            }
            return intValue;
        }
        if (primitive.isString()) {
            return primitive.getAsString();
        }
        return primitive.getAsString();
    }

    private static JsonObject asJsonObject(final Map<String, Object> map) {
        if (map == null) {
            return new JsonObject();
        }
        final JsonObject object = new JsonObject();
        for (final Map.Entry<String, Object> entry : map.entrySet()) {
            final String key = entry.getKey();
            final Object value = entry.getValue();

            if (value == null) {
                object.add(key, JsonNull.INSTANCE);
            }
            else if (value instanceof Boolean) {
                object.add(key, new JsonPrimitive((Boolean) value));
            }
            else if (value instanceof Number) {
                object.add(key, new JsonPrimitive((Number) value));
            }
            else if (value instanceof String) {
                object.add(key, new JsonPrimitive((String) value));
            }
            else if (value instanceof Map) {
                object.add(key, asJsonObject((Map<String, Object>) value));
            }
            else if (value instanceof List) {
                object.add(key, asJsonArray((List<Object>) value));
            }
            else {
                object.add(key, new JsonPrimitive(value.toString()));
            }
        }
        return object;
    }

    private static JsonArray asJsonArray(final List<Object> list) {
        if (list == null) {
            return new JsonArray();
        }
        final JsonArray array = new JsonArray();
        for (final Object value : list) {
            if (value == null) {
                array.add(JsonNull.INSTANCE);
            }
            else if (value instanceof Boolean) {
                array.add(new JsonPrimitive((Boolean) value));
            }
            else if (value instanceof Number) {
                array.add(new JsonPrimitive((Number) value));
            }
            else if (value instanceof String) {
                array.add(new JsonPrimitive((String) value));
            }
            else if (value instanceof Map) {
                array.add(asJsonObject((Map<String, Object>) value));
            }
            else if (value instanceof List) {
                array.add(asJsonArray((List<Object>) value));
            }
            else {
                array.add(new JsonPrimitive(value.toString()));
            }
        }
        return array;
    }

    private static <A, B, C> Map<A, C> map(final Map<A, B> source, final Function<B, C> function) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(function);
        final Map<A, C> map = new HashMap<>(source.size());
        for (final Map.Entry<A, B> entry : source.entrySet()) {
            map.put(entry.getKey(), function.apply(entry.getValue()));
        }
        return map;
    }

    private static List<Field> getDeclaredFields(final Class<?> clazz) {
        final List<Field> fields = new ArrayList<>();
        for (Class<?> clazz0 = clazz; clazz0 != null; clazz0 = clazz0.getSuperclass()) {
            fields.addAll(Arrays.asList(clazz0.getDeclaredFields()));
        }
        return fields;
    }

    private static List<Annotation> getAnnotations(final Field field) {
        final Annotation[] annotations = field.getDeclaredAnnotations();
        return (annotations != null) ? Arrays.asList(annotations) : Collections.emptyList();
    }

    boolean isPropertyField(final Field field) {
        return (this.getStatus(field).isValid());
    }

    Status getStatus(final Field field) {
        if (field == null) {
            return Status.OTHER;
        }
        final List<Property> annotations = getAnnotations(field).stream()
                .filter(annotation -> annotation instanceof Property)
                .map(annotation -> (Property) annotation)
                .collect(Collectors.toList())
        ;
        if (annotations.size() == 0) {
            return Status.NONE;
        }
        if (Modifier.isStatic(field.getModifiers())) {
            return Status.STATIC;
        }
        if (!Modifier.isFinal(field.getModifiers())) {
            return Status.ASSIGNABLE;
        }
        if (!Value.class.isAssignableFrom(field.getType())) {
            return Status.WRONG_TYPE;
        }
        if (this.getValue(field) == null) {
            return Status.NULL;
        }
        final boolean isHidden = getAnnotations(field).stream().anyMatch(annotation -> annotation instanceof Hidden);
        return (isHidden) ? Status.HIDDEN : Status.VALID;
    }

    private Value<Object> getValue(final Field field) {
        try {
            field.setAccessible(true);
            final Object value = field.get(this);
            if (value instanceof Value) {
                return (Value<Object>) value;
            }
        }
        catch (final ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
        return null;
    }

    private enum Status {
        VALID, HIDDEN, STATIC, ASSIGNABLE, WRONG_TYPE, NONE, NULL, OTHER;

        boolean isValid() {
            return (this == VALID) || (this == HIDDEN);
        }

        String getMessage() {
            return this.getMessage("");
        }
        String getMessage(final Field field) {
            return this.getMessage(field.getDeclaringClass().getName() + "." + field.getName());
        }
        private String getMessage(final String field) {
            if (this == VALID) {
                return "Valid";
            }
            if (this == STATIC) {
                return "Static field " + field + " annotated with " + Property.class.getName();
            }
            if (this == ASSIGNABLE) {
                return "Assignable field " + field + " annotated with " + Property.class.getName();
            }
            if (this == WRONG_TYPE) {
                return "Field " + field + " of type " + field + " unassignable to " + Value.class.getName();
            }
            if (this == NONE) {
                return "Field " + field + " not annotated with " + Property.class.getName();
            }
            if (this == NULL) {
                return "Field " + field + " has (null) value";
            }
            if (this == OTHER) {
                return "Other";
            }
            return OTHER.getMessage(field);
        }
    }

}