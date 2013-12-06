package mitm;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mitm.ProxyDataFilter.OnRedirectInterceptListener;

/**
 * ConnectionManager handles forwarding data through the proxy 
 * between the client and server.
 * 
 * It will promote the connection to the server to SSL if
 * the server sends a redirect while keeping the client
 * connection without SSL.
 */
public class ConnectionManager
{
  private byte[] m_lastMessage;
  private Socket m_localSocket;
  private Socket m_remoteSocket;
  private ConnectionDetails m_connectionDetails;
  private PrintWriter m_outputWriter;
  private ProxyDataFilter m_requestFilter;
  private ProxyDataFilter m_responseFilter;
  private HalfSSLSocketFactory m_halfSSLsocketFactory;
  private StreamThread m_clientServerStream;
  private StreamThread m_serverClientStream;
  
  public ConnectionManager(
      Socket localSocket,
      Socket remoteSocket,
      ConnectionDetails connectionDetails,
      byte[] connectMessage,
      PrintWriter outputWriter) throws Exception
  {
    m_localSocket = localSocket;
    m_remoteSocket = remoteSocket;
    m_connectionDetails = connectionDetails;
    m_lastMessage = connectMessage;
    m_outputWriter = outputWriter;
    m_requestFilter = new ProxyDataFilter();
    m_responseFilter = new ProxyDataFilter();
    m_halfSSLsocketFactory = new HalfSSLSocketFactory();
    
    m_responseFilter.setOnRedirectInterceptListener(new OnRedirectInterceptListener()
    {
      @Override
      public void onRedirectIntercepted(String msg)
      {
        try
        {
          // Try to close the old connection to server.. but really we don't care.
          m_remoteSocket.close();
        }
        catch (Exception e) {}
        
        try
        {
          byte[] ddd = modifyHeaderFromRedirect(m_lastMessage, msg);       
          
          System.out.println(new String(ddd, "UTF-8"));
          
          // Promote our outgoing stream to SSL
          m_remoteSocket = m_halfSSLsocketFactory.createClientSocket(m_connectionDetails.getRemoteHost(), 443);
          m_remoteSocket.getOutputStream().write(ddd, 0, ddd.length);
          m_clientServerStream.changeOutputStream(m_remoteSocket.getOutputStream());
          m_serverClientStream.changeInputStream(m_remoteSocket.getInputStream());         
        }
        catch (Exception e) 
        {
          e.printStackTrace(System.err);
        }
      }
    });

    String request = new String(m_lastMessage, "UTF-8");
    request = Strippers.removeAcceptEncoding(request);
    request = Strippers.removeCookie(request);
    m_lastMessage = request.getBytes("UTF-8");
    
    System.out.println(request);
    
    // Forward client request to server
    m_remoteSocket.getOutputStream().write(m_lastMessage, 0, m_lastMessage.length);

    launchThreadPair();
  }
    
  private byte[] modifyHeaderFromRedirect(byte[] requestAsBytes, String response) throws Exception
  {
    String request = new String(requestAsBytes, "UTF-8");

    request = setRedirectUri(request, response);
    request = setCookie(request, response);
    request = Strippers.removeAcceptEncoding(request);

    return request.getBytes("UTF-8");
  }
  
  private String setCookie(String request, String response) throws Exception
  {
    Pattern setCookiePattern = Pattern.compile("Set-Cookie:\\s([^\r]+)\r\n");
    Matcher setCookieMatcher = setCookiePattern.matcher(response);
    
    request = Strippers.removeCookie(request);
    
    if(setCookieMatcher.find()) 
    {
      String cookie = "\r\nCookie: " + setCookieMatcher.group(1);  
      request = request.replaceFirst("\r\n\r\n", cookie + "\r\n\r\n");
      System.err.println("-- Added cookie from redirect");
    }
    
    return request;
  }

  private String setRedirectUri(String request, String response)
  {
    Pattern locationPattern = Pattern.compile("Location:\\s([^\r]+)\r\n");
    Matcher locationMatcher = locationPattern.matcher(response);
    
    if(locationMatcher.find())
    {
      String location = locationMatcher.group(1);
      
      if (location.indexOf("http://") > 7)
      {
        // hack for wellsfargo.com (???) where location 
        // looks like https://blah.comhttp://blah.com
        location = location.replaceAll("http://.*", "");
        
        if (location.length() == 0)
          throw new IllegalStateException("WTF wells fargo!");
      }
      
      request = request.replaceFirst("http[^\\s]+", location);
      System.err.println("-- upgraded to https");
    }
    
    return request;
  }

  /*
   * Launch a pair of threads that: (1) Copy data sent from the client to the
   * remote server (2) Copy data sent from the remote server to the client
   */
  private final void launchThreadPair() throws IOException
  {
    m_clientServerStream = new StreamThread(
        new ConnectionDetails(
          m_connectionDetails.getLocalHost(),
          m_connectionDetails.getLocalPort(),
          m_connectionDetails.getRemoteHost(),
          m_connectionDetails.getRemotePort(),
          m_connectionDetails.isSecure()),
        m_localSocket.getInputStream(),
        m_remoteSocket.getOutputStream(),
        m_requestFilter,
        m_outputWriter);
    
    m_serverClientStream = new StreamThread(
        new ConnectionDetails(
          m_connectionDetails.getRemoteHost(),
          m_connectionDetails.getRemotePort(),
          m_connectionDetails.getLocalHost(),
          m_connectionDetails.getLocalPort(),
          m_connectionDetails.isSecure()),
        m_remoteSocket.getInputStream(),
        m_localSocket.getOutputStream(),
        m_responseFilter,
        m_outputWriter);
  }
}
