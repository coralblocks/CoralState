package com.coralblocks.coralstate;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;

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
import com.coralblocks.coralds.util.IntHolder;
import com.coralblocks.coralds.util.LongHolder;
import com.coralblocks.coralproto.Proto;

/**
 * Serializes a {@link State} to a {@link ByteBuffer}.
 *
 * <p>This serializer is deliberately single-threaded and is not thread-safe. It also uses the
 * mutable Proto instance owned by each registered {@link StateCodec}.</p>
 *
 * <p>Each value node contains its byte length followed by one readable identifier and its
 * type-specific payload. Registered codec objects and every concrete CoralDS collection are
 * supported as explicit wire types.</p>
 */
final class StateSerializer {

	static final String MAGIC = "CSTA";
	static final short FORMAT_VERSION = 1;
	static final String CORAL_PROTO_WIRE_NAME = "CoralProto";

	static final String ARRAY_LINKED_LIST_WIRE_NAME = "ArrayLinkedList";
	static final String ARRAY_LIST_WIRE_NAME = "ArrayList";
	static final String INT_ARRAY_LIST_WIRE_NAME = "IntArrayList";
	static final String INT_LINKED_LIST_WIRE_NAME = "IntLinkedList";
	static final String LINKED_LIST_WIRE_NAME = "LinkedList";
	static final String LONG_ARRAY_LIST_WIRE_NAME = "LongArrayList";
	static final String LONG_LINKED_LIST_WIRE_NAME = "LongLinkedList";

	static final String BYTE_BUFFER_MAP_WIRE_NAME = "ByteBufferMap";
	static final String BYTE_MAP_WIRE_NAME = "ByteMap";
	static final String CHAR_MAP_WIRE_NAME = "CharMap";
	static final String CHAR_SEQUENCE_MAP_WIRE_NAME = "CharSequenceMap";
	static final String IDENTITY_MAP_WIRE_NAME = "IdentityMap";
	static final String INT_MAP_WIRE_NAME = "IntMap";
	static final String LINKED_MAP_WIRE_NAME = "LinkedMap";
	static final String LONG_MAP_WIRE_NAME = "LongMap";
	static final String MAP_WIRE_NAME = "Map";

	static final String IDENTITY_SET_WIRE_NAME = "IdentitySet";
	static final String INT_SET_WIRE_NAME = "IntSet";
	static final String LINKED_SET_WIRE_NAME = "LinkedSet";
	static final String LONG_SET_WIRE_NAME = "LongSet";
	static final String SET_WIRE_NAME = "Set";

	static final int MAX_WIRE_NAME_LENGTH = CHAR_SEQUENCE_MAP_WIRE_NAME.length();

	private final IdentitySet<Object> activeContainers = new IdentitySet<>();

	int getSerializedLength(State state) {
		if (state == null) throw new IllegalArgumentException("State cannot be null");

		activeContainers.clear();
		try {
			int length = MAGIC.length() + Short.BYTES + Integer.BYTES;
			CharSequenceMap<Object> values = state.internalValues();
			Iterator<Object> iter = values.iterator();
			while(iter.hasNext()) {
				Object value = iter.next();
				length = addLength(length, charsLength(values.getCurrIteratorKey()));
				length = addLength(length, getValueLength(value, state.getRegistry()));
			}
			return length;
		} finally {
			activeContainers.clear();
		}
	}

	int write(State state, ByteBuffer buffer) {
		if (state == null) throw new IllegalArgumentException("State cannot be null");
		if (buffer == null) throw new IllegalArgumentException("ByteBuffer cannot be null");

		int startPosition = buffer.position();
		ByteOrder originalOrder = buffer.order();
		activeContainers.clear();
		try {
			buffer.order(ByteOrder.BIG_ENDIAN);
			writeRawChars(MAGIC, buffer);
			buffer.putShort(FORMAT_VERSION);
			buffer.putInt(state.size());
			CharSequenceMap<Object> values = state.internalValues();
			Iterator<Object> iter = values.iterator();
			while(iter.hasNext()) {
				Object value = iter.next();
				writeChars(values.getCurrIteratorKey(), buffer);
				writeValue(value, state.getRegistry(), buffer);
			}
			return buffer.position() - startPosition;
		} catch (RuntimeException e) {
			buffer.position(startPosition);
			throw e;
		} finally {
			activeContainers.clear();
			buffer.order(originalOrder);
		}
	}

	private void writeValue(Object value, StateRegistry registry, ByteBuffer buffer) {
		if (value == null) throw new IllegalArgumentException("State values cannot be null");

		Class<?> type = value.getClass();
		if (type == ArrayLinkedList.class) writeArrayLinkedList((ArrayLinkedList<?>) value, registry, buffer);
		else if (type == ArrayList.class) writeArrayList((ArrayList<?>) value, registry, buffer);
		else if (type == IntArrayList.class) writeIntArrayList((IntArrayList) value, buffer);
		else if (type == IntLinkedList.class) writeIntLinkedList((IntLinkedList) value, buffer);
		else if (type == LinkedList.class) writeLinkedList((LinkedList<?>) value, registry, buffer);
		else if (type == LongArrayList.class) writeLongArrayList((LongArrayList) value, buffer);
		else if (type == LongLinkedList.class) writeLongLinkedList((LongLinkedList) value, buffer);
		else if (type == ByteBufferMap.class) writeByteBufferMap((ByteBufferMap<?>) value, registry, buffer);
		else if (type == ByteMap.class) writeByteMap((ByteMap<?>) value, registry, buffer);
		else if (type == CharMap.class) writeCharMap((CharMap<?>) value, registry, buffer);
		else if (type == CharSequenceMap.class) writeCharSequenceMap((CharSequenceMap<?>) value, registry, buffer);
		else if (type == IdentityMap.class) writeIdentityMap((IdentityMap<?, ?>) value, registry, buffer);
		else if (type == IntMap.class) writeIntMap((IntMap<?>) value, registry, buffer);
		else if (type == LinkedMap.class) writeLinkedMap((LinkedMap<?, ?>) value, registry, buffer);
		else if (type == LongMap.class) writeLongMap((LongMap<?>) value, registry, buffer);
		else if (type == Map.class) writeMap((Map<?, ?>) value, registry, buffer);
		else if (type == IdentitySet.class) writeIdentitySet((IdentitySet<?>) value, registry, buffer);
		else if (type == IntSet.class) writeIntSet((IntSet) value, buffer);
		else if (type == LinkedSet.class) writeLinkedSet((LinkedSet<?>) value, registry, buffer);
		else if (type == LongSet.class) writeLongSet((LongSet) value, buffer);
		else if (type == Set.class) writeSet((Set<?>) value, registry, buffer);
		else writeCodecObject(value, registry, buffer);
	}

	private int getValueLength(Object value, StateRegistry registry) {
		if (value == null) throw new IllegalArgumentException("State values cannot be null");

		Class<?> type = value.getClass();
		if (type == ArrayLinkedList.class) return getArrayLinkedListLength((ArrayLinkedList<?>) value, registry);
		if (type == ArrayList.class) return getArrayListLength((ArrayList<?>) value, registry);
		if (type == IntArrayList.class) return getIntArrayListLength((IntArrayList) value);
		if (type == IntLinkedList.class) return getIntLinkedListLength((IntLinkedList) value);
		if (type == LinkedList.class) return getLinkedListLength((LinkedList<?>) value, registry);
		if (type == LongArrayList.class) return getLongArrayListLength((LongArrayList) value);
		if (type == LongLinkedList.class) return getLongLinkedListLength((LongLinkedList) value);
		if (type == ByteBufferMap.class) return getByteBufferMapLength((ByteBufferMap<?>) value, registry);
		if (type == ByteMap.class) return getByteMapLength((ByteMap<?>) value, registry);
		if (type == CharMap.class) return getCharMapLength((CharMap<?>) value, registry);
		if (type == CharSequenceMap.class) return getCharSequenceMapLength((CharSequenceMap<?>) value, registry);
		if (type == IdentityMap.class) return getIdentityMapLength((IdentityMap<?, ?>) value, registry);
		if (type == IntMap.class) return getIntMapLength((IntMap<?>) value, registry);
		if (type == LinkedMap.class) return getLinkedMapLength((LinkedMap<?, ?>) value, registry);
		if (type == LongMap.class) return getLongMapLength((LongMap<?>) value, registry);
		if (type == Map.class) return getMapLength((Map<?, ?>) value, registry);
		if (type == IdentitySet.class) return getIdentitySetLength((IdentitySet<?>) value, registry);
		if (type == IntSet.class) return getIntSetLength((IntSet) value);
		if (type == LinkedSet.class) return getLinkedSetLength((LinkedSet<?>) value, registry);
		if (type == LongSet.class) return getLongSetLength((LongSet) value);
		if (type == Set.class) return getSetLength((Set<?>) value, registry);
		return getCodecObjectLength(value, registry);
	}

	private void writeArrayLinkedList(ArrayLinkedList<?> list, StateRegistry registry, ByteBuffer buffer) {
		enterContainer(list, "serializing");
		try {
			int node = beginNode(ARRAY_LINKED_LIST_WIRE_NAME, buffer);
			buffer.putInt(list.getArraySize());
			buffer.putInt(list.size());
			writeObjectElements(list, registry, buffer);
			finishNode(node, buffer);
		} finally { exitContainer(list); }
	}

	private int getArrayLinkedListLength(ArrayLinkedList<?> list, StateRegistry registry) {
		enterContainer(list, "measuring");
		try {
			return getObjectIterableLength(list, registry,
					addLength(nodeBaseLength(ARRAY_LINKED_LIST_WIRE_NAME), Integer.BYTES * 2));
		} finally { exitContainer(list); }
	}

	private void writeArrayList(ArrayList<?> list, StateRegistry registry, ByteBuffer buffer) {
		enterContainer(list, "serializing");
		try {
			int node = beginNode(ARRAY_LIST_WIRE_NAME, buffer);
			buffer.putInt(list.getInitialCapacity());
			buffer.putFloat(list.getGrowthFactor());
			buffer.putInt(list.size());
			writeObjectElements(list, registry, buffer);
			finishNode(node, buffer);
		} finally { exitContainer(list); }
	}

	private int getArrayListLength(ArrayList<?> list, StateRegistry registry) {
		enterContainer(list, "measuring");
		try {
			return getObjectIterableLength(list, registry,
					addLength(nodeBaseLength(ARRAY_LIST_WIRE_NAME), Integer.BYTES + Float.BYTES + Integer.BYTES));
		} finally { exitContainer(list); }
	}

	private void writeIntArrayList(IntArrayList list, ByteBuffer buffer) {
		int node = beginNode(INT_ARRAY_LIST_WIRE_NAME, buffer);
		buffer.putInt(list.getInitialCapacity());
		buffer.putFloat(list.getGrowthFactor());
		buffer.putInt(list.size());
		for (int i = 0; i < list.size(); i++) buffer.putInt(list.get(i));
		finishNode(node, buffer);
	}

	private int getIntArrayListLength(IntArrayList list) {
		int length = addLength(nodeBaseLength(INT_ARRAY_LIST_WIRE_NAME), Integer.BYTES + Float.BYTES + Integer.BYTES);
		return addLength(length, multiplyLength(list.size(), Integer.BYTES));
	}

	private void writeIntLinkedList(IntLinkedList list, ByteBuffer buffer) {
		int node = beginNode(INT_LINKED_LIST_WIRE_NAME, buffer);
		buffer.putInt(list.getInitialCapacity());
		buffer.putInt(list.size());
		Iterator<IntHolder> iter = list.iterator();
		while(iter.hasNext()) buffer.putInt(iter.next().get());
		finishNode(node, buffer);
	}

	private int getIntLinkedListLength(IntLinkedList list) {
		int length = addLength(nodeBaseLength(INT_LINKED_LIST_WIRE_NAME), Integer.BYTES * 2);
		return addLength(length, multiplyLength(list.size(), Integer.BYTES));
	}

	private void writeLinkedList(LinkedList<?> list, StateRegistry registry, ByteBuffer buffer) {
		enterContainer(list, "serializing");
		try {
			int node = beginNode(LINKED_LIST_WIRE_NAME, buffer);
			buffer.putInt(list.getInitialCapacity());
			buffer.putInt(list.size());
			writeObjectElements(list, registry, buffer);
			finishNode(node, buffer);
		} finally { exitContainer(list); }
	}

	private int getLinkedListLength(LinkedList<?> list, StateRegistry registry) {
		enterContainer(list, "measuring");
		try {
			return getObjectIterableLength(list, registry,
					addLength(nodeBaseLength(LINKED_LIST_WIRE_NAME), Integer.BYTES * 2));
		} finally { exitContainer(list); }
	}

	private void writeLongArrayList(LongArrayList list, ByteBuffer buffer) {
		int node = beginNode(LONG_ARRAY_LIST_WIRE_NAME, buffer);
		buffer.putInt(list.getInitialCapacity());
		buffer.putFloat(list.getGrowthFactor());
		buffer.putInt(list.size());
		for (int i = 0; i < list.size(); i++) buffer.putLong(list.get(i));
		finishNode(node, buffer);
	}

	private int getLongArrayListLength(LongArrayList list) {
		int length = addLength(nodeBaseLength(LONG_ARRAY_LIST_WIRE_NAME), Integer.BYTES + Float.BYTES + Integer.BYTES);
		return addLength(length, multiplyLength(list.size(), Long.BYTES));
	}

	private void writeLongLinkedList(LongLinkedList list, ByteBuffer buffer) {
		int node = beginNode(LONG_LINKED_LIST_WIRE_NAME, buffer);
		buffer.putInt(list.getInitialCapacity());
		buffer.putInt(list.size());
		Iterator<LongHolder> iter = list.iterator();
		while(iter.hasNext()) buffer.putLong(iter.next().get());
		finishNode(node, buffer);
	}

	private int getLongLinkedListLength(LongLinkedList list) {
		int length = addLength(nodeBaseLength(LONG_LINKED_LIST_WIRE_NAME), Integer.BYTES * 2);
		return addLength(length, multiplyLength(list.size(), Long.BYTES));
	}

	private void writeByteBufferMap(ByteBufferMap<?> map, StateRegistry registry, ByteBuffer buffer) {
		enterContainer(map, "serializing");
		try {
			int node = beginNode(BYTE_BUFFER_MAP_WIRE_NAME, buffer);
			buffer.putInt(map.getInitialCapacity());
			buffer.putShort(map.getMaxKeyLength());
			buffer.putFloat(map.getLoadFactor());
			buffer.put((byte) (map.isDirectBuffer() ? 1 : 0));
			buffer.putInt(map.size());
			Iterator<?> iter = map.iterator();
			while(iter.hasNext()) {
				Object value = iter.next();
				ByteBuffer key = map.getCurrIteratorKey();
				buffer.putInt(key.remaining());
				for (int i = key.position(); i < key.limit(); i++) buffer.put(key.get(i));
				writeValue(value, registry, buffer);
			}
			finishNode(node, buffer);
		} finally { exitContainer(map); }
	}

	private int getByteBufferMapLength(ByteBufferMap<?> map, StateRegistry registry) {
		enterContainer(map, "measuring");
		try {
			int length = addLength(nodeBaseLength(BYTE_BUFFER_MAP_WIRE_NAME),
					Integer.BYTES + Short.BYTES + Float.BYTES + Byte.BYTES + Integer.BYTES);
			Iterator<?> iter = map.iterator();
			while(iter.hasNext()) {
				Object value = iter.next();
				length = addLength(length, Integer.BYTES);
				length = addLength(length, map.getCurrIteratorKey().remaining());
				length = addLength(length, getValueLength(value, registry));
			}
			return length;
		} finally { exitContainer(map); }
	}

	private void writeByteMap(ByteMap<?> map, StateRegistry registry, ByteBuffer buffer) {
		enterContainer(map, "serializing");
		try {
			int node = beginNode(BYTE_MAP_WIRE_NAME, buffer);
			buffer.putInt(map.size());
			Iterator<?> iter = map.iterator();
			while(iter.hasNext()) {
				Object value = iter.next();
				buffer.put(map.getCurrIteratorKey());
				writeValue(value, registry, buffer);
			}
			finishNode(node, buffer);
		} finally { exitContainer(map); }
	}

	private int getByteMapLength(ByteMap<?> map, StateRegistry registry) {
		enterContainer(map, "measuring");
		try {
			int length = addLength(nodeBaseLength(BYTE_MAP_WIRE_NAME), Integer.BYTES);
			Iterator<?> iter = map.iterator();
			while(iter.hasNext()) {
				Object value = iter.next();
				length = addLength(length, Byte.BYTES);
				length = addLength(length, getValueLength(value, registry));
			}
			return length;
		} finally { exitContainer(map); }
	}

	private void writeCharMap(CharMap<?> map, StateRegistry registry, ByteBuffer buffer) {
		enterContainer(map, "serializing");
		try {
			int node = beginNode(CHAR_MAP_WIRE_NAME, buffer);
			buffer.putInt(map.size());
			Iterator<?> iter = map.iterator();
			while(iter.hasNext()) {
				Object value = iter.next();
				buffer.put((byte) map.getCurrIteratorKey());
				writeValue(value, registry, buffer);
			}
			finishNode(node, buffer);
		} finally { exitContainer(map); }
	}

	private int getCharMapLength(CharMap<?> map, StateRegistry registry) {
		enterContainer(map, "measuring");
		try {
			int length = addLength(nodeBaseLength(CHAR_MAP_WIRE_NAME), Integer.BYTES);
			Iterator<?> iter = map.iterator();
			while(iter.hasNext()) {
				Object value = iter.next();
				length = addLength(length, Byte.BYTES);
				length = addLength(length, getValueLength(value, registry));
			}
			return length;
		} finally { exitContainer(map); }
	}

	private void writeCharSequenceMap(CharSequenceMap<?> map, StateRegistry registry, ByteBuffer buffer) {
		enterContainer(map, "serializing");
		try {
			int node = beginNode(CHAR_SEQUENCE_MAP_WIRE_NAME, buffer);
			buffer.putInt(map.getInitialCapacity());
			buffer.putShort(map.getMaxKeyLength());
			buffer.putFloat(map.getLoadFactor());
			buffer.putInt(map.size());
			Iterator<?> iter = map.iterator();
			while(iter.hasNext()) {
				Object value = iter.next();
				writeChars(map.getCurrIteratorKey(), buffer);
				writeValue(value, registry, buffer);
			}
			finishNode(node, buffer);
		} finally { exitContainer(map); }
	}

	private int getCharSequenceMapLength(CharSequenceMap<?> map, StateRegistry registry) {
		enterContainer(map, "measuring");
		try {
			int length = addLength(nodeBaseLength(CHAR_SEQUENCE_MAP_WIRE_NAME),
					Integer.BYTES + Short.BYTES + Float.BYTES + Integer.BYTES);
			Iterator<?> iter = map.iterator();
			while(iter.hasNext()) {
				Object value = iter.next();
				length = addLength(length, charsLength(map.getCurrIteratorKey()));
				length = addLength(length, getValueLength(value, registry));
			}
			return length;
		} finally { exitContainer(map); }
	}

	private void writeIdentityMap(IdentityMap<?, ?> map, StateRegistry registry, ByteBuffer buffer) {
		writeObjectMap(map, map.getInitialCapacity(), map.getLoadFactor(), IDENTITY_MAP_WIRE_NAME, registry, buffer);
	}

	private int getIdentityMapLength(IdentityMap<?, ?> map, StateRegistry registry) {
		return getObjectMapLength(map, IDENTITY_MAP_WIRE_NAME, registry);
	}

	private void writeIntMap(IntMap<?> map, StateRegistry registry, ByteBuffer buffer) {
		enterContainer(map, "serializing");
		try {
			int node = beginNode(INT_MAP_WIRE_NAME, buffer);
			writeMapConfiguration(map.getInitialCapacity(), map.getLoadFactor(), map.size(), buffer);
			Iterator<?> iter = map.iterator();
			while(iter.hasNext()) {
				Object value = iter.next();
				buffer.putInt(map.getCurrIteratorKey());
				writeValue(value, registry, buffer);
			}
			finishNode(node, buffer);
		} finally { exitContainer(map); }
	}

	private int getIntMapLength(IntMap<?> map, StateRegistry registry) {
		enterContainer(map, "measuring");
		try {
			int length = addLength(nodeBaseLength(INT_MAP_WIRE_NAME), mapConfigurationLength());
			Iterator<?> iter = map.iterator();
			while(iter.hasNext()) {
				Object value = iter.next();
				length = addLength(length, Integer.BYTES);
				length = addLength(length, getValueLength(value, registry));
			}
			return length;
		} finally { exitContainer(map); }
	}

	private void writeLinkedMap(LinkedMap<?, ?> map, StateRegistry registry, ByteBuffer buffer) {
		writeObjectMap(map, map.getInitialCapacity(), map.getLoadFactor(), LINKED_MAP_WIRE_NAME, registry, buffer);
	}

	private int getLinkedMapLength(LinkedMap<?, ?> map, StateRegistry registry) {
		return getObjectMapLength(map, LINKED_MAP_WIRE_NAME, registry);
	}

	private void writeLongMap(LongMap<?> map, StateRegistry registry, ByteBuffer buffer) {
		enterContainer(map, "serializing");
		try {
			int node = beginNode(LONG_MAP_WIRE_NAME, buffer);
			writeMapConfiguration(map.getInitialCapacity(), map.getLoadFactor(), map.size(), buffer);
			Iterator<?> iter = map.iterator();
			while(iter.hasNext()) {
				Object value = iter.next();
				buffer.putLong(map.getCurrIteratorKey());
				writeValue(value, registry, buffer);
			}
			finishNode(node, buffer);
		} finally { exitContainer(map); }
	}

	private int getLongMapLength(LongMap<?> map, StateRegistry registry) {
		enterContainer(map, "measuring");
		try {
			int length = addLength(nodeBaseLength(LONG_MAP_WIRE_NAME), mapConfigurationLength());
			Iterator<?> iter = map.iterator();
			while(iter.hasNext()) {
				Object value = iter.next();
				length = addLength(length, Long.BYTES);
				length = addLength(length, getValueLength(value, registry));
			}
			return length;
		} finally { exitContainer(map); }
	}

	private void writeMap(Map<?, ?> map, StateRegistry registry, ByteBuffer buffer) {
		writeObjectMap(map, map.getInitialCapacity(), map.getLoadFactor(), MAP_WIRE_NAME, registry, buffer);
	}

	private int getMapLength(Map<?, ?> map, StateRegistry registry) {
		return getObjectMapLength(map, MAP_WIRE_NAME, registry);
	}

	private void writeIdentitySet(IdentitySet<?> set, StateRegistry registry, ByteBuffer buffer) {
		writeObjectSet(set, set.getInitialCapacity(), set.getLoadFactor(), set.size(), IDENTITY_SET_WIRE_NAME, registry, buffer);
	}

	private int getIdentitySetLength(IdentitySet<?> set, StateRegistry registry) {
		return getObjectSetLength(set, IDENTITY_SET_WIRE_NAME, registry);
	}

	private void writeIntSet(IntSet set, ByteBuffer buffer) {
		int node = beginNode(INT_SET_WIRE_NAME, buffer);
		writeMapConfiguration(set.getInitialCapacity(), set.getLoadFactor(), set.size(), buffer);
		Iterator<IntHolder> iter = set.iterator();
		while(iter.hasNext()) buffer.putInt(iter.next().get());
		finishNode(node, buffer);
	}

	private int getIntSetLength(IntSet set) {
		int length = addLength(nodeBaseLength(INT_SET_WIRE_NAME), mapConfigurationLength());
		return addLength(length, multiplyLength(set.size(), Integer.BYTES));
	}

	private void writeLinkedSet(LinkedSet<?> set, StateRegistry registry, ByteBuffer buffer) {
		writeObjectSet(set, set.getInitialCapacity(), set.getLoadFactor(), set.size(), LINKED_SET_WIRE_NAME, registry, buffer);
	}

	private int getLinkedSetLength(LinkedSet<?> set, StateRegistry registry) {
		return getObjectSetLength(set, LINKED_SET_WIRE_NAME, registry);
	}

	private void writeLongSet(LongSet set, ByteBuffer buffer) {
		int node = beginNode(LONG_SET_WIRE_NAME, buffer);
		writeMapConfiguration(set.getInitialCapacity(), set.getLoadFactor(), set.size(), buffer);
		Iterator<LongHolder> iter = set.iterator();
		while(iter.hasNext()) buffer.putLong(iter.next().get());
		finishNode(node, buffer);
	}

	private int getLongSetLength(LongSet set) {
		int length = addLength(nodeBaseLength(LONG_SET_WIRE_NAME), mapConfigurationLength());
		return addLength(length, multiplyLength(set.size(), Long.BYTES));
	}

	private void writeSet(Set<?> set, StateRegistry registry, ByteBuffer buffer) {
		writeObjectSet(set, set.getInitialCapacity(), set.getLoadFactor(), set.size(), SET_WIRE_NAME, registry, buffer);
	}

	private int getSetLength(Set<?> set, StateRegistry registry) {
		return getObjectSetLength(set, SET_WIRE_NAME, registry);
	}

	private void writeObjectMap(Object mapObject, int initialCapacity, float loadFactor, String wireName,
			StateRegistry registry, ByteBuffer buffer) {
		enterContainer(mapObject, "serializing");
		try {
			int node = beginNode(wireName, buffer);
			Iterator<?> iter;
			if (mapObject instanceof IdentityMap<?, ?>) iter = ((IdentityMap<?, ?>) mapObject).iterator();
			else if (mapObject instanceof LinkedMap<?, ?>) iter = ((LinkedMap<?, ?>) mapObject).iterator();
			else iter = ((Map<?, ?>) mapObject).iterator();
			int size = mapObject instanceof IdentityMap<?, ?> ? ((IdentityMap<?, ?>) mapObject).size()
					: mapObject instanceof LinkedMap<?, ?> ? ((LinkedMap<?, ?>) mapObject).size() : ((Map<?, ?>) mapObject).size();
			writeMapConfiguration(initialCapacity, loadFactor, size, buffer);
			while(iter.hasNext()) {
				Object value = iter.next();
				Object key = mapObject instanceof IdentityMap<?, ?> ? ((IdentityMap<?, ?>) mapObject).getCurrIteratorKey()
						: mapObject instanceof LinkedMap<?, ?> ? ((LinkedMap<?, ?>) mapObject).getCurrIteratorKey()
						: ((Map<?, ?>) mapObject).getCurrIteratorKey();
				writeValue(key, registry, buffer);
				writeValue(value, registry, buffer);
			}
			finishNode(node, buffer);
		} finally { exitContainer(mapObject); }
	}

	private int getObjectMapLength(Object mapObject, String wireName, StateRegistry registry) {
		enterContainer(mapObject, "measuring");
		try {
			int length = addLength(nodeBaseLength(wireName), mapConfigurationLength());
			Iterator<?> iter = mapObject instanceof IdentityMap<?, ?> ? ((IdentityMap<?, ?>) mapObject).iterator()
					: mapObject instanceof LinkedMap<?, ?> ? ((LinkedMap<?, ?>) mapObject).iterator() : ((Map<?, ?>) mapObject).iterator();
			while(iter.hasNext()) {
				Object value = iter.next();
				Object key = mapObject instanceof IdentityMap<?, ?> ? ((IdentityMap<?, ?>) mapObject).getCurrIteratorKey()
						: mapObject instanceof LinkedMap<?, ?> ? ((LinkedMap<?, ?>) mapObject).getCurrIteratorKey()
						: ((Map<?, ?>) mapObject).getCurrIteratorKey();
				length = addLength(length, getValueLength(key, registry));
				length = addLength(length, getValueLength(value, registry));
			}
			return length;
		} finally { exitContainer(mapObject); }
	}

	private void writeObjectSet(Iterable<?> set, int initialCapacity, float loadFactor, int size, String wireName,
			StateRegistry registry, ByteBuffer buffer) {
		enterContainer(set, "serializing");
		try {
			int node = beginNode(wireName, buffer);
			writeMapConfiguration(initialCapacity, loadFactor, size, buffer);
			writeObjectElements(set, registry, buffer);
			finishNode(node, buffer);
		} finally { exitContainer(set); }
	}

	private int getObjectSetLength(Iterable<?> set, String wireName, StateRegistry registry) {
		enterContainer(set, "measuring");
		try {
			return getObjectIterableLength(set, registry, addLength(nodeBaseLength(wireName), mapConfigurationLength()));
		} finally { exitContainer(set); }
	}

	private void writeMapConfiguration(int initialCapacity, float loadFactor, int size, ByteBuffer buffer) {
		buffer.putInt(initialCapacity);
		buffer.putFloat(loadFactor);
		buffer.putInt(size);
	}

	private static int mapConfigurationLength() {
		return Integer.BYTES + Float.BYTES + Integer.BYTES;
	}

	private int writeObjectElements(Iterable<?> values, StateRegistry registry, ByteBuffer buffer) {
		int count = 0;
		Iterator<?> iter = values.iterator();
		while(iter.hasNext()) {
			writeValue(iter.next(), registry, buffer);
			count++;
		}
		return count;
	}

	private int getObjectIterableLength(Iterable<?> values, StateRegistry registry, int length) {
		Iterator<?> iter = values.iterator();
		while(iter.hasNext()) length = addLength(length, getValueLength(iter.next(), registry));
		return length;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private int getCodecObjectLength(Object value, StateRegistry registry) {
		StateCodec codec = registry.findByJavaType(value.getClass());
		if (codec == null) throw new IllegalArgumentException("Unsupported State value type: " + value.getClass().getName());
		Proto proto = codec.getProto();
		codec.encode(value, proto);
		int protoLength = proto.getLength();
		if (protoLength < 0) throw new IllegalStateException("Invalid Proto length for " + value.getClass().getName());
		return addLength(nodeBaseLength(CORAL_PROTO_WIRE_NAME), protoLength);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void writeCodecObject(Object value, StateRegistry registry, ByteBuffer buffer) {
		StateCodec codec = registry.findByJavaType(value.getClass());
		if (codec == null) throw new IllegalArgumentException("Unsupported State value type: " + value.getClass().getName());
		Proto proto = codec.getProto();
		codec.encode(value, proto);
		int node = beginNode(CORAL_PROTO_WIRE_NAME, buffer);
		int protoPosition = buffer.position();
		proto.write(buffer);
		int actualProtoLength = buffer.position() - protoPosition;
		if (actualProtoLength != proto.getLength()) {
			throw new IllegalStateException("Proto length mismatch for " + value.getClass().getName()
					+ ": expected=" + proto.getLength() + " actual=" + actualProtoLength);
		}
		finishNode(node, buffer);
	}

	private void enterContainer(Object container, String operation) {
		if (!activeContainers.add(container)) {
			throw new IllegalArgumentException("Cyclic " + container.getClass().getSimpleName()
					+ " detected while " + operation + " State");
		}
	}

	private void exitContainer(Object container) {
		activeContainers.remove(container);
	}

	private static int beginNode(String wireName, ByteBuffer buffer) {
		int nodeLengthPosition = buffer.position();
		buffer.putInt(0);
		writeChars(wireName, buffer);
		return nodeLengthPosition;
	}

	private static void finishNode(int nodeLengthPosition, ByteBuffer buffer) {
		buffer.putInt(nodeLengthPosition, buffer.position() - nodeLengthPosition - Integer.BYTES);
	}

	private static void writeChars(CharSequence value, ByteBuffer buffer) {
		buffer.putInt(value.length());
		writeRawChars(value, buffer);
	}

	private static void writeRawChars(CharSequence value, ByteBuffer buffer) {
		for (int i = 0; i < value.length(); i++) buffer.put((byte) value.charAt(i));
	}

	private static int nodeBaseLength(CharSequence wireName) {
		return addLength(Integer.BYTES, charsLength(wireName));
	}

	private static int charsLength(CharSequence value) {
		return addLength(Integer.BYTES, value.length());
	}

	private static int multiplyLength(int count, int elementLength) {
		if (count < 0 || count > Integer.MAX_VALUE / elementLength) {
			throw new IllegalArgumentException("Serialized State exceeds the maximum ByteBuffer size");
		}
		return count * elementLength;
	}

	private static int addLength(int left, int right) {
		if (right < 0 || left > Integer.MAX_VALUE - right) {
			throw new IllegalArgumentException("Serialized State exceeds the maximum ByteBuffer size");
		}
		return left + right;
	}
}
