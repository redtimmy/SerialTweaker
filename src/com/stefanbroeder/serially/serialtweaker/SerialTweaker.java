package com.stefanbroeder.serially.serialtweaker;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SerialTweaker {
	
	public static final String JAVA_TOOLS_PATH = System.getProperty("user.home") + "/.serially";
	public static final String JAR_PATH = JAVA_TOOLS_PATH + "/jars";
	public static boolean prompt = false;
	public static String prompt_string = null;
	public ArrayList<JavaClass> preload_list = null;
	private ArrayList<DeserializedObject> deserialized_objects = null;
	private ArrayList<Class<?>> first_types = null;
	private static int maxDepth = 3;
	static String key = null;
	
	// HELPER FUNCTIONS
	
	private class JavaClass {
		public String name;
		public String serialVersionUID;
	}
	
	private class JavaJar {
		public int id;
		public String name;
	}
	
	private static void printUsage() {
		System.out.println(
				"-----------------\n" +
                "Serially - v1.1\n" +
                "by Stefan Broeder\n" +
                "-----------------\n" +
				"Usage:\n" + 
				"\n" + 
				"SerialTweaker -b base64_encoded_java_object [OPTIONS]\n" + 
				"SerialTweaker -v url_to_get_viewstate_from [OPTIONS]\n" + 
				"\n" + 
				"OPTIONS:\n" + 
				"-k      DES key to decrypt the object. Format: Base64\n" +
				"-d      Maximum depth (to prevent from printing deeply nested objects). Default: 3. To disable, set 0.\n");
	}
	
	
	private String getSerialVersionUIDFromError(String message) {
		String serialVersionUID = null;
		Pattern p = Pattern.compile("stream classdesc serialVersionUID = ([-]?\\d+)");
	    Matcher m = p.matcher(message);
	    if(m.find()) {
	    	serialVersionUID = m.group(1);
	    }
		return serialVersionUID;
	}
	
	// MAIN FUNCTIONS
	
	public static void main(String[] args) {
		byte[] serObj = parseArguments(args);
		new SerialTweaker(serObj);
	}


	public SerialTweaker(byte[] serObj) {
		this.preload_list = new ArrayList<>();
		this.deserialized_objects = new ArrayList<>();
		this.first_types = new ArrayList<>();
		
		deserialize(serObj);
		parseDeserializedObjects();
		reserialize();
	}

	private static byte[] parseArguments(String[] args) {
		if(args.length < 2) {
			printUsage();
			System.exit(1);
		}
		
		String serObjB64 = null;
		
		// Read mandatory serial object in args 0 and 1
		switch(args[0]) {
			case "-b" :
				serObjB64 = args[1];
				break;
			case "-v" :
				String url = args[1];
				Viewstate v = new Viewstate(url);
				serObjB64 = v.getViewStateFromURL();
				break;
		}
		
		byte[] serObj = Base64.getDecoder().decode(serObjB64);
		
		// Parse options
		for(int pos = 2; pos < args.length; pos += 2) {
			switch(args[pos]) {
				case "-k":
					key = args[pos+1];
					Util.printInfo("Decrypting serialized object with key: "+key);
					serObj = Util.decryptDES(key, serObj);
					break;
				case "-p":
					prompt = true;
					if(args.length > pos+1 && args[pos+1].charAt(0) != '-') {
						prompt_string = args[pos+1];
					}
					break;
				case "-d":
					maxDepth = Integer.parseInt(args[pos+1]);
					if(maxDepth == 0) {
						maxDepth = Integer.MAX_VALUE;
					}
					break;
			}
		}

		return serObj;	
	}
	
	private void deserialize(byte[] serObj) {
		Util.printHeader("Starting analysis of serialized object");
		
		// First run, preload classes that are described in serialized object
		readClassDescriptors(serObj);
		
		// Dynamically load the found classes	
		loadPreloadList();
		
		// Deserialize full object
		Util.printHeader("Starting deserialization of object");
		
		
		while(true) {
			try {
				ByteArrayInputStream bis = new ByteArrayInputStream(serObj);
				ObjectInputStream ois = new ObjectInputStream(bis);
				
				// In one serialized stream can be multiple serialized objects. 
				// Deserialize them one by one
	
				int obj_nr = 0;
				while(true) {
					try {
						Object o = ois.readObject();
						
						DeserializedObject desObj = new DeserializedObject(o, this.first_types.get(obj_nr));
						deserialized_objects.add(desObj);
						obj_nr++;
					} catch (EOFException e) {
						// No more objects to deserialize
						return;
					} finally {
						ois.close();
						bis.close();
					}
				}
			} catch (InvalidClassException e) {
				//e.printStackTrace();
				String serialVersionUID = getSerialVersionUIDFromError(e.getMessage());
				tryToLoadDynamically(e.classname, serialVersionUID, true);
			} catch (java.lang.ClassNotFoundException e) {
				//e.printStackTrace();
				String classname = e.getMessage();
				tryToLoadDynamically(classname, null, true);
			} catch(java.lang.NoClassDefFoundError e) {
				//e.printStackTrace();
				//TODO Need to restart the full java program after a NoClassDefFoundError, because readObject() doesn't see the preloaded class..
				String classname = e.getMessage().replace('/', '.');
				tryToLoadDynamically(classname, null, true);
			} catch (Exception e) {
				e.printStackTrace();
				Util.terminate("Unable to deserialize object, exiting");
			}
		}
	}


	private void readClassDescriptors(byte[] serObj) {
		Util.DBG("Classes mentioned directly in the serialized object: ");
		MyObjectInputStream mois = null;
		try {
			ByteArrayInputStream bis = new ByteArrayInputStream(serObj);
			mois = new MyObjectInputStream(bis, this);
		} catch(Exception e) {
			Util.terminate("Unable to deserialize object, exiting");
		}

		while(true) {
			try {
				mois.readObject();
			} catch(Exception e) {
				// All class descriptors read at this point
				try {
					mois.close();
				} catch (IOException e2) {
					e2.printStackTrace();
				}
				return;
			} finally {
				this.first_types.add(mois.getFirstType());
			}
		}
	}

	private void loadPreloadList() {
		Util.printInfo("Preloading libraries:");
		for(JavaClass jc : preload_list) {
			tryToLoadDynamically(jc.name, jc.serialVersionUID, false);
		}
	}

	private void tryToLoadDynamically(String classname, String serialVersionUID, boolean terminate) {
		String error_string = classname;
		if(serialVersionUID != null) {
			error_string += "("+serialVersionUID+")";
		}
		
		String jar_name = findJarName(classname, serialVersionUID);
		if(jar_name == null) {
			if(terminate) {
				Util.terminate("Unable to find "+error_string+" in database, please add the proper jar file.\n"
						+ "Try to find a jar file containing the class via https://search.maven.org/search?q=fc:"+classname);
			}
		} else {
			if(!loadJar(jar_name) && terminate) {
				Util.terminate("Could not dynamically load "+jar_name+", exiting");
			}
		}

	}

	private boolean loadJar(String jar_name) {
		String path = JAR_PATH + "/" + jar_name;
		File file = new File(path);
		return loadLibrary(file);
	}
	
    private synchronized boolean loadLibrary(java.io.File jar) {
        try {
            java.net.URLClassLoader loader = (java.net.URLClassLoader)ClassLoader.getSystemClassLoader();
            java.net.URL url = jar.toURI().toURL();
            for (java.net.URL it : java.util.Arrays.asList(loader.getURLs())){
                if (it.equals(url)){
                	System.out.println("- "+jar.getName()+" contains the class but is already loaded");
                    return true;
                }
            }
            java.lang.reflect.Method method = java.net.URLClassLoader.class.getDeclaredMethod("addURL", new Class[]{java.net.URL.class});
            method.setAccessible(true);
            method.invoke(loader, new Object[]{url});
            System.out.println("- " + jar.getName() + " is loaded succesfully");
        } catch (final java.lang.NoSuchMethodException | 
            java.lang.IllegalAccessException | 
            java.net.MalformedURLException | 
            java.lang.reflect.InvocationTargetException e){
        	Util.terminate("Couldn't dynamically load jar file: "+ e.getMessage());
        }
		return true;
    }
	
	private String findJarName(String classname, String serialVersionUID) {
		Connection connection = null;
		String path = JAVA_TOOLS_PATH + "/java.sqlite";
		ArrayList<JavaJar> jars = new ArrayList<>();
		
        try {
        	Class.forName("org.sqlite.JDBC");		// This line is required to load the library
        	connection = DriverManager.getConnection("jdbc:sqlite:" + path);
        	String query = "SELECT filename FROM jar WHERE rowid IN (SELECT jarID FROM class WHERE fqdn = ?";
        	PreparedStatement statement = null;
        	
        	if(serialVersionUID == null) {
        		statement = connection.prepareStatement(query + ")");
        		statement.setString(1, classname);
        	} else {
        		query += "AND serialVersionUID = ?";
        		statement = connection.prepareStatement(query + ")");
        		statement.setString(1, classname);
        		statement.setString(2, serialVersionUID);
        	}
          
        	ResultSet rs = statement.executeQuery();
        	while(rs.next()) {
        		JavaJar jar = new JavaJar();
        		jar.name = rs.getString("filename");
        		jars.add(jar);
        	}
        	if(jars.size() > 1) {
        		if(prompt) {
        			Util.printInfo("The class \"" + classname + "\" has been found in multiple jar files:");
        			return handlePrompt(jars);
        		} else {
        			return jars.get(0).name;
        		}
        	} else if(jars.size() == 1) {
        		return jars.get(0).name;
        	} else {
        		return null;
        	}
        } catch(SQLException | ClassNotFoundException e) {
          System.err.println(e.getMessage());
        } finally {
          try {
        	  if(connection != null) connection.close();
          } catch(SQLException e) {
            System.err.println(e.getMessage());
          }
        }
		return null;
	}
	
	private String handlePrompt(ArrayList<JavaJar> jars) {
		for(int i = 0; i < jars.size(); i++) {
			System.out.println("["+i+"] "+jars.get(i).name);
		}
		
		int chosen_id = 0;
		do {
			chosen_id = Util.readIntFromStdin("Enter the id of the jar you would like to load: ");
		} while(chosen_id < jars.size());
		return jars.get(chosen_id).name;
	}

	public void addPreloadJar(String name, long serialVersionUID) {
		JavaClass jc = new JavaClass();
		jc.name = name;
		jc.serialVersionUID = Long.toString(serialVersionUID);
		this.preload_list.add(jc);
	}

	private void parseDeserializedObjects() {
		System.out.printf("The stream contains %d objects\n", deserialized_objects.size());
		for(int index = 0; index < deserialized_objects.size(); index++) {
			System.out.printf("Showing object %d\n", index+1);
			DeserializedObject desObj = deserialized_objects.get(index);

			ObjectModifier om = new ObjectModifier(desObj, maxDepth);
			om.showFields();
			 
			while(Util.readStringFromStdin("\nWould you like to modify a field in this object? [y/n]: ").equals("y")) {
				Object newValue = null;
				int id = Util.readIntFromStdin("Enter the ID of the field you would like to modify: ");
				Class<?> modifiableFieldType = om.getModifiableFieldType(id);
				if(modifiableFieldType == null) { // Object array element
					Util.printInfo("Modifying element of Object[]");
					String newType = Util.readStringFromStdin("Enter the type for the element [java.lang.String]: ");
					newValue = Util.readStringFromStdin("Enter the new value for the field. Leave blank for new object: ");
					
					if(newType.equals("")) {
						newType = "java.lang.String";
					}

					try {
						if(newValue.equals("")) {
							newValue = Class.forName(newType).getConstructor().newInstance();
						} else {
							newValue = Class.forName(newType).getConstructor(Class.forName("java.lang.String")).newInstance(newValue);
						}
						
						newValue = Class.forName(newType).cast(newValue);
					} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
							| InvocationTargetException | NoSuchMethodException | SecurityException
							| ClassNotFoundException e) {
						e.printStackTrace();
					}
					
				} else {
					String typeName = modifiableFieldType.getSimpleName();
					Util.printInfo("Modifying field of type " + typeName);
					if(modifiableFieldType.getSimpleName().equals("String")) {
						newValue = Util.readStringFromStdin("Enter the new value for the field: ");
					} else if (modifiableFieldType.isPrimitive()) {
						switch(typeName) {
						case "byte":
							String byteValueStr = Util.readStringFromStdin("Enter byte value in decimal: ");
							newValue = Byte.parseByte(byteValueStr);
							break;
						case "int":
							String intValueStr = Util.readStringFromStdin("Enter integer value: ");
							newValue = Integer.valueOf(intValueStr);
							break;
						case "char":
							String charValueStr = Util.readStringFromStdin("Enter character value: ");
							newValue = charValueStr.charAt(0);
							break;
						case "boolean":
							String bValueStr = Util.readStringFromStdin("Enter T or F: ");
							newValue = bValueStr.charAt(0) == 'T';		
							break;
						case "float":
						case "double":
							String doubleValueStr = Util.readStringFromStdin("Enter double value: ");
							newValue = Double.parseDouble(doubleValueStr);
							break;
						}
					} else if (modifiableFieldType.isArray()) {
						int newLength = Util.readIntFromStdin("Enter the length of the new array: ");
						switch(typeName.substring(0, typeName.length()-2)) {
						case "byte":
							byte[] byteArr = new byte[newLength];
							for(int i=0; i < newLength; i++) {
								String byteValueStr = Util.readStringFromStdin("Enter byte value for index " + i + " in decimal: ");
								byteArr[i] = Byte.parseByte(byteValueStr);
							}
							newValue = byteArr;
							break;
						case "int":
							int[] intArr = new int[newLength];
							for(int i=0; i < newLength; i++) {
								String intValueStr = Util.readStringFromStdin("Enter integer value for index " + i + " : ");
								intArr[i] = Integer.valueOf(intValueStr);
							}
							newValue = intArr;
							break;
						case "char":
							char[] cArr = new char[newLength];
							for(int i=0; i < newLength; i++) {
								String charValueStr = Util.readStringFromStdin("Enter character for index " + i + " : ");
								cArr[i] = charValueStr.charAt(0);
							}
							newValue = cArr;
							break;
						case "boolean":
							boolean[] bArr = new boolean[newLength];
							for(int i=0; i < newLength; i++) {
								String bValueStr = Util.readStringFromStdin("Enter T or F for index " + i + " : ");
								bArr[i] = bValueStr.charAt(0) == 'T';							
							}
							newValue = bArr;
							break;
						case "float":
						case "double":
							double[] dArr = new double[newLength];
							for(int i=0; i < newLength; i++) {
								String doubleValueStr = Util.readStringFromStdin("Enter double value for index " + i + " : ");
								dArr[i] = Double.parseDouble(doubleValueStr);
							}
							newValue = dArr;
							break;
						case "Object":
							Object[] objArr = new Object[newLength];
							for(int i=0; i < newLength; i++) {
								String classStr = Util.readStringFromStdin("Enter class name for Object on index " + i + " : ");
								try {
									objArr[i] = Class.forName(classStr).getDeclaredConstructor().newInstance();
								} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
										| InvocationTargetException | NoSuchMethodException | SecurityException
										| ClassNotFoundException e) {
									Util.printError("Unable to create new instance of " + classStr);
									i--;
								}
							}
							newValue = objArr;
						}
						
					} else {
						if(Util.readStringFromStdin("Do you want to reinitialize this field to a new instance? [y/n]: ").equals("y")) {
							try {
								newValue = modifiableFieldType.getConstructor().newInstance();
							} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
									| InvocationTargetException | NoSuchMethodException | SecurityException e) {
								Util.printError("Creating new instance failed or impossible");
							}
						}
					}
				}
		
				om.modifyField(id, newValue);
				
				om.reset();
				Util.readStringFromStdin("Press enter to continue..");
				om.showFields();
			}
			
			// Replace old deserialized_object with new one
			this.deserialized_objects.set(index, om.getDeserializedObject());
		}
	}

	private void reserialize() {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try { 
			ObjectOutputStream oos = new ObjectOutputStream(bos);
			for(DeserializedObject desObj : deserialized_objects) {
				oos.writeObject(desObj.o);
			}
			oos.flush();
			oos.close();
		} catch (IOException e) {
			Util.terminate("There was an issue with reserialization of the object");
		}
		
		byte[] reserialized_object  = bos.toByteArray();
		
		if(key != null) {
			reserialized_object = Util.encryptDES(key, reserialized_object);
		}
		
		Util.printHeader("Reserialized object: ");
		String reserialized_object_b64 = Base64.getEncoder().encodeToString(reserialized_object);
		System.out.println(reserialized_object_b64);
	}
}
