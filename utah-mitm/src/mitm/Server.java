package mitm;

import java.io.IOException;

public class Server
{
  
  public static void main(String[] args)
  {
    // Default values.
    ProxyDataFilter requestFilter = new ProxyDataFilter();
    ProxyDataFilter responseFilter = new ProxyDataFilter();
    int localPort = 8888;
    String localHost = "localhost";
    int timeout = 0;
    
    final StringBuffer startMessage = new StringBuffer();
    startMessage.append("Initializing Utah proxy with the parameters:"
        + "\n   Local host:       " + localHost
        + "\n   Local port:       " + localPort);
    startMessage.append("\n   (setup could take a few seconds)");
    System.err.println(startMessage);
    
    try
    {
      PlainProxyEngine ppe = new PlainProxyEngine(
          new PlainSocketFactory(), requestFilter, responseFilter, localHost, localPort, timeout);
      
      ppe.run();
    }
    catch (IOException e)
    {
      e.printStackTrace();
    }
  }
}
