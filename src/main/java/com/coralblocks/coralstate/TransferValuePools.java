package com.coralblocks.coralstate;

import java.nio.ByteBuffer;

import com.coralblocks.coralds.map.CharSequenceMap;
import com.coralblocks.coralpool.ArrayObjectPool;
import com.coralblocks.coralpool.ObjectBuilder;
import com.coralblocks.coralpool.ObjectPool;

final class TransferValuePools {

	static final int NOT_TRANSFER_VALUE = 0;
	static final int CHAR_SEQUENCE = 1;
	static final int BYTE_BUFFER = 2;

	private static final int INITIAL_POOL_CAPACITY = 8;
	private static final int INITIAL_VALUE_CAPACITY = 256;

	private static final ObjectBuilder<MutableCharSequence> CHAR_SEQUENCE_BUILDER =
			new ObjectBuilder<MutableCharSequence>() {
		@Override
		public MutableCharSequence newInstance() {
			return new MutableCharSequence();
		}
	};

	private static final ObjectBuilder<MutableBytes> BYTE_BUFFER_BUILDER =
			new ObjectBuilder<MutableBytes>() {
		@Override
		public MutableBytes newInstance() {
			return new MutableBytes();
		}
	};

	private final ObjectPool<MutableCharSequence> charSequencePool =
			new ArrayObjectPool<>(INITIAL_POOL_CAPACITY, CHAR_SEQUENCE_BUILDER);
	private final ObjectPool<MutableBytes> byteBufferPool =
			new ArrayObjectPool<>(INITIAL_POOL_CAPACITY, BYTE_BUFFER_BUILDER);

	Object putCharSequence(CharSequenceMap<Object> values, CharSequence key, CharSequence source) {
		MutableCharSequence mutable = charSequencePool.get();
		try {
			mutable.set(source);
			return values.put(key, mutable);
		} catch (RuntimeException e) {
			release(mutable);
			throw e;
		}
	}

	Object putByteBuffer(CharSequenceMap<Object> values, CharSequence key, ByteBuffer source) {
		MutableBytes mutable = byteBufferPool.get();
		try {
			mutable.set(source);
			return values.put(key, mutable);
		} catch (RuntimeException e) {
			release(mutable);
			throw e;
		}
	}

	CharSequence getCharSequence(Object value, CharSequence key) {
		return (MutableCharSequence) require(value, key, CHAR_SEQUENCE);
	}

	ByteBuffer getByteBuffer(CharSequenceMap<Object> values, CharSequence key) {
		return ((MutableBytes) require(values, key, BYTE_BUFFER)).readOnlyView();
	}

	CharSequence releaseRemovedCharSequence(CharSequenceMap<Object> values, CharSequence key,
			Object value) {
		MutableCharSequence mutable = (MutableCharSequence) requireRemoved(values, key, value,
				CHAR_SEQUENCE);
		release(mutable);
		return mutable;
	}

	ByteBuffer removeByteBuffer(CharSequenceMap<Object> values, CharSequence key) {
		Object value = values.remove(key);
		MutableBytes mutable = (MutableBytes) requireRemoved(values, key, value, BYTE_BUFFER);
		ByteBuffer readOnly = mutable.readOnlyView();
		release(mutable);
		return readOnly;
	}

	Object acquireCharSequence(ByteBuffer source, int length) {
		MutableCharSequence mutable = charSequencePool.get();
		try {
			mutable.readFrom(source, length);
			return mutable;
		} catch (RuntimeException e) {
			release(mutable);
			throw e;
		}
	}

	Object acquireByteBuffer(ByteBuffer source, int length) {
		MutableBytes mutable = byteBufferPool.get();
		try {
			mutable.readFrom(source, length);
			return mutable;
		} catch (RuntimeException e) {
			release(mutable);
			throw e;
		}
	}

	void release(Object value) {
		switch(typeOf(value)) {
			case CHAR_SEQUENCE:
				charSequencePool.release((MutableCharSequence) value);
				break;
			case BYTE_BUFFER:
				byteBufferPool.release((MutableBytes) value);
				break;
			default:
				break;
		}
	}

	ObjectPool<?> poolFor(Object value) {
		switch(typeOf(value)) {
			case CHAR_SEQUENCE: return charSequencePool;
			case BYTE_BUFFER: return byteBufferPool;
			default: throw new IllegalArgumentException("Not a pooled scalar-transfer value");
		}
	}

	private static MutableTransferValue require(CharSequenceMap<Object> values, CharSequence key,
			int expectedType) {
		return require(values.get(key), key, expectedType);
	}

	private static MutableTransferValue require(Object value, CharSequence key, int expectedType) {
		if (typeOf(value) != expectedType) {
			throw new IllegalArgumentException("State key does not contain a "
					+ typeName(expectedType) + " value: " + key);
		}
		return (MutableTransferValue) value;
	}

	private static MutableTransferValue requireRemoved(CharSequenceMap<Object> values,
			CharSequence key, Object value, int expectedType) {
		if (typeOf(value) != expectedType) {
			if (value != null) values.put(key, value);
			throw new IllegalArgumentException("State key does not contain a "
					+ typeName(expectedType) + " value: " + key);
		}
		return (MutableTransferValue) value;
	}

	static int typeOf(Object value) {
		if (value == null) return NOT_TRANSFER_VALUE;
		Class<?> type = value.getClass();
		if (type == MutableCharSequence.class) return CHAR_SEQUENCE;
		if (type == MutableBytes.class) return BYTE_BUFFER;
		return NOT_TRANSFER_VALUE;
	}

	static int charSequenceLength(Object value) {
		return ((MutableCharSequence) value).length();
	}

	static char charSequenceCharAt(Object value, int index) {
		return ((MutableCharSequence) value).charAt(index);
	}

	static int byteBufferLength(Object value) {
		return ((MutableBytes) value).length;
	}

	static void writeByteBuffer(Object value, ByteBuffer destination) {
		MutableBytes bytes = (MutableBytes) value;
		destination.put(bytes.writable.array(), 0, bytes.length);
	}

	private static String typeName(int type) {
		switch(type) {
			case CHAR_SEQUENCE: return "CharSequence";
			case BYTE_BUFFER: return "ByteBuffer";
			default: return "non-transfer";
		}
	}

	private abstract static class MutableTransferValue {
	}

	private static final class MutableCharSequence extends MutableTransferValue implements CharSequence {

		private final StringBuilder value = new StringBuilder(INITIAL_VALUE_CAPACITY);

		private void set(CharSequence source) {
			if (source == this) {
				throw new IllegalArgumentException("Cannot store a released CharSequence holder");
			}
			value.setLength(0);
			value.append(source);
		}

		private void readFrom(ByteBuffer source, int length) {
			value.setLength(0);
			value.ensureCapacity(length);
			for (int i = 0; i < length; i++) value.append(source.getChar());
		}

		@Override
		public int length() {
			return value.length();
		}

		@Override
		public char charAt(int index) {
			return value.charAt(index);
		}

		@Override
		public CharSequence subSequence(int start, int end) {
			return value.subSequence(start, end);
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) return true;
			if (!(o instanceof MutableCharSequence other)) return false;
			return CharSequence.compare(value, other.value) == 0;
		}

		@Override
		public int hashCode() {
			int hash = 0;
			for (int i = 0; i < value.length(); i++) hash = 31 * hash + value.charAt(i);
			return hash;
		}

		@Override
		public String toString() {
			return value.toString();
		}
	}

	private static final class MutableBytes extends MutableTransferValue {

		private ByteBuffer writable = ByteBuffer.allocate(INITIAL_VALUE_CAPACITY);
		private ByteBuffer readOnly = writable.asReadOnlyBuffer();
		private int length;

		private void set(ByteBuffer source) {
			int sourcePosition = source.position();
			int sourceLength = source.remaining();
			ensureCapacity(sourceLength);
			for (int i = 0; i < sourceLength; i++) {
				writable.put(i, source.get(sourcePosition + i));
			}
			length = sourceLength;
		}

		private void readFrom(ByteBuffer source, int sourceLength) {
			ensureCapacity(sourceLength);
			source.get(writable.array(), 0, sourceLength);
			length = sourceLength;
		}

		private ByteBuffer readOnlyView() {
			readOnly.clear();
			readOnly.limit(length);
			return readOnly;
		}

		private void ensureCapacity(int requiredCapacity) {
			int currentCapacity = writable.capacity();
			if (requiredCapacity <= currentCapacity) return;
			int newCapacity = currentCapacity <= Integer.MAX_VALUE / 2
					? Math.max(requiredCapacity, currentCapacity * 2)
					: requiredCapacity;
			writable = ByteBuffer.allocate(newCapacity);
			readOnly = writable.asReadOnlyBuffer();
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) return true;
			if (!(o instanceof MutableBytes other)) return false;
			if (length != other.length) return false;
			for (int i = 0; i < length; i++) {
				if (writable.get(i) != other.writable.get(i)) return false;
			}
			return true;
		}

		@Override
		public int hashCode() {
			int hash = 1;
			for (int i = 0; i < length; i++) hash = 31 * hash + writable.get(i);
			return hash;
		}

		@Override
		public String toString() {
			return "ByteBuffer[remaining=" + length + ']';
		}
	}
}
