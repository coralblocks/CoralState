package com.coralblocks.coralstate;

import java.nio.ByteBuffer;
import java.util.Iterator;

import com.coralblocks.coralds.map.CharSequenceMap;

public class State {

	private final CharSequenceMap<Object> values = new CharSequenceMap<>();
	private final PrimitiveValuePools primitiveValues = new PrimitiveValuePools();
	private final TransferValuePools transferValues = new TransferValuePools();
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
	 * containers remain mutable, they are validated again when the State is serialized. A non-String
	 * {@link CharSequence} or a {@link ByteBuffer} is copied into internal pooled storage and is only
	 * supported as a top-level State value. Copying a ByteBuffer does not change its position and uses
	 * only its remaining bytes. State keys are encoded as one byte per character and therefore only
	 * support Latin-1 characters.
	 */
	public void put(CharSequence key, Object value) {
		checkPutKey(key);
		if (value == null) throw new IllegalArgumentException("State value cannot be null");
		if (!(value instanceof String) && value instanceof CharSequence charSequence) {
			putCharSequence(key, charSequence);
			return;
		}
		if (value instanceof ByteBuffer byteBuffer) {
			putByteBuffer(key, byteBuffer);
			return;
		}
		serializer.validateForPut(value, registry);
		releaseInternal(values.put(key, value));
	}

	public void put(CharSequence key, boolean value) {
		checkPutKey(key);
		Object previous = values.get(key);
		primitiveValues.putBoolean(values, key, value);
		transferValues.release(previous);
	}

	public void put(CharSequence key, byte value) {
		checkPutKey(key);
		Object previous = values.get(key);
		primitiveValues.putByte(values, key, value);
		transferValues.release(previous);
	}

	public void put(CharSequence key, char value) {
		checkPutKey(key);
		Object previous = values.get(key);
		primitiveValues.putChar(values, key, value);
		transferValues.release(previous);
	}

	public void put(CharSequence key, short value) {
		checkPutKey(key);
		Object previous = values.get(key);
		primitiveValues.putShort(values, key, value);
		transferValues.release(previous);
	}

	public void put(CharSequence key, int value) {
		checkPutKey(key);
		Object previous = values.get(key);
		primitiveValues.putInt(values, key, value);
		transferValues.release(previous);
	}

	public void put(CharSequence key, long value) {
		checkPutKey(key);
		Object previous = values.get(key);
		primitiveValues.putLong(values, key, value);
		transferValues.release(previous);
	}

	public void put(CharSequence key, float value) {
		checkPutKey(key);
		Object previous = values.get(key);
		primitiveValues.putFloat(values, key, value);
		transferValues.release(previous);
	}

	public void put(CharSequence key, double value) {
		checkPutKey(key);
		Object previous = values.get(key);
		primitiveValues.putDouble(values, key, value);
		transferValues.release(previous);
	}
	
	public Object get(CharSequence key) {
		checkKey(key);
		Object value = values.get(key);
		if (PrimitiveValuePools.typeOf(value) != PrimitiveValuePools.NOT_PRIMITIVE) {
			throw new IllegalArgumentException("Primitive State values require a typed getter: " + key);
		}
		if (TransferValuePools.typeOf(value) != TransferValuePools.NOT_TRANSFER_VALUE) {
			throw new IllegalArgumentException("Scalar-transfer State values require a typed getter: " + key);
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

	/**
	 * Returns a borrowed String or read-only pooled CharSequence value. A pooled value remains valid
	 * only until its State entry is replaced or removed and must not be retained beyond that point.
	 */
	public CharSequence getCharSequence(CharSequence key) {
		checkKey(key);
		Object value = values.get(key);
		if (value instanceof String string) return string;
		return transferValues.getCharSequence(values, key);
	}

	/**
	 * Returns a borrowed read-only ByteBuffer positioned at zero and limited to the stored byte
	 * length. Repeated calls for the same entry return and reset the same view. The view remains valid
	 * only until its State entry is replaced or removed and must not be retained beyond that point.
	 */
	public ByteBuffer getByteBuffer(CharSequence key) {
		checkKey(key);
		return transferValues.getByteBuffer(values, key);
	}

	public Object remove(CharSequence key) {
		checkKey(key);
		Object value = values.get(key);
		if (PrimitiveValuePools.typeOf(value) != PrimitiveValuePools.NOT_PRIMITIVE) {
			throw new IllegalArgumentException("Primitive State values require a typed remover: " + key);
		}
		if (TransferValuePools.typeOf(value) != TransferValuePools.NOT_TRANSFER_VALUE) {
			throw new IllegalArgumentException("Scalar-transfer State values require a typed remover: " + key);
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

	/**
	 * Removes and returns a String or borrowed pooled CharSequence value. A returned pooled value
	 * must be consumed before another mutation of this State can reuse its holder.
	 */
	public CharSequence removeCharSequence(CharSequence key) {
		checkKey(key);
		Object value = values.get(key);
		if (value instanceof String string) {
			values.remove(key);
			return string;
		}
		return transferValues.removeCharSequence(values, key);
	}

	/**
	 * Removes and returns a borrowed read-only ByteBuffer view. The returned view must be consumed
	 * before another mutation of this State can reuse its holder.
	 */
	public ByteBuffer removeByteBuffer(CharSequence key) {
		checkKey(key);
		return transferValues.removeByteBuffer(values, key);
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

	private static void checkPutKey(CharSequence key) {
		checkKey(key);
		StateSerializer.validateKeyForPut(key, "State key");
	}

	private void putCharSequence(CharSequence key, CharSequence value) {
		Object previous = values.get(key);
		transferValues.putCharSequence(values, key, value);
		releaseInternal(previous);
	}

	private void putByteBuffer(CharSequence key, ByteBuffer value) {
		Object previous = values.get(key);
		transferValues.putByteBuffer(values, key, value);
		releaseInternal(previous);
	}

	private void releaseInternal(Object value) {
		primitiveValues.release(value);
		transferValues.release(value);
	}

	CharSequenceMap<Object> internalValues() {
		return values;
	}

	PrimitiveValuePools primitiveValues() {
		return primitiveValues;
	}

	TransferValuePools transferValues() {
		return transferValues;
	}
}
