package com.coralblocks.coralstate;

import java.nio.ByteBuffer;
import java.util.Iterator;

import com.coralblocks.coralds.map.CharSequenceMap;

public class State {

	private final CharSequenceMap<Object> values = new CharSequenceMap<>();
	private final PrimitiveValuePools primitiveValues = new PrimitiveValuePools();
	private final StateRegistry registry;
	private final StateSerializer serializer = new StateSerializer();
	private final StateDeserializer deserializer = new StateDeserializer();
	
	public State(StateRegistry registry) {
		if (registry == null) throw new IllegalArgumentException("StateRegistry cannot be null");
		this.registry = registry;
	}
	
	public StateRegistry getRegistry() {
		return registry;
	}

	/**
	 * Stores an object supported by CoralState's built-in wire format or by this State's registry.
	 * CoralDS object containers are recursively validated in their current state. Because those
	 * containers remain mutable, they are validated again when the State is serialized.
	 */
	public void put(CharSequence key, Object value) {
		checkKey(key);
		serializer.validateForPut(value, registry);
		primitiveValues.release(values.put(key, value));
	}

	public void put(CharSequence key, boolean value) {
		checkKey(key);
		primitiveValues.putBoolean(values, key, value);
	}

	public void put(CharSequence key, byte value) {
		checkKey(key);
		primitiveValues.putByte(values, key, value);
	}

	public void put(CharSequence key, char value) {
		checkKey(key);
		primitiveValues.putChar(values, key, value);
	}

	public void put(CharSequence key, short value) {
		checkKey(key);
		primitiveValues.putShort(values, key, value);
	}

	public void put(CharSequence key, int value) {
		checkKey(key);
		primitiveValues.putInt(values, key, value);
	}

	public void put(CharSequence key, long value) {
		checkKey(key);
		primitiveValues.putLong(values, key, value);
	}

	public void put(CharSequence key, float value) {
		checkKey(key);
		primitiveValues.putFloat(values, key, value);
	}

	public void put(CharSequence key, double value) {
		checkKey(key);
		primitiveValues.putDouble(values, key, value);
	}
	
	public Object get(CharSequence key) {
		checkKey(key);
		Object value = values.get(key);
		if (PrimitiveValuePools.typeOf(value) != PrimitiveValuePools.NOT_PRIMITIVE) {
			throw new IllegalArgumentException("Primitive State values require a typed getter: " + key);
		}
		return value;
	}

	public boolean getBoolean(CharSequence key) {
		checkKey(key);
		return primitiveValues.getBoolean(values, key);
	}

	public byte getByte(CharSequence key) {
		checkKey(key);
		return primitiveValues.getByte(values, key);
	}

	public char getChar(CharSequence key) {
		checkKey(key);
		return primitiveValues.getChar(values, key);
	}

	public short getShort(CharSequence key) {
		checkKey(key);
		return primitiveValues.getShort(values, key);
	}

	public int getInt(CharSequence key) {
		checkKey(key);
		return primitiveValues.getInt(values, key);
	}

	public long getLong(CharSequence key) {
		checkKey(key);
		return primitiveValues.getLong(values, key);
	}

	public float getFloat(CharSequence key) {
		checkKey(key);
		return primitiveValues.getFloat(values, key);
	}

	public double getDouble(CharSequence key) {
		checkKey(key);
		return primitiveValues.getDouble(values, key);
	}

	public Object remove(CharSequence key) {
		checkKey(key);
		Object value = values.get(key);
		if (PrimitiveValuePools.typeOf(value) != PrimitiveValuePools.NOT_PRIMITIVE) {
			throw new IllegalArgumentException("Primitive State values require a typed remover: " + key);
		}
		return values.remove(key);
	}

	public boolean removeBoolean(CharSequence key) {
		checkKey(key);
		return primitiveValues.removeBoolean(values, key);
	}

	public byte removeByte(CharSequence key) {
		checkKey(key);
		return primitiveValues.removeByte(values, key);
	}

	public char removeChar(CharSequence key) {
		checkKey(key);
		return primitiveValues.removeChar(values, key);
	}

	public short removeShort(CharSequence key) {
		checkKey(key);
		return primitiveValues.removeShort(values, key);
	}

	public int removeInt(CharSequence key) {
		checkKey(key);
		return primitiveValues.removeInt(values, key);
	}

	public long removeLong(CharSequence key) {
		checkKey(key);
		return primitiveValues.removeLong(values, key);
	}

	public float removeFloat(CharSequence key) {
		checkKey(key);
		return primitiveValues.removeFloat(values, key);
	}

	public double removeDouble(CharSequence key) {
		checkKey(key);
		return primitiveValues.removeDouble(values, key);
	}
	
	public boolean check(CharSequence key) {
		checkKey(key);
		return values.containsKey(key);
	}

	public int size() {
		return values.size();
	}

	public boolean isEmpty() {
		return values.isEmpty();
	}

	/**
	 * Returns the exact number of bytes required to serialize this State.
	 *
	 * @return the serialized length of this State
	 * @throws IllegalStateException if this State contains an unsupported value or cyclic container
	 */
	public int getSerializedLength() {
		return serializer.getSerializedLength(this);
	}

	/**
	 * Writes this State at the buffer's current position.
	 *
	 * @param buffer the destination buffer
	 * @return the number of bytes written
	 * @throws IllegalArgumentException if the buffer is null
	 * @throws IllegalStateException if this State contains an unsupported value or cyclic container
	 */
	public int writeTo(ByteBuffer buffer) {
		return serializer.write(this, buffer);
	}

	/**
	 * Reads a serialized State from the buffer's current position into this empty State.
	 *
	 * @param buffer the source buffer
	 * @return the number of bytes consumed
	 * @throws IllegalArgumentException if the buffer is null, this State is not empty, or the data is invalid
	 */
	public int readFrom(ByteBuffer buffer) {
		return deserializer.read(this, buffer);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof State other)) return false;
		if (size() != other.size()) return false;

		Iterator<Object> iter = values.iterator();
		while(iter.hasNext()) {
			Object value = iter.next();
			Object otherValue = other.values.get(values.getCurrIteratorKey());
			if (otherValue == null || !value.equals(otherValue)) return false;
		}
		return true;
	}

	@Override
	public int hashCode() {
		int hash = 0;
		Iterator<Object> iter = values.iterator();
		while(iter.hasNext()) {
			Object value = iter.next();
			hash += hashCode(values.getCurrIteratorKey()) ^ value.hashCode();
		}
		return hash;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("State{");

		Iterator<Object> iter = values.iterator();
		boolean first = true;
		while(iter.hasNext()) {
			Object value = iter.next();
			if (!first) sb.append(", ");
			first = false;
			sb.append(values.getCurrIteratorKey()).append('=');
			sb.append(value == this ? "(this State)" : value);
		}

		sb.append('}');
		return sb.toString();
	}

	private static int hashCode(CharSequence value) {
		int hash = 0;
		for (int i = 0; i < value.length(); i++) {
			hash = 31 * hash + value.charAt(i);
		}
		return hash;
	}

	private static void checkKey(CharSequence key) {
		if (key == null) throw new IllegalArgumentException("State key cannot be null");
	}

	CharSequenceMap<Object> internalValues() {
		return values;
	}

	PrimitiveValuePools primitiveValues() {
		return primitiveValues;
	}
}
