package mitm;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProxyEngine implements Runnable
{
  private String m_localHost;
  private int m_localPort;
  private PrintWriter m_outputWriter;
  private PlainSocketFactory m_plainSocketFactory;
  private ServerSocket m_serverSocket;
  
  private final Pattern m_httpConnectPattern;

  public ProxyEngine(
      String localHost,
      int localPort,
      int timeout) throws IOException
  {
    m_localHost = localHost;
    m_localPort = localPort;
    m_outputWriter = new PrintWriter(System.out, true);
    m_plainSocketFactory = new PlainSocketFactory();
    m_httpConnectPattern = Pattern.compile("^([A-Z]+)[ \\t]+http://([^/:]+):?(\\d*)/.*\r\n\r\n", Pattern.DOTALL);

    m_serverSocket = m_plainSocketFactory.createServerSocket(m_localHost, m_localPort, timeout);    
  }

  @Override
  public void run()
  {
    final byte[] buffer = new byte[40960];
    
    while (true)
    {
      try
      {
        final Socket localSocket = m_serverSocket.accept();
  
        System.out.println("[PlainProxyEngine] New proxy connection");
        
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
          
          Socket remoteSocket = m_plainSocketFactory.createClientSocket(remoteHost, remotePort);
          
          byte[] connectMessage = new byte[bytesRead];
          System.arraycopy(buffer, 0, connectMessage, 0, bytesRead);
          
          // Delegate work to a new manager for the current connection
          new ConnectionManager(
              localSocket, 
              remoteSocket, 
              new ConnectionDetails(m_localHost, m_localPort, remoteHost, remotePort, false), 
              connectMessage,
              m_outputWriter);
        }
      }
      catch (Exception e)
      {
        e.printStackTrace(System.err);
      }
    }
  }
}
