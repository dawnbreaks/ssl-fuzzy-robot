package mitm;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import javax.net.SocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;


public class HalfSSLSocketFactory implements ISocketFactory
{
  final SocketFactory m_clientSocketFactory;
  final SSLContext m_sslContext;
  public KeyStore ks = null;
  
  public HalfSSLSocketFactory() throws Exception
  {
    m_sslContext = SSLContext.getInstance("SSL");

 // TODO: Do we need the KeyStore stuff? -kevin
    final KeyManagerFactory keyManagerFactory = KeyManagerFactory
        .getInstance(KeyManagerFactory.getDefaultAlgorithm());

    final String keyStoreFile = System
        .getProperty(JSSEConstants.KEYSTORE_PROPERTY);
    final char[] keyStorePassword = System.getProperty(
        JSSEConstants.KEYSTORE_PASSWORD_PROPERTY, "").toCharArray();
    final String keyStoreType = System.getProperty(
        JSSEConstants.KEYSTORE_TYPE_PROPERTY, "jks");

    final KeyStore keyStore;

    if (keyStoreFile != null)
    {
      keyStore = KeyStore.getInstance(keyStoreType);
      keyStore.load(new FileInputStream(keyStoreFile), keyStorePassword);

      this.ks = keyStore;
    }
    else
    {
      keyStore = null;
    }

    keyManagerFactory.init(keyStore, keyStorePassword);

    m_sslContext.init(keyManagerFactory.getKeyManagers(),
        new TrustManager[] { new TrustEveryone() }, null);

    m_clientSocketFactory = m_sslContext.getSocketFactory();
  }

  /**
   * This always creates a plain HTTP socket (no SSL)
   */
  @Override
  public ServerSocket createServerSocket(String localHost, int localPort, int timeout) throws IOException
  {
    final ServerSocket socket = new ServerSocket(localPort, 50, InetAddress.getByName(localHost));
    socket.setSoTimeout(timeout);
    return socket;
  }

  /**
   * This always creates an SSL socket.
   */
  @Override
  public Socket createClientSocket(String remoteHost, int remotePort) throws IOException
  {
    final SSLSocket socket = (SSLSocket) m_clientSocketFactory
        .createSocket(remoteHost, remotePort);

    socket.setEnabledCipherSuites(socket.getSupportedCipherSuites());

    socket.startHandshake();

    return socket;
  }
  
  /**
   * We're carrying out a MITM attack, we don't care whether the cert chains
   * are trusted or not ;-)
   */
  private static class TrustEveryone implements X509TrustManager
  {
    public void checkClientTrusted(X509Certificate[] chain, String authenticationType)
    {
    }

    public void checkServerTrusted(X509Certificate[] chain, String authenticationType)
    {
    }

    public X509Certificate[] getAcceptedIssuers()
    {
      return null;
    }
  }
}
