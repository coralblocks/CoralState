package com.coralblocks.coralstate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;

import org.junit.Before;
import org.junit.Test;

import com.coralblocks.coralds.list.ArrayList;
import com.coralblocks.coralds.map.Map;
import com.coralblocks.coralpool.ArrayObjectPool;
import com.coralblocks.coralstate.example.Person;
import com.coralblocks.coralstate.example.PersonCodec;

public class StateValueValidationTest {

	private State state;

	@Before
	public void setUp() {
		StateRegistry registry = new StateRegistry();
		registry.register(new PersonCodec(), new ArrayObjectPool<Person>(4, Person.class));
		state = new State(registry);
	}

	@Test
	public void acceptsRegisteredObjectsAndSupportedCoralDSContainers() {
		Person person = new Person("Alice", 42);
		ArrayList<Person> people = new ArrayList<>();
		people.add(person);

		state.put("person", person);
		state.put("people", people);

		assertSame(person, state.get("person"));
		assertSame(people, state.get("people"));
	}

	@Test
	public void rejectsJdkContainersAndUnregisteredObjects() {
		IllegalArgumentException jdkListError = assertThrows(IllegalArgumentException.class,
				() -> state.put("jdkList", new java.util.ArrayList<>()));
		IllegalArgumentException animalError = assertThrows(IllegalArgumentException.class,
				() -> state.put("animal", new Animal()));

		assertTrue(jdkListError.getMessage().contains(java.util.ArrayList.class.getName()));
		assertTrue(animalError.getMessage().contains(Animal.class.getName()));
		assertTrue(state.isEmpty());
	}

	@Test
	public void recursivelyRejectsUnsupportedContainerElementsAndObjectMapKeys() {
		ArrayList<Object> animals = new ArrayList<>();
		animals.add(new Animal());
		Map<Object, Person> peopleByAnimal = new Map<>();
		peopleByAnimal.put(new Animal(), new Person("Alice", 42));

		IllegalArgumentException elementError = assertThrows(IllegalArgumentException.class,
				() -> state.put("animals", animals));
		IllegalArgumentException keyError = assertThrows(IllegalArgumentException.class,
				() -> state.put("peopleByAnimal", peopleByAnimal));

		assertTrue(elementError.getMessage().contains(Animal.class.getName()));
		assertTrue(keyError.getMessage().contains(Animal.class.getName()));
		assertTrue(state.isEmpty());
	}

	@Test
	public void rejectsCycles() {
		ArrayList<Object> cyclic = new ArrayList<>();
		cyclic.add(cyclic);

		IllegalArgumentException cycleError = assertThrows(IllegalArgumentException.class,
				() -> state.put("cycle", cyclic));

		assertTrue(cycleError.getMessage().contains("Cyclic ArrayList"));
		assertTrue(state.isEmpty());
	}

	@Test
	public void failedValidationDoesNotReplaceTheExistingValue() {
		state.put("value", 42L);

		assertThrows(IllegalArgumentException.class, () -> state.put("value", new Animal()));

		assertEquals(42L, state.getLong("value"));
		assertEquals(1, state.size());
	}

	@Test
	public void acceptsSharedContainersThatAreNotCyclic() {
		ArrayList<Person> shared = new ArrayList<>();
		shared.add(new Person("Alice", 42));
		ArrayList<ArrayList<Person>> outer = new ArrayList<>();
		outer.add(shared);
		outer.add(shared);

		state.put("outer", outer);

		assertSame(outer, state.get("outer"));
	}

	@Test
	public void serializationStillRejectsAContainerMadeInvalidAfterPut() {
		ArrayList<Object> values = new ArrayList<>();
		state.put("values", values);
		values.add(new Animal());

		IllegalStateException lengthError = assertThrows(IllegalStateException.class,
				() -> state.getSerializedLength());
		IllegalStateException writeError = assertThrows(IllegalStateException.class,
				() -> state.writeTo(ByteBuffer.allocate(128)));

		assertTrue(lengthError.getMessage().contains(Animal.class.getName()));
		assertTrue(writeError.getMessage().contains(Animal.class.getName()));
	}

	private static final class Animal {
	}
}
