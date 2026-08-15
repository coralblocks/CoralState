package com.coralblocks.coralstate.example;

public final class Person {
	
	public static final int MAX_NAME = 64;

	private StringBuilder name = new StringBuilder(MAX_NAME); // mutable string for no garbage
	private int age;
	
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

	public void setName(CharSequence name) {
		this.name.setLength(0);
		this.name.append(name);
	}
	
	public void setAge(int age) {
		this.age = age;
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof Person)) return false;

		Person other = (Person) o;
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
		return "Person{name=" + name + ", age=" + age + "}";
	}

}
