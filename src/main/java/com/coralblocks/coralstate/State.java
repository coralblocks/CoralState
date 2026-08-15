package com.coralblocks.coralstate;

import java.util.LinkedHashMap;
import java.util.Map;

public class State {

	private final Map<Object, Object> values = new LinkedHashMap<>();
	
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

	Map<Object, Object> internalValues() {
		return values;
	}
}
