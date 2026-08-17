package com.coralblocks.coralstate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.ByteBuffer;
import java.util.Iterator;

import org.junit.Before;
import org.junit.Test;

import com.coralblocks.coralds.list.ArrayLinkedList;
import com.coralblocks.coralds.list.ArrayList;
import com.coralblocks.coralds.list.IntArrayList;
import com.coralblocks.coralds.list.IntLinkedList;
import com.coralblocks.coralds.list.LinkedList;
import com.coralblocks.coralds.list.LongArrayList;
import com.coralblocks.coralds.list.LongLinkedList;
import com.coralblocks.coralds.map.ByteBufferMap;
import com.coralblocks.coralds.map.ByteMap;
import com.coralblocks.coralds.map.CharMap;
import com.coralblocks.coralds.map.CharSequenceMap;
import com.coralblocks.coralds.map.IdentityMap;
import com.coralblocks.coralds.map.IntMap;
import com.coralblocks.coralds.map.LinkedMap;
import com.coralblocks.coralds.map.LongMap;
import com.coralblocks.coralds.map.Map;
import com.coralblocks.coralds.set.IdentitySet;
import com.coralblocks.coralds.set.IntSet;
import com.coralblocks.coralds.set.LinkedSet;
import com.coralblocks.coralds.set.LongSet;
import com.coralblocks.coralds.set.Set;
import com.coralblocks.coralpool.ArrayObjectPool;
import com.coralblocks.coralstate.example.Person;
import com.coralblocks.coralstate.example.PersonCodec;

public class CoralDSStateSerializationTest {

	private StateRegistry registry;

	@Before
	public void setUp() {
		registry = new StateRegistry();
		registry.register(new PersonCodec(), new ArrayObjectPool<Person>(256, Person.class));
	}

	@Test
	public void roundTripsEveryValueBasedCoralDSDataStructure() {
		State source = new State(registry);

		ArrayLinkedList<Person> arrayLinkedList = new ArrayLinkedList<>(3);
		arrayLinkedList.addLast(person("ArrayLinkedList one", 1));
		arrayLinkedList.addLast(person("ArrayLinkedList two", 2));
		source.put("arrayLinkedList", arrayLinkedList);

		ArrayList<Person> arrayList = new ArrayList<>(2, 1.5f);
		arrayList.add(person("ArrayList one", 3));
		arrayList.add(person("ArrayList two", 4));
		source.put("arrayList", arrayList);

		IntArrayList intArrayList = new IntArrayList(3, 1.75f);
		intArrayList.add(-10);
		intArrayList.add(20);
		source.put("intArrayList", intArrayList);

		IntLinkedList intLinkedList = new IntLinkedList(4);
		intLinkedList.add(-30);
		intLinkedList.add(40);
		source.put("intLinkedList", intLinkedList);

		LinkedList<Person> linkedList = new LinkedList<>(5);
		linkedList.add(person("LinkedList one", 5));
		linkedList.add(person("LinkedList two", 6));
		source.put("linkedList", linkedList);

		LongArrayList longArrayList = new LongArrayList(6, 2f);
		longArrayList.add(-5_000_000_000L);
		longArrayList.add(6_000_000_000L);
		source.put("longArrayList", longArrayList);

		LongLinkedList longLinkedList = new LongLinkedList(7);
		longLinkedList.add(-7_000_000_000L);
		longLinkedList.add(8_000_000_000L);
		source.put("longLinkedList", longLinkedList);

		ByteBufferMap<Person> byteBufferMap = new ByteBufferMap<>(8, (short) 16, 0.5f, true);
		byteBufferMap.put(new byte[] { 1, 2, 3 }, person("ByteBufferMap one", 7));
		byteBufferMap.put(new byte[] { -1, 0 }, person("ByteBufferMap two", 8));
		source.put("byteBufferMap", byteBufferMap);

		ByteMap<Person> byteMap = new ByteMap<>();
		byteMap.put((byte) -10, person("ByteMap one", 9));
		byteMap.put((byte) 20, person("ByteMap two", 10));
		source.put("byteMap", byteMap);

		CharMap<Person> charMap = new CharMap<>();
		charMap.put('A', person("CharMap one", 11));
		charMap.put('Z', person("CharMap two", 12));
		source.put("charMap", charMap);

		CharSequenceMap<Person> charSequenceMap = new CharSequenceMap<>(9, (short) 24, 0.6f);
		charSequenceMap.put("first", person("CharSequenceMap one", 13));
		charSequenceMap.put(new StringBuilder("ação"), person("CharSequenceMap two", 14));
		source.put("charSequenceMap", charSequenceMap);

		IntMap<Person> intMap = new IntMap<>(10, 0.55f);
		intMap.put(-1, person("IntMap one", 15));
		intMap.put(2, person("IntMap two", 16));
		source.put("intMap", intMap);

		LinkedMap<Person, Person> linkedMap = new LinkedMap<>(11, 0.65f);
		linkedMap.put(person("LinkedMap key one", 17), person("LinkedMap value one", 18));
		linkedMap.put(person("LinkedMap key two", 19), person("LinkedMap value two", 20));
		source.put("linkedMap", linkedMap);

		LongMap<Person> longMap = new LongMap<>(12, 0.7f);
		longMap.put(-3_000_000_000L, person("LongMap one", 21));
		longMap.put(4_000_000_000L, person("LongMap two", 22));
		source.put("longMap", longMap);

		Map<Person, Person> map = new Map<>(13, 0.75f);
		map.put(person("Map key one", 23), person("Map value one", 24));
		map.put(person("Map key two", 25), person("Map value two", 26));
		source.put("map", map);

		IntSet intSet = new IntSet(14, 0.6f);
		intSet.add(-100);
		intSet.add(200);
		source.put("intSet", intSet);

		LinkedSet<Person> linkedSet = new LinkedSet<>(15, 0.65f);
		linkedSet.add(person("LinkedSet one", 27));
		linkedSet.add(person("LinkedSet two", 28));
		source.put("linkedSet", linkedSet);

		LongSet longSet = new LongSet(16, 0.7f);
		longSet.add(-9_000_000_000L);
		longSet.add(10_000_000_000L);
		source.put("longSet", longSet);

		Set<Person> set = new Set<>(17, 0.75f);
		set.add(person("Set one", 29));
		set.add(person("Set two", 30));
		source.put("set", set);

		State restored = roundTrip(source);

		assertEquals(source, restored);
		assertEquals(source.hashCode(), restored.hashCode());
		assertEquals(19, restored.size());
		assertConstructorConfiguration(restored);
	}

	@Test
	public void roundTripsIdentityCollectionsWithoutClaimingReferenceIdentity() {
		IdentityMap<Person, Person> identityMap = new IdentityMap<>(18, 0.55f);
		Person originalKey = person("IdentityMap key", 31);
		Person originalValue = person("IdentityMap value", 32);
		identityMap.put(originalKey, originalValue);

		IdentitySet<Person> identitySet = new IdentitySet<>(19, 0.6f);
		Person originalElement = person("IdentitySet element", 33);
		identitySet.add(originalElement);

		State source = new State(registry);
		source.put("identityMap", identityMap);
		source.put("identitySet", identitySet);
		State restored = roundTrip(source);

		assertNotEquals(source, restored);

		@SuppressWarnings("unchecked")
		IdentityMap<Person, Person> restoredMap = (IdentityMap<Person, Person>) restored.get("identityMap");
		assertEquals(18, restoredMap.getInitialCapacity());
		assertEquals(0.55f, restoredMap.getLoadFactor(), 0f);
		assertEquals(1, restoredMap.size());
		Iterator<Person> mapIterator = restoredMap.iterator();
		assertTrue(mapIterator.hasNext());
		assertEquals(originalValue, mapIterator.next());
		assertEquals(originalKey, restoredMap.getCurrIteratorKey());
		assertFalse(mapIterator.hasNext());

		@SuppressWarnings("unchecked")
		IdentitySet<Person> restoredSet = (IdentitySet<Person>) restored.get("identitySet");
		assertEquals(19, restoredSet.getInitialCapacity());
		assertEquals(0.6f, restoredSet.getLoadFactor(), 0f);
		assertEquals(1, restoredSet.size());
		Iterator<Person> setIterator = restoredSet.iterator();
		assertTrue(setIterator.hasNext());
		assertEquals(originalElement, setIterator.next());
		assertFalse(setIterator.hasNext());
	}

	@Test
	public void roundTripsRecursivelyNestedCoralDSDataStructures() {
		ArrayList<Person> people = new ArrayList<>(1, 2f);
		people.add(person("Nested one", 34));
		people.add(person("Nested two", 35));

		CharSequenceMap<ArrayList<Person>> inner = new CharSequenceMap<>(2, (short) 16, 0.5f);
		inner.put("people", people);
		CharSequenceMap<CharSequenceMap<ArrayList<Person>>> outer =
				new CharSequenceMap<>(3, (short) 16, 0.6f);
		outer.put("inner", inner);

		State source = new State(registry);
		source.put("outer", outer);
		State restored = roundTrip(source);

		assertEquals(source, restored);
		assertEquals(source.hashCode(), restored.hashCode());
	}

	@Test
	public void rejectsCyclesInMapsAndSets() {
		Map<Person, Object> cyclicMap = new Map<>();
		State mapState = new State(registry);
		mapState.put("cycle", cyclicMap);
		cyclicMap.put(person("cycle key", 36), cyclicMap);
		assertCycleRejected("Map", mapState);

		Set<Object> cyclicSet = new Set<>();
		State setState = new State(registry);
		setState.put("cycle", cyclicSet);
		cyclicSet.add(cyclicSet);
		assertCycleRejected("Set", setState);
	}

	private State roundTrip(State source) {
		int serializedLength = source.getSerializedLength();
		ByteBuffer buffer = ByteBuffer.allocate(serializedLength);
		assertEquals(serializedLength, source.writeTo(buffer));
		assertEquals(serializedLength, buffer.position());
		buffer.flip();

		State restored = new State(registry);
		assertEquals(serializedLength, restored.readFrom(buffer));
		assertEquals(0, buffer.remaining());
		return restored;
	}

	private void assertCycleRejected(String type, State state) {
		try {
			state.getSerializedLength();
			fail("Expected cyclic " + type + " to be rejected while measuring");
		} catch (IllegalStateException e) {
			assertTrue(e.getMessage().contains("Cyclic " + type));
		}

		ByteBuffer buffer = ByteBuffer.allocate(512);
		buffer.position(7);
		try {
			state.writeTo(buffer);
			fail("Expected cyclic " + type + " to be rejected while serializing");
		} catch (IllegalStateException e) {
			assertTrue(e.getMessage().contains("Cyclic " + type));
		}
		assertEquals(7, buffer.position());
	}

	private static Person person(String name, int age) {
		return new Person(name, age);
	}

	private static void assertConstructorConfiguration(State state) {
		ArrayLinkedList<?> arrayLinkedList = (ArrayLinkedList<?>) state.get("arrayLinkedList");
		assertEquals(3, arrayLinkedList.getArraySize());

		ArrayList<?> arrayList = (ArrayList<?>) state.get("arrayList");
		assertEquals(2, arrayList.getInitialCapacity());
		assertEquals(1.5f, arrayList.getGrowthFactor(), 0f);

		IntArrayList intArrayList = (IntArrayList) state.get("intArrayList");
		assertEquals(3, intArrayList.getInitialCapacity());
		assertEquals(1.75f, intArrayList.getGrowthFactor(), 0f);

		assertEquals(4, ((IntLinkedList) state.get("intLinkedList")).getInitialCapacity());
		assertEquals(5, ((LinkedList<?>) state.get("linkedList")).getInitialCapacity());

		LongArrayList longArrayList = (LongArrayList) state.get("longArrayList");
		assertEquals(6, longArrayList.getInitialCapacity());
		assertEquals(2f, longArrayList.getGrowthFactor(), 0f);
		assertEquals(7, ((LongLinkedList) state.get("longLinkedList")).getInitialCapacity());

		ByteBufferMap<?> byteBufferMap = (ByteBufferMap<?>) state.get("byteBufferMap");
		assertEquals(8, byteBufferMap.getInitialCapacity());
		assertEquals(16, byteBufferMap.getMaxKeyLength());
		assertEquals(0.5f, byteBufferMap.getLoadFactor(), 0f);
		assertTrue(byteBufferMap.isDirectBuffer());

		CharSequenceMap<?> charSequenceMap = (CharSequenceMap<?>) state.get("charSequenceMap");
		assertEquals(9, charSequenceMap.getInitialCapacity());
		assertEquals(24, charSequenceMap.getMaxKeyLength());
		assertEquals(0.6f, charSequenceMap.getLoadFactor(), 0f);

		IntMap<?> intMap = (IntMap<?>) state.get("intMap");
		assertEquals(10, intMap.getInitialCapacity());
		assertEquals(0.55f, intMap.getLoadFactor(), 0f);

		LinkedMap<?, ?> linkedMap = (LinkedMap<?, ?>) state.get("linkedMap");
		assertEquals(11, linkedMap.getInitialCapacity());
		assertEquals(0.65f, linkedMap.getLoadFactor(), 0f);

		LongMap<?> longMap = (LongMap<?>) state.get("longMap");
		assertEquals(12, longMap.getInitialCapacity());
		assertEquals(0.7f, longMap.getLoadFactor(), 0f);

		Map<?, ?> map = (Map<?, ?>) state.get("map");
		assertEquals(13, map.getInitialCapacity());
		assertEquals(0.75f, map.getLoadFactor(), 0f);

		IntSet intSet = (IntSet) state.get("intSet");
		assertEquals(14, intSet.getInitialCapacity());
		assertEquals(0.6f, intSet.getLoadFactor(), 0f);

		LinkedSet<?> linkedSet = (LinkedSet<?>) state.get("linkedSet");
		assertEquals(15, linkedSet.getInitialCapacity());
		assertEquals(0.65f, linkedSet.getLoadFactor(), 0f);

		LongSet longSet = (LongSet) state.get("longSet");
		assertEquals(16, longSet.getInitialCapacity());
		assertEquals(0.7f, longSet.getLoadFactor(), 0f);

		Set<?> set = (Set<?>) state.get("set");
		assertEquals(17, set.getInitialCapacity());
		assertEquals(0.75f, set.getLoadFactor(), 0f);
	}
}
