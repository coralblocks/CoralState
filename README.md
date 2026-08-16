# CoralState
CoralState is a lightweight, garbage-free and fast Java library for collecting objects and data structures into an in-memory state, serializing the complete state to a ByteBuffer, and restoring it later from disk, memory, or the network.

## Features

- Simple key-based API for adding, getting, checking and removing values.
- Fast binary serialization of the complete State to and from a `ByteBuffer`.
- Support for all Java primitives without boxing.
- Support for `String` and top-level `CharSequence` and `ByteBuffer` values.
- Support for nested CoralDS lists, maps and sets.
- Support for application objects through CoralProto codecs
- Validation of unsupported values before they are added or serialized.
- Designed for garbage-free, single-threaded applications.

## Example

Register the `Person` codec and pool:

```java
StateRegistry registry = new StateRegistry();
registry.register(new PersonCodec(), new ArrayObjectPool<Person>(4, Person.class));
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

Person person = (Person) restored.get("person");
ArrayList<Person> restoredPeople = (ArrayList<Person>) restored.get("people");
```

## Schema Evolution

CoralState support for schema evolution is naturally inherited from CoralProto. For example, fields can be appended to `PersonProto` while preserving compatibility with older State data.
