package com.coralblocks.coralstate.example;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;

import org.junit.Test;

import com.coralblocks.coralds.list.ArrayList;
import com.coralblocks.coralpool.ArrayObjectPool;
import com.coralblocks.coralpool.ObjectPool;
import com.coralblocks.coralstate.State;
import com.coralblocks.coralstate.StateRegistry;

public class PersonSerializationTest {

	@Test
	public void serializesAndDeserializesState() {
		ObjectPool<Person> personPool = new ArrayObjectPool<>(4, Person.class);
		StateRegistry registry = new StateRegistry();
		registry.register(new PersonCodec(), personPool);

		State original = new State(registry);
		original.put("person", new Person("Alice", 31));

		ArrayList<Person> people = new ArrayList<>(3, 2f);
		people.add(new Person("Bob", 42));
		people.add(new Person("Carol", 27));
		people.add(new Person("Dave", 35));
		original.put("people", people);

		ByteBuffer buffer = ByteBuffer.allocate(1024);
		int bytesWritten = original.writeTo(buffer);
		buffer.flip();

		State restored = new State(registry);
		int bytesRead = restored.readFrom(buffer);

		System.out.println("Before: " + original);
		System.out.println("After:  " + restored);

		assertEquals(original, restored);
		assertEquals(original.hashCode(), restored.hashCode());
		assertEquals(original.toString(), restored.toString());
		assertEquals(bytesWritten, bytesRead);
		assertEquals(bytesWritten, buffer.position());
		assertEquals(0, buffer.remaining());

		@SuppressWarnings("unchecked")
		ArrayList<Person> restoredPeople = (ArrayList<Person>) restored.get("people");
		assertEquals(3, restoredPeople.getInitialCapacity());
		assertEquals(2f, restoredPeople.getGrowthFactor(), 0f);
	}
}
