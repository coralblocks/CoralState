package com.coralblocks.coralstate;

import com.coralblocks.coralds.map.CharSequenceMap;
import com.coralblocks.coralpool.ArrayObjectPool;
import com.coralblocks.coralpool.ObjectBuilder;
import com.coralblocks.coralpool.ObjectPool;

final class PrimitiveValuePools {

	static final int NOT_PRIMITIVE = 0;
	static final int BOOLEAN = 1;
	static final int BYTE = 2;
	static final int CHAR = 3;
	static final int SHORT = 4;
	static final int INT = 5;
	static final int LONG = 6;
	static final int FLOAT = 7;
	static final int DOUBLE = 8;

	private static final int INITIAL_POOL_CAPACITY = 8;

	private static final ObjectBuilder<MutableBoolean> BOOLEAN_BUILDER = new ObjectBuilder<MutableBoolean>() {
		@Override
		public MutableBoolean newInstance() {
			return new MutableBoolean();
		}
	};

	private static final ObjectBuilder<MutableByte> BYTE_BUILDER = new ObjectBuilder<MutableByte>() {
		@Override
		public MutableByte newInstance() {
			return new MutableByte();
		}
	};

	private static final ObjectBuilder<MutableChar> CHAR_BUILDER = new ObjectBuilder<MutableChar>() {
		@Override
		public MutableChar newInstance() {
			return new MutableChar();
		}
	};

	private static final ObjectBuilder<MutableShort> SHORT_BUILDER = new ObjectBuilder<MutableShort>() {
		@Override
		public MutableShort newInstance() {
			return new MutableShort();
		}
	};

	private static final ObjectBuilder<MutableInt> INT_BUILDER = new ObjectBuilder<MutableInt>() {
		@Override
		public MutableInt newInstance() {
			return new MutableInt();
		}
	};

	private static final ObjectBuilder<MutableLong> LONG_BUILDER = new ObjectBuilder<MutableLong>() {
		@Override
		public MutableLong newInstance() {
			return new MutableLong();
		}
	};

	private static final ObjectBuilder<MutableFloat> FLOAT_BUILDER = new ObjectBuilder<MutableFloat>() {
		@Override
		public MutableFloat newInstance() {
			return new MutableFloat();
		}
	};

	private static final ObjectBuilder<MutableDouble> DOUBLE_BUILDER = new ObjectBuilder<MutableDouble>() {
		@Override
		public MutableDouble newInstance() {
			return new MutableDouble();
		}
	};

	private final ObjectPool<MutableBoolean> booleanPool = new ArrayObjectPool<>(INITIAL_POOL_CAPACITY, BOOLEAN_BUILDER);
	private final ObjectPool<MutableByte> bytePool = new ArrayObjectPool<>(INITIAL_POOL_CAPACITY, BYTE_BUILDER);
	private final ObjectPool<MutableChar> charPool = new ArrayObjectPool<>(INITIAL_POOL_CAPACITY, CHAR_BUILDER);
	private final ObjectPool<MutableShort> shortPool = new ArrayObjectPool<>(INITIAL_POOL_CAPACITY, SHORT_BUILDER);
	private final ObjectPool<MutableInt> intPool = new ArrayObjectPool<>(INITIAL_POOL_CAPACITY, INT_BUILDER);
	private final ObjectPool<MutableLong> longPool = new ArrayObjectPool<>(INITIAL_POOL_CAPACITY, LONG_BUILDER);
	private final ObjectPool<MutableFloat> floatPool = new ArrayObjectPool<>(INITIAL_POOL_CAPACITY, FLOAT_BUILDER);
	private final ObjectPool<MutableDouble> doublePool = new ArrayObjectPool<>(INITIAL_POOL_CAPACITY, DOUBLE_BUILDER);

	void putBoolean(CharSequenceMap<Object> values, CharSequence key, boolean value) {
		put(values, key, acquireBoolean(value));
	}

	void putByte(CharSequenceMap<Object> values, CharSequence key, byte value) {
		put(values, key, acquireByte(value));
	}

	void putChar(CharSequenceMap<Object> values, CharSequence key, char value) {
		put(values, key, acquireChar(value));
	}

	void putShort(CharSequenceMap<Object> values, CharSequence key, short value) {
		put(values, key, acquireShort(value));
	}

	void putInt(CharSequenceMap<Object> values, CharSequence key, int value) {
		put(values, key, acquireInt(value));
	}

	void putLong(CharSequenceMap<Object> values, CharSequence key, long value) {
		put(values, key, acquireLong(value));
	}

	void putFloat(CharSequenceMap<Object> values, CharSequence key, float value) {
		put(values, key, acquireFloat(value));
	}

	void putDouble(CharSequenceMap<Object> values, CharSequence key, double value) {
		put(values, key, acquireDouble(value));
	}

	boolean getBoolean(CharSequenceMap<Object> values, CharSequence key) {
		return ((MutableBoolean) require(values, key, BOOLEAN)).value;
	}

	byte getByte(CharSequenceMap<Object> values, CharSequence key) {
		return ((MutableByte) require(values, key, BYTE)).value;
	}

	char getChar(CharSequenceMap<Object> values, CharSequence key) {
		return ((MutableChar) require(values, key, CHAR)).value;
	}

	short getShort(CharSequenceMap<Object> values, CharSequence key) {
		return ((MutableShort) require(values, key, SHORT)).value;
	}

	int getInt(CharSequenceMap<Object> values, CharSequence key) {
		return ((MutableInt) require(values, key, INT)).value;
	}

	long getLong(CharSequenceMap<Object> values, CharSequence key) {
		return ((MutableLong) require(values, key, LONG)).value;
	}

	float getFloat(CharSequenceMap<Object> values, CharSequence key) {
		return ((MutableFloat) require(values, key, FLOAT)).value;
	}

	double getDouble(CharSequenceMap<Object> values, CharSequence key) {
		return ((MutableDouble) require(values, key, DOUBLE)).value;
	}

	boolean removeBoolean(CharSequenceMap<Object> values, CharSequence key) {
		MutableBoolean mutable = (MutableBoolean) remove(values, key, BOOLEAN);
		boolean value = mutable.value;
		booleanPool.release(mutable);
		return value;
	}

	byte removeByte(CharSequenceMap<Object> values, CharSequence key) {
		MutableByte mutable = (MutableByte) remove(values, key, BYTE);
		byte value = mutable.value;
		bytePool.release(mutable);
		return value;
	}

	char removeChar(CharSequenceMap<Object> values, CharSequence key) {
		MutableChar mutable = (MutableChar) remove(values, key, CHAR);
		char value = mutable.value;
		charPool.release(mutable);
		return value;
	}

	short removeShort(CharSequenceMap<Object> values, CharSequence key) {
		MutableShort mutable = (MutableShort) remove(values, key, SHORT);
		short value = mutable.value;
		shortPool.release(mutable);
		return value;
	}

	int removeInt(CharSequenceMap<Object> values, CharSequence key) {
		MutableInt mutable = (MutableInt) remove(values, key, INT);
		int value = mutable.value;
		intPool.release(mutable);
		return value;
	}

	long removeLong(CharSequenceMap<Object> values, CharSequence key) {
		MutableLong mutable = (MutableLong) remove(values, key, LONG);
		long value = mutable.value;
		longPool.release(mutable);
		return value;
	}

	float removeFloat(CharSequenceMap<Object> values, CharSequence key) {
		MutableFloat mutable = (MutableFloat) remove(values, key, FLOAT);
		float value = mutable.value;
		floatPool.release(mutable);
		return value;
	}

	double removeDouble(CharSequenceMap<Object> values, CharSequence key) {
		MutableDouble mutable = (MutableDouble) remove(values, key, DOUBLE);
		double value = mutable.value;
		doublePool.release(mutable);
		return value;
	}

	MutablePrimitive acquireBoolean(boolean value) {
		MutableBoolean mutable = booleanPool.get();
		mutable.value = value;
		return mutable;
	}

	MutablePrimitive acquireByte(byte value) {
		MutableByte mutable = bytePool.get();
		mutable.value = value;
		return mutable;
	}

	MutablePrimitive acquireChar(char value) {
		MutableChar mutable = charPool.get();
		mutable.value = value;
		return mutable;
	}

	MutablePrimitive acquireShort(short value) {
		MutableShort mutable = shortPool.get();
		mutable.value = value;
		return mutable;
	}

	MutablePrimitive acquireInt(int value) {
		MutableInt mutable = intPool.get();
		mutable.value = value;
		return mutable;
	}

	MutablePrimitive acquireLong(long value) {
		MutableLong mutable = longPool.get();
		mutable.value = value;
		return mutable;
	}

	MutablePrimitive acquireFloat(float value) {
		MutableFloat mutable = floatPool.get();
		mutable.value = value;
		return mutable;
	}

	MutablePrimitive acquireDouble(double value) {
		MutableDouble mutable = doublePool.get();
		mutable.value = value;
		return mutable;
	}

	void release(Object value) {
		switch(typeOf(value)) {
			case BOOLEAN: booleanPool.release((MutableBoolean) value); break;
			case BYTE: bytePool.release((MutableByte) value); break;
			case CHAR: charPool.release((MutableChar) value); break;
			case SHORT: shortPool.release((MutableShort) value); break;
			case INT: intPool.release((MutableInt) value); break;
			case LONG: longPool.release((MutableLong) value); break;
			case FLOAT: floatPool.release((MutableFloat) value); break;
			case DOUBLE: doublePool.release((MutableDouble) value); break;
			default: break;
		}
	}

	ObjectPool<?> poolFor(Object value) {
		switch(typeOf(value)) {
			case BOOLEAN: return booleanPool;
			case BYTE: return bytePool;
			case CHAR: return charPool;
			case SHORT: return shortPool;
			case INT: return intPool;
			case LONG: return longPool;
			case FLOAT: return floatPool;
			case DOUBLE: return doublePool;
			default: throw new IllegalArgumentException("Not a pooled primitive value");
		}
	}

	private void put(CharSequenceMap<Object> values, CharSequence key, MutablePrimitive mutable) {
		Object previous;
		try {
			previous = values.put(key, mutable);
		} catch (RuntimeException e) {
			release(mutable);
			throw e;
		}
		release(previous);
	}

	private static MutablePrimitive require(CharSequenceMap<Object> values, CharSequence key,
			int expectedType) {
		Object value = values.get(key);
		if (typeOf(value) != expectedType) {
			throw new IllegalArgumentException("State key does not contain a "
					+ typeName(expectedType) + " value: " + key);
		}
		return (MutablePrimitive) value;
	}

	private static MutablePrimitive remove(CharSequenceMap<Object> values, CharSequence key,
			int expectedType) {
		MutablePrimitive mutable = require(values, key, expectedType);
		values.remove(key);
		return mutable;
	}

	static int typeOf(Object value) {
		if (value == null) return NOT_PRIMITIVE;
		Class<?> type = value.getClass();
		if (type == MutableBoolean.class) return BOOLEAN;
		if (type == MutableByte.class) return BYTE;
		if (type == MutableChar.class) return CHAR;
		if (type == MutableShort.class) return SHORT;
		if (type == MutableInt.class) return INT;
		if (type == MutableLong.class) return LONG;
		if (type == MutableFloat.class) return FLOAT;
		if (type == MutableDouble.class) return DOUBLE;
		return NOT_PRIMITIVE;
	}

	static boolean booleanValue(Object value) {
		return ((MutableBoolean) value).value;
	}

	static byte byteValue(Object value) {
		return ((MutableByte) value).value;
	}

	static char charValue(Object value) {
		return ((MutableChar) value).value;
	}

	static short shortValue(Object value) {
		return ((MutableShort) value).value;
	}

	static int intValue(Object value) {
		return ((MutableInt) value).value;
	}

	static long longValue(Object value) {
		return ((MutableLong) value).value;
	}

	static float floatValue(Object value) {
		return ((MutableFloat) value).value;
	}

	static double doubleValue(Object value) {
		return ((MutableDouble) value).value;
	}

	static String typeName(int type) {
		switch(type) {
			case BOOLEAN: return "boolean";
			case BYTE: return "byte";
			case CHAR: return "char";
			case SHORT: return "short";
			case INT: return "int";
			case LONG: return "long";
			case FLOAT: return "float";
			case DOUBLE: return "double";
			default: return "non-primitive";
		}
	}

	abstract static class MutablePrimitive {
	}

	private static final class MutableBoolean extends MutablePrimitive {
		private boolean value;

		@Override
		public boolean equals(Object o) {
			return o instanceof MutableBoolean other && value == other.value;
		}

		@Override
		public int hashCode() {
			return Boolean.hashCode(value);
		}

		@Override
		public String toString() {
			return Boolean.toString(value);
		}
	}

	private static final class MutableByte extends MutablePrimitive {
		private byte value;

		@Override
		public boolean equals(Object o) {
			return o instanceof MutableByte other && value == other.value;
		}

		@Override
		public int hashCode() {
			return Byte.hashCode(value);
		}

		@Override
		public String toString() {
			return Byte.toString(value);
		}
	}

	private static final class MutableChar extends MutablePrimitive {
		private char value;

		@Override
		public boolean equals(Object o) {
			return o instanceof MutableChar other && value == other.value;
		}

		@Override
		public int hashCode() {
			return Character.hashCode(value);
		}

		@Override
		public String toString() {
			return Character.toString(value);
		}
	}

	private static final class MutableShort extends MutablePrimitive {
		private short value;

		@Override
		public boolean equals(Object o) {
			return o instanceof MutableShort other && value == other.value;
		}

		@Override
		public int hashCode() {
			return Short.hashCode(value);
		}

		@Override
		public String toString() {
			return Short.toString(value);
		}
	}

	private static final class MutableInt extends MutablePrimitive {
		private int value;

		@Override
		public boolean equals(Object o) {
			return o instanceof MutableInt other && value == other.value;
		}

		@Override
		public int hashCode() {
			return Integer.hashCode(value);
		}

		@Override
		public String toString() {
			return Integer.toString(value);
		}
	}

	private static final class MutableLong extends MutablePrimitive {
		private long value;

		@Override
		public boolean equals(Object o) {
			return o instanceof MutableLong other && value == other.value;
		}

		@Override
		public int hashCode() {
			return Long.hashCode(value);
		}

		@Override
		public String toString() {
			return Long.toString(value);
		}
	}

	private static final class MutableFloat extends MutablePrimitive {
		private float value;

		@Override
		public boolean equals(Object o) {
			return o instanceof MutableFloat other && Float.compare(value, other.value) == 0;
		}

		@Override
		public int hashCode() {
			return Float.hashCode(value);
		}

		@Override
		public String toString() {
			return Float.toString(value);
		}
	}

	private static final class MutableDouble extends MutablePrimitive {
		private double value;

		@Override
		public boolean equals(Object o) {
			return o instanceof MutableDouble other && Double.compare(value, other.value) == 0;
		}

		@Override
		public int hashCode() {
			return Double.hashCode(value);
		}

		@Override
		public String toString() {
			return Double.toString(value);
		}
	}
}
