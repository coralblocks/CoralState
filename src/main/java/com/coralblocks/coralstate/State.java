package com.coralblocks.coralstate;

import java.nio.ByteBuffer;
import java.util.Iterator;

import com.coralblocks.coralds.map.CharSequenceMap;

public class State {

	private final CharSequenceMap<Object> values = new CharSequenceMap<>();
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
	
	public void put(CharSequence key, Object value) {
		if (key == null) throw new IllegalArgumentException("State key cannot be null");
		if (value == null) throw new IllegalArgumentException("State value cannot be null");
		values.put(key, value);
	}
	
	public Object get(CharSequence key) {
		if (key == null) throw new IllegalArgumentException("State key cannot be null");
		return values.get(key);
	}

	public Object remove(CharSequence key) {
		if (key == null) throw new IllegalArgumentException("State key cannot be null");
		return values.remove(key);
	}
	
	public boolean check(CharSequence key) {
		if (key == null) throw new IllegalArgumentException("State key cannot be null");
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
	 * @throws IllegalArgumentException if this State contains an unsupported value or cyclic container
	 */
	public int getSerializedLength() {
		return serializer.getSerializedLength(this);
	}

	/**
	 * Writes this State at the buffer's current position.
	 *
	 * @param buffer the destination buffer
	 * @return the number of bytes written
	 * @throws IllegalArgumentException if the buffer is null or this State contains an unsupported value
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
		if (!(o instanceof State)) return false;

		State other = (State) o;
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

	CharSequenceMap<Object> internalValues() {
		return values;
	}
}
