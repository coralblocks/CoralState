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
