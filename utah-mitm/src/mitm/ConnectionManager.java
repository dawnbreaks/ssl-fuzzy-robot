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
      public void onRedirectIntercepted(HttpMessage response)
      {
        try
        {
          // Try to close the old connection to server.. but really we don't care.
          m_remoteSocket.close();
        }
        catch (Exception e) {}
        
        try
        {
          byte[] ddd = modifyHeaderFromRedirect(m_lastMessage, response.getDataAsString());          
          HttpMessage lastRequest = new HttpMessage(m_lastMessage);
          
          if(!getRequestMethod(lastRequest).equals("GET"))
          {
            HttpMessage request = new HttpMessage();
            request.appendHeader("Top", "GET " + response.get("Location").get(0) + " HTTP/1.1");
            request.appendHeader("Host", lastRequest.get("Host").get(0));
            request.appendHeader("Accept-Language", lastRequest.get("Accept-Language").get(0));
            request.appendHeader("User-Agent", lastRequest.get("User-Agent").get(0));
            request.appendHeader("Accept", lastRequest.get("Accept").get(0));
            request.appendHeader("Proxy-Connection", lastRequest.get("Proxy-Connection").get(0));
            request.appendHeader("Referer", response.get("Location").get(0));
            
            String cookies = "";
            for(int i = 0; i < response.get("Set-Cookie").size(); i++)
            {
              String cookie = response.get("Set-Cookie").get(i).split(";")[0];
              if(!cookie.contains("deleted")) 
              {
                cookies += cookie;
               
                if(i+1 < response.get("Set-Cookie").size()) {
                  cookies += "; ";
                }
              }        
            }
            
            request.appendHeader("Cookie", cookies);
            
            ddd = request.getData();
          }
          
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

    HttpMessage request = new HttpMessage(m_lastMessage);
    CookieCleaner cc = CookieCleaner.getInstance();
    
    String method = getRequestMethod(request);
    String path = getRequestPath(request);

    cleanHeaders(request);
    
    System.out.println(request.getDataAsString());
    
    if(cc.isClean(method, m_connectionDetails.getLocalHost(), m_connectionDetails.getRemoteHost(), request)) 
    {
      // Forward client request to server
      System.err.println("-- Clean cookies");  
      
      m_lastMessage = request.getData();
      m_remoteSocket.getOutputStream().write(m_lastMessage, 0, m_lastMessage.length);
    }
    else
    {
      System.err.println("-- Dirty cookies");
      HttpMessage redirect = cc.getExpiredCookieRedirectMessage(method, m_connectionDetails.getLocalHost(), m_connectionDetails.getRemoteHost(), request, path);
      System.out.println(redirect.getDataAsString());
      m_localSocket.getOutputStream().write(redirect.getData());
    }
    
    launchThreadPair();
  }

  private void cleanHeaders(HttpMessage request)
  {
    request.removeHeader("Accept-Encoding");
    request.removeHeader("If-Modified-Since");
    request.removeHeader("Cache-Control");
  }
  
  private String getRequestMethod(HttpMessage request) 
  {
    // get method of request
    Pattern httpMethodPattern = Pattern.compile("^([A-Z]+)");
    Matcher httpMethodMatcher = httpMethodPattern.matcher(request.getTopLine());
    httpMethodMatcher.find();
    
    String method = httpMethodMatcher.group(1);
    return method;
  }
  
  private String getRequestPath(HttpMessage request)
  {
    Pattern httpPathPattern = Pattern.compile("http://[^/\\s]+([^\\s]+)");
    Matcher httpPathMatcher = httpPathPattern.matcher(request.getTopLine());
    String path = "";
    
    if(httpPathMatcher.find()) 
    {
      path = httpPathMatcher.group(1);
    }
    
    return path;
  }

  private byte[] modifyHeaderFromRedirect(byte[] requestAsBytes, String response) throws Exception
  {
    String request = new String(requestAsBytes, "UTF-8");

    request = setRedirectUri(request, response);
    request = Strippers.removeAcceptEncoding(request);
//    request = response.replaceAll("https", "http");

    return request.getBytes("UTF-8");
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
          m_connectionDetails.isSecure(),
          m_connectionDetails.isServer()),
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
          m_connectionDetails.isSecure(),
          !m_connectionDetails.isServer()),
        m_remoteSocket.getInputStream(),
        m_localSocket.getOutputStream(),
        m_responseFilter,
        m_outputWriter);
  }
}
