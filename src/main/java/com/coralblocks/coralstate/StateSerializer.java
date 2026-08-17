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
 * type-specific payload. Strings, pooled scalar-transfer values, primitives, registered codec
 * objects, and every concrete CoralDS collection are supported as explicit wire types.</p>
 */
final class StateSerializer {

	static final String MAGIC = "CSTA";
	static final short FORMAT_VERSION = 1;
	static final String CORAL_PROTO_WIRE_NAME = "CoralProto";
	static final String STRING_WIRE_NAME = "String";
	static final String CHAR_SEQUENCE_WIRE_NAME = "CharSequence";
	static final String BYTE_BUFFER_WIRE_NAME = "ByteBuffer";
	static final String BOOLEAN_WIRE_NAME = "Boolean";
	static final String BYTE_WIRE_NAME = "Byte";
	static final String CHAR_WIRE_NAME = "Char";
	static final String SHORT_WIRE_NAME = "Short";
	static final String INT_WIRE_NAME = "Int";
	static final String LONG_WIRE_NAME = "Long";
	static final String FLOAT_WIRE_NAME = "Float";
	static final String DOUBLE_WIRE_NAME = "Double";

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

	static final int MAX_WIRE_NAME_LENGTH;

	static {
		MAX_WIRE_NAME_LENGTH = maximumWireNameLength(
				CORAL_PROTO_WIRE_NAME, STRING_WIRE_NAME, CHAR_SEQUENCE_WIRE_NAME,
				BYTE_BUFFER_WIRE_NAME, BOOLEAN_WIRE_NAME, BYTE_WIRE_NAME, CHAR_WIRE_NAME,
				SHORT_WIRE_NAME, INT_WIRE_NAME, LONG_WIRE_NAME, FLOAT_WIRE_NAME,
				DOUBLE_WIRE_NAME, ARRAY_LINKED_LIST_WIRE_NAME, ARRAY_LIST_WIRE_NAME,
				INT_ARRAY_LIST_WIRE_NAME, INT_LINKED_LIST_WIRE_NAME, LINKED_LIST_WIRE_NAME,
				LONG_ARRAY_LIST_WIRE_NAME, LONG_LINKED_LIST_WIRE_NAME, BYTE_BUFFER_MAP_WIRE_NAME,
				BYTE_MAP_WIRE_NAME, CHAR_MAP_WIRE_NAME, CHAR_SEQUENCE_MAP_WIRE_NAME,
				IDENTITY_MAP_WIRE_NAME, INT_MAP_WIRE_NAME, LINKED_MAP_WIRE_NAME,
				LONG_MAP_WIRE_NAME, MAP_WIRE_NAME, IDENTITY_SET_WIRE_NAME, INT_SET_WIRE_NAME,
				LINKED_SET_WIRE_NAME, LONG_SET_WIRE_NAME, SET_WIRE_NAME);
	}

	private final IdentitySet<Object> activeContainers = new IdentitySet<>();

	void validateForPut(Object value, StateRegistry registry) {
		activeContainers.clear();
		try {
			validateValue(value, registry);
		} finally {
			activeContainers.clear();
		}
	}

	int getSerializedLength(State state) {
		if (state == null) throw new IllegalArgumentException("State cannot be null");

		activeContainers.clear();
		try {
			int length = MAGIC.length() + Short.BYTES + Integer.BYTES;
			CharSequenceMap<Object> values = state.internalValues();
			Iterator<Object> iter = values.iterator();
			while(iter.hasNext()) {
				Object value = iter.next();
				length = addLength(length, keyLength(values.getCurrIteratorKey(), "State key"));
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
				writeKey(values.getCurrIteratorKey(), "State key", buffer);
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
		if (value == null) throw new IllegalStateException("State values cannot be null");

		int transferType = TransferValuePools.typeOf(value);
		if (transferType != TransferValuePools.NOT_TRANSFER_VALUE) {
			requireTopLevelTransferValue();
			writeTransferValue(value, transferType, buffer);
			return;
		}

		int primitiveType = PrimitiveValuePools.typeOf(value);
		if (primitiveType != PrimitiveValuePools.NOT_PRIMITIVE) {
			writePrimitive(value, primitiveType, buffer);
			return;
		}

		Class<?> type = value.getClass();
		if (type == String.class) writeString((String) value, buffer);
		else if (type == ArrayLinkedList.class) writeArrayLinkedList((ArrayLinkedList<?>) value, registry, buffer);
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
		if (value == null) throw new IllegalStateException("State values cannot be null");

		int transferType = TransferValuePools.typeOf(value);
		if (transferType != TransferValuePools.NOT_TRANSFER_VALUE) {
			requireTopLevelTransferValue();
			return getTransferValueLength(value, transferType);
		}

		int primitiveType = PrimitiveValuePools.typeOf(value);
		if (primitiveType != PrimitiveValuePools.NOT_PRIMITIVE) {
			return getPrimitiveLength(primitiveType);
		}

		Class<?> type = value.getClass();
		if (type == String.class) return getStringLength((String) value);
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

	private void validateValue(Object value, StateRegistry registry) {
		if (value == null) throw new IllegalArgumentException("State values cannot be null");
		if (PrimitiveValuePools.typeOf(value) != PrimitiveValuePools.NOT_PRIMITIVE) return;
		if (TransferValuePools.typeOf(value) != TransferValuePools.NOT_TRANSFER_VALUE) {
			throw new IllegalArgumentException("Pooled scalar-transfer values are only supported at the top level");
		}

		Class<?> type = value.getClass();
		if (type == String.class) return;
		if (isPrimitiveContainer(type)) return;
		if (type == CharSequenceMap.class) {
			validateCharSequenceMap((CharSequenceMap<?>) value, registry);
			return;
		}
		if (isObjectMap(type)) {
			validateObjectMap(value, registry);
			return;
		}
		if (isObjectIterable(type)) {
			validateObjectIterable(value, (Iterable<?>) value, registry);
			return;
		}
		if (registry.findByJavaType(type) == null) {
			throw new IllegalArgumentException("Unsupported State value type: " + type.getName());
		}
	}

	private void validateCharSequenceMap(CharSequenceMap<?> map, StateRegistry registry) {
		enterContainerForValidation(map);
		try {
			Iterator<?> iter = map.iterator();
			while(iter.hasNext()) {
				Object value = iter.next();
				validateKeyForPut(map.getCurrIteratorKey(), "CharSequenceMap key");
				validateValue(value, registry);
			}
		} finally {
			exitContainer(map);
		}
	}

	private void validateObjectMap(Object mapObject, StateRegistry registry) {
		enterContainerForValidation(mapObject);
		try {
			Iterator<?> iter = getObjectMapIterator(mapObject);
			while(iter.hasNext()) {
				Object value = iter.next();
				Object key = getCurrentObjectMapKey(mapObject);
				validateValue(key, registry);
				validateValue(value, registry);
			}
		} finally {
			exitContainer(mapObject);
		}
	}

	private void validateObjectIterable(Object container, Iterable<?> values, StateRegistry registry) {
		enterContainerForValidation(container);
		try {
			Iterator<?> iter = values.iterator();
			while(iter.hasNext()) validateValue(iter.next(), registry);
		} finally {
			exitContainer(container);
		}
	}

	private static boolean isPrimitiveContainer(Class<?> type) {
		return type == IntArrayList.class || type == IntLinkedList.class
				|| type == LongArrayList.class || type == LongLinkedList.class
				|| type == IntSet.class || type == LongSet.class;
	}

	private static boolean isObjectMap(Class<?> type) {
		return type == IdentityMap.class || type == LinkedMap.class || type == Map.class;
	}

	private static boolean isObjectIterable(Class<?> type) {
		return type == ArrayLinkedList.class || type == ArrayList.class || type == LinkedList.class
				|| type == ByteBufferMap.class || type == ByteMap.class || type == CharMap.class
				|| type == IntMap.class || type == LongMap.class
				|| type == IdentitySet.class || type == LinkedSet.class || type == Set.class;
	}

	private void writeString(String value, ByteBuffer buffer) {
		int node = beginNode(STRING_WIRE_NAME, buffer);
		buffer.putInt(value.length());
		for (int i = 0; i < value.length(); i++) buffer.putChar(value.charAt(i));
		finishNode(node, buffer);
	}

	private static int getStringLength(String value) {
		int length = addLength(nodeBaseLength(STRING_WIRE_NAME), Integer.BYTES);
		return addLength(length, multiplyLength(value.length(), Character.BYTES));
	}

	private void writeTransferValue(Object value, int transferType, ByteBuffer buffer) {
		switch(transferType) {
			case TransferValuePools.CHAR_SEQUENCE:
				writeCharSequence(value, buffer);
				break;
			case TransferValuePools.BYTE_BUFFER:
				writeByteBuffer(value, buffer);
				break;
			default:
				throw new IllegalArgumentException("Unsupported scalar-transfer State value type: "
						+ transferType);
		}
	}

	private void writeCharSequence(Object value, ByteBuffer buffer) {
		int length = TransferValuePools.charSequenceLength(value);
		int node = beginNode(CHAR_SEQUENCE_WIRE_NAME, buffer);
		buffer.putInt(length);
		for (int i = 0; i < length; i++) {
			buffer.putChar(TransferValuePools.charSequenceCharAt(value, i));
		}
		finishNode(node, buffer);
	}

	private void writeByteBuffer(Object value, ByteBuffer buffer) {
		int length = TransferValuePools.byteBufferLength(value);
		int node = beginNode(BYTE_BUFFER_WIRE_NAME, buffer);
		buffer.putInt(length);
		TransferValuePools.writeByteBuffer(value, buffer);
		finishNode(node, buffer);
	}

	private static int getTransferValueLength(Object value, int transferType) {
		switch(transferType) {
			case TransferValuePools.CHAR_SEQUENCE:
				int charLength = addLength(nodeBaseLength(CHAR_SEQUENCE_WIRE_NAME), Integer.BYTES);
				return addLength(charLength, multiplyLength(
						TransferValuePools.charSequenceLength(value), Character.BYTES));
			case TransferValuePools.BYTE_BUFFER:
				int byteLength = addLength(nodeBaseLength(BYTE_BUFFER_WIRE_NAME), Integer.BYTES);
				return addLength(byteLength, TransferValuePools.byteBufferLength(value));
			default:
				throw new IllegalArgumentException("Unsupported scalar-transfer State value type: "
						+ transferType);
		}
	}

	private void requireTopLevelTransferValue() {
		if (!activeContainers.isEmpty()) {
			throw new IllegalStateException("CharSequence and ByteBuffer transfer values are only supported "
					+ "at the top level of State");
		}
	}

	private void writePrimitive(Object value, int primitiveType, ByteBuffer buffer) {
		int node = beginNode(primitiveWireName(primitiveType), buffer);
		switch(primitiveType) {
			case PrimitiveValuePools.BOOLEAN:
				buffer.put(PrimitiveValuePools.booleanValue(value) ? (byte) 1 : (byte) 0);
				break;
			case PrimitiveValuePools.BYTE:
				buffer.put(PrimitiveValuePools.byteValue(value));
				break;
			case PrimitiveValuePools.CHAR:
				buffer.putChar(PrimitiveValuePools.charValue(value));
				break;
			case PrimitiveValuePools.SHORT:
				buffer.putShort(PrimitiveValuePools.shortValue(value));
				break;
			case PrimitiveValuePools.INT:
				buffer.putInt(PrimitiveValuePools.intValue(value));
				break;
			case PrimitiveValuePools.LONG:
				buffer.putLong(PrimitiveValuePools.longValue(value));
				break;
			case PrimitiveValuePools.FLOAT:
				buffer.putFloat(PrimitiveValuePools.floatValue(value));
				break;
			case PrimitiveValuePools.DOUBLE:
				buffer.putDouble(PrimitiveValuePools.doubleValue(value));
				break;
			default:
				throw new IllegalArgumentException("Unsupported primitive State value type: " + primitiveType);
		}
		finishNode(node, buffer);
	}

	private static int getPrimitiveLength(int primitiveType) {
		return addLength(nodeBaseLength(primitiveWireName(primitiveType)), primitiveSize(primitiveType));
	}

	private static String primitiveWireName(int primitiveType) {
		switch(primitiveType) {
			case PrimitiveValuePools.BOOLEAN: return BOOLEAN_WIRE_NAME;
			case PrimitiveValuePools.BYTE: return BYTE_WIRE_NAME;
			case PrimitiveValuePools.CHAR: return CHAR_WIRE_NAME;
			case PrimitiveValuePools.SHORT: return SHORT_WIRE_NAME;
			case PrimitiveValuePools.INT: return INT_WIRE_NAME;
			case PrimitiveValuePools.LONG: return LONG_WIRE_NAME;
			case PrimitiveValuePools.FLOAT: return FLOAT_WIRE_NAME;
			case PrimitiveValuePools.DOUBLE: return DOUBLE_WIRE_NAME;
			default: throw new IllegalArgumentException("Unsupported primitive State value type: " + primitiveType);
		}
	}

	private static int primitiveSize(int primitiveType) {
		switch(primitiveType) {
			case PrimitiveValuePools.BOOLEAN: return Byte.BYTES;
			case PrimitiveValuePools.BYTE: return Byte.BYTES;
			case PrimitiveValuePools.CHAR: return Character.BYTES;
			case PrimitiveValuePools.SHORT: return Short.BYTES;
			case PrimitiveValuePools.INT: return Integer.BYTES;
			case PrimitiveValuePools.LONG: return Long.BYTES;
			case PrimitiveValuePools.FLOAT: return Float.BYTES;
			case PrimitiveValuePools.DOUBLE: return Double.BYTES;
			default: throw new IllegalArgumentException("Unsupported primitive State value type: " + primitiveType);
		}
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
				writeKey(map.getCurrIteratorKey(), "CharSequenceMap key", buffer);
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
				length = addLength(length, keyLength(map.getCurrIteratorKey(), "CharSequenceMap key"));
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
			Iterator<?> iter = getObjectMapIterator(mapObject);
			writeMapConfiguration(initialCapacity, loadFactor, getObjectMapSize(mapObject), buffer);
			while(iter.hasNext()) {
				Object value = iter.next();
				Object key = getCurrentObjectMapKey(mapObject);
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
			Iterator<?> iter = getObjectMapIterator(mapObject);
			while(iter.hasNext()) {
				Object value = iter.next();
				Object key = getCurrentObjectMapKey(mapObject);
				length = addLength(length, getValueLength(key, registry));
				length = addLength(length, getValueLength(value, registry));
			}
			return length;
		} finally { exitContainer(mapObject); }
	}

	private static Iterator<?> getObjectMapIterator(Object mapObject) {
		if (mapObject instanceof IdentityMap<?, ?> map) return map.iterator();
		if (mapObject instanceof LinkedMap<?, ?> map) return map.iterator();
		if (mapObject instanceof Map<?, ?> map) return map.iterator();
		throw new IllegalArgumentException("Unsupported object map type: " + mapObject.getClass().getName());
	}

	private static int getObjectMapSize(Object mapObject) {
		if (mapObject instanceof IdentityMap<?, ?> map) return map.size();
		if (mapObject instanceof LinkedMap<?, ?> map) return map.size();
		if (mapObject instanceof Map<?, ?> map) return map.size();
		throw new IllegalArgumentException("Unsupported object map type: " + mapObject.getClass().getName());
	}

	private static Object getCurrentObjectMapKey(Object mapObject) {
		if (mapObject instanceof IdentityMap<?, ?> map) return map.getCurrIteratorKey();
		if (mapObject instanceof LinkedMap<?, ?> map) return map.getCurrIteratorKey();
		if (mapObject instanceof Map<?, ?> map) return map.getCurrIteratorKey();
		throw new IllegalArgumentException("Unsupported object map type: " + mapObject.getClass().getName());
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

	private void writeObjectElements(Iterable<?> values, StateRegistry registry, ByteBuffer buffer) {
		Iterator<?> iter = values.iterator();
		while(iter.hasNext()) writeValue(iter.next(), registry, buffer);
	}

	private int getObjectIterableLength(Iterable<?> values, StateRegistry registry, int length) {
		Iterator<?> iter = values.iterator();
		while(iter.hasNext()) length = addLength(length, getValueLength(iter.next(), registry));
		return length;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private int getCodecObjectLength(Object value, StateRegistry registry) {
		StateCodec codec = registry.findByJavaType(value.getClass());
		if (codec == null) throw new IllegalStateException("Unsupported State value type: " + value.getClass().getName());
		Proto proto = codec.getProto();
		codec.encode(value, proto);
		int protoLength = proto.getLength();
		if (protoLength < 0) throw new IllegalStateException("Invalid Proto length for " + value.getClass().getName());
		return addLength(nodeBaseLength(CORAL_PROTO_WIRE_NAME), protoLength);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void writeCodecObject(Object value, StateRegistry registry, ByteBuffer buffer) {
		StateCodec codec = registry.findByJavaType(value.getClass());
		if (codec == null) throw new IllegalStateException("Unsupported State value type: " + value.getClass().getName());
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
			throw new IllegalStateException("Cyclic " + container.getClass().getSimpleName()
					+ " detected while " + operation + " State");
		}
	}

	private void enterContainerForValidation(Object container) {
		if (!activeContainers.add(container)) {
			throw new IllegalArgumentException("Cyclic " + container.getClass().getSimpleName()
					+ " detected while validating State");
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

	private static void writeKey(CharSequence key, String description, ByteBuffer buffer) {
		buffer.putInt(key.length());
		writeRawChars(key, description, buffer);
	}

	private static void writeRawChars(CharSequence value, ByteBuffer buffer) {
		for (int i = 0; i < value.length(); i++) buffer.put((byte) value.charAt(i));
	}

	private static void writeRawChars(CharSequence value, String description, ByteBuffer buffer) {
		for (int i = 0; i < value.length(); i++) {
			char character = value.charAt(i);
			if (character > 0xff) {
				throw new IllegalStateException(description
						+ " contains a character outside Latin-1 at index " + i);
			}
			buffer.put((byte) character);
		}
	}

	private static int maximumWireNameLength(String... wireNames) {
		int maximum = 0;
		for (String wireName : wireNames) maximum = Math.max(maximum, wireName.length());
		return maximum;
	}

	private static int nodeBaseLength(CharSequence wireName) {
		return addLength(Integer.BYTES, charsLength(wireName));
	}

	private static int charsLength(CharSequence value) {
		return addLength(Integer.BYTES, value.length());
	}

	private static int keyLength(CharSequence key, String description) {
		requireSerializableKey(key, description);
		return charsLength(key);
	}

	static void validateKeyForPut(CharSequence key, String description) {
		for (int i = 0; i < key.length(); i++) {
			if (key.charAt(i) > 0xff) {
				throw new IllegalArgumentException(description
						+ " contains a character outside Latin-1 at index " + i);
			}
		}
	}

	private static void requireSerializableKey(CharSequence key, String description) {
		for (int i = 0; i < key.length(); i++) {
			if (key.charAt(i) > 0xff) {
				throw new IllegalStateException(description
						+ " contains a character outside Latin-1 at index " + i);
			}
		}
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
