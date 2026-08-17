# CoralState
CoralState is a lightweight, garbage-free and fast Java library for collecting objects and data structures into an in-memory state, serializing the complete state to a ByteBuffer, and restoring it later from disk, memory, or the network.

It uses [CoralDS](https://github.com/coralblocks/CoralDS) for fast garbage-free data-structures, [CoralProto](https://github.com/coralblocks/CoralProto) for fast garbage-free serialization and [CoralPool](https://github.com/coralblocks/CoralPool) for fast garbage-free object pooling.

## Features

- Simple key-based API for adding, getting, checking and removing values.
- Fast binary serialization of the complete State to and from a `ByteBuffer` without creating any garbage.
- Support for all Java primitives without boxing.
- Support for `String` and top-level `CharSequence` and `ByteBuffer` values.
- Support for all CoralDS data structures (maps, lists and sets).
- Support for recursively nested data structures, such as lists of maps and maps of lists.
- Support for application objects through CoralProto codecs.
- Validation of unsupported values before they are added or serialized.
- Reusable State instances.
- Support for easy schema evolution through CoralProto.
- Designed for single-threaded applications. Non-thread-safe by design.

## Example

Define a mutable object class, for example `Person`.

```java
public final class Person {

    public static final int MAX_NAME = 64;

    private final StringBuilder name = new StringBuilder(MAX_NAME);
    private int age;

    public Person() { 
        // must be defined so CoralState can create pooled instances
    }

    public Person(CharSequence name, int age) {
        setName(name);
        setAge(age);
    }

    public CharSequence getName() {
        return name;
    }

    public void setName(CharSequence name) {
        this.name.setLength(0);
        this.name.append(name);
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
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
}
```

Define its CoralProto codec:

```java
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
    }

    @Override
    public void decode(PersonProto personProto, Person person) {
        person.setName(personProto.name.get());
        person.setAge(personProto.age.get());
    }

    public static final class PersonProto extends AbstractProto {

        public static final char TYPE = 'P';
        public static final char SUBTYPE = 'R';

        public final TypeField type = new TypeField(this, TYPE);
        public final SubtypeField subtype = new SubtypeField(this, SUBTYPE);
        public final VarCharsField name = new VarCharsField(this, Person.MAX_NAME);
        public final IntField age = new IntField(this);
    }
}
```

Register the `Person` codec:

```java
StateRegistry registry = new StateRegistry();
registry.register(new PersonCodec());
```

Create a State containing one `Person` and a CoralDS list of three people:

```java
State original = new State(registry);
original.put("person", new Person("Alice", 31));

ArrayList<Person> people = new ArrayList<>();
people.add(new Person("Bob", 42));
people.add(new Person("Carol", 27));
people.add(new Person("Dave", 35));
original.put("people", people);
```

Write and restore the complete State:

```java
ByteBuffer buffer = ByteBuffer.allocate(original.getSerializedLength());
original.writeTo(buffer);
buffer.flip();

State restored = new State(registry);
restored.readFrom(buffer);

Assert.assertEquals(original, restored);

Person person = (Person) restored.get("person");
ArrayList<Person> restoredPeople = (ArrayList<Person>) restored.get("people");
```

The same State can be retained across reads:

```java
State reusable = new State(registry);
reusable.readFrom(buffer);

reusable.clear();
reusable.readFrom(nextBuffer);
```

## Schema Evolution

CoralState support for schema evolution is naturally inherited from CoralProto. For example, fields can be appended to `PersonProto` while preserving compatibility with older State data.
