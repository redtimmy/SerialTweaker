package com.stefanbroeder.serially.serialtweaker;

public class DeserializedObject {
	public Object o;
	public Class<?> type;
	
	public DeserializedObject(Object o, Class<?> type) {
		this.o = o;
		this.type = type;
	}
}