package com.coralblocks.coralstate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;

import org.junit.Test;

import com.coralblocks.coralds.list.IntArrayList;

public class StatePrimitiveTest {

	@Test
	public void storesGetsAndRemovesEveryPrimitiveType() {
		State state = new State(new StateRegistry());
		putAllPrimitives(state);

		assertTrue(state.getBoolean(new StringBuilder("boolean")));
		assertEquals(Byte.MIN_VALUE, state.getByte("byte"));
		assertEquals('\uCAFE', state.getChar("char"));
		assertEquals(Short.MIN_VALUE, state.getShort("short"));
		assertEquals(Integer.MIN_VALUE, state.getInt("int"));
		assertEquals(9_876_543_210L, state.getLong("long"));
		assertEquals(-12.5f, state.getFloat("float"), 0f);
		assertEquals(Math.PI, state.getDouble("double"), 0d);
		assertEquals(8, state.size());

		assertTrue(state.removeBoolean("boolean"));
		assertEquals(Byte.MIN_VALUE, state.removeByte("byte"));
		assertEquals('\uCAFE', state.removeChar("char"));
		assertEquals(Short.MIN_VALUE, state.removeShort("short"));
		assertEquals(Integer.MIN_VALUE, state.removeInt("int"));
		assertEquals(9_876_543_210L, state.removeLong("long"));
		assertEquals(-12.5f, state.removeFloat("float"), 0f);
		assertEquals(Math.PI, state.removeDouble("double"), 0d);
		assertTrue(state.isEmpty());
	}

	@Test
	public void requiresTypedAccessForPrimitiveValues() {
		State state = new State(new StateRegistry());
		state.put("number", 9_876_543_210L);

		assertThrows(IllegalArgumentException.class, () -> state.get("number"));
		assertThrows(IllegalArgumentException.class, () -> state.remove("number"));
		IllegalArgumentException getIntError = assertThrows(
				IllegalArgumentException.class, () -> state.getInt("number"));
		IllegalArgumentException removeIntError = assertThrows(
				IllegalArgumentException.class, () -> state.removeInt("number"));
		assertThrows(IllegalArgumentException.class, () -> state.getLong("missing"));
		assertThrows(IllegalArgumentException.class, () -> state.removeLong("missing"));
		assertEquals("State key does not contain an int value: number", getIntError.getMessage());
		assertEquals("State key does not contain an int value: number", removeIntError.getMessage());
		assertTrue(state.check("number"));
		assertEquals(9_876_543_210L, state.getLong("number"));
	}

	@Test
	public void releasesRemovedAndReplacedPrimitiveHoldersToTheirPools() {
		State state = new State(new StateRegistry());
		state.put("number", 1L);
		Object firstHolder = state.internalValues().get("number");

		state.put("number", 2L);
		Object secondHolder = state.internalValues().get("number");
		assertNotSame(firstHolder, secondHolder);

		assertEquals(2L, state.removeLong("number"));
		state.put("reused", 3L);
		assertSame(secondHolder, state.internalValues().get("reused"));

		IntArrayList object = new IntArrayList();
		state.put("reused", object);
		assertSame(object, state.get("reused"));
		state.put("another", 4L);
		assertSame(secondHolder, state.internalValues().get("another"));
	}

	@Test
	public void serializesAndDeserializesEveryPrimitiveType() {
		State original = new State(new StateRegistry());
		putAllPrimitives(original);

		ByteBuffer buffer = serialize(original);
		int serializedLength = buffer.remaining();
		State restored = new State(new StateRegistry());

		assertEquals(serializedLength, restored.readFrom(buffer));
		assertFalse(buffer.hasRemaining());
		assertEquals(original, restored);
		assertEquals(original.hashCode(), restored.hashCode());
		assertTrue(restored.getBoolean("boolean"));
		assertEquals(Byte.MIN_VALUE, restored.getByte("byte"));
		assertEquals('\uCAFE', restored.getChar("char"));
		assertEquals(Short.MIN_VALUE, restored.getShort("short"));
		assertEquals(Integer.MIN_VALUE, restored.getInt("int"));
		assertEquals(9_876_543_210L, restored.getLong("long"));
		assertEquals(-12.5f, restored.getFloat("float"), 0f);
		assertEquals(Math.PI, restored.getDouble("double"), 0d);
	}

	@Test
	public void returnsDeserializedPrimitiveHoldersToTheirPoolsAfterFailure() {
		State source = new State(new StateRegistry());
		source.put("a", 11L);
		source.put("b", 22L);
		ByteBuffer buffer = serialize(source);
		buffer.limit(buffer.limit() - 1);

		State restored = new State(new StateRegistry());
		restored.put("seed", 0L);
		Object expectedReusedHolder = restored.internalValues().get("seed");
		restored.removeLong("seed");

		assertThrows(IllegalArgumentException.class, () -> restored.readFrom(buffer));
		assertTrue(restored.isEmpty());
		assertEquals(0, buffer.position());

		restored.put("after", 33L);
		assertSame(expectedReusedHolder, restored.internalValues().get("after"));
	}

	private static void putAllPrimitives(State state) {
		state.put("boolean", true);
		state.put("byte", Byte.MIN_VALUE);
		state.put("char", '\uCAFE');
		state.put("short", Short.MIN_VALUE);
		state.put("int", Integer.MIN_VALUE);
		state.put("long", 9_876_543_210L);
		state.put("float", -12.5f);
		state.put("double", Math.PI);
	}

	private static ByteBuffer serialize(State state) {
		ByteBuffer buffer = ByteBuffer.allocate(state.getSerializedLength());
		assertEquals(buffer.capacity(), state.writeTo(buffer));
		buffer.flip();
		return buffer;
	}
}
