package com.coralblocks.coralstate;

import com.coralblocks.coralds.map.LinkedMap;

public class State {

	private final LinkedMap<Object, Object> values = new LinkedMap<>();
	
	public void put(Object key, Object value) {
		values.put(key, value);
	}
	
	public Object get(Object key) {
		return values.get(key);
	}
	
	public boolean check(Object key) {
		return values.containsKey(key);
	}

	public int size() {
		return values.size();
	}

	public boolean isEmpty() {
		return values.isEmpty();
	}

	LinkedMap<Object, Object> internalValues() {
		return values;
	}
}
