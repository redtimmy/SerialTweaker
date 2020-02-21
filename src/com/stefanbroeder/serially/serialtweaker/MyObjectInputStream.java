package com.stefanbroeder.serially.serialtweaker;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectStreamClass;

public class MyObjectInputStream extends java.io.ObjectInputStream {
	
	private SerialTweaker serialTweaker;
	private Class<?> firstType;

	public MyObjectInputStream(InputStream in, SerialTweaker serialTweaker) throws IOException {
		super(in);
		this.serialTweaker = serialTweaker;
		this.firstType = null;
	}
	

	@Override
	protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
		ObjectStreamClass descriptor = super.readClassDescriptor();

	    // Nasty trick to get the depth from the number of readOrdinaryObject calls in stacktrace
	    int depth = 0;

	    for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
	    	//System.err.println(ste);
	    	if(ste.toString().contains("readOrdinaryObject")) {
	    		depth++;
	    	}
	    }
	    
	    String prefix = "";
	    for(int i=0; i < depth; i++) {
	    	prefix += "  ";
	    }
	    
	    
	    //if(depth == 0) {  // TODO dirty-fixed, was 0
	    if(this.firstType == null) {
	    	this.firstType = Class.forName(descriptor.getName());
	    }
	    
	    serialTweaker.addPreloadJar(descriptor.getName(), descriptor.getSerialVersionUID());
	    Util.DBG(prefix+"- "+descriptor.getName() + "(" + descriptor.getSerialVersionUID()+")");
	    return descriptor;
	  }
	
	@Override
	protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
		ClassLoader loader = ClassLoader.getSystemClassLoader();
		String cname = desc.getName();
		return Class.forName(cname, false, loader);
	}
	

	public Class<?> getFirstType() {
		return this.firstType;
	}
	
}
