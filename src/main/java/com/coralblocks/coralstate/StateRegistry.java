package com.coralblocks.coralstate;

import com.coralblocks.coralds.map.IntMap;
import com.coralblocks.coralds.map.Map;
import com.coralblocks.coralpool.ArrayObjectPool;
import com.coralblocks.coralpool.ObjectPool;
import com.coralblocks.coralproto.Proto;

public final class StateRegistry {

	private static final int DEFAULT_POOL_SIZE = 64;

	private final Map<Class<?>, StateCodec<?,?>> byJavaType = new Map<>();
	private final IntMap<StateCodec<?,?>> byProtoType = new IntMap<>();
	private final Map<Class<?>, ObjectPool<?>> poolsByJavaType = new Map<>();

	/**
	 * Registers a codec using a default object pool containing 64 instances. The codec's Java type
	 * must provide a public empty constructor.
	 */
	public <T, P extends Proto> StateRegistry register(StateCodec<T, P> stateCodec) {
		P proto = stateCodec.getProto();
		Class<T> javaType = stateCodec.javaType();
		int protoKey = protoKey(proto.getType(), proto.getSubtype(), proto.getVersion());
		validateRegistration(javaType, proto, protoKey);
		return register(stateCodec, new ArrayObjectPool<T>(DEFAULT_POOL_SIZE, javaType),
				javaType, protoKey);
	}

	/**
	 * Registers a codec using an application-provided object pool.
	 */
	public <T, P extends Proto> StateRegistry register(StateCodec<T, P> stateCodec, ObjectPool<T> pool) {
		P proto = stateCodec.getProto();
		Class<T> javaType = stateCodec.javaType();
		int protoKey = protoKey(proto.getType(), proto.getSubtype(), proto.getVersion());
		validateRegistration(javaType, proto, protoKey);
		return register(stateCodec, pool, javaType, protoKey);
	}

	private void validateRegistration(Class<?> javaType, Proto proto, int protoKey) {
		if (byJavaType.containsKey(javaType)) {
			throw new IllegalArgumentException("A codec is already registered for " + javaType.getName());
		}
		
		if (byProtoType.containsKey(protoKey)) {
			
			throw new IllegalArgumentException("A codec is already registered for "
					+ describeProto(proto.getType(), proto.getSubtype(), proto.getVersion()));
		}
	}

	private <T, P extends Proto> StateRegistry register(StateCodec<T, P> stateCodec,
			ObjectPool<T> pool, Class<T> javaType, int protoKey) {
		byJavaType.put(javaType, stateCodec);
		byProtoType.put(protoKey, stateCodec);
		poolsByJavaType.put(javaType, pool);
		
		return this;
	}

	public int size() {
		return byJavaType.size();
	}

	@SuppressWarnings("unchecked")
	public <T> StateCodec<T, ?> findByJavaType(Class<T> javaType) {
		return (StateCodec<T, ?>) byJavaType.get(javaType);
	}

	public StateCodec<?, ?> findByProtoType(char type, char subtype, short version) {
		return byProtoType.get(protoKey(type, subtype, version));
	}

	/**
	 * Returns the object pool registered for the given Java type, or {@code null} when the type is
	 * not registered.
	 */
	@SuppressWarnings("unchecked")
	public <T> ObjectPool<T> getPool(Class<T> javaType) {
		return (ObjectPool<T>) poolsByJavaType.get(javaType);
	}

	private static int protoKey(char type, char subtype, short version) {
		return ((type & 0xff) << 24)
				| ((subtype & 0xff) << 16)
				| (version & 0xffff);
	}

	private static String describeProto(char type, char subtype, short version) {
		return "type='" + type + "' subtype='" + subtype + "' version=" + version;
	}
}
