package com.coralblocks.coralstate;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;

import com.coralblocks.coralds.list.ArrayList;
import com.coralblocks.coralds.map.CharSequenceMap;
import com.coralblocks.coralpool.ObjectPool;
import com.coralblocks.coralproto.Proto;

/**
 * Deserializes a {@link State} written by {@link StateSerializer}.
 *
 * <p>This deserializer is deliberately single-threaded and is not thread-safe. The initial
 * implementation supports the {@code CoralProto} and {@code ArrayList} wire identifiers.</p>
 */
final class StateDeserializer {

	private static final int PROTO_HEADER_LENGTH = 4;

	private final StringBuilder keyBuilder = new StringBuilder();
	private final StringBuilder identifierBuilder = new StringBuilder();

	/**
	 * Reads a serialized State into an empty destination State.
	 *
	 * <p>The wire format is always read as big-endian. The buffer's original byte order is
	 * restored before this method returns. If deserialization fails, its original position is
	 * restored.</p>
	 *
	 * @param state the empty destination State
	 * @param buffer the source buffer
	 * @return the number of bytes consumed
	 */
	int read(State state, ByteBuffer buffer) {
		if (state == null) throw new IllegalArgumentException("State cannot be null");
		if (buffer == null) throw new IllegalArgumentException("ByteBuffer cannot be null");
		if (!state.isEmpty()) throw new IllegalArgumentException("Destination State must be empty");

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
			State decodedState = new State(state.getRegistry());
			for (int i = 0; i < entryCount; i++) {
				readChars(buffer, keyBuilder, "State key");
				decodedState.put(keyBuilder, readValue(state.getRegistry(), buffer));
			}

			copyValues(decodedState, state);
			return buffer.position() - startPosition;
		} catch (RuntimeException e) {
			buffer.position(startPosition);
			throw e;
		} finally {
			keyBuilder.setLength(0);
			identifierBuilder.setLength(0);
			buffer.order(originalOrder);
		}
	}

	private Object readValue(StateRegistry registry, ByteBuffer buffer) {
		int nodeLength = readNonNegativeInt(buffer, "node length");
		if (nodeLength > buffer.remaining()) {
			throw new IllegalArgumentException("Invalid node length: " + nodeLength);
		}

		int nodeEnd = buffer.position() + nodeLength;
		int originalLimit = buffer.limit();
		buffer.limit(nodeEnd);

		try {
			readChars(buffer, identifierBuilder, "node identifier");

			Object value;
			if (CharSequence.compare(identifierBuilder, StateSerializer.CORAL_PROTO_WIRE_NAME) == 0) {
				value = readCodecObject(registry, buffer);
			} else if (CharSequence.compare(identifierBuilder, StateSerializer.ARRAY_LIST_WIRE_NAME) == 0) {
				value = readArrayList(registry, buffer);
			} else {
				throw new IllegalArgumentException("Unsupported node identifier: " + identifierBuilder);
			}

			if (buffer.position() != nodeEnd) {
				throw new IllegalArgumentException("Node was not fully consumed: remaining=" + buffer.remaining());
			}
			return value;
		} finally {
			buffer.limit(originalLimit);
		}
	}

	private ArrayList<Object> readArrayList(StateRegistry registry, ByteBuffer buffer) {
		int initialCapacity = readInt(buffer, "ArrayList initial capacity");
		float growthFactor = readFloat(buffer, "ArrayList growth factor");
		int size = readNonNegativeInt(buffer, "ArrayList size");

		ArrayList<Object> list = new ArrayList<>(initialCapacity, growthFactor);
		for (int i = 0; i < size; i++) {
			list.add(readValue(registry, buffer));
		}
		return list;
	}

	private Object readCodecObject(StateRegistry registry, ByteBuffer buffer) {
		if (buffer.remaining() < PROTO_HEADER_LENGTH) {
			throw new IllegalArgumentException("CoralProto node is missing its header");
		}

		char type = (char) (buffer.get() & 0xff);
		char subtype = (char) (buffer.get() & 0xff);
		short version = buffer.getShort();

		StateCodec<?, ?> codec = registry.findByProtoType(type, subtype, version);
		if (codec == null) {
			throw new IllegalArgumentException("No codec is registered for Proto type='" + type
					+ "', subtype='" + subtype + "', version=" + version);
		}

		return decode(codec, registry, buffer);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Object decode(StateCodec codec, StateRegistry registry, ByteBuffer buffer) {
		Proto proto = codec.getProto();
		proto.read(buffer);

		ObjectPool pool = registry.findPoolByJavaType(codec.javaType());
		Object object = pool.get();
		try {
			codec.decode(proto, object);
			return object;
		} catch (RuntimeException e) {
			pool.release(object);
			throw e;
		}
	}

	private static void copyValues(State source, State destination) {
		CharSequenceMap<Object> values = source.internalValues();
		Iterator<Object> iter = values.iterator();
		while(iter.hasNext()) {
			Object value = iter.next();
			destination.put(values.getCurrIteratorKey(), value);
		}
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

	private static void readChars(ByteBuffer buffer, StringBuilder destination, String description) {
		int length = readNonNegativeInt(buffer, description + " length");
		if (length > buffer.remaining()) {
			throw new IllegalArgumentException("Invalid " + description + " length: " + length);
		}

		destination.setLength(0);
		for (int i = 0; i < length; i++) {
			destination.append((char) (buffer.get() & 0xff));
		}
	}

	private static int readNonNegativeInt(ByteBuffer buffer, String description) {
		int value = readInt(buffer, description);
		if (value < 0) throw new IllegalArgumentException("Invalid " + description + ": " + value);
		return value;
	}

	private static int readInt(ByteBuffer buffer, String description) {
		if (buffer.remaining() < Integer.BYTES) {
			throw new IllegalArgumentException("Snapshot is missing " + description);
		}
		return buffer.getInt();
	}

	private static short readShort(ByteBuffer buffer, String description) {
		if (buffer.remaining() < Short.BYTES) {
			throw new IllegalArgumentException("Snapshot is missing " + description);
		}
		return buffer.getShort();
	}

	private static float readFloat(ByteBuffer buffer, String description) {
		if (buffer.remaining() < Float.BYTES) {
			throw new IllegalArgumentException("Snapshot is missing " + description);
		}
		return buffer.getFloat();
	}
}
