package mitm;

import java.io.IOException;

public class Server
{
  
  public static void main(String[] args)
  {    
    // Default values.
    int localPort = 8888;
    String localHost = "localhost";
    int timeout = 0;
    
    String info = "Initializing Utah proxy with the parameters:"
        + "\n   Local host:       " + localHost
        + "\n   Local port:       " + localPort
        + "\n   (setup could take a few seconds)";
    System.err.println(info);
    
    try
    {
      ProxyEngine ppe = new ProxyEngine(localHost, localPort, timeout);
      
      ppe.run();
    }
    catch (IOException e)
    {
      e.printStackTrace();
    }
  }
}
