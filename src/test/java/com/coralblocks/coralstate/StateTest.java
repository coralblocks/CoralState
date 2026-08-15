package com.coralblocks.coralstate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StateTest {

	@Test
	public void usesCharSequenceKeys() {
		State state = new State(new StateRegistry());
		Object value = new Object();

		state.put(new StringBuilder("answer"), value);

		assertSame(value, state.get("answer"));
		assertTrue(state.check(new StringBuilder("answer")));
		assertFalse(state.check("missing"));
		assertEquals(1, state.size());
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
	public void rejectsNullRegistry() {
		new State(null);
	}

	@Test
	public void comparesAndPrintsEntriesByValue() {
		State first = new State(new StateRegistry());
		first.put("name", "Coral");
		first.put("version", Integer.valueOf(1));

		State second = new State(new StateRegistry());
		second.put(new StringBuilder("version"), Integer.valueOf(1));
		second.put(new StringBuilder("name"), new String("Coral"));

		assertEquals(first, second);
		assertEquals(first.hashCode(), second.hashCode());
		assertTrue(first.toString().contains("name=Coral"));
		assertTrue(first.toString().contains("version=1"));
	}
}
