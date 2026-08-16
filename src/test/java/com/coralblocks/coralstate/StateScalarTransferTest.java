package com.coralblocks.coralstate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;

import org.junit.Test;

import com.coralblocks.coralds.list.ArrayList;

public class StateScalarTransferTest {

	@Test
	public void returnsTheCopiedCharSequenceAsAReadOnlyBorrowedHolder() {
		StringBuilder source = new StringBuilder("ação \uD800 coral");
		State state = new State(new StateRegistry());

		state.put("text", source);
		source.setLength(0);
		source.append("changed");

		assertThrows(IllegalArgumentException.class, () -> state.get("text"));
		assertThrows(IllegalArgumentException.class, () -> state.remove("text"));

		CharSequence borrowed = state.getCharSequence("text");
		assertEquals(12, borrowed.length());
		assertEquals("ação \uD800 coral", borrowed.toString());
		assertFalse(borrowed instanceof StringBuilder);
		assertSame(borrowed, state.getCharSequence("text"));

		CharSequence removed = state.removeCharSequence("text");
		assertSame(borrowed, removed);
		assertEquals("ação \uD800 coral", removed.toString());
		assertTrue(state.isEmpty());
	}

	@Test
	public void returnsTheCopiedBytesAsAReadOnlyBorrowedView() {
		byte[] sourceBytes = { 99, 1, 2, 3, 88 };
		ByteBuffer source = ByteBuffer.wrap(sourceBytes);
		source.position(1);
		source.limit(4);
		State state = new State(new StateRegistry());

		state.put("bytes", source);
		assertEquals(1, source.position());
		assertEquals(4, source.limit());
		sourceBytes[1] = 42;

		assertThrows(IllegalArgumentException.class, () -> state.get("bytes"));
		assertThrows(IllegalArgumentException.class, () -> state.remove("bytes"));

		ByteBuffer borrowed = state.getByteBuffer("bytes");
		assertTrue(borrowed.isReadOnly());
		assertEquals(0, borrowed.position());
		assertEquals(3, borrowed.remaining());
		assertEquals(1, borrowed.get());
		assertEquals(2, borrowed.get());
		assertThrows(ReadOnlyBufferException.class, () -> borrowed.put(0, (byte) 9));

		ByteBuffer borrowedAgain = state.getByteBuffer("bytes");
		assertSame(borrowed, borrowedAgain);
		assertEquals(0, borrowed.position());
		assertEquals(3, borrowed.remaining());
		assertEquals(1, borrowed.get(0));
		assertEquals(2, borrowed.get(1));
		assertEquals(3, borrowed.get(2));

		ByteBuffer removed = state.removeByteBuffer("bytes");
		assertSame(borrowed, removed);
		assertEquals(0, removed.position());
		assertEquals(3, removed.remaining());
		assertEquals(1, removed.get());
		assertEquals(2, removed.get());
		assertEquals(3, removed.get());
		assertTrue(state.isEmpty());
	}

	@Test
	public void typedRemoversRejectTheWrongValueTypeWithoutRemovingIt() {
		State state = new State(new StateRegistry());
		state.put("bytes", ByteBuffer.wrap(new byte[] { 1, 2, 3 }));
		state.put("text", new StringBuilder("text"));

		assertThrows(IllegalArgumentException.class, () -> state.removeCharSequence("bytes"));
		assertThrows(IllegalArgumentException.class, () -> state.removeByteBuffer("text"));
		assertThrows(IllegalArgumentException.class, () -> state.removeByteBuffer("missing"));

		assertTrue(state.check("bytes"));
		assertTrue(state.check("text"));
	}

	@Test
	public void stringsKeepTheirExistingObjectSemanticsInTheBorrowedApi() {
		State state = new State(new StateRegistry());
		String value = new String("CoralState");
		state.put("text", value);

		assertSame(value, state.get("text"));
		assertSame(value, state.getCharSequence("text"));
		assertSame(value, state.removeCharSequence("text"));
		assertTrue(state.isEmpty());
	}

	@Test
	public void differentEntriesOwnDifferentBorrowedHoldersAndViews() {
		State state = new State(new StateRegistry());
		state.put("text1", new StringBuilder("one"));
		state.put("text2", new StringBuilder("two"));
		state.put("bytes1", ByteBuffer.wrap(new byte[] { 1 }));
		state.put("bytes2", ByteBuffer.wrap(new byte[] { 2 }));

		assertNotSame(state.getCharSequence("text1"), state.getCharSequence("text2"));
		assertNotSame(state.getByteBuffer("bytes1"), state.getByteBuffer("bytes2"));
		assertEquals("one", state.getCharSequence("text1").toString());
		assertEquals("two", state.getCharSequence("text2").toString());
		assertEquals(1, state.getByteBuffer("bytes1").get(0));
		assertEquals(2, state.getByteBuffer("bytes2").get(0));
	}

	@Test
	public void roundTripsCharSequenceAndByteBufferTransferValues() {
		String everyJavaChar = everyJavaChar();
		ByteBuffer directBytes = ByteBuffer.allocateDirect(6);
		directBytes.put(new byte[] { 9, 8, 7, 6, 5, 4 });
		directBytes.flip();
		directBytes.position(1);
		directBytes.limit(5);

		State original = new State(new StateRegistry());
		original.put("text", new StringBuilder(everyJavaChar));
		original.put("bytes", directBytes);
		original.put("emptyText", new StringBuilder());
		original.put("emptyBytes", ByteBuffer.allocate(0));
		State restored = roundTrip(original);

		assertEquals(original, restored);
		assertEquals(original.hashCode(), restored.hashCode());
		assertEquals(0, restored.getCharSequence("emptyText").length());
		assertEquals(0, restored.getByteBuffer("emptyBytes").remaining());
		assertEquals(everyJavaChar, restored.getCharSequence("text").toString());
		ByteBuffer bytes = restored.getByteBuffer("bytes");
		assertEquals(8, bytes.get());
		assertEquals(7, bytes.get());
		assertEquals(6, bytes.get());
		assertEquals(5, bytes.get());
		assertFalse(bytes.hasRemaining());
	}

	@Test
	public void rejectsNonStringCharSequencesAndByteBuffersInsideContainers() {
		ArrayList<Object> values = new ArrayList<>();
		State state = new State(new StateRegistry());

		values.add(new StringBuilder("text"));
		assertThrows(IllegalArgumentException.class, () -> state.put("values", values));
		values.clear();
		values.add(ByteBuffer.allocate(1));
		assertThrows(IllegalArgumentException.class, () -> state.put("values", values));
		assertTrue(state.isEmpty());
	}

	@Test
	public void detectsAContainerMadeInvalidByADeferredTransferValue() {
		ArrayList<Object> values = new ArrayList<>();
		State state = new State(new StateRegistry());
		state.put("values", values);

		values.add(new StringBuilder("text"));

		assertThrows(IllegalStateException.class, state::getSerializedLength);
		assertThrows(IllegalStateException.class, () -> state.writeTo(ByteBuffer.allocate(256)));
	}

	@Test
	public void reusesRemovedAndCrossTypeReplacedTransferHolders() {
		State state = new State(new StateRegistry());
		state.put("text", new StringBuilder("first"));
		Object charHolder = state.internalValues().get("text");
		CharSequence removedText = state.removeCharSequence("text");
		assertSame(charHolder, removedText);
		assertEquals("first", removedText.toString());
		state.put("otherText", new StringBuilder("second"));
		assertSame(charHolder, state.internalValues().get("otherText"));
		assertSame(removedText, state.getCharSequence("otherText"));
		assertEquals("second", removedText.toString());

		state.put("bytes", ByteBuffer.wrap(new byte[] { 1 }));
		Object byteHolder = state.internalValues().get("bytes");
		ByteBuffer removedBytes = state.removeByteBuffer("bytes");
		assertEquals(1, removedBytes.get(0));
		state.put("bytes", ByteBuffer.wrap(new byte[] { 2 }));
		assertSame(byteHolder, state.internalValues().get("bytes"));
		assertSame(removedBytes, state.getByteBuffer("bytes"));
		assertEquals(2, removedBytes.get(0));

		state.put("bytes", 7L);
		state.put("otherBytes", ByteBuffer.wrap(new byte[] { 2 }));
		assertSame(byteHolder, state.internalValues().get("otherBytes"));
	}

	@Test
	public void releasesAHolderWhenCopyingTheSourceFails() {
		State state = new State(new StateRegistry());
		state.put("seed", new StringBuilder("seed"));
		Object expectedHolder = state.internalValues().get("seed");
		state.removeCharSequence("seed");

		assertThrows(IllegalStateException.class, () -> state.put("failed", new FailingCharSequence()));
		assertTrue(state.isEmpty());

		state.put("after", new StringBuilder("after"));
		assertSame(expectedHolder, state.internalValues().get("after"));
	}

	@Test
	public void returnsDeserializedTransferHoldersToTheirPoolsAfterFailure() {
		State source = new State(new StateRegistry());
		source.put("text", new StringBuilder("CoralState"));
		source.put("bytes", ByteBuffer.wrap(new byte[] { 1, 2, 3 }));
		ByteBuffer buffer = serialize(source);
		buffer.putInt(StateSerializer.MAGIC.length() + Short.BYTES, 3);

		State restored = new State(new StateRegistry());
		restored.put("seedText", new StringBuilder("seed"));
		Object expectedCharHolder = restored.internalValues().get("seedText");
		restored.removeCharSequence("seedText");
		restored.put("seedBytes", ByteBuffer.wrap(new byte[] { 0 }));
		Object expectedByteHolder = restored.internalValues().get("seedBytes");
		restored.removeByteBuffer("seedBytes");

		assertThrows(IllegalArgumentException.class, () -> restored.readFrom(buffer));
		assertTrue(restored.isEmpty());
		assertEquals(0, buffer.position());

		restored.put("afterText", new StringBuilder("after"));
		assertSame(expectedCharHolder, restored.internalValues().get("afterText"));
		restored.put("afterBytes", ByteBuffer.wrap(new byte[] { 4 }));
		assertSame(expectedByteHolder, restored.internalValues().get("afterBytes"));
	}

	@Test
	public void rejectsNestedTransferWireNodes() {
		ByteBuffer buffer = nestedCharSequenceSnapshot();
		State restored = new State(new StateRegistry());

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> restored.readFrom(buffer));

		assertTrue(error.getMessage().contains("top level"));
		assertTrue(restored.isEmpty());
		assertEquals(0, buffer.position());
	}

	private static State roundTrip(State original) {
		ByteBuffer buffer = serialize(original);
		int serializedLength = buffer.remaining();
		State restored = new State(new StateRegistry());
		assertEquals(serializedLength, restored.readFrom(buffer));
		assertFalse(buffer.hasRemaining());
		return restored;
	}

	private static ByteBuffer serialize(State state) {
		ByteBuffer buffer = ByteBuffer.allocate(state.getSerializedLength());
		assertEquals(buffer.capacity(), state.writeTo(buffer));
		buffer.flip();
		return buffer;
	}

	private static ByteBuffer nestedCharSequenceSnapshot() {
		ByteBuffer buffer = ByteBuffer.allocate(256);
		putRawChars(buffer, StateSerializer.MAGIC);
		buffer.putShort(StateSerializer.FORMAT_VERSION);
		buffer.putInt(1);
		putChars(buffer, "values");
		int listNode = beginNode(buffer, StateSerializer.ARRAY_LIST_WIRE_NAME);
		buffer.putInt(4);
		buffer.putFloat(2f);
		buffer.putInt(1);
		int textNode = beginNode(buffer, StateSerializer.CHAR_SEQUENCE_WIRE_NAME);
		buffer.putInt(1);
		buffer.putChar('x');
		finishNode(buffer, textNode);
		finishNode(buffer, listNode);
		buffer.flip();
		return buffer;
	}

	private static int beginNode(ByteBuffer buffer, String wireName) {
		int node = buffer.position();
		buffer.putInt(0);
		putChars(buffer, wireName);
		return node;
	}

	private static void finishNode(ByteBuffer buffer, int node) {
		buffer.putInt(node, buffer.position() - node - Integer.BYTES);
	}

	private static void putChars(ByteBuffer buffer, String value) {
		buffer.putInt(value.length());
		putRawChars(buffer, value);
	}

	private static void putRawChars(ByteBuffer buffer, String value) {
		for (int i = 0; i < value.length(); i++) buffer.put((byte) value.charAt(i));
	}

	private static String everyJavaChar() {
		StringBuilder builder = new StringBuilder(Character.MAX_VALUE + 1);
		for (int value = Character.MIN_VALUE; value <= Character.MAX_VALUE; value++) {
			builder.append((char) value);
		}
		return builder.toString();
	}

	private static final class FailingCharSequence implements CharSequence {

		@Override
		public int length() {
			return 2;
		}

		@Override
		public char charAt(int index) {
			if (index == 1) throw new IllegalStateException("copy failed");
			return 'x';
		}

		@Override
		public CharSequence subSequence(int start, int end) {
			throw new UnsupportedOperationException();
		}
	}
}
