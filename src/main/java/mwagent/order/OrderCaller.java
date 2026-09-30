package mwagent.order;

import static mwagent.common.Config.getConfig;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.logging.Level;
import java.util.regex.Pattern;

import org.json.simple.JSONObject;

import mwagent.common.LogSafe;

public final class OrderCaller {

	private static final String ORDER_PACKAGE = "mwagent.order.";

	// simple class name only: no nested package, no inner class ($), no path characters
	private static final Pattern SIMPLE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

	/**
	 * Resolves command_class (already prefixed with "mwagent.order.") to a concrete Order subclass.
	 * The class is loaded without initialization, so a rejected name never runs static code.
	 *
	 * @return the class, or null if the name is not an executable Order
	 */
	static Class<? extends Order> resolveOrderClass(String orderedClass) {
		if (orderedClass == null || !orderedClass.startsWith(ORDER_PACKAGE)) {
			return null;
		}
		if (!SIMPLE_NAME.matcher(orderedClass.substring(ORDER_PACKAGE.length())).matches()) {
			return null;
		}
		try {
			Class<?> c = Class.forName(orderedClass, false, OrderCaller.class.getClassLoader());
			if (!Order.class.isAssignableFrom(c) || Modifier.isAbstract(c.getModifiers())) {
				return null;
			}
			return c.asSubclass(Order.class);
		} catch (ClassNotFoundException e) {
			return null;
		}
	}

	public static int executeOrder(String orderedClass, JSONObject command){
    	
    	try {

    		getConfig().getLogger().info("orderedClass : "+orderedClass);
    		Class<? extends Order> order = resolveOrderClass(orderedClass);
    		if (order == null) {
    			getConfig().getLogger().warning("Unknown or not executable command_class rejected : " + LogSafe.safe(orderedClass));
    			return -2;
    		}
    		Constructor<? extends Order> orderConstructor = order.getConstructor(JSONObject.class);
    		
    		//Create Order Object & deliver a command 
    		Order orderObj = orderConstructor.newInstance(command);
    		
    		//Execute command & Make Results
    		int rtn = orderObj.execute();
    		
    		//Send results to the server (REST)
    		if(rtn>0){
    			orderObj.sendResults();
    		}else{
    			getConfig().getLogger().warning("executeOrder failed. orderedClass : "+orderedClass);
    		}
    		
    		getConfig().getLogger().info("finished OrderCaller.");
        	
    	}catch (Exception e) {
    		getConfig().getLogger().log(Level.WARNING, e.getMessage(), e);    		
    		return -3;
    	}    	
    	
    	return 1;
    }

}
