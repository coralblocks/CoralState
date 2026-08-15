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
}
