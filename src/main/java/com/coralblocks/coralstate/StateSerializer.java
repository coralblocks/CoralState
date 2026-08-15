package com.coralblocks.coralstate;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;

import com.coralblocks.coralds.list.ArrayList;
import com.coralblocks.coralds.map.CharSequenceMap;
import com.coralblocks.coralds.set.IdentitySet;
import com.coralblocks.coralproto.Proto;

/**
 * Serializes a {@link State} to a {@link ByteBuffer}.
 *
 * <p>This serializer is deliberately single-threaded and is not thread-safe. It also uses the
 * mutable Proto instance owned by each registered {@link StateCodec}.</p>
 *
 * <p>The initial format supports registered codec objects and CoralDS {@link ArrayList}
 * instances. Additional CoralDS data structures can be added as explicit wire types later.</p>
 *
 * <p>Each value node contains its byte length followed by one readable identifier and its
 * type-specific payload. The supported identifiers are currently {@code CoralProto} and
 * {@code ArrayList}.</p>
 */
public final class StateSerializer {

	/** The four one-byte characters that identify the beginning of a serialized State. */
	public static final String MAGIC = "CSTA";

	/** The current State wire-format version. */
	public static final short FORMAT_VERSION = 1;

	static final String CORAL_PROTO_WIRE_NAME = "CoralProto";
	static final String ARRAY_LIST_WIRE_NAME = "ArrayList";

	private final IdentitySet<Object> activeContainers = new IdentitySet<>();

	/**
	 * Writes the complete state at the buffer's current position.
	 *
	 * <p>The wire format is always big-endian. The buffer's original byte order is restored
	 * before this method returns. If serialization fails, its original position is restored.</p>
	 *
	 * @param state the State to serialize
	 * @param buffer the destination buffer
	 * @return the number of bytes written
	 * @throws IllegalArgumentException if an unsupported value or cyclic container is found
	 */
	public int write(State state, ByteBuffer buffer) {
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

		if (value.getClass() == ArrayList.class) {
			writeArrayList((ArrayList<?>) value, registry, buffer);
			return;
		}

		writeCodecObject(value, registry, buffer);
	}

	private void writeArrayList(ArrayList<?> list, StateRegistry registry, ByteBuffer buffer) {
		if (!activeContainers.add(list)) {
			throw new IllegalArgumentException("Cyclic ArrayList detected while serializing State");
		}

		try {
			int nodeLengthPosition = buffer.position();
			buffer.putInt(0);
			int nodePosition = buffer.position();

			writeChars(ARRAY_LIST_WIRE_NAME, buffer);
			buffer.putInt(list.getInitialCapacity());
			buffer.putFloat(list.getGrowthFactor());
			buffer.putInt(list.size());

			for (int i = 0; i < list.size(); i++) {
				writeValue(list.get(i), registry, buffer);
			}

			buffer.putInt(nodeLengthPosition, buffer.position() - nodePosition);
		} finally {
			activeContainers.remove(list);
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void writeCodecObject(Object value, StateRegistry registry, ByteBuffer buffer) {
		StateCodec codec = registry.findByJavaType(value.getClass());
		if (codec == null) {
			throw new IllegalArgumentException("Unsupported State value type: " + value.getClass().getName());
		}

		Proto proto = codec.getProto();
		codec.encode(value, proto);

		int nodeLengthPosition = buffer.position();
		buffer.putInt(0);
		int nodePosition = buffer.position();
		writeChars(CORAL_PROTO_WIRE_NAME, buffer);
		int protoPosition = buffer.position();
		proto.write(buffer);

		int actualProtoLength = buffer.position() - protoPosition;
		if (actualProtoLength != proto.getLength()) {
			throw new IllegalStateException("Proto length mismatch for " + value.getClass().getName()
					+ ": expected=" + proto.getLength() + " actual=" + actualProtoLength);
		}

		buffer.putInt(nodeLengthPosition, buffer.position() - nodePosition);
	}

	private static void writeChars(CharSequence value, ByteBuffer buffer) {
		buffer.putInt(value.length());
		writeRawChars(value, buffer);
	}

	private static void writeRawChars(CharSequence value, ByteBuffer buffer) {
		for (int i = 0; i < value.length(); i++) {
			buffer.put((byte) value.charAt(i));
		}
	}
}
