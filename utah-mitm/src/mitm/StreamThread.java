// This file is part of The Grinder software distribution. Refer to
// the file LICENSE which is part of The Grinder distribution for
// licensing details. The Grinder distribution is available on the
// Internet at http://grinder.sourceforge.net/

package mitm;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.SocketException;
import mitm.HttpParser.*;

/**
 * Copies bytes from an InputStream to an OutputStream. Uses a ProxyDataFilter
 * to log the contents appropriately.
 * 
 */
public class StreamThread implements Runnable
{
  // For simplicity, the filters take a buffer oriented approach.
  // This means that they all break at buffer boundaries. Our buffer
  // is huge, so we shouldn't practically cause a problem, but the
  // network clearly can by giving us message fragments.
  // We really ought to take a stream oriented approach.
  private final static int BUFFER_SIZE = 65536;

  private ConnectionDetails m_connectionDetails;
  private InputStream m_in;
  private OutputStream m_out;
  private ProxyDataFilter m_filter;
  private PrintWriter m_outputWriter;
  private HttpParser m_httpParser;
  private OnMessageParsedListener m_parsedListener;

  public StreamThread(ConnectionDetails connectionDetails, InputStream in,
      OutputStream out, ProxyDataFilter filter, PrintWriter outputWriter)
  {
    m_connectionDetails = connectionDetails;
    m_in = in;
    m_out = out;
    m_filter = filter;
    m_outputWriter = outputWriter;
    m_httpParser = new HttpParser(new OnMessageParsedListener()
    {
      @Override
      public void newMessageParsed(HttpMessage message)
      {
        try
        {
          final HttpMessage newMessage = m_filter.handle(m_connectionDetails, message);
  
          m_outputWriter.flush();
  
          if (newMessage != null)
          {
            m_out.write(newMessage.getData());
          }
          else
          {
            m_out.write(new byte[0]);
          }
        }
        catch (SocketException e) {}
        catch (Exception e) { e.printStackTrace(System.err); }
      }
    });

    final Thread t = new Thread(this, "Filter thread for "
        + m_connectionDetails.getDescription());

    try
    {
      m_filter.connectionOpened(m_connectionDetails);
    }
    catch (Exception e)
    {
      e.printStackTrace(System.err);
    }

    t.start();
  }
  
  public void changeInputStream(InputStream in)
  {
    try 
    { 
      m_in.close(); 
    }
    catch (Exception e) {}
    
    m_in = in;
  }
  
  public void changeOutputStream(OutputStream out)
  {
    try 
    { 
      m_out.close(); 
    }
    catch (Exception e) {}
    
    m_out = out;
  }

  public void run()
  {
    try
    {
      while (true)
      {
        byte[] buffer = new byte[BUFFER_SIZE];
        final int bytesRead = m_in.read(buffer, 0, BUFFER_SIZE);

        if (bytesRead == -1)
        {
          break;
        }
        
        // Resize buffer to min size
        byte[] data = new byte[bytesRead];
        System.arraycopy(buffer, 0, data, 0, bytesRead);
        m_httpParser.parse(data);
      }
    }
    catch (SocketException e)
    {
      // Be silent about SocketExceptions.
    }
    catch (Exception e)
    {
      e.printStackTrace(System.err);
    }

    try
    {
      m_filter.connectionClosed(m_connectionDetails);
    }
    catch (Exception e)
    {
      e.printStackTrace(System.err);
    }

    m_outputWriter.flush();

    // We're exiting, usually because the in stream has been
    // closed. Whatever, close our streams. This will cause the
    // paired thread to exit to.
    try
    {
      m_out.close();
    }
    catch (Exception e)
    {
    }

    try
    {
      m_in.close();
    }
    catch (Exception e)
    {
    }
  }
  
}
