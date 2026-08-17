package com.coralblocks.coralstate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
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
	public void rollsBackErrorsAndReusesTheState() {
		State source = new State(registry);
		source.put("a", new Person("Alice", 31));
		source.put("b", new Person("Bob", 42));
		ByteBuffer buffer = serialize(source);

		TrackingPersonPool errorPool = new TrackingPersonPool();
		ErrorOnSecondDecodePersonCodec errorCodec = new ErrorOnSecondDecodePersonCodec();
		StateRegistry errorRegistry = new StateRegistry();
		errorRegistry.register(errorCodec, errorPool);
		State restored = new State(errorRegistry);
		int startPosition = buffer.position();

		AssertionError error = assertThrows(AssertionError.class, () -> restored.readFrom(buffer));
		assertTrue(error.getMessage().contains("deliberate decode failure"));
		assertTrue(restored.isEmpty());
		assertEquals(startPosition, buffer.position());
		assertEquals(2, errorPool.getCount);
		assertEquals(2, errorPool.releaseCount);

		errorCodec.failOnSecondDecode = false;
		assertEquals(buffer.limit(), restored.readFrom(buffer));
		assertEquals(source, restored);
		assertEquals(4, errorPool.getCount);
		assertEquals(2, errorPool.releaseCount);
	}

	@Test
	public void rejectsExcessiveValueNestingAndReusesTheState() {
		Object nested = "leaf";
		for (int i = 0; i < StateDeserializer.MAX_VALUE_DEPTH; i++) {
			ArrayList<Object> parent = new ArrayList<>(1);
			parent.add(nested);
			nested = parent;
		}

		State source = new State(registry);
		source.put("a", new Person("Alice", 31));
		source.put("b", nested);
		ByteBuffer buffer = serialize(source);
		State restored = new State(registry);

		assertReadFails(restored, buffer, "Maximum State value nesting depth exceeded");
		assertEquals(1, personPool.getCount);
		assertEquals(1, personPool.releaseCount);

		State reusableSource = new State(registry);
		reusableSource.put("person", new Person("Bob", 42));
		ByteBuffer reusableBuffer = serialize(reusableSource);
		assertEquals(reusableBuffer.limit(), restored.readFrom(reusableBuffer));
		assertEquals(reusableSource, restored);
		assertEquals(2, personPool.getCount);
		assertEquals(1, personPool.releaseCount);
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

	private static final class ErrorOnSecondDecodePersonCodec
			implements StateCodec<Person, PersonCodec.PersonProto> {

		private final PersonCodec.PersonProto proto = new PersonCodec.PersonProto();
		private boolean failOnSecondDecode = true;
		private int decodeCount;

		@Override
		public Class<Person> javaType() {
			return Person.class;
		}

		@Override
		public PersonCodec.PersonProto getProto() {
			return proto;
		}

		@Override
		public void encode(Person person, PersonCodec.PersonProto personProto) {
			personProto.name.set(person.getName());
			personProto.age.set(person.getAge());
		}

		@Override
		public void decode(PersonCodec.PersonProto personProto, Person person) {
			if (failOnSecondDecode && ++decodeCount == 2) {
				throw new AssertionError("deliberate decode failure");
			}
			person.setName(personProto.name.get());
			person.setAge(personProto.age.get());
		}
	}
}
