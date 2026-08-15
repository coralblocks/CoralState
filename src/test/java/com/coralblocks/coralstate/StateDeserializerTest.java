package com.coralblocks.coralstate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.ByteBuffer;

import org.junit.Before;
import org.junit.Test;

import com.coralblocks.coralds.list.ArrayList;
import com.coralblocks.coralds.map.IntMap;
import com.coralblocks.coralds.set.IntSet;
import com.coralblocks.coralds.set.Set;
import com.coralblocks.coralpool.ArrayObjectPool;
import com.coralblocks.coralpool.ObjectPool;
import com.coralblocks.coralstate.example.Person;
import com.coralblocks.coralstate.example.PersonCodec;

public class StateDeserializerTest {

	private static final int STATE_HEADER_LENGTH = StateSerializer.MAGIC.length() + Short.BYTES + Integer.BYTES;

	private StateRegistry registry;
	private TrackingPersonPool personPool;

	@Before
	public void setUp() {
		personPool = new TrackingPersonPool();
		registry = new StateRegistry();
		registry.register(new PersonCodec(), personPool);
	}

	@Test
	public void returnsAllDecodedObjectsToTheirPoolsAfterFailureAndReusesTheJournal() {
		ArrayList<Person> people = new ArrayList<>(3);
		people.add(new Person("Alice", 31));
		people.add(new Person("Bob", 42));
		people.add(new Person("Carol", 27));
		State source = new State(registry);
		source.put("a", people);
		source.put("b", new Person("Dave", 35));
		ByteBuffer buffer = serialize(source);
		assertEquals('a', (char) (buffer.get(STATE_HEADER_LENGTH + Integer.BYTES) & 0xff));
		int completeLimit = buffer.limit();
		buffer.limit(completeLimit - 1);

		State restored = new State(registry);
		assertReadFails(restored, buffer, "Invalid node length");
		assertEquals(3, personPool.getCount);
		assertEquals(3, personPool.releaseCount);

		buffer.limit(completeLimit);
		assertEquals(completeLimit, restored.readFrom(buffer));
		assertEquals(source, restored);
		assertEquals(7, personPool.getCount);
		assertEquals(3, personPool.releaseCount);
	}

	@Test
	public void rejectsDuplicateStateKeysAndRollsBackDecodedObjects() {
		State source = new State(registry);
		source.put("a", new Person("Alice", 31));
		source.put("b", new Person("Bob", 42));
		ByteBuffer buffer = serialize(source);

		int firstKeyLengthPosition = STATE_HEADER_LENGTH;
		int firstKeyLength = buffer.getInt(firstKeyLengthPosition);
		int firstKeyPosition = firstKeyLengthPosition + Integer.BYTES;
		int firstNodeLengthPosition = firstKeyPosition + firstKeyLength;
		int firstNodeLength = buffer.getInt(firstNodeLengthPosition);
		int secondKeyLengthPosition = firstNodeLengthPosition + Integer.BYTES + firstNodeLength;
		int secondKeyLength = buffer.getInt(secondKeyLengthPosition);
		int secondKeyPosition = secondKeyLengthPosition + Integer.BYTES;
		assertEquals(firstKeyLength, secondKeyLength);
		for (int i = 0; i < firstKeyLength; i++) {
			buffer.put(secondKeyPosition + i, buffer.get(firstKeyPosition + i));
		}

		assertReadFails(new State(registry), buffer, "Duplicate State key");
		assertEquals(2, personPool.getCount);
		assertEquals(2, personPool.releaseCount);
	}

	@Test
	public void rejectsDuplicatePrimitiveMapKeysAndRollsBackDecodedObjects() {
		IntMap<Person> map = new IntMap<>(4);
		map.put(1, new Person("Alice", 31));
		map.put(2, new Person("Bob", 42));
		State source = new State(registry);
		source.put("map", map);
		ByteBuffer buffer = serialize(source);

		int payloadPosition = rootPayloadPosition(buffer);
		int sizePosition = payloadPosition + Integer.BYTES + Float.BYTES;
		assertEquals(2, buffer.getInt(sizePosition));
		int firstKeyPosition = sizePosition + Integer.BYTES;
		int firstKey = buffer.getInt(firstKeyPosition);
		int firstValueLengthPosition = firstKeyPosition + Integer.BYTES;
		int firstValueLength = buffer.getInt(firstValueLengthPosition);
		int secondKeyPosition = firstValueLengthPosition + Integer.BYTES + firstValueLength;
		buffer.putInt(secondKeyPosition, firstKey);

		assertReadFails(new State(registry), buffer, "Duplicate IntMap key");
		assertEquals(2, personPool.getCount);
		assertEquals(2, personPool.releaseCount);
	}

	@Test
	public void rejectsDuplicatePrimitiveSetElements() {
		IntSet set = new IntSet(4);
		set.add(10);
		set.add(20);
		State source = new State(registry);
		source.put("set", set);
		ByteBuffer buffer = serialize(source);

		int payloadPosition = rootPayloadPosition(buffer);
		int sizePosition = payloadPosition + Integer.BYTES + Float.BYTES;
		assertEquals(2, buffer.getInt(sizePosition));
		int firstElementPosition = sizePosition + Integer.BYTES;
		int secondElementPosition = firstElementPosition + Integer.BYTES;
		buffer.putInt(secondElementPosition, buffer.getInt(firstElementPosition));

		assertReadFails(new State(registry), buffer, "Duplicate IntSet element");
		assertEquals(0, personPool.getCount);
		assertEquals(0, personPool.releaseCount);
	}

	@Test
	public void rejectsDuplicateObjectSetElementsAndRollsBackDecodedObjects() {
		Set<Person> set = new Set<>(4);
		set.add(new Person("Alice", 31));
		set.add(new Person("Carol", 27));
		State source = new State(registry);
		source.put("set", set);
		ByteBuffer buffer = serialize(source);

		int payloadPosition = rootPayloadPosition(buffer);
		int sizePosition = payloadPosition + Integer.BYTES + Float.BYTES;
		assertEquals(2, buffer.getInt(sizePosition));
		int firstNodeLengthPosition = sizePosition + Integer.BYTES;
		int firstNodeLength = buffer.getInt(firstNodeLengthPosition);
		int secondNodeLengthPosition = firstNodeLengthPosition + Integer.BYTES + firstNodeLength;
		assertEquals(firstNodeLength, buffer.getInt(secondNodeLengthPosition));
		for (int i = 0; i < Integer.BYTES + firstNodeLength; i++) {
			buffer.put(secondNodeLengthPosition + i, buffer.get(firstNodeLengthPosition + i));
		}

		assertReadFails(new State(registry), buffer, "Duplicate Set element");
		assertEquals(2, personPool.getCount);
		assertEquals(2, personPool.releaseCount);
	}

	private ByteBuffer serialize(State state) {
		ByteBuffer buffer = ByteBuffer.allocate(state.getSerializedLength());
		state.writeTo(buffer);
		buffer.flip();
		return buffer;
	}

	private static int rootPayloadPosition(ByteBuffer buffer) {
		int position = STATE_HEADER_LENGTH;
		int stateKeyLength = buffer.getInt(position);
		position += Integer.BYTES + stateKeyLength;
		position += Integer.BYTES;
		int identifierLength = buffer.getInt(position);
		return position + Integer.BYTES + identifierLength;
	}

	private static void assertReadFails(State state, ByteBuffer buffer, String expectedMessage) {
		int startPosition = buffer.position();
		try {
			state.readFrom(buffer);
			fail("Expected State deserialization to fail");
		} catch (IllegalArgumentException e) {
			assertTrue(e.getMessage().contains(expectedMessage));
		}
		assertTrue(state.isEmpty());
		assertEquals(startPosition, buffer.position());
	}

	private static final class TrackingPersonPool implements ObjectPool<Person> {

		private final ObjectPool<Person> delegate = new ArrayObjectPool<>(16, Person.class);
		private int getCount;
		private int releaseCount;

		@Override
		public Person get() {
			getCount++;
			return delegate.get();
		}

		@Override
		public void release(Person person) {
			releaseCount++;
			delegate.release(person);
		}
	}
}
