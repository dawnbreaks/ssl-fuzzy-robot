/*
Copyright 2007 Srinivas Inguva

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 * Neither the name of Stanford University nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 */

package mitm;

import java.io.PrintWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProxyDataFilter
{
  public interface OnRedirectInterceptListener
  {
    public void onRedirectIntercepted(HttpMessage response);
  }
  
  private PrintWriter m_out = new PrintWriter(System.out, true);
  private Pattern m_httpHeaderPattern;
  private Pattern m_serverRedirectPattern;
  private Pattern m_httpPattern;
  private Pattern m_httpsPattern;
  private OnRedirectInterceptListener m_redirectListener;

  public ProxyDataFilter()
  {
    m_httpHeaderPattern = Pattern.compile("^([A-Z]+.*\r\n\r\n)(.*)", Pattern.DOTALL);
    
    // This don't feel like a completely correct way to match domains.. oh well.
    m_serverRedirectPattern = Pattern.compile(
        "^.*HTTP.* (30\\d [^\r]*).*(https://[^\r]+).*\r\n\r\n", Pattern.DOTALL);
    
    m_httpPattern = Pattern.compile("(http://[^\\s]+)");
    m_httpsPattern = Pattern.compile("(https://[^\\s]+)");
  }
  
  public void setOutputPrintWriter(PrintWriter outputPrintWriter)
  {
    m_out.flush();
    m_out = outputPrintWriter;
  }

  public PrintWriter getOutputPrintWriter()
  {
    return m_out;
  }
  
  public void setOnRedirectInterceptListener(OnRedirectInterceptListener l)
  {
    m_redirectListener = l;
  }

  public byte[] handle(ConnectionDetails connectionDetails, byte[] data) throws Exception
  {
    String message = new String(data, "UTF-8");
    Matcher redirectMatcher = m_serverRedirectPattern.matcher(message);
    Matcher httpMatcher = m_httpPattern.matcher(message);
    Matcher httpsMatcher = m_httpsPattern.matcher(message);

    message = Strippers.removeAcceptEncoding(message);
    
    System.err.println("------ " + connectionDetails.getDescription() + " ------");
    System.out.println(message);
    
    if (redirectMatcher.find())
    {
      String url = redirectMatcher.group(2);
      
      // Intercept redirects
      System.err.println("-- Intecepted redirect: "
          + redirectMatcher.group(1) + " for " + url);
      
      if (m_redirectListener != null)
      {
        m_redirectListener.onRedirectIntercepted(new HttpMessage(data));
      }

      // Avoid closing the client connection by returning a non-null byte array.
      return new byte[0];
    }
    else if (!connectionDetails.isServer() && httpsMatcher.find())
    {
      UrlMonitor urlMonitor = UrlMonitor.getInstance();
      String client = connectionDetails.getLocalHost();
      
      // Down-grade links
      do 
      {
        String url = httpsMatcher.group();
        urlMonitor.addSecureLink(client, url);
      } while(httpsMatcher.find());
      
      String stripped = message.replace("https", " http");
      return stripped.getBytes("UTF-8");
    }
    else if (connectionDetails.isServer() && httpMatcher.find())
    {
      UrlMonitor urlMonitor = UrlMonitor.getInstance();
      String client = connectionDetails.getRemoteHost();
      String response = message;
      
      // upgrade links
      do 
      {
        String url = httpMatcher.group();
        if(urlMonitor.isSecureLink(client, url))
        {
          String secureUrl = url.replaceAll("http", "https");
          response = response.replaceAll(url, secureUrl);
          System.err.println("-- Upgrade to https: " + secureUrl);
        }
        
      } while(httpsMatcher.find());
      
      return response.getBytes("UTF-8");
    }
    
    return null;
  }
  
  // Old code to print data in hex/ascci
  private String extractData(byte[] buffer, int bytesRead)
  {
    final StringBuffer stringBuffer = new StringBuffer();

    boolean inHex = false;

    for (int i = 0; i < bytesRead; i++)
    {
      final int value = (buffer[i] & 0xFF);

      // If it's ASCII, print it as a char.
      if (value == '\r' || value == '\n'
          || (value >= ' ' && value <= '~'))
      {
        if (inHex)
        {
          stringBuffer.append(']');
          inHex = false;
        }

        stringBuffer.append((char) value);
      }
      else
      { // else print the value
        if (!inHex)
        {
          stringBuffer.append('[');
          inHex = true;
        }

        if (value <= 0xf)
        { // Where's "HexNumberFormatter?"
          stringBuffer.append("0");
        }

        stringBuffer.append(Integer.toHexString(value).toUpperCase());
      }
    }
    
    return stringBuffer.toString();
  }

  public void connectionOpened(ConnectionDetails connectionDetails)
  {
    System.err.println("--- " + connectionDetails.getDescription()
        + " opened --");
  }

  public void connectionClosed(ConnectionDetails connectionDetails)
  {
    System.err.println("--- " + connectionDetails.getDescription()
        + " closed --");
  }
}
