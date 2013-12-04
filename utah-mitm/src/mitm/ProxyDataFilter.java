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
    public void onRedirectIntercepted(String uri);
  }
  
  private PrintWriter m_out = new PrintWriter(System.out, true);
  private Pattern m_serverRedirectPattern;
  private Pattern m_httpsPattern;
  private OnRedirectInterceptListener m_redirectListener;

  public ProxyDataFilter()
  {
    // This don't feel like a completely correct way to match domains.. oh well.
    m_serverRedirectPattern = Pattern.compile(
        "^.*HTTP.* (30\\d [^\r]*).*(https://[^\r]+).*\r\n\r\n", Pattern.DOTALL);
    
    m_httpsPattern = Pattern.compile("^(.*)https://(.*)", Pattern.DOTALL);
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

  public byte[] handle(ConnectionDetails connectionDetails, byte[] buffer, int bytesRead) throws java.io.IOException
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
    
    // Look for a redirect in the data
    String dataAsString = stringBuffer.toString();
    Matcher redirectMatcher = m_serverRedirectPattern.matcher(dataAsString);
    Matcher httpsMatcher = m_httpsPattern.matcher(dataAsString);
    
    // Note: We really should bulk up entire HTTP messages before attempting to do match,
    // but this will probably work most of the time...
    if (redirectMatcher.find())
    {
      m_out.println("-- Intecepted redirect: "
          + redirectMatcher.group(1) + " for " + redirectMatcher.group(2));
      
      if (m_redirectListener != null)
      {
        m_redirectListener.onRedirectIntercepted(redirectMatcher.group(2));
      }

      // Avoid closing the client connection by returning a non-null byte array.
      return new byte[0];
    }
    else if (httpsMatcher.find())
    {
// FIXME: It seems like this isn't matching because data is gzipped? or encrypted??
      String stripped = dataAsString.replace("https", " http");
      return stripped.getBytes("US-ASCII");
    }
    else
    {
      m_out.println("------ " + connectionDetails.getDescription()
          + " ------");
      m_out.println(stringBuffer);
      return null;
    }
  }

  public void connectionOpened(ConnectionDetails connectionDetails)
  {
    m_out.println("--- " + connectionDetails.getDescription()
        + " opened --");
  }

  public void connectionClosed(ConnectionDetails connectionDetails)
  {
    m_out.println("--- " + connectionDetails.getDescription()
        + " closed --");
  }
}
