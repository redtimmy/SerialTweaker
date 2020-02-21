package com.stefanbroeder.serially.serialtweaker;

/* NOTES
 * 
 * For classes that add fields to the serialized object via writeObject() implementation, 
 * the program will not show these fields because it only prints those which are non-static / non-transient 
 * 
 */

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

public class ObjectModifier {
	
	private ArrayList<Modifiable> modifiables;
	private DeserializedObject desObj;
	private ArrayList<String> breadcrumbs;	
	private int id = 0;
	private int maxDepth;

	
	private class Modifiable {
		public int id;
	}
	
	private class ModifiableField extends Modifiable {
		public Field field;
		public Object object;
		
		public ModifiableField(int id, Field field, Object object) {
			this.id = id;
			this.field = field;
			this.object = object;
		}
	}
	
	private class ModifiableArrayElement extends Modifiable {
		public Object[] objArr;
		public int index;
		
		public ModifiableArrayElement(int id, Object[] objArr, int index) {
			this.id = id;
			this.objArr = objArr;
			this.index = index;
		}
	}
	
	public ObjectModifier(DeserializedObject desObj, int maxDepth) {
		this.modifiables = new ArrayList<Modifiable>();
		this.desObj = desObj;
		this.breadcrumbs = new ArrayList<String>();
		this.maxDepth = maxDepth;
	}

	public void showFields() {
		showFields(desObj);
	}

	public void modifyField(int id, Object newValue) {
		Modifiable m = modifiables.get(id);
		
		if(m instanceof ModifiableField) {
			modifyModifiableField((ModifiableField) m, newValue);
		} else if(m instanceof ModifiableArrayElement) {
			ModifiableArrayElement mae = (ModifiableArrayElement) m;
			mae.objArr[mae.index] = newValue;
		}
	}
	
	public void modifyModifiableField(ModifiableField mf, Object newValue) {
		mf.field.setAccessible(true);
			
		try {
			mf.field.set(mf.object, newValue);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
		}
	}
	
	public Class<?> getModifiableFieldType(int id) {
		Modifiable m = this.modifiables.get(id);
		if(m instanceof ModifiableField) {
			return ((ModifiableField) m).field.getType();
		} else {
			return null;
		}
	}

	private void showFields(DeserializedObject desObj) {
		// At the root, each deserialized object is either an Object or an Object[]
		if(isObjectArray(desObj)) {
			recurseObjectArray(desObj);
		} else {
			saveBreadcrumb(desObj.type.getSimpleName());
			printBreadcrumbs();
			if(this.breadcrumbs.size() <= this.maxDepth) {
				printVariables(desObj);
			}
			popBreadcrumb();
		}
	}
	


	private void printBreadcrumbs() {
		printInfo(getBreadcrumbsString(), true);		
	}

	private String getBreadcrumbsString() {
		StringBuilder bcs = new StringBuilder();
		for(int i = 0 ; i < breadcrumbs.size(); i++) {
			bcs.append(breadcrumbs.get(i));
			if(i < breadcrumbs.size() - 1) {
				bcs.append(" > ");
			}
		}
		
		return bcs.toString();
	}

	private void printVariables(DeserializedObject desObj) {
		boolean fieldsFound = false;
		for(Field field : desObj.type.getDeclaredFields()) {
			fieldsFound = true;
			field.setAccessible(true); // Set to public

			int id = saveModifiableField(field, desObj.o);

			Object value = getFieldValue(desObj, field);
			String fieldString = createFieldString(field, value);
			printField(id, fieldString);

			if(valueIsObject(field, value)) {
				DeserializedObject valueAsObject = new DeserializedObject(value, value.getClass());
				showFields(valueAsObject);
			}
		}
		if(!fieldsFound) {
			printInfo("No serializable fields in object", false);
		}
	}
	
	private int getNextID() {
		return id++;
	}

	private int saveModifiableField(Field field, Object object) {
		int id = getNextID(); 
		ModifiableField mf = new ModifiableField(id, field, object);
		modifiables.add(mf);
		return id;
	}
	
	private int saveModifiableArrayElement(Object[] objArr, int index) {
		int id = getNextID();
		ModifiableArrayElement mae = new ModifiableArrayElement(id, objArr, index); 
		modifiables.add(mae);
		return id;
	}

	private boolean valueIsObject(Field field, Object value) {
		return (value != null &&
				field.getType() instanceof Object &&
				!(field.getType().getSimpleName().equals("String")) &&
				!(field.getType().isPrimitive()));
	}

	private String createFieldString(Field field, Object value) {
		String output = field.getName();
		String modifier_string = Modifier.toString(field.getModifiers())+" ";
		String type_string = field.getType().getSimpleName()+" ";
		if(value != null) {
			if(field.getType().getSimpleName().equals("String")) {
				value = "\""+value+"\"";
			}
			output += " = "+value;
		} else {
			output += " = null";
		}
		
		if(fieldNotSerialized(field.getModifiers())) {
			output += " (field not serialized by default)";
		}
		return modifier_string + type_string + output;
	}

	private Object getFieldValue(DeserializedObject desObj, Field field) {
		Object value = null;
		try {
			value = field.get(desObj.o);
		} catch (Exception e) {
			e.printStackTrace();
			Util.terminate("Couldn't obtain value of field");
		}
		return value;
	}

	private boolean fieldNotSerialized(int modifiers) {
		// Transient and static fields are not saved in serialized object
		return Modifier.isTransient(modifiers) ||
		Modifier.isStatic(modifiers);
	}

	private void recurseObjectArray(DeserializedObject desObj) {
		// Iterate elements of Object[], recurse to show fields if element not null
		Object[] objArr = (Object[]) desObj.o;
		String type_name = getClassNameWithoutBrackets(desObj); 
		
		for(int index = 0; index < objArr.length; index++) {
			Object c = objArr[index];
			saveBreadcrumb(type_name+"["+index+"/"+(objArr.length-1)+"]");
			printBreadcrumbs();
			if(c == null) {
				int id = saveModifiableArrayElement(objArr, index);
				printField(id, "Null");
			} else if(c instanceof String) {
				int id = saveModifiableArrayElement(objArr, index);
				printField(id, "\"" + c.toString()+ "\"");
			} else {
				DeserializedObject do2 = new DeserializedObject(c, c.getClass());
				showFields(do2);
			}
			popBreadcrumb();
		}
		
	}

	private void popBreadcrumb() {
		breadcrumbs.remove(breadcrumbs.size()-1);
	}

	private void saveBreadcrumb(String string) {
		this.breadcrumbs.add(string);
	}

	private String getClassNameWithoutBrackets(DeserializedObject desObj) {
		return desObj.type.getSimpleName().substring(0, desObj.type.getSimpleName().length()-2);
	}

	private boolean isObjectArray(DeserializedObject desObj) {
		return desObj.type.getName().startsWith("[Ljava.lang.Object");
	}

	private String getIndentation(int depth) {
		String indent = "";
		for(int i = 0; i < depth - 1; i++) {
			indent += "  ";
		}
		return indent;
	}
	
	private void printInfo(String message, boolean newline) {
		int depth = this.breadcrumbs.size();
		String indent = getIndentation(depth);
		System.out.println((newline?"\n":"") + indent + message);
	}
	
	private void printField(int id, String message) {
		int depth = this.breadcrumbs.size();
		String indent = getIndentation(depth);
		String id_string = "";
		
		if(id > -1) {
			id_string = Integer.toString(id);
			indent.substring(0, Math.max(0, indent.length()-id_string.length()));
		}
		
		System.out.println(indent + id_string + " " + message);
	}
	public DeserializedObject getDeserializedObject() {
		return desObj;
	}

	public void reset() {
		modifiables.clear();
		this.id = 0;
	}

}