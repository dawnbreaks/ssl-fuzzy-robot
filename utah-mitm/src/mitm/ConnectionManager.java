package mitm;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mitm.ResponseFilter.OnRedirectInterceptListener;
import mitm.RequestFilter.OnNewRequestListener;

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
  private HttpMessage m_lastMessage;
  private Socket m_localSocket;
  private Socket m_remoteSocket;
  private ConnectionDetails m_connectionDetails;
  private PrintWriter m_outputWriter;
  private IDataFilter m_requestFilter;
  private IDataFilter m_responseFilter;
  private HalfSSLSocketFactory m_halfSSLsocketFactory;
  private StreamThread m_clientServerStream;
  private StreamThread m_serverClientStream;
  
  public ConnectionManager(
      Socket localSocket,
      Socket remoteSocket,
      ConnectionDetails connectionDetails,
      HttpMessage connectMessage,
      PrintWriter outputWriter) throws Exception
  {
    m_localSocket = localSocket;
    m_remoteSocket = remoteSocket;
    m_connectionDetails = connectionDetails;
    m_lastMessage = connectMessage;
    m_outputWriter = outputWriter;
    m_requestFilter = new RequestFilter(buildOnNewRequestListener());
    m_responseFilter = new ResponseFilter(buildOnRedirectInterceptListener());
    m_halfSSLsocketFactory = new HalfSSLSocketFactory();

    m_lastMessage = m_requestFilter.handle(m_connectionDetails, m_lastMessage);
    System.out.println(m_lastMessage.getDataAsString());
    
    CookieCleaner cc = CookieCleaner.getInstance();
    String method = getRequestMethod(m_lastMessage);
    String path = getRequestPath(m_lastMessage);
    
    if(cc.isClean(method, m_connectionDetails.getLocalHost(), m_connectionDetails.getRemoteHost(), m_lastMessage)) 
    {
      // Forward client request to server
      System.err.println("-- Clean cookies");      
      m_remoteSocket.getOutputStream().write(m_lastMessage.getData());
    }
    else
    {
      System.err.println("-- Dirty cookies");
      HttpMessage redirect = cc.getExpiredCookieRedirectMessage(method, m_connectionDetails.getLocalHost(), m_connectionDetails.getRemoteHost(), m_lastMessage, path);
      System.out.println(redirect.getDataAsString());
      m_localSocket.getOutputStream().write(redirect.getData());
    }
    
    launchThreadPair();
  }
  
  private OnRedirectInterceptListener buildOnRedirectInterceptListener()
  {
    OnRedirectInterceptListener listener = new OnRedirectInterceptListener()
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
                    
          HttpMessage request = m_lastMessage;
          
          
          if(!getRequestMethod(m_lastMessage).equals("GET"))
          {
//            request = new HttpMessage();
//            request.appendHeader("Top", "GET " + response.get("Location").get(0) + " HTTP/1.1");
//            request.appendHeader("Host", m_lastMessage.get("Host").get(0));
//            request.appendHeader("Accept-Language", m_lastMessage.get("Accept-Language").get(0));
//            request.appendHeader("User-Agent", m_lastMessage.get("User-Agent").get(0));
//            request.appendHeader("Accept", m_lastMessage.get("Accept").get(0));
//            request.appendHeader("Proxy-Connection", m_lastMessage.get("Proxy-Connection").get(0));
//            request.appendHeader("Referer", response.get("Location").get(0));
//            
//            String cookies = "";
//            for(int i = 0; i < response.get("Set-Cookie").size(); i++)
//            {
//              String cookie = response.get("Set-Cookie").get(i).split(";")[0];
//              if(!cookie.contains("deleted")) 
//              {
//                cookies += cookie;
//               
//                if(i+1 < response.get("Set-Cookie").size()) {
//                  cookies += "; ";
//                }
//              }        
//            }
//            
//            request.appendHeader("Cookie", cookies);
            m_localSocket.getOutputStream().write(response.getData());
          }
          else 
          {
            modifyHeaderFromRedirect(request, response);            
            System.out.println(request.getDataAsString());
            
            // Promote our outgoing stream to SSL
            m_remoteSocket = m_halfSSLsocketFactory.createClientSocket(m_connectionDetails.getRemoteHost(), 443); 
            m_clientServerStream.changeOutputStream(m_remoteSocket.getOutputStream());
            m_serverClientStream.changeInputStream(m_remoteSocket.getInputStream());         
            m_remoteSocket.getOutputStream().write(request.getData());
          }
        }
        catch (Exception e)
        {
          e.printStackTrace(System.err);
        }
      }
    };
    
    return listener;
  }

  private OnNewRequestListener buildOnNewRequestListener()
  {
    OnNewRequestListener listener = new OnNewRequestListener()
    {  
      @Override
      public void onNewRequest(HttpMessage message)
      {
        m_lastMessage = message;
        
      }
    }; 
    
    return listener;
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

  private void modifyHeaderFromRedirect(HttpMessage request, HttpMessage response) throws Exception
  {
    setRedirectUri(request, response);
    Strippers.removeAcceptEncoding(request);
  }

  private void setRedirectUri(HttpMessage request, HttpMessage response)
  {
    String location = response.get("Location").get(0);
    
    if (location.indexOf("http://") > 7)
    {
      // hack for wellsfargo.com (???) where location 
      // looks like https://blah.comhttp://blah.com
      location = location.replaceAll("http://.*", "");
      
      if (location.length() == 0)
        throw new IllegalStateException("WTF wells fargo!");
    }
    
    String requestTop = request.getTopLine();
    requestTop = requestTop.replaceFirst("http[^\\s]+", location);
    request.replaceHeader("Top", requestTop);
    
    System.err.println("-- upgraded to https");
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
          m_connectionDetails.getRemotePort()),
        m_localSocket.getInputStream(),
        m_remoteSocket.getOutputStream(),
        m_requestFilter,
        m_outputWriter);
    
    m_serverClientStream = new StreamThread(
        new ConnectionDetails(
          m_connectionDetails.getRemoteHost(),
          m_connectionDetails.getRemotePort(),
          m_connectionDetails.getLocalHost(),
          m_connectionDetails.getLocalPort()),
        m_remoteSocket.getInputStream(),
        m_localSocket.getOutputStream(),
        m_responseFilter,
        m_outputWriter);
  }
}
