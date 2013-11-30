package mitm;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlainProxyEngine extends AbstractProxyEngine
{
  private final Pattern m_httpConnectPattern;

  public PlainProxyEngine(
      ISocketFactory socketFactory,
      ProxyDataFilter requestFilter, 
      ProxyDataFilter responseFilter, 
      String localHost,
      int localPort,
      int timeout) throws IOException
  {
    super(
        socketFactory, 
        requestFilter, 
        responseFilter, 
        new ConnectionDetails(localHost, localPort, "", -1, false), 
        timeout);
    
    m_httpConnectPattern =  Pattern.compile("^([A-Z]+)[ \\t]+http://([^/:]+):?(\\d*)/.*\r\n\r\n", Pattern.DOTALL);
  }

  @Override
  public void run()
  {
    final byte[] buffer = new byte[40960];
    
    while (true)
    {
      try
      {
        final Socket localSocket = getServerSocket().accept();
  
        System.out.println("[PlainProxyEngine] New proxy proxy connection");
        
        final BufferedInputStream in =
            new BufferedInputStream(localSocket.getInputStream(), buffer.length);

        in.mark(buffer.length);
        
        // Read a buffer full.
        final int bytesRead = in.read(buffer);
        
        final String line = bytesRead > 0 ? new String(buffer, 0, bytesRead, "US-ASCII") : "";
        final Matcher httpConnectMatcher = m_httpConnectPattern.matcher(line);
        
        if (httpConnectMatcher.find()) 
        {
          // HTTP proxy request.

          final String remoteHost = httpConnectMatcher.group(2);
          int remotePort = 80;
          try 
          {
            remotePort = Integer.parseInt(httpConnectMatcher.group(3));
          }
          catch (final NumberFormatException e) 
          {
            // remotePort = 80;
          }
          
          Socket remoteSocket = getSocketFactory().createClientSocket(remoteHost, remotePort);
          
          // Forward client request to server
          remoteSocket.getOutputStream().write(buffer, 0, bytesRead);
    
          this.launchThreadPair(
              localSocket, 
              remoteSocket,
              remoteHost);
        }
      }
      catch (IOException e)
      {
        e.printStackTrace(System.err);
      }
    }
  }
}
