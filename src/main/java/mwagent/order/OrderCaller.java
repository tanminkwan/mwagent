package mwagent.order;

import static mwagent.common.Config.getConfig;

import java.lang.reflect.Constructor;
import java.util.logging.Level;

import org.json.simple.JSONObject;

public final class OrderCaller {
	
	public static int executeOrder(String orderedClass, JSONObject command){
    	
    	try {

    		getConfig().getLogger().info("orderedClass : "+orderedClass);
    		//- Order order = (Order) Class.forName(orderedClass).getDeclaredConstructor().newInstance(command);
    		Class<?> order = Class.forName(orderedClass);
    		Constructor<?> orderConstructor = order.getConstructor(JSONObject.class);
    		
    		//Create Order Object & deliver a command 
    		Order orderObj = (Order)orderConstructor.newInstance(command);
    		
    		//Execute command & Make Results
    		int rtn = orderObj.execute();
    		
    		//Send results to the server (REST)
    		if(rtn>0){
    			orderObj.sendResults();
    		}else{
    			getConfig().getLogger().warning("executeOrder failed. orderedClass : "+orderedClass);
    		}
    		
    		getConfig().getLogger().info("finished OrderCaller.");
        	
    	}catch (ClassNotFoundException e) {
    		getConfig().getLogger().log(Level.WARNING, e.getMessage(), e);
    		return -2;
    	}catch (Exception e) {
    		getConfig().getLogger().log(Level.WARNING, e.getMessage(), e);    		
    		return -3;
    	}    	
    	
    	return 1;
    }

}
