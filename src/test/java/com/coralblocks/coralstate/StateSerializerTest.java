package com.coralblocks.coralstate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.Before;
import org.junit.Test;

import com.coralblocks.coralds.list.ArrayList;
import com.coralblocks.coralpool.ArrayObjectPool;
import com.coralblocks.coralstate.example.Person;
import com.coralblocks.coralstate.example.PersonCodec;

public class StateSerializerTest {

	private StateRegistry registry;

	@Before
	public void setUp() {
		registry = new StateRegistry();
		registry.register(new PersonCodec(), new ArrayObjectPool<Person>(4, Person.class));
	}

	@Test
	public void writesCodecObject() {
		State state = new State(registry);
		state.put("person", new Person("Alice", 42));
		ByteBuffer buffer = ByteBuffer.allocate(256);

		int written = state.writeTo(buffer);

		assertEquals(buffer.position(), written);
		buffer.flip();
		assertHeader(buffer, 1);
		assertEquals("person", readChars(buffer));
		int nodeLength = buffer.getInt();
		assertEquals(nodeLength, buffer.remaining());
		assertEquals(StateSerializer.CORAL_PROTO_WIRE_NAME, readChars(buffer));
		assertPerson(buffer, "Alice", 42);
	}

	@Test
	public void writesArrayListTypeAndOriginalConstructorArguments() {
		ArrayList<Person> people = new ArrayList<>(1, 2.0f);
		people.add(new Person("Alice", 42));
		people.add(new Person("Bob", 37));

		State state = new State(registry);
		state.put("people", people);
		ByteBuffer buffer = ByteBuffer.allocate(512);

		state.writeTo(buffer);

		buffer.flip();
		assertHeader(buffer, 1);
		assertEquals("people", readChars(buffer));
		int nodeLength = buffer.getInt();
		assertEquals(nodeLength, buffer.remaining());
		assertEquals(StateSerializer.ARRAY_LIST_WIRE_NAME, readChars(buffer));
		assertEquals(1, buffer.getInt());
		assertEquals(2.0f, buffer.getFloat(), 0f);
		assertEquals(2, buffer.getInt());
		assertCodecPerson(buffer, "Alice", 42);
		assertCodecPerson(buffer, "Bob", 37);
		assertFalseRemaining(buffer);
	}

	@Test
	public void calculatesExactSerializedLength() {
		ArrayList<Person> people = new ArrayList<>(2, 2.0f);
		people.add(new Person("Alice", 42));
		people.add(new Person("Bob", 37));

		State state = new State(registry);
		state.put("person", new Person("Carol", 31));
		state.put("people", people);

		int serializedLength = state.getSerializedLength();
		ByteBuffer buffer = ByteBuffer.allocate(serializedLength + 7);
		buffer.position(7);

		assertEquals(serializedLength, state.writeTo(buffer));
		assertEquals(0, buffer.remaining());
	}

	@Test
	public void writesOneByteCharSequenceKeys() {
		State state = new State(registry);
		state.put("ação", new Person("Coral", 1));
		ByteBuffer buffer = ByteBuffer.allocate(256);

		state.writeTo(buffer);

		buffer.flip();
		assertHeader(buffer, 1);
		assertEquals("ação", readChars(buffer));
	}

	@Test
	public void rejectsCyclicArrayListAndRestoresBufferPosition() {
		ArrayList<Object> cyclic = new ArrayList<>();
		cyclic.add(cyclic);
		State state = new State(registry);
		state.put("cyclic", cyclic);
		ByteBuffer buffer = ByteBuffer.allocate(256);
		buffer.position(7);

		try {
			state.writeTo(buffer);
			fail("Expected cyclic ArrayList to be rejected");
		} catch (IllegalArgumentException e) {
			assertTrue(e.getMessage().contains("Cyclic ArrayList"));
		}

		assertEquals(7, buffer.position());
	}

	@Test
	public void rejectsUnsupportedValuesClearly() {
		State state = new State(registry);
		state.put("unsupported", new Object());

		try {
			state.writeTo(ByteBuffer.allocate(128));
			fail("Expected unsupported type to be rejected");
		} catch (IllegalArgumentException e) {
			assertTrue(e.getMessage().contains(Object.class.getName()));
		}
	}

	@Test
	public void restoresTheOriginalByteOrder() {
		State state = new State(registry);
		state.put("person", new Person("Alice", 42));
		ByteBuffer buffer = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);

		state.writeTo(buffer);

		assertEquals(ByteOrder.LITTLE_ENDIAN, buffer.order());
		buffer.flip();
		buffer.order(ByteOrder.BIG_ENDIAN);
		assertHeader(buffer, 1);
	}

	private static void assertHeader(ByteBuffer buffer, int expectedEntries) {
		assertEquals(StateSerializer.MAGIC, readRawChars(buffer, StateSerializer.MAGIC.length()));
		assertEquals(StateSerializer.FORMAT_VERSION, buffer.getShort());
		assertEquals(expectedEntries, buffer.getInt());
	}

	private static void assertCodecPerson(ByteBuffer buffer, String name, int age) {
		int nodeLength = buffer.getInt();
		int oldLimit = buffer.limit();
		buffer.limit(buffer.position() + nodeLength);
		assertEquals(StateSerializer.CORAL_PROTO_WIRE_NAME, readChars(buffer));
		assertPerson(buffer, name, age);
		assertEquals(0, buffer.remaining());
		buffer.limit(oldLimit);
	}

	private static void assertPerson(ByteBuffer buffer, String name, int age) {
		assertEquals(PersonCodec.PersonProto.TYPE, (char) (buffer.get() & 0xff));
		assertEquals(PersonCodec.PersonProto.SUBTYPE, (char) (buffer.get() & 0xff));
		assertEquals(0, buffer.getShort());
		assertEquals(name, readChars(buffer));
		assertEquals(age, buffer.getInt());
	}

	private static String readChars(ByteBuffer buffer) {
		int length = buffer.getInt();
		return readRawChars(buffer, length);
	}

	private static String readRawChars(ByteBuffer buffer, int length) {
		StringBuilder sb = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			sb.append((char) (buffer.get() & 0xff));
		}
		return sb.toString();
	}

	private static void assertFalseRemaining(ByteBuffer buffer) {
		assertEquals(0, buffer.remaining());
	}
}
