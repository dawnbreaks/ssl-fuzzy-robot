//Based on SnifferSocketFactory.java from The Grinder distribution.
// The Grinder distribution is available at http://grinder.sourceforge.net/

package mitm;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public interface ISocketFactory
{
  ServerSocket createServerSocket(String localHost, int localPort, int timeout)
      throws IOException;

  Socket createClientSocket(String remoteHost, int remotePort)
      throws IOException;
}
