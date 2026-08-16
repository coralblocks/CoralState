package com.coralblocks.coralstate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;

import org.junit.Test;

import com.coralblocks.coralds.list.ArrayList;
import com.coralblocks.coralds.map.Map;

public class StateStringTest {

	@Test
	public void storesGetsAndRemovesTheOriginalString() {
		State state = new State(new StateRegistry());
		String value = new String("Alice");

		state.put("name", value);

		assertSame(value, state.get("name"));
		assertSame(value, state.remove("name"));
		assertTrue(state.isEmpty());
	}

	@Test
	public void roundTripsStringsWithEveryJavaChar() {
		String everyJavaChar = everyJavaChar();
		State original = new State(new StateRegistry());
		original.put("empty", "");
		original.put("ascii", "CoralState");
		original.put("unicode", "ação 漢字 🪸");
		original.put("unpairedSurrogate", "before\uD800after");
		original.put("everyJavaChar", everyJavaChar);

		State restored = roundTrip(original);

		assertEquals(original, restored);
		assertEquals("", restored.get("empty"));
		assertEquals("CoralState", restored.get("ascii"));
		assertEquals("ação 漢字 🪸", restored.get("unicode"));
		assertEquals("before\uD800after", restored.get("unpairedSurrogate"));
		assertEquals(everyJavaChar, restored.get("everyJavaChar"));
	}

	@Test
	public void roundTripsStringsInsideCoralDSContainers() {
		ArrayList<String> names = new ArrayList<>();
		names.add("Alice");
		names.add("Beto");
		Map<String, String> translations = new Map<>();
		translations.put("hello", "olá");

		State original = new State(new StateRegistry());
		original.put("names", names);
		original.put("translations", translations);
		State restored = roundTrip(original);

		assertEquals(original, restored);
		ArrayList<?> restoredNames = (ArrayList<?>) restored.get("names");
		assertEquals(String.class, restoredNames.get(0).getClass());
		assertEquals("Alice", restoredNames.get(0));
		assertEquals("Beto", restoredNames.get(1));
	}

	@Test
	public void rejectsUnregisteredStringBuilders() {
		State state = new State(new StateRegistry());

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> state.put("text", new StringBuilder("CoralState")));

		assertTrue(error.getMessage().contains(StringBuilder.class.getName()));
		assertTrue(state.isEmpty());
	}

	@Test
	public void rejectsAnInvalidStringLengthAtomically() {
		State original = new State(new StateRegistry());
		original.put("text", "A");
		ByteBuffer buffer = serialize(original);
		int stringLengthPosition = findStringLengthPosition(buffer);
		buffer.putInt(stringLengthPosition, 2);

		State restored = new State(new StateRegistry());
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> restored.readFrom(buffer));

		assertTrue(error.getMessage().contains("Invalid String length"));
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
		int serializedLength = state.getSerializedLength();
		ByteBuffer buffer = ByteBuffer.allocate(serializedLength);
		assertEquals(serializedLength, state.writeTo(buffer));
		assertEquals(serializedLength, buffer.position());
		buffer.flip();
		return buffer;
	}

	private static int findStringLengthPosition(ByteBuffer buffer) {
		int position = StateSerializer.MAGIC.length() + Short.BYTES + Integer.BYTES;
		int keyLength = buffer.getInt(position);
		position += Integer.BYTES + keyLength;
		position += Integer.BYTES;
		int wireNameLength = buffer.getInt(position);
		return position + Integer.BYTES + wireNameLength;
	}

	private static String everyJavaChar() {
		StringBuilder builder = new StringBuilder(Character.MAX_VALUE + 1);
		for (int value = Character.MIN_VALUE; value <= Character.MAX_VALUE; value++) {
			builder.append((char) value);
		}
		return builder.toString();
	}
}
