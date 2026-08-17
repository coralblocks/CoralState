package com.coralblocks.coralstate;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.coralblocks.coralds.list.ArrayLinkedList;
import com.coralblocks.coralds.list.ArrayList;
import com.coralblocks.coralds.list.IntArrayList;
import com.coralblocks.coralds.list.IntLinkedList;
import com.coralblocks.coralds.list.LinkedList;
import com.coralblocks.coralds.list.LongArrayList;
import com.coralblocks.coralds.list.LongLinkedList;
import com.coralblocks.coralds.map.ByteBufferMap;
import com.coralblocks.coralds.map.ByteMap;
import com.coralblocks.coralds.map.CharMap;
import com.coralblocks.coralds.map.CharSequenceMap;
import com.coralblocks.coralds.map.IdentityMap;
import com.coralblocks.coralds.map.IntMap;
import com.coralblocks.coralds.map.LinkedMap;
import com.coralblocks.coralds.map.LongMap;
import com.coralblocks.coralds.map.Map;
import com.coralblocks.coralds.set.IdentitySet;
import com.coralblocks.coralds.set.IntSet;
import com.coralblocks.coralds.set.LinkedSet;
import com.coralblocks.coralds.set.LongSet;
import com.coralblocks.coralds.set.Set;
import com.coralblocks.coralpool.ObjectPool;
import com.coralblocks.coralproto.Proto;

/**
 * Deserializes a {@link State} written by {@link StateSerializer}.
 *
 * <p>This deserializer is deliberately single-threaded and is not thread-safe. Strings, pooled
 * scalar-transfer values, primitives, registered codec objects, and every concrete CoralDS
 * collection are supported as explicit wire types.</p>
 */
final class StateDeserializer {

	private static final int PROTO_HEADER_LENGTH = 4;
	private static final int INITIAL_KEY_DEPTH = 4;
	private static final int INITIAL_ROLLBACK_CAPACITY = 16;
	private static final int INITIAL_STRING_CAPACITY = 256;
	// Preserve normal empty-container defaults plus one growth step without letting a tiny node
	// request arbitrary eager allocation.
	private static final int MIN_CONTAINER_ALLOCATION_LIMIT = 2 * Math.max(
			ByteBufferMap.DEFAULT_INITIAL_CAPACITY, CharSequenceMap.DEFAULT_INITIAL_CAPACITY);
	// Pooled key maps allocate maximum-key storage per preloaded or decoded entry.
	private static final int KEY_STORAGE_WIRE_AMPLIFICATION = 8;
	private static final long MIN_KEY_STORAGE_LIMIT = (long) MIN_CONTAINER_ALLOCATION_LIMIT
			* Math.max(ByteBufferMap.DEFAULT_MAX_KEY_LENGTH, CharSequenceMap.DEFAULT_MAX_KEY_LENGTH);
	// A container level consumes several Java frames; keep hostile wire nesting well below the
	// platform stack limit instead of relying on StackOverflowError recovery.
	static final int MAX_VALUE_DEPTH = 128;

	private final ArrayList<StringBuilder> keyBuilders = new ArrayList<>(INITIAL_KEY_DEPTH);
	private final StringBuilder identifierBuilder = new StringBuilder(StateSerializer.MAX_WIRE_NAME_LENGTH);
	private final StringBuilder stringBuilder = new StringBuilder(INITIAL_STRING_CAPACITY);
	private final RollbackJournal rollbackJournal = new RollbackJournal();
	private State targetState;
	private int valueDepth;

	StateDeserializer() {
		for (int i = 0; i < INITIAL_KEY_DEPTH; i++) {
			keyBuilders.add(new StringBuilder(CharSequenceMap.DEFAULT_MAX_KEY_LENGTH));
		}
	}

	int read(State state, ByteBuffer buffer) {
		if (state == null) throw new IllegalArgumentException("State cannot be null");
		if (buffer == null) throw new IllegalArgumentException("ByteBuffer cannot be null");
		if (!state.isEmpty()) throw new IllegalArgumentException("Destination State must be empty");
		if (valueDepth != 0) throw new IllegalStateException("StateDeserializer value depth was not cleared");
		rollbackJournal.begin();
		targetState = state;

		int startPosition = buffer.position();
		ByteOrder originalOrder = buffer.order();
		try {
			buffer.order(ByteOrder.BIG_ENDIAN);
			readMagic(buffer);
			short formatVersion = readShort(buffer, "State format version");
			if (formatVersion != StateSerializer.FORMAT_VERSION) {
				throw new IllegalArgumentException("Unsupported State format version: " + formatVersion);
			}

			int entryCount = readNonNegativeInt(buffer, "State entry count");
			for (int i = 0; i < entryCount; i++) {
				StringBuilder keyBuilder = getKeyBuilder(0, CharSequenceMap.DEFAULT_MAX_KEY_LENGTH);
				readChars(buffer, keyBuilder, "State key length", CharSequenceMap.DEFAULT_MAX_KEY_LENGTH);
				Object value = readValue(state.getRegistry(), buffer, 1);
				if (state.internalValues().put(keyBuilder, value) != null) {
					throw new IllegalArgumentException("Duplicate State key: " + keyBuilder);
				}
			}
			int bytesRead = buffer.position() - startPosition;
			rollbackJournal.commit();
			return bytesRead;
		} catch (Throwable failure) {
			try {
				state.internalValues().clear();
			} catch (Throwable recoveryFailure) {
				suppress(failure, recoveryFailure);
			}
			try {
				rollbackJournal.rollback();
			} catch (Throwable recoveryFailure) {
				suppress(failure, recoveryFailure);
			}
			try {
				buffer.position(startPosition);
			} catch (Throwable recoveryFailure) {
				suppress(failure, recoveryFailure);
			}
			throw failure;
		} finally {
			targetState = null;
			clearBuilders();
			buffer.order(originalOrder);
		}
	}

	private Object readValue(StateRegistry registry, ByteBuffer buffer, int keyDepth) {
		if (valueDepth >= MAX_VALUE_DEPTH) {
			throw new IllegalArgumentException("Maximum State value nesting depth exceeded: "
					+ MAX_VALUE_DEPTH);
		}
		valueDepth++;
		try {
			int nodeLength = readNonNegativeInt(buffer, "node length");
			if (nodeLength > buffer.remaining()) throw new IllegalArgumentException("Invalid node length: " + nodeLength);

			int nodeEnd = buffer.position() + nodeLength;
			int originalLimit = buffer.limit();
			buffer.limit(nodeEnd);
			try {
				readChars(buffer, identifierBuilder, "node identifier length", StateSerializer.MAX_WIRE_NAME_LENGTH);
				Object value = readIdentifiedValue(registry, buffer, keyDepth);
				if (buffer.position() != nodeEnd) {
					throw new IllegalArgumentException("Node was not fully consumed: remaining=" + buffer.remaining());
				}
				return value;
			} finally {
				buffer.limit(originalLimit);
			}
		} finally {
			valueDepth--;
		}
	}

	private Object readIdentifiedValue(StateRegistry registry, ByteBuffer buffer, int keyDepth) {
		if (identifierEquals(StateSerializer.CORAL_PROTO_WIRE_NAME)) return readCodecObject(registry, buffer);
		if (identifierEquals(StateSerializer.STRING_WIRE_NAME)) return readString(buffer);
		if (identifierEquals(StateSerializer.CHAR_SEQUENCE_WIRE_NAME)) {
			requireTopLevelTransferValue();
			return readCharSequence(buffer);
		}
		if (identifierEquals(StateSerializer.BYTE_BUFFER_WIRE_NAME)) {
			requireTopLevelTransferValue();
			return readByteBuffer(buffer);
		}
		if (identifierEquals(StateSerializer.BOOLEAN_WIRE_NAME)) return readBooleanValue(buffer);
		if (identifierEquals(StateSerializer.BYTE_WIRE_NAME)) return readByteValue(buffer);
		if (identifierEquals(StateSerializer.CHAR_WIRE_NAME)) return readCharValue(buffer);
		if (identifierEquals(StateSerializer.SHORT_WIRE_NAME)) return readShortValue(buffer);
		if (identifierEquals(StateSerializer.INT_WIRE_NAME)) return readIntValue(buffer);
		if (identifierEquals(StateSerializer.LONG_WIRE_NAME)) return readLongValue(buffer);
		if (identifierEquals(StateSerializer.FLOAT_WIRE_NAME)) return readFloatValue(buffer);
		if (identifierEquals(StateSerializer.DOUBLE_WIRE_NAME)) return readDoubleValue(buffer);

		if (identifierEquals(StateSerializer.ARRAY_LINKED_LIST_WIRE_NAME)) return readArrayLinkedList(registry, buffer, keyDepth);
		if (identifierEquals(StateSerializer.ARRAY_LIST_WIRE_NAME)) return readArrayList(registry, buffer, keyDepth);
		if (identifierEquals(StateSerializer.INT_ARRAY_LIST_WIRE_NAME)) return readIntArrayList(buffer);
		if (identifierEquals(StateSerializer.INT_LINKED_LIST_WIRE_NAME)) return readIntLinkedList(buffer);
		if (identifierEquals(StateSerializer.LINKED_LIST_WIRE_NAME)) return readLinkedList(registry, buffer, keyDepth);
		if (identifierEquals(StateSerializer.LONG_ARRAY_LIST_WIRE_NAME)) return readLongArrayList(buffer);
		if (identifierEquals(StateSerializer.LONG_LINKED_LIST_WIRE_NAME)) return readLongLinkedList(buffer);

		if (identifierEquals(StateSerializer.BYTE_BUFFER_MAP_WIRE_NAME)) return readByteBufferMap(registry, buffer, keyDepth);
		if (identifierEquals(StateSerializer.BYTE_MAP_WIRE_NAME)) return readByteMap(registry, buffer, keyDepth);
		if (identifierEquals(StateSerializer.CHAR_MAP_WIRE_NAME)) return readCharMap(registry, buffer, keyDepth);
		if (identifierEquals(StateSerializer.CHAR_SEQUENCE_MAP_WIRE_NAME)) return readCharSequenceMap(registry, buffer, keyDepth);
		if (identifierEquals(StateSerializer.IDENTITY_MAP_WIRE_NAME)) return readIdentityMap(registry, buffer, keyDepth);
		if (identifierEquals(StateSerializer.INT_MAP_WIRE_NAME)) return readIntMap(registry, buffer, keyDepth);
		if (identifierEquals(StateSerializer.LINKED_MAP_WIRE_NAME)) return readLinkedMap(registry, buffer, keyDepth);
		if (identifierEquals(StateSerializer.LONG_MAP_WIRE_NAME)) return readLongMap(registry, buffer, keyDepth);
		if (identifierEquals(StateSerializer.MAP_WIRE_NAME)) return readMap(registry, buffer, keyDepth);

		if (identifierEquals(StateSerializer.IDENTITY_SET_WIRE_NAME)) return readIdentitySet(registry, buffer, keyDepth);
		if (identifierEquals(StateSerializer.INT_SET_WIRE_NAME)) return readIntSet(buffer);
		if (identifierEquals(StateSerializer.LINKED_SET_WIRE_NAME)) return readLinkedSet(registry, buffer, keyDepth);
		if (identifierEquals(StateSerializer.LONG_SET_WIRE_NAME)) return readLongSet(buffer);
		if (identifierEquals(StateSerializer.SET_WIRE_NAME)) return readSet(registry, buffer, keyDepth);

		throw new IllegalArgumentException("Unsupported node identifier: " + identifierBuilder);
	}

	private String readString(ByteBuffer buffer) {
		int length = readNonNegativeInt(buffer, "String length");
		if (length > buffer.remaining() / Character.BYTES) {
			throw new IllegalArgumentException("Invalid String length: " + length);
		}
		stringBuilder.setLength(0);
		stringBuilder.ensureCapacity(length);
		for (int i = 0; i < length; i++) stringBuilder.append(buffer.getChar());
		return stringBuilder.toString();
	}

	private Object readCharSequence(ByteBuffer buffer) {
		int length = readNonNegativeInt(buffer, "CharSequence length");
		if (length > buffer.remaining() / Character.BYTES) {
			throw new IllegalArgumentException("Invalid CharSequence length: " + length);
		}
		return recordTransfer(targetState.transferValues().acquireCharSequence(buffer, length));
	}

	private Object readByteBuffer(ByteBuffer buffer) {
		int length = readNonNegativeInt(buffer, "ByteBuffer length");
		if (length > buffer.remaining()) {
			throw new IllegalArgumentException("Invalid ByteBuffer length: " + length);
		}
		return recordTransfer(targetState.transferValues().acquireByteBuffer(buffer, length));
	}

	private void requireTopLevelTransferValue() {
		if (valueDepth != 1) {
			throw new IllegalArgumentException("CharSequence and ByteBuffer transfer values are only "
					+ "supported at the top level of State");
		}
	}

	private Object recordTransfer(Object value) {
		ObjectPool<?> pool = targetState.transferValues().poolFor(value);
		return recordAcquired(pool, value);
	}

	private Object readBooleanValue(ByteBuffer buffer) {
		return recordPrimitive(targetState.primitiveValues().acquireBoolean(
				readBoolean(buffer, "boolean value")));
	}

	private Object readByteValue(ByteBuffer buffer) {
		return recordPrimitive(targetState.primitiveValues().acquireByte(
				readByte(buffer, "byte value")));
	}

	private Object readCharValue(ByteBuffer buffer) {
		return recordPrimitive(targetState.primitiveValues().acquireChar(
				readChar(buffer, "char value")));
	}

	private Object readShortValue(ByteBuffer buffer) {
		return recordPrimitive(targetState.primitiveValues().acquireShort(
				readShort(buffer, "short value")));
	}

	private Object readIntValue(ByteBuffer buffer) {
		return recordPrimitive(targetState.primitiveValues().acquireInt(
				readInt(buffer, "int value")));
	}

	private Object readLongValue(ByteBuffer buffer) {
		return recordPrimitive(targetState.primitiveValues().acquireLong(
				readLong(buffer, "long value")));
	}

	private Object readFloatValue(ByteBuffer buffer) {
		return recordPrimitive(targetState.primitiveValues().acquireFloat(
				readFloat(buffer, "float value")));
	}

	private Object readDoubleValue(ByteBuffer buffer) {
		return recordPrimitive(targetState.primitiveValues().acquireDouble(
				readDouble(buffer, "double value")));
	}

	private Object recordPrimitive(Object value) {
		ObjectPool<?> pool = targetState.primitiveValues().poolFor(value);
		return recordAcquired(pool, value);
	}

	private Object recordAcquired(ObjectPool<?> pool, Object value) {
		try {
			rollbackJournal.record(pool, value);
			return value;
		} catch (Throwable failure) {
			try {
				RollbackJournal.release(pool, value);
			} catch (Throwable releaseFailure) {
				suppress(failure, releaseFailure);
			}
			throw failure;
		}
	}

	private ArrayLinkedList<Object> readArrayLinkedList(StateRegistry registry, ByteBuffer buffer, int keyDepth) {
		int arraySize = readInt(buffer, "ArrayLinkedList array size");
		int size = readNonNegativeInt(buffer, "ArrayLinkedList size");
		validateEntryCount(size, buffer, Integer.BYTES, "ArrayLinkedList");
		validateCapacity(arraySize, size, buffer, true, "ArrayLinkedList array size");
		ArrayLinkedList<Object> list = new ArrayLinkedList<>(arraySize);
		for (int i = 0; i < size; i++) list.addLast(readValue(registry, buffer, keyDepth));
		return list;
	}

	private ArrayList<Object> readArrayList(StateRegistry registry, ByteBuffer buffer, int keyDepth) {
		int initialCapacity = readInt(buffer, "ArrayList initial capacity");
		float growthFactor = readFloat(buffer, "ArrayList growth factor");
		int size = readNonNegativeInt(buffer, "ArrayList size");
		validateEntryCount(size, buffer, Integer.BYTES, "ArrayList");
		validateListConfiguration(initialCapacity, growthFactor, size, buffer, "ArrayList");
		ArrayList<Object> list = new ArrayList<>(initialCapacity, growthFactor);
		for (int i = 0; i < size; i++) list.add(readValue(registry, buffer, keyDepth));
		return list;
	}

	private IntArrayList readIntArrayList(ByteBuffer buffer) {
		int initialCapacity = readInt(buffer, "IntArrayList initial capacity");
		float growthFactor = readFloat(buffer, "IntArrayList growth factor");
		int size = readNonNegativeInt(buffer, "IntArrayList size");
		validateEntryCount(size, buffer, Integer.BYTES, "IntArrayList");
		validateListConfiguration(initialCapacity, growthFactor, size, buffer, "IntArrayList");
		IntArrayList list = new IntArrayList(initialCapacity, growthFactor);
		for (int i = 0; i < size; i++) list.add(readInt(buffer, "IntArrayList element"));
		return list;
	}

	private IntLinkedList readIntLinkedList(ByteBuffer buffer) {
		int initialCapacity = readInt(buffer, "IntLinkedList initial capacity");
		int size = readNonNegativeInt(buffer, "IntLinkedList size");
		validateEntryCount(size, buffer, Integer.BYTES, "IntLinkedList");
		validateCapacity(initialCapacity, size, buffer, true, "IntLinkedList initial capacity");
		IntLinkedList list = new IntLinkedList(initialCapacity);
		for (int i = 0; i < size; i++) list.add(readInt(buffer, "IntLinkedList element"));
		return list;
	}

	private LinkedList<Object> readLinkedList(StateRegistry registry, ByteBuffer buffer, int keyDepth) {
		int initialCapacity = readInt(buffer, "LinkedList initial capacity");
		int size = readNonNegativeInt(buffer, "LinkedList size");
		validateEntryCount(size, buffer, Integer.BYTES, "LinkedList");
		validateCapacity(initialCapacity, size, buffer, true, "LinkedList initial capacity");
		LinkedList<Object> list = new LinkedList<>(initialCapacity);
		for (int i = 0; i < size; i++) list.add(readValue(registry, buffer, keyDepth));
		return list;
	}

	private LongArrayList readLongArrayList(ByteBuffer buffer) {
		int initialCapacity = readInt(buffer, "LongArrayList initial capacity");
		float growthFactor = readFloat(buffer, "LongArrayList growth factor");
		int size = readNonNegativeInt(buffer, "LongArrayList size");
		validateEntryCount(size, buffer, Long.BYTES, "LongArrayList");
		validateListConfiguration(initialCapacity, growthFactor, size, buffer, "LongArrayList");
		LongArrayList list = new LongArrayList(initialCapacity, growthFactor);
		for (int i = 0; i < size; i++) list.add(readLong(buffer, "LongArrayList element"));
		return list;
	}

	private LongLinkedList readLongLinkedList(ByteBuffer buffer) {
		int initialCapacity = readInt(buffer, "LongLinkedList initial capacity");
		int size = readNonNegativeInt(buffer, "LongLinkedList size");
		validateEntryCount(size, buffer, Long.BYTES, "LongLinkedList");
		validateCapacity(initialCapacity, size, buffer, true, "LongLinkedList initial capacity");
		LongLinkedList list = new LongLinkedList(initialCapacity);
		for (int i = 0; i < size; i++) list.add(readLong(buffer, "LongLinkedList element"));
		return list;
	}

	private ByteBufferMap<Object> readByteBufferMap(StateRegistry registry, ByteBuffer buffer, int keyDepth) {
		int initialCapacity = readInt(buffer, "ByteBufferMap initial capacity");
		short maxKeyLength = readShort(buffer, "ByteBufferMap maximum key length");
		float loadFactor = readFloat(buffer, "ByteBufferMap load factor");
		boolean direct = readBoolean(buffer, "ByteBufferMap direct-buffer flag");
		int size = readNonNegativeInt(buffer, "ByteBufferMap size");
		validateEntryCount(size, buffer, Integer.BYTES * 2, "ByteBufferMap");
		int preloadCount = validateMapConfiguration(
				initialCapacity, loadFactor, size, buffer, "ByteBufferMap");
		validateKeyStorage(Math.max(preloadCount, size), maxKeyLength, buffer, "ByteBufferMap");
		ByteBufferMap<Object> map = new ByteBufferMap<>(initialCapacity, maxKeyLength, loadFactor, direct);
		for (int i = 0; i < size; i++) {
			int keyLength = readNonNegativeInt(buffer, "ByteBufferMap key length");
			if (keyLength > maxKeyLength || keyLength > buffer.remaining()) {
				throw new IllegalArgumentException("Invalid ByteBufferMap key length: " + keyLength);
			}
			int keyStart = buffer.position();
			int keyEnd = keyStart + keyLength;
			buffer.position(keyEnd);
			Object value = readValue(registry, buffer, keyDepth);
			if (putByteBufferKey(map, buffer, keyStart, keyEnd, value) != null) {
				throw new IllegalArgumentException("Duplicate ByteBufferMap key");
			}
		}
		return map;
	}

	private static Object putByteBufferKey(ByteBufferMap<Object> map, ByteBuffer buffer,
			int keyStart, int keyEnd, Object value) {
		int valueEnd = buffer.position();
		int originalLimit = buffer.limit();
		try {
			buffer.position(keyStart);
			buffer.limit(keyEnd);
			return map.put(buffer, value);
		} finally {
			buffer.limit(originalLimit);
			buffer.position(valueEnd);
		}
	}

	private ByteMap<Object> readByteMap(StateRegistry registry, ByteBuffer buffer, int keyDepth) {
		int size = readNonNegativeInt(buffer, "ByteMap size");
		validateEntryCount(size, buffer, Byte.BYTES + Integer.BYTES, "ByteMap");
		if (size > 256) throw new IllegalArgumentException("Invalid ByteMap size: " + size);
		ByteMap<Object> map = new ByteMap<>();
		for (int i = 0; i < size; i++) {
			byte key = readByte(buffer, "ByteMap key");
			if (map.put(key, readValue(registry, buffer, keyDepth)) != null) {
				throw new IllegalArgumentException("Duplicate ByteMap key: " + key);
			}
		}
		return map;
	}

	private CharMap<Object> readCharMap(StateRegistry registry, ByteBuffer buffer, int keyDepth) {
		int size = readNonNegativeInt(buffer, "CharMap size");
		validateEntryCount(size, buffer, Byte.BYTES + Integer.BYTES, "CharMap");
		if (size > 128) throw new IllegalArgumentException("Invalid CharMap size: " + size);
		CharMap<Object> map = new CharMap<>();
		for (int i = 0; i < size; i++) {
			char key = (char) (readByte(buffer, "CharMap key") & 0xff);
			if (map.put(key, readValue(registry, buffer, keyDepth)) != null) {
				throw new IllegalArgumentException("Duplicate CharMap key: " + key);
			}
		}
		return map;
	}

	private CharSequenceMap<Object> readCharSequenceMap(StateRegistry registry, ByteBuffer buffer, int keyDepth) {
		int initialCapacity = readInt(buffer, "CharSequenceMap initial capacity");
		short maxKeyLength = readShort(buffer, "CharSequenceMap maximum key length");
		float loadFactor = readFloat(buffer, "CharSequenceMap load factor");
		int size = readNonNegativeInt(buffer, "CharSequenceMap size");
		validateEntryCount(size, buffer, Integer.BYTES * 2, "CharSequenceMap");
		int preloadCount = validateMapConfiguration(
				initialCapacity, loadFactor, size, buffer, "CharSequenceMap");
		validateKeyStorage(Math.max(preloadCount, size), maxKeyLength, buffer, "CharSequenceMap");
		CharSequenceMap<Object> map = new CharSequenceMap<>(initialCapacity, maxKeyLength, loadFactor);
		for (int i = 0; i < size; i++) {
			StringBuilder keyBuilder = getKeyBuilder(keyDepth, maxKeyLength);
			readChars(buffer, keyBuilder, "CharSequenceMap key length", maxKeyLength);
			Object value = readValue(registry, buffer, keyDepth + 1);
			if (map.put(keyBuilder, value) != null) {
				throw new IllegalArgumentException("Duplicate CharSequenceMap key: " + keyBuilder);
			}
		}
		return map;
	}

	private IdentityMap<Object, Object> readIdentityMap(StateRegistry registry, ByteBuffer buffer, int keyDepth) {
		int initialCapacity = readInt(buffer, "IdentityMap initial capacity");
		float loadFactor = readFloat(buffer, "IdentityMap load factor");
		int size = readNonNegativeInt(buffer, "IdentityMap size");
		validateEntryCount(size, buffer, Integer.BYTES * 2, "IdentityMap");
		validateMapConfiguration(initialCapacity, loadFactor, size, buffer, "IdentityMap");
		IdentityMap<Object, Object> map = new IdentityMap<>(initialCapacity, loadFactor);
		for (int i = 0; i < size; i++) {
			Object key = readValue(registry, buffer, keyDepth);
			if (map.put(key, readValue(registry, buffer, keyDepth)) != null) {
				throw new IllegalArgumentException("Duplicate IdentityMap key");
			}
		}
		return map;
	}

	private IntMap<Object> readIntMap(StateRegistry registry, ByteBuffer buffer, int keyDepth) {
		int initialCapacity = readInt(buffer, "IntMap initial capacity");
		float loadFactor = readFloat(buffer, "IntMap load factor");
		int size = readNonNegativeInt(buffer, "IntMap size");
		validateEntryCount(size, buffer, Integer.BYTES * 2, "IntMap");
		validateMapConfiguration(initialCapacity, loadFactor, size, buffer, "IntMap");
		IntMap<Object> map = new IntMap<>(initialCapacity, loadFactor);
		for (int i = 0; i < size; i++) {
			int key = readInt(buffer, "IntMap key");
			if (map.put(key, readValue(registry, buffer, keyDepth)) != null) {
				throw new IllegalArgumentException("Duplicate IntMap key: " + key);
			}
		}
		return map;
	}

	private LinkedMap<Object, Object> readLinkedMap(StateRegistry registry, ByteBuffer buffer, int keyDepth) {
		int initialCapacity = readInt(buffer, "LinkedMap initial capacity");
		float loadFactor = readFloat(buffer, "LinkedMap load factor");
		int size = readNonNegativeInt(buffer, "LinkedMap size");
		validateEntryCount(size, buffer, Integer.BYTES * 2, "LinkedMap");
		validateMapConfiguration(initialCapacity, loadFactor, size, buffer, "LinkedMap");
		LinkedMap<Object, Object> map = new LinkedMap<>(initialCapacity, loadFactor);
		for (int i = 0; i < size; i++) {
			Object key = readValue(registry, buffer, keyDepth);
			if (map.put(key, readValue(registry, buffer, keyDepth)) != null) {
				throw new IllegalArgumentException("Duplicate LinkedMap key");
			}
		}
		return map;
	}

	private LongMap<Object> readLongMap(StateRegistry registry, ByteBuffer buffer, int keyDepth) {
		int initialCapacity = readInt(buffer, "LongMap initial capacity");
		float loadFactor = readFloat(buffer, "LongMap load factor");
		int size = readNonNegativeInt(buffer, "LongMap size");
		validateEntryCount(size, buffer, Long.BYTES + Integer.BYTES, "LongMap");
		validateMapConfiguration(initialCapacity, loadFactor, size, buffer, "LongMap");
		LongMap<Object> map = new LongMap<>(initialCapacity, loadFactor);
		for (int i = 0; i < size; i++) {
			long key = readLong(buffer, "LongMap key");
			if (map.put(key, readValue(registry, buffer, keyDepth)) != null) {
				throw new IllegalArgumentException("Duplicate LongMap key: " + key);
			}
		}
		return map;
	}

	private Map<Object, Object> readMap(StateRegistry registry, ByteBuffer buffer, int keyDepth) {
		int initialCapacity = readInt(buffer, "Map initial capacity");
		float loadFactor = readFloat(buffer, "Map load factor");
		int size = readNonNegativeInt(buffer, "Map size");
		validateEntryCount(size, buffer, Integer.BYTES * 2, "Map");
		validateMapConfiguration(initialCapacity, loadFactor, size, buffer, "Map");
		Map<Object, Object> map = new Map<>(initialCapacity, loadFactor);
		for (int i = 0; i < size; i++) {
			Object key = readValue(registry, buffer, keyDepth);
			if (map.put(key, readValue(registry, buffer, keyDepth)) != null) {
				throw new IllegalArgumentException("Duplicate Map key");
			}
		}
		return map;
	}

	private IdentitySet<Object> readIdentitySet(StateRegistry registry, ByteBuffer buffer, int keyDepth) {
		int initialCapacity = readInt(buffer, "IdentitySet initial capacity");
		float loadFactor = readFloat(buffer, "IdentitySet load factor");
		int size = readNonNegativeInt(buffer, "IdentitySet size");
		validateEntryCount(size, buffer, Integer.BYTES, "IdentitySet");
		validateMapConfiguration(initialCapacity, loadFactor, size, buffer, "IdentitySet");
		IdentitySet<Object> set = new IdentitySet<>(initialCapacity, loadFactor);
		for (int i = 0; i < size; i++) {
			if (!set.add(readValue(registry, buffer, keyDepth))) {
				throw new IllegalArgumentException("Duplicate IdentitySet element");
			}
		}
		return set;
	}

	private IntSet readIntSet(ByteBuffer buffer) {
		int initialCapacity = readInt(buffer, "IntSet initial capacity");
		float loadFactor = readFloat(buffer, "IntSet load factor");
		int size = readNonNegativeInt(buffer, "IntSet size");
		validateEntryCount(size, buffer, Integer.BYTES, "IntSet");
		validateMapConfiguration(initialCapacity, loadFactor, size, buffer, "IntSet");
		IntSet set = new IntSet(initialCapacity, loadFactor);
		for (int i = 0; i < size; i++) {
			int element = readInt(buffer, "IntSet element");
			if (!set.add(element)) throw new IllegalArgumentException("Duplicate IntSet element: " + element);
		}
		return set;
	}

	private LinkedSet<Object> readLinkedSet(StateRegistry registry, ByteBuffer buffer, int keyDepth) {
		int initialCapacity = readInt(buffer, "LinkedSet initial capacity");
		float loadFactor = readFloat(buffer, "LinkedSet load factor");
		int size = readNonNegativeInt(buffer, "LinkedSet size");
		validateEntryCount(size, buffer, Integer.BYTES, "LinkedSet");
		validateMapConfiguration(initialCapacity, loadFactor, size, buffer, "LinkedSet");
		LinkedSet<Object> set = new LinkedSet<>(initialCapacity, loadFactor);
		for (int i = 0; i < size; i++) {
			if (!set.add(readValue(registry, buffer, keyDepth))) {
				throw new IllegalArgumentException("Duplicate LinkedSet element");
			}
		}
		return set;
	}

	private LongSet readLongSet(ByteBuffer buffer) {
		int initialCapacity = readInt(buffer, "LongSet initial capacity");
		float loadFactor = readFloat(buffer, "LongSet load factor");
		int size = readNonNegativeInt(buffer, "LongSet size");
		validateEntryCount(size, buffer, Long.BYTES, "LongSet");
		validateMapConfiguration(initialCapacity, loadFactor, size, buffer, "LongSet");
		LongSet set = new LongSet(initialCapacity, loadFactor);
		for (int i = 0; i < size; i++) {
			long element = readLong(buffer, "LongSet element");
			if (!set.add(element)) throw new IllegalArgumentException("Duplicate LongSet element: " + element);
		}
		return set;
	}

	private Set<Object> readSet(StateRegistry registry, ByteBuffer buffer, int keyDepth) {
		int initialCapacity = readInt(buffer, "Set initial capacity");
		float loadFactor = readFloat(buffer, "Set load factor");
		int size = readNonNegativeInt(buffer, "Set size");
		validateEntryCount(size, buffer, Integer.BYTES, "Set");
		validateMapConfiguration(initialCapacity, loadFactor, size, buffer, "Set");
		Set<Object> set = new Set<>(initialCapacity, loadFactor);
		for (int i = 0; i < size; i++) {
			if (!set.add(readValue(registry, buffer, keyDepth))) {
				throw new IllegalArgumentException("Duplicate Set element");
			}
		}
		return set;
	}

	private static void validateEntryCount(int size, ByteBuffer buffer,
			int minimumEntryLength, String description) {
		if (size > buffer.remaining() / minimumEntryLength) {
			throw new IllegalArgumentException("Invalid " + description + " size: " + size);
		}
	}

	private static void validateCapacity(int capacity, int size, ByteBuffer buffer,
			boolean allowZero, String description) {
		if (capacity < (allowZero ? 0 : 1) || capacity > allocationLimit(size, buffer)) {
			throw new IllegalArgumentException("Invalid " + description + ": " + capacity);
		}
	}

	private static void validateListConfiguration(int initialCapacity, float growthFactor,
			int size, ByteBuffer buffer, String description) {
		validateCapacity(initialCapacity, size, buffer, false,
				description + " initial capacity");
		if (!Float.isFinite(growthFactor) || growthFactor <= 1f) {
			throw new IllegalArgumentException("Invalid " + description
					+ " growth factor: " + growthFactor);
		}

		long growthBase = Math.max(initialCapacity, Math.max(1L, size - 1L));
		double projectedCapacity = growthBase * (double) growthFactor;
		if (projectedCapacity > allocationLimit(size, buffer) * 2L) {
			throw new IllegalArgumentException("Invalid " + description
					+ " growth factor: " + growthFactor);
		}
	}

	private static int validateMapConfiguration(int initialCapacity, float loadFactor,
			int size, ByteBuffer buffer, String description) {
		validateCapacity(initialCapacity, size, buffer, false,
				description + " initial capacity");
		if (!Float.isFinite(loadFactor) || loadFactor <= 0f) {
			throw new IllegalArgumentException("Invalid " + description
					+ " load factor: " + loadFactor);
		}

		long limit = allocationLimit(size, buffer);
		int preloadCount = mapThreshold(initialCapacity, loadFactor);
		if (preloadCount > limit) {
			throw new IllegalArgumentException("Invalid " + description
					+ " load factor: " + loadFactor);
		}

		long capacity = initialCapacity;
		long threshold = preloadCount;
		long count = 0;
		while(count < size) {
			if (count >= threshold) {
				capacity *= 2L;
				if (capacity > limit || capacity > Integer.MAX_VALUE) {
					throw new IllegalArgumentException("Invalid " + description
							+ " capacity for declared size: " + initialCapacity);
				}
				threshold = mapThreshold((int) capacity, loadFactor);
			}
			if (threshold > count) count = Math.min((long) size, threshold);
			else count++;
		}
		return preloadCount;
	}

	private static void validateKeyStorage(int allocatedEntryCount, short maxKeyLength,
			ByteBuffer buffer, String description) {
		if (maxKeyLength < 0) {
			throw new IllegalArgumentException("Invalid " + description
					+ " maximum key length: " + maxKeyLength);
		}
		long storage = (long) allocatedEntryCount * maxKeyLength;
		long limit = Math.max(MIN_KEY_STORAGE_LIMIT,
				(long) buffer.remaining() * KEY_STORAGE_WIRE_AMPLIFICATION);
		if (storage > limit) {
			throw new IllegalArgumentException("Invalid " + description
					+ " preallocated key storage: " + storage);
		}
	}

	private static int mapThreshold(int capacity, float loadFactor) {
		return Math.max(1, Math.round(capacity * loadFactor));
	}

	private static long allocationLimit(int size, ByteBuffer buffer) {
		return Math.max(MIN_CONTAINER_ALLOCATION_LIMIT, (long) size + buffer.remaining());
	}

	private Object readCodecObject(StateRegistry registry, ByteBuffer buffer) {
		if (buffer.remaining() < PROTO_HEADER_LENGTH) throw new IllegalArgumentException("CoralProto node is missing its header");
		char type = (char) (buffer.get() & 0xff);
		char subtype = (char) (buffer.get() & 0xff);
		short version = buffer.getShort();
		StateCodec<?, ?> codec = registry.findByProtoType(type, subtype, version);
		if (codec == null) {
			throw new IllegalArgumentException("No codec is registered for Proto type='" + type
					+ "', subtype='" + subtype + "', version=" + version);
		}
		Object object = decode(codec, registry, buffer);
		// Older CoralProto schemas leave appended fields unread. The State node length provides the
		// boundary needed to ignore those unknown fields without consuming bytes from the next node.
		buffer.position(buffer.limit());
		return object;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Object decode(StateCodec codec, StateRegistry registry, ByteBuffer buffer) {
		Proto proto = codec.getProto();
		proto.read(buffer);
		ObjectPool pool = registry.getPool(codec.javaType());
		Object object = pool.get();
		try {
			codec.decode(proto, object);
			rollbackJournal.record(pool, object);
			return object;
		} catch (Throwable failure) {
			try {
				pool.release(object);
			} catch (Throwable releaseFailure) {
				suppress(failure, releaseFailure);
			}
			throw failure;
		}
	}

	/**
	 * Retains decoded codec objects and their pools until the complete State has been read.
	 * The journal and its CoralDS lists are cached and reused by this deserializer.
	 */
	private static final class RollbackJournal {

		private final ArrayList<ObjectPool<?>> pools = new ArrayList<>(INITIAL_ROLLBACK_CAPACITY);
		private final ArrayList<Object> objects = new ArrayList<>(INITIAL_ROLLBACK_CAPACITY);

		private void begin() {
			if (!pools.isEmpty() || !objects.isEmpty()) {
				throw new IllegalStateException("StateDeserializer rollback journal was not cleared");
			}
		}

		private void record(ObjectPool<?> pool, Object object) {
			pools.add(pool);
			objects.add(object);
		}

		private void commit() {
			pools.clear();
			objects.clear();
		}

		private void rollback() {
			Throwable failure = null;
			try {
				for (int i = objects.size() - 1; i >= 0; i--) {
					try {
						release(pools.get(i), objects.get(i));
					} catch (Throwable releaseFailure) {
						if (failure == null) failure = releaseFailure;
						else suppress(failure, releaseFailure);
					}
				}
			} finally {
				commit();
			}
			if (failure != null) rethrow(failure);
		}

		@SuppressWarnings({ "rawtypes", "unchecked" })
		private static void release(ObjectPool pool, Object object) {
			pool.release(object);
		}

		private static void rethrow(Throwable failure) {
			if (failure instanceof RuntimeException runtimeException) throw runtimeException;
			if (failure instanceof Error error) throw error;
			throw new IllegalStateException("Unexpected checked exception while rolling back State", failure);
		}
	}

	private static void suppress(Throwable failure, Throwable suppressed) {
		if (failure == suppressed) return;
		try {
			failure.addSuppressed(suppressed);
		} catch (Throwable ignored) {
			// Preserve the primary failure even when suppression cannot allocate during recovery.
		}
	}

	private boolean identifierEquals(CharSequence wireName) {
		return CharSequence.compare(identifierBuilder, wireName) == 0;
	}

	private StringBuilder getKeyBuilder(int depth, int capacity) {
		while(keyBuilders.size() <= depth) {
			keyBuilders.add(new StringBuilder(Math.max(capacity, CharSequenceMap.DEFAULT_MAX_KEY_LENGTH)));
		}
		StringBuilder builder = keyBuilders.get(depth);
		builder.ensureCapacity(capacity);
		return builder;
	}

	private void clearBuilders() {
		for (int i = 0; i < keyBuilders.size(); i++) keyBuilders.get(i).setLength(0);
		identifierBuilder.setLength(0);
		stringBuilder.setLength(0);
	}

	private static void readMagic(ByteBuffer buffer) {
		if (buffer.remaining() < StateSerializer.MAGIC.length()) {
			throw new IllegalArgumentException("Snapshot is missing the State magic value");
		}
		for (int i = 0; i < StateSerializer.MAGIC.length(); i++) {
			if (buffer.get() != (byte) StateSerializer.MAGIC.charAt(i)) {
				throw new IllegalArgumentException("Invalid State magic value");
			}
		}
	}

	private static void readChars(ByteBuffer buffer, StringBuilder destination,
			String lengthDescription, int maxLength) {
		int length = readNonNegativeInt(buffer, lengthDescription);
		if (length > maxLength || length > buffer.remaining()) {
			throw new IllegalArgumentException("Invalid " + lengthDescription + ": " + length);
		}
		destination.setLength(0);
		for (int i = 0; i < length; i++) destination.append((char) (buffer.get() & 0xff));
	}

	private static boolean readBoolean(ByteBuffer buffer, String description) {
		byte value = readByte(buffer, description);
		if (value == 0) return false;
		if (value == 1) return true;
		throw new IllegalArgumentException("Invalid " + description + ": " + value);
	}

	private static int readNonNegativeInt(ByteBuffer buffer, String description) {
		int value = readInt(buffer, description);
		if (value < 0) throw new IllegalArgumentException("Invalid " + description + ": " + value);
		return value;
	}

	private static byte readByte(ByteBuffer buffer, String description) {
		if (buffer.remaining() < Byte.BYTES) throw new IllegalArgumentException("Snapshot is missing " + description);
		return buffer.get();
	}

	private static char readChar(ByteBuffer buffer, String description) {
		if (buffer.remaining() < Character.BYTES) throw new IllegalArgumentException("Snapshot is missing " + description);
		return buffer.getChar();
	}

	private static short readShort(ByteBuffer buffer, String description) {
		if (buffer.remaining() < Short.BYTES) throw new IllegalArgumentException("Snapshot is missing " + description);
		return buffer.getShort();
	}

	private static int readInt(ByteBuffer buffer, String description) {
		if (buffer.remaining() < Integer.BYTES) throw new IllegalArgumentException("Snapshot is missing " + description);
		return buffer.getInt();
	}

	private static long readLong(ByteBuffer buffer, String description) {
		if (buffer.remaining() < Long.BYTES) throw new IllegalArgumentException("Snapshot is missing " + description);
		return buffer.getLong();
	}

	private static float readFloat(ByteBuffer buffer, String description) {
		if (buffer.remaining() < Float.BYTES) throw new IllegalArgumentException("Snapshot is missing " + description);
		return buffer.getFloat();
	}

	private static double readDouble(ByteBuffer buffer, String description) {
		if (buffer.remaining() < Double.BYTES) throw new IllegalArgumentException("Snapshot is missing " + description);
		return buffer.getDouble();
	}
}
