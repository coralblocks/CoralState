package com.coralblocks.coralstate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import com.coralblocks.coralpool.ArrayObjectPool;
import com.coralblocks.coralpool.ObjectPool;
import com.coralblocks.coralproto.AbstractProto;
import com.coralblocks.coralproto.field.SubtypeField;
import com.coralblocks.coralproto.field.TypeField;

public class StateRegistryTest {

	@Test
	public void registerCreatesADefaultPoolWithSixtyFourObjects() {
		PooledObject.instances = 0;
		StateRegistry registry = new StateRegistry();

		assertSame(registry, registry.register(new PooledObjectCodec()));

		assertEquals(1, registry.size());
		assertEquals(64, PooledObject.instances);
		ObjectPool<PooledObject> pool = registry.getPool(PooledObject.class);
		assertNotNull(pool);
		PooledObject object = pool.get();
		assertNotNull(object);
		pool.release(object);
	}

	@Test
	public void registerStillAcceptsAnApplicationProvidedPool() {
		PooledObject.instances = 0;
		ObjectPool<PooledObject> pool = new ArrayObjectPool<>(2, PooledObject.class);
		StateRegistry registry = new StateRegistry();

		registry.register(new PooledObjectCodec(), pool);

		assertSame(pool, registry.getPool(PooledObject.class));
		assertEquals(2, PooledObject.instances);
	}

	public static final class PooledObject {

		private static int instances;

		public PooledObject() {
			instances++;
		}
	}

	private static final class PooledObjectCodec
			implements StateCodec<PooledObject, PooledObjectProto> {

		private final PooledObjectProto proto = new PooledObjectProto();

		@Override
		public Class<PooledObject> javaType() {
			return PooledObject.class;
		}

		@Override
		public PooledObjectProto getProto() {
			return proto;
		}

		@Override
		public void encode(PooledObject object, PooledObjectProto proto) {
		}

		@Override
		public void decode(PooledObjectProto proto, PooledObject object) {
		}
	}

	@SuppressWarnings("unused") // Field construction defines the type and subtype on AbstractProto.
	private static final class PooledObjectProto extends AbstractProto {

		private static final char TYPE = 'T';
		private static final char SUBTYPE = 'D';

		public final TypeField type = new TypeField(this, TYPE);
		public final SubtypeField subtype = new SubtypeField(this, SUBTYPE);
	}
}
