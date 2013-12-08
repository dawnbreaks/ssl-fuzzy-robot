package mitm;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpParser
{
  public interface OnMessageParsedListener
  {
    public void newMessageParsed(HttpMessage message);
  }
  
  private OnMessageParsedListener m_parseListener;
  private HttpMessage m_partialMessage;
  private boolean m_haveFullHeader;
  private byte[] m_buffer;
  
  private Pattern m_httpHeaderPattern;
  private Pattern m_headerLinePattern;
  private Pattern m_endChunkedPattern;
  
  
  public HttpParser(OnMessageParsedListener l)
  {
    m_parseListener = l;
    m_partialMessage = new HttpMessage();
    m_haveFullHeader = false;
    m_buffer = new byte[0];
    
    m_httpHeaderPattern = Pattern.compile("^([A-Z]+.*\r\n\r\n)(.*)", Pattern.DOTALL);
    m_headerLinePattern = Pattern.compile("^([^:]+):\\s+(.+)");
    m_endChunkedPattern = Pattern.compile("^.*0\r\n\r\n", Pattern.DOTALL);
  }
  
  public void parse(byte[] data) throws Exception
  {
    addToBuffer(data);
    
    if (parseData())
    {
      HttpMessage m = m_partialMessage;
      m_partialMessage = new HttpMessage();
      m_parseListener.newMessageParsed(m);
    }
  }
  
  private boolean parseData() throws Exception
  {
    if (!m_haveFullHeader)
    {
      String dataAsString = new String(m_buffer, "UTF-8");
      
      Matcher headerMatcher = m_httpHeaderPattern.matcher(dataAsString);
      boolean doneTop = false;
      if (headerMatcher.find())
      {
        String headerData = headerMatcher.group(1);
        String[] headers = headerData.split("\r\n");
        for (String headerLine : headers)
        {
          if (!doneTop)
          {
            m_partialMessage.appendHeader("Top", headerLine);
            doneTop = true;
          }
          else
          {
            Matcher headerLineMatcher = m_headerLinePattern.matcher(headerLine);
            
            if (headerLineMatcher.find())
            {
              String header = headerLineMatcher.group(1);
              String value = headerLineMatcher.group(2);
              
              m_partialMessage.appendHeader(header, value);
            }
          }
        }

        // Remove the parsed bytes from the buffer and recur
        trimFrontBuffer(headerData.getBytes("UTF-8").length);
        m_haveFullHeader = true;
        return parseData();
      }
    }
    else
    {
      if (getContentLength() != null)
      {
        int contentLength = getContentLength();
        if (m_buffer.length < contentLength)
        {
          return false;
        }
        else if (m_buffer.length == contentLength)
        {
          m_partialMessage.setBodyBytes(m_buffer);
          m_buffer = new byte[0];
          m_haveFullHeader = false;
          return true;
        }
        else
        {
          throw new IllegalStateException("Too much data in buffer");
        }
      }
      else if (isChunkEncoded())
      {
        String bodyAsString = new String(m_buffer, "UTF-8");
        // This is cheating and imperfect (body data may contain a matching pattern)
        Matcher endChunkedMatcher = m_endChunkedPattern.matcher(bodyAsString);
        if (endChunkedMatcher.find())
        {
          m_partialMessage.setBodyBytes(m_buffer);
          m_buffer = new byte[0];
          m_haveFullHeader = false;
          return true;
        }
      }
      else if (m_buffer.length == 0)
      {
        // Assume that we are already done
        m_buffer = new byte[0];
        m_haveFullHeader = false;
        return true;
      }
      else
      {
        throw new IllegalStateException("How to parse now??\r\n" + m_partialMessage.getDataAsString());
      }
    }
    
    return false;
  }
  
  private Integer getContentLength()
  {
    String headerName = "Content-Length";
    if (!m_partialMessage.containsHeader(headerName))
    {
      headerName = headerName.toLowerCase();
      if (!m_partialMessage.containsHeader(headerName))
      {
        return null;
      }
    }
    return Integer.parseInt(m_partialMessage.get(headerName).get(0));
  }
  
  private boolean isChunkEncoded()
  {
    String headerName = "Transfer-Encoding";
    if (!m_partialMessage.containsHeader(headerName))
    {
      headerName = headerName.toLowerCase();
      if (!m_partialMessage.containsHeader(headerName))
      {
        return false;
      }
    }
    
    return m_partialMessage.containsHeader(headerName) &&
           m_partialMessage.get(headerName).get(0).equalsIgnoreCase("chunked");
  }
  
  private void addToBuffer(byte[] data)
  {
    byte[] newBuffer = new byte[m_buffer.length + data.length];
    System.arraycopy(m_buffer, 0, newBuffer, 0, m_buffer.length);
    System.arraycopy(data, 0, newBuffer, m_buffer.length, data.length);
    m_buffer = newBuffer;
  }
  
  private void trimFrontBuffer(int bytes)
  {
    try
    {
      byte[] newBuffer = new byte[m_buffer.length - bytes];
      System.arraycopy(m_buffer, bytes, newBuffer, 0, newBuffer.length);
      m_buffer = newBuffer;
    }
    catch (Exception e)
    {
      // Got a negative index exception somehow O.o
      try
      {
        System.err.println("trimFrontBuffer Exception:\r\n" 
            + "message data: " + m_partialMessage.getDataAsString() 
            + "m_buffer: " + new String(m_buffer, "UTF-8"));
      } 
      catch (Exception ignore) {}
      throw e;
    }
  }

}
