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
    Pattern httpPattern = Pattern.compile("(http://[^\\s]+)");
    Matcher httpMatcher = httpPattern.matcher(message.getBodyAsString());
    
    cleanHeaders(message);
    
    System.err.println("------ " + connectionDetails.getDescription() + " ------");
    System.out.println(message.getHeadersAsString());
    
    UrlMonitor urlMonitor = UrlMonitor.getInstance();
    String client = connectionDetails.getRemoteHost();
    String body = message.getBodyAsString();
        
    while (httpMatcher.find())
    {
      String url = httpMatcher.group();
      
      if(urlMonitor.isSecureLink(client, url))
      {
        String secureUrl = url.replaceAll(" http", "https");
        body = body.replaceAll(url, secureUrl);
        System.err.println("-- Upgrade to https: " + secureUrl);
      }
      
      message.setBodyBytes(body.getBytes("UTF-8"));
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
