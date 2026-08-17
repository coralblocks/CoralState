package com.coralblocks.coralstate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;

import org.junit.Test;

import com.coralblocks.coralds.list.ArrayList;

public class StateTest {

	@Test
	public void usesCharSequenceKeys() {
		State state = new State(new StateRegistry());
		ArrayList<Object> value = new ArrayList<>();

		state.put(new StringBuilder("answer"), value);

		assertSame(value, state.get("answer"));
		assertTrue(state.check(new StringBuilder("answer")));
		assertFalse(state.check("missing"));
		assertEquals(1, state.size());
	}

	@Test
	public void removesEntriesByCharSequenceKey() {
		State state = new State(new StateRegistry());
		ArrayList<Object> value = new ArrayList<>();
		state.put("answer", value);

		assertSame(value, state.remove(new StringBuilder("answer")));
		assertFalse(state.check("answer"));
		assertEquals(0, state.size());
		assertTrue(state.isEmpty());
		assertNull(state.remove("missing"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNullValues() {
		new State(new StateRegistry()).put("key", null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNullKeys() {
		new State(new StateRegistry()).put(null, new Object());
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNullKeysWhenRemoving() {
		new State(new StateRegistry()).remove(null);
	}

	@Test
	public void rejectsStateKeysOutsideLatin1BeforeMutation() {
		State state = new State(new StateRegistry());
		ArrayList<Object> original = new ArrayList<>();
		state.put("valid", original);

		IllegalArgumentException objectError = assertThrows(IllegalArgumentException.class,
				() -> state.put("日本", "value"));
		IllegalArgumentException primitiveError = assertThrows(IllegalArgumentException.class,
				() -> state.put("valid中", 42));

		assertTrue(objectError.getMessage().contains("outside Latin-1"));
		assertTrue(primitiveError.getMessage().contains("outside Latin-1"));
		assertSame(original, state.get("valid"));
		assertEquals(1, state.size());
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNullRegistry() {
		new State(null);
	}

	@Test
	public void comparesAndPrintsEntriesByValue() {
		State first = new State(new StateRegistry());
		first.put("active", true);
		first.put("version", 1);

		State second = new State(new StateRegistry());
		second.put(new StringBuilder("version"), 1);
		second.put(new StringBuilder("active"), true);

		assertEquals(first, second);
		assertEquals(first.hashCode(), second.hashCode());
		assertTrue(first.toString().contains("active=true"));
		assertTrue(first.toString().contains("version=1"));
	}

	@Test
	public void clearsEntriesAndReusesInternalHolders() {
		State state = new State(new StateRegistry());
		ArrayList<Object> applicationObject = new ArrayList<>();
		state.put("object", applicationObject);
		state.put("number", 1L);
		state.put("text", new StringBuilder("first"));
		state.put("bytes", ByteBuffer.wrap(new byte[] { 1, 2 }));
		Object numberHolder = state.internalValues().get("number");
		Object textHolder = state.internalValues().get("text");
		Object byteHolder = state.internalValues().get("bytes");

		state.clear();

		assertTrue(state.isEmpty());
		state.put("newNumber", 2L);
		state.put("newText", new StringBuilder("second"));
		state.put("newBytes", ByteBuffer.wrap(new byte[] { 3 }));
		assertSame(numberHolder, state.internalValues().get("newNumber"));
		assertSame(textHolder, state.internalValues().get("newText"));
		assertSame(byteHolder, state.internalValues().get("newBytes"));
	}

	@Test
	public void readsAnotherSnapshotAfterClear() {
		State first = new State(new StateRegistry());
		first.put("number", 11L);
		State second = new State(new StateRegistry());
		second.put("text", new StringBuilder("second"));
		State restored = new State(new StateRegistry());

		ByteBuffer firstBuffer = serialize(first);
		assertEquals(firstBuffer.remaining(), restored.readFrom(firstBuffer));
		assertEquals(11L, restored.getLong("number"));

		restored.clear();
		ByteBuffer secondBuffer = serialize(second);
		assertEquals(secondBuffer.remaining(), restored.readFrom(secondBuffer));
		assertEquals("second", restored.getCharSequence("text").toString());
	}

	private static ByteBuffer serialize(State state) {
		ByteBuffer buffer = ByteBuffer.allocate(state.getSerializedLength());
		state.writeTo(buffer);
		buffer.flip();
		return buffer;
	}
}
