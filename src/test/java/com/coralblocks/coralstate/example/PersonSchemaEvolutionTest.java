package com.coralblocks.coralstate.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.coralblocks.coralds.list.ArrayList;
import com.coralblocks.coralpool.ArrayObjectPool;
import com.coralblocks.coralpool.ObjectPool;
import com.coralblocks.coralproto.field.ProtoField;
import com.coralblocks.coralstate.State;
import com.coralblocks.coralstate.StateCodec;
import com.coralblocks.coralstate.StateRegistry;

public class PersonSchemaEvolutionTest {

	private static final String PERSON_CLASS_NAME = Person.class.getName();
	private static final String PERSON_CODEC_CLASS_NAME = PersonCodec.class.getName();

	private static final String EVOLVED_PERSON_SOURCE = """
			package com.coralblocks.coralstate.example;

			public final class Person {

				public static final int MAX_NAME = 64;

				private StringBuilder name = new StringBuilder(MAX_NAME);
				private int age;
				private String city;

				public Person() {

				}

				public Person(CharSequence name, int age) {
					this.name.append(name);
					this.age = age;
				}

				public CharSequence getName() {
					return name;
				}

				public int getAge() {
					return age;
				}

				public String getCity() {
					return city;
				}

				public void setName(CharSequence name) {
					this.name.setLength(0);
					this.name.append(name);
				}

				public void setAge(int age) {
					this.age = age;
				}

				public void setCity(String city) {
					this.city = city;
				}

				@Override
				public boolean equals(Object o) {
					if (o == this) return true;
					if (!(o instanceof Person other)) return false;
					return age == other.age && CharSequence.compare(name, other.name) == 0;
				}

				@Override
				public int hashCode() {
					int hash = 0;
					for (int i = 0; i < name.length(); i++) {
						hash = 31 * hash + name.charAt(i);
					}
					return 31 * hash + age;
				}

				@Override
				public String toString() {
					return "Person{name=" + name + ", age=" + age + ", city=" + city + "}";
				}
			}
			""";

	private static final String EVOLVED_PERSON_CODEC_SOURCE = """
			package com.coralblocks.coralstate.example;

			import com.coralblocks.coralproto.AbstractProto;
			import com.coralblocks.coralproto.field.IntField;
			import com.coralblocks.coralproto.field.SubtypeField;
			import com.coralblocks.coralproto.field.TypeField;
			import com.coralblocks.coralproto.field.VarCharsField;
			import com.coralblocks.coralstate.StateCodec;

			public final class PersonCodec implements StateCodec<Person, PersonCodec.PersonProto> {

				private final PersonProto personProto = new PersonProto();

				@Override
				public Class<Person> javaType() {
					return Person.class;
				}

				@Override
				public PersonProto getProto() {
					return personProto;
				}

				@Override
				public void encode(Person person, PersonProto personProto) {
					personProto.name.set(person.getName());
					personProto.age.set(person.getAge());
					if (person.getCity() == null) {
						personProto.city.clear();
					} else {
						personProto.city.set(person.getCity());
					}
				}

				@Override
				public void decode(PersonProto personProto, Person person) {
					person.setName(personProto.name.get());
					person.setAge(personProto.age.get());
					CharSequence city = personProto.city.get();
					person.setCity(city.length() == 0 ? null : city.toString());
				}

				public static final class PersonProto extends AbstractProto {

					public static final char TYPE = 'P';
					public static final char SUBTYPE = 'R';

					public final TypeField type = new TypeField(this, TYPE);
					public final SubtypeField subtype = new SubtypeField(this, SUBTYPE);

					public final VarCharsField name = new VarCharsField(this, Person.MAX_NAME);
					public final IntField age = new IntField(this);
					public final VarCharsField city = new VarCharsField(this, Person.MAX_NAME);
				}
			}
			""";

	@ClassRule
	public static final TemporaryFolder COMPILED_SCHEMA = new TemporaryFolder();

	private static EvolvedSchemaClassLoader evolvedClassLoader;
	private static Class<?> evolvedPersonClass;
	private static Class<?> evolvedPersonCodecClass;

	@BeforeClass
	public static void compileEvolvedSchema() throws Exception {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		assertNotNull("These tests must run on a JDK so JavaCompiler is available", compiler);

		Path sourceRoot = COMPILED_SCHEMA.newFolder("src").toPath();
		Path packageDirectory = sourceRoot.resolve("com/coralblocks/coralstate/example");
		Files.createDirectories(packageDirectory);

		Path personSource = packageDirectory.resolve("Person.java");
		Path codecSource = packageDirectory.resolve("PersonCodec.java");
		Files.writeString(personSource, EVOLVED_PERSON_SOURCE, StandardCharsets.UTF_8);
		Files.writeString(codecSource, EVOLVED_PERSON_CODEC_SOURCE, StandardCharsets.UTF_8);

		Path classesDirectory = COMPILED_SCHEMA.newFolder("classes").toPath();
		ByteArrayOutputStream compilerOutput = new ByteArrayOutputStream();
		int result = compiler.run(null, compilerOutput, compilerOutput,
				"-classpath", System.getProperty("java.class.path"),
				"-d", classesDirectory.toString(),
				personSource.toString(), codecSource.toString());
		assertEquals(compilerOutput.toString(StandardCharsets.UTF_8), 0, result);

		evolvedClassLoader = new EvolvedSchemaClassLoader(
				new URL[] { classesDirectory.toUri().toURL() }, Person.class.getClassLoader());
		evolvedPersonClass = evolvedClassLoader.loadClass(PERSON_CLASS_NAME);
		evolvedPersonCodecClass = evolvedClassLoader.loadClass(PERSON_CODEC_CLASS_NAME);

		assertEquals(Person.class.getName(), evolvedPersonClass.getName());
		assertNotSame(Person.class, evolvedPersonClass);
		assertNotSame(Person.class.getClassLoader(), evolvedPersonClass.getClassLoader());

		StateCodec<?, ?> evolvedCodec = (StateCodec<?, ?>) evolvedPersonCodecClass
				.getDeclaredConstructor().newInstance();
		Object evolvedProto = evolvedCodec.getProto();
		ProtoField cityField = (ProtoField) evolvedProto.getClass().getField("city").get(evolvedProto);
		assertFalse("The appended city field must not be optional", cityField.isOptional());
	}

	@AfterClass
	public static void closeEvolvedSchemaClassLoader() throws IOException {
		if (evolvedClassLoader != null) evolvedClassLoader.close();
	}

	@Test
	public void evolvedSchemaReadsOldPersonAndPeopleWithNullCities() throws Exception {
		State oldState = createOldState();
		State restored = deserialize(serialize(oldState), createEvolvedRegistry());

		assertEvolvedPerson(restored.get("person"), "Alice", 31, null);
		assertEvolvedPeople(restored.get("people"), null, null, null);
	}

	@Test
	public void oldSchemaReadsEvolvedPersonAndPeopleAndIgnoresCities() throws Exception {
		State evolvedState = createEvolvedState();
		ByteBuffer serialized = serialize(evolvedState);

		State evolvedCopy = deserialize(serialized.duplicate(), createEvolvedRegistry());
		assertEvolvedPerson(evolvedCopy.get("person"), "Alice", 31, "Austin");
		assertEvolvedPeople(evolvedCopy.get("people"), "Boston", "Chicago", "Denver");

		State restored = deserialize(serialized, createOldRegistry());

		assertOldPerson(restored.get("person"), "Alice", 31);
		assertOldPeople(restored.get("people"));
	}

	private static State createOldState() {
		State state = new State(createOldRegistry());
		state.put("person", new Person("Alice", 31));

		ArrayList<Person> people = new ArrayList<>(3, 2f);
		people.add(new Person("Bob", 42));
		people.add(new Person("Carol", 27));
		people.add(new Person("Dave", 35));
		state.put("people", people);
		return state;
	}

	private static State createEvolvedState() throws Exception {
		State state = new State(createEvolvedRegistry());
		state.put("person", newEvolvedPerson("Alice", 31, "Austin"));

		ArrayList<Object> people = new ArrayList<>(3, 2f);
		people.add(newEvolvedPerson("Bob", 42, "Boston"));
		people.add(newEvolvedPerson("Carol", 27, "Chicago"));
		people.add(newEvolvedPerson("Dave", 35, "Denver"));
		state.put("people", people);
		return state;
	}

	private static StateRegistry createOldRegistry() {
		return new StateRegistry().register(
				new PersonCodec(), new ArrayObjectPool<Person>(4, Person.class));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static StateRegistry createEvolvedRegistry() throws Exception {
		StateCodec codec = (StateCodec) evolvedPersonCodecClass.getDeclaredConstructor().newInstance();
		ObjectPool pool = new ArrayObjectPool(4, evolvedPersonClass);
		return new StateRegistry().register(codec, pool);
	}

	private static Object newEvolvedPerson(String name, int age, String city) throws Exception {
		Object person = evolvedPersonClass.getDeclaredConstructor(CharSequence.class, int.class)
				.newInstance(name, age);
		evolvedPersonClass.getMethod("setCity", String.class).invoke(person, city);
		return person;
	}

	private static ByteBuffer serialize(State state) {
		ByteBuffer buffer = ByteBuffer.allocate(state.getSerializedLength());
		assertEquals(buffer.capacity(), state.writeTo(buffer));
		buffer.flip();
		return buffer;
	}

	private static State deserialize(ByteBuffer buffer, StateRegistry registry) {
		int serializedLength = buffer.remaining();
		State state = new State(registry);
		assertEquals(serializedLength, state.readFrom(buffer));
		assertFalse(buffer.hasRemaining());
		return state;
	}

	private static void assertEvolvedPeople(Object value, String bobCity, String carolCity,
			String daveCity) throws Exception {
		assertSame(ArrayList.class, value.getClass());
		ArrayList<?> people = (ArrayList<?>) value;
		assertEquals(3, people.size());
		assertEvolvedPerson(people.get(0), "Bob", 42, bobCity);
		assertEvolvedPerson(people.get(1), "Carol", 27, carolCity);
		assertEvolvedPerson(people.get(2), "Dave", 35, daveCity);
	}

	private static void assertOldPeople(Object value) {
		assertSame(ArrayList.class, value.getClass());
		ArrayList<?> people = (ArrayList<?>) value;
		assertEquals(3, people.size());
		assertOldPerson(people.get(0), "Bob", 42);
		assertOldPerson(people.get(1), "Carol", 27);
		assertOldPerson(people.get(2), "Dave", 35);
	}

	private static void assertEvolvedPerson(Object value, String name, int age, String city)
			throws Exception {
		assertSame(evolvedPersonClass, value.getClass());
		assertEquals(name, value.getClass().getMethod("getName").invoke(value).toString());
		assertEquals(age, value.getClass().getMethod("getAge").invoke(value));
		assertEquals(city, value.getClass().getMethod("getCity").invoke(value));
	}

	private static void assertOldPerson(Object value, String name, int age) {
		assertSame(Person.class, value.getClass());
		Person person = (Person) value;
		assertEquals(name, person.getName().toString());
		assertEquals(age, person.getAge());
	}

	private static final class EvolvedSchemaClassLoader extends URLClassLoader {

		private EvolvedSchemaClassLoader(URL[] urls, ClassLoader parent) {
			super(urls, parent);
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			if (isEvolvedSchemaClass(name)) {
				Class<?> type = findLoadedClass(name);
				if (type == null) type = findClass(name);
				if (resolve) resolveClass(type);
				return type;
			}
			return super.loadClass(name, resolve);
		}

		private static boolean isEvolvedSchemaClass(String name) {
			return name.equals(PERSON_CLASS_NAME)
					|| name.equals(PERSON_CODEC_CLASS_NAME)
					|| name.startsWith(PERSON_CODEC_CLASS_NAME + '$');
		}
	}
}
