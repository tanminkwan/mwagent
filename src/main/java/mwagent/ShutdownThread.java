package mwagent;

import static mwagent.common.Config.getConfig;

import java.util.logging.Level;

public class ShutdownThread extends Thread {

	@Override
	public void run() {
		
		getConfig().getLogger().info("Shutdown Started");
		System.out.println("Shutdown Started");
        
        if (Thread.activeCount() > 2) {
        	
            for(Thread t : Thread.getAllStackTraces().keySet()){
            	getConfig().getLogger().fine("THREAD :"+t.getName()+ " Is Deamon : "+ t.isDaemon()+ " Is Alive : " +t.isAlive());
            }

            try {        	
                Thread.sleep(15000);
            } catch (InterruptedException e) {
            	getConfig().getLogger().log(Level.WARNING, e.getMessage(), e);
            }
        }
        /*
        while (Thread.activeCount() > 2) {
        	System.out.println("Thread Count :"+Thread.activeCount());	
			logger.info("Thread Count :"+Thread.activeCount());
			Thread.getAllStackTraces().keySet().forEach((t)-> System.out.println("THREAD :"+t.getName()+ " Is Deamon : "+ t.isDaemon()+ " Is Alive : " +t.isAlive()));
    		   
 	       try {        	
 	    	   Thread.sleep(1000);
	       } catch (InterruptedException e) {
	           logger.log(Level.SEVERE, e.getMessage(), e);
	       }
        }
        */	            
		System.out.println("Shutdown End");
		getConfig().getLogger().info("Shutdown End");
	}
}
