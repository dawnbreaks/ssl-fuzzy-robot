package mitm;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RequestFilter implements IDataFilter
{
  private OnNewRequestListener m_newRequestListener;
  
  public interface OnNewRequestListener
  {
    public void onNewRequest(HttpMessage message);
  }
  
  public RequestFilter(OnNewRequestListener l) 
  {
    m_newRequestListener = l;
  }
  
  @Override
  public HttpMessage handle(ConnectionDetails connectionDetails, HttpMessage message) throws Exception
  {    
    Pattern httpPattern = Pattern.compile("(http://[\\w\\d:#@%/;$()~_?\\+-=\\\\.&]*)", Pattern.CASE_INSENSITIVE);
    
    cleanHeaders(message);
    
    String topLine = message.getTopLine();
    
    if (topLine.startsWith("POST"))
    {
      topLine = topLine.replaceFirst("http", "https");
    }
    else
    {
      UrlMonitor urlMonitor = UrlMonitor.getInstance();
      Matcher httpMatcher = httpPattern.matcher(topLine);
      while (httpMatcher.find())
      {
        String url = httpMatcher.group();
        
        if(urlMonitor.isSecureLink(connectionDetails.getRemoteHost(), url))
        {
          String secureUrl = url.replaceAll("http", "https");
          topLine = topLine.replaceAll(url, secureUrl);
          System.err.println("-- Upgrade to https: " + secureUrl);
        }
      }
    }
    message.replaceHeader("Top", topLine);
    
    System.err.println("------ " + connectionDetails.getDescription() + " ------");
    System.out.println(message.getHeadersAsString());    
    
    if (message.getTopLine().startsWith("POST"))
    {
      System.out.println(message.getBodyAsString());
    }
    
    m_newRequestListener.onNewRequest(message);
    return message;
  }
  
  public void cleanHeaders(HttpMessage request)
  {
    request.removeHeader("Accept-Encoding");
    request.removeHeader("If-Modified-Since");
    request.removeHeader("Cache-Control");
    request.removeHeader("Content-Security");
  }

  @Override
  public void connectionOpened(ConnectionDetails connectionDetails)
  {
    System.err.println("--- " + connectionDetails.getDescription() + " opened --");
  }
  
  @Override
  public void connectionClosed(ConnectionDetails connectionDetails)
  {
    System.err.println("--- " + connectionDetails.getDescription() + " closed --");
  }

}
