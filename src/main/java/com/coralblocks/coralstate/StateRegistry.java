package com.coralblocks.coralstate;

import com.coralblocks.coralds.map.IntMap;
import com.coralblocks.coralds.map.Map;
import com.coralblocks.coralpool.ObjectPool;
import com.coralblocks.coralproto.Proto;

public final class StateRegistry {

	private final Map<Class<?>, StateCodec<?,?>> byJavaType = new Map<>();
	private final IntMap<StateCodec<?,?>> byProtoType = new IntMap<>();
	private final Map<Class<?>, ObjectPool<?>> poolsByJavaType = new Map<>();

	public <T, P extends Proto> StateRegistry register(StateCodec<T, P> stateCodec, ObjectPool<T> pool) {
		
		Proto proto = stateCodec.getProto();
		Class<T> javaType = stateCodec.javaType();

		int protoKey = protoKey(proto.getType(), proto.getSubtype(), proto.getVersion());

		if (byJavaType.containsKey(javaType)) {
			throw new IllegalArgumentException("A codec is already registered for " + javaType.getName());
		}
		
		if (byProtoType.containsKey(protoKey)) {
			
			throw new IllegalArgumentException("A codec is already registered for "
					+ describeProto(proto.getType(), proto.getSubtype(), proto.getVersion()));
		}

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

	@SuppressWarnings("unchecked")
	<T> ObjectPool<T> findPoolByJavaType(Class<T> javaType) {
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
