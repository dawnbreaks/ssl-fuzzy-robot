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
      public void onRedirectIntercepted(String uri)
      {
        try
        {
          // Try to close the old connection to server.. but really we don't care.
          m_remoteSocket.close();
        }
        catch (Exception e) {}
        
        try
        {
          byte[] ddd = upgradeHeader(m_lastMessage, uri);
          
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

    m_lastMessage = Strippers.removeAcceptEncoding(m_lastMessage);
    m_lastMessage = Strippers.removeCookie(m_lastMessage);
    
    System.out.println(new String(m_lastMessage, "UTF-8"));
    
    // Forward client request to server
    m_remoteSocket.getOutputStream().write(m_lastMessage, 0, m_lastMessage.length);

    launchThreadPair();
  }
  
  private byte[] upgradeHeader(byte[] request, String uri) throws Exception
  {
    String data = new String(request, "UTF-8");
    data = data.replaceFirst("http[^ ]+", uri);
    
    System.err.println("-- upgraded request to https");
    System.out.println(data);
    
    return data.getBytes("UTF-8");
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
