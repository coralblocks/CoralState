package com.coralblocks.coralstate;

import com.coralblocks.coralds.map.CharSequenceMap;

public class State {

	private final CharSequenceMap<Object> values = new CharSequenceMap<>();
	private final StateRegistry registry;
	
	public State(StateRegistry registry) {
		if (registry == null) throw new IllegalArgumentException("StateRegistry cannot be null");
		this.registry = registry;
	}
	
	public StateRegistry getRegistry() {
		return registry;
	}
	
	public void put(CharSequence key, Object value) {
		if (key == null) throw new IllegalArgumentException("State key cannot be null");
		if (value == null) throw new IllegalArgumentException("State value cannot be null");
		values.put(key, value);
	}
	
	public Object get(CharSequence key) {
		if (key == null) throw new IllegalArgumentException("State key cannot be null");
		return values.get(key);
	}
	
	public boolean check(CharSequence key) {
		if (key == null) throw new IllegalArgumentException("State key cannot be null");
		return values.containsKey(key);
	}

	public int size() {
		return values.size();
	}

	public boolean isEmpty() {
		return values.isEmpty();
	}

	CharSequenceMap<Object> internalValues() {
		return values;
	}
}
