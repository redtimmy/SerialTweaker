package com.stefanbroeder.serially.serialtweaker;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Viewstate {
	
	private String url;

	public Viewstate(String url) {
		this.url = url;
	}

	public String getViewStateFromURL() {
		String result = Util.executeGet(url);
		
		String viewState = "";
		Pattern p = Pattern.compile("ViewState\" value=\"([^\"\\s]*)");
		//TODO jsf_state is not supported at this point
	    Matcher m = p.matcher(result);
	    if(m.find()) {
	    	viewState = m.group(1);
	    	Util.printInfo("Succesfully fetched ViewState parameter from URL");
	    	Util.DBG("ViewState: "+ viewState);
	    } else {
	    	Util.terminate("No ViewState found in response from "+url);
	    }
		
		return viewState;
	}
}
