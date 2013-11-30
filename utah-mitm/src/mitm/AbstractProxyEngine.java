//Based on HTTPProxySnifferEngine.java from The Grinder distribution.
// The Grinder distribution is available at http://grinder.sourceforge.net/

package mitm;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public abstract class AbstractProxyEngine implements Runnable
{

  public static final String ACCEPT_TIMEOUT_MESSAGE = "Listen time out";
  private final ProxyDataFilter m_requestFilter;
  private final ProxyDataFilter m_responseFilter;
  private final ConnectionDetails m_connectionDetails;

  private final PrintWriter m_outputWriter;

  public final ISocketFactory m_socketFactory;
  protected ServerSocket m_serverSocket;

  public AbstractProxyEngine(
      ISocketFactory socketFactory,
      ProxyDataFilter requestFilter,
      ProxyDataFilter responseFilter,
      ConnectionDetails connectionDetails,
      int timeout) throws IOException
  {
    m_socketFactory = socketFactory;
    m_requestFilter = requestFilter;
    m_responseFilter = responseFilter;
    m_connectionDetails = connectionDetails;

    m_outputWriter = requestFilter.getOutputPrintWriter();

    m_serverSocket = m_socketFactory.createServerSocket(
        connectionDetails.getLocalHost(),
        connectionDetails.getLocalPort(), timeout);
  }

  // run() method from Runnable is implemented in subclasses

  public final ServerSocket getServerSocket()
  {
    return m_serverSocket;
  }

  protected final ISocketFactory getSocketFactory()
  {
    return m_socketFactory;
  }

  protected final ConnectionDetails getConnectionDetails()
  {
    return m_connectionDetails;
  }

  /*
   * Launch a pair of threads that: (1) Copy data sent from the client to the
   * remote server (2) Copy data sent from the remote server to the client
   */
  protected final void launchThreadPair(
      Socket localSocket,
      Socket remoteSocket,
      String remoteHost) throws IOException
  {
    new StreamThread(
        new ConnectionDetails(
          m_connectionDetails.getLocalHost(),
          localSocket.getPort(),
          remoteHost,
          remoteSocket.getPort(),
          m_connectionDetails.isSecure()),
        localSocket.getInputStream(),
        remoteSocket.getOutputStream(),
        m_requestFilter,
        m_outputWriter);
    
    new StreamThread(
        new ConnectionDetails(
          remoteHost,
          remoteSocket.getPort(),
          m_connectionDetails.getLocalHost(),
          localSocket.getPort(),
          m_connectionDetails.isSecure()),
        remoteSocket.getInputStream(),
        localSocket.getOutputStream(),
        m_responseFilter,
        m_outputWriter);
  }
}
