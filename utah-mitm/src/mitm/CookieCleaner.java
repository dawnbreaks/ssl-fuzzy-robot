package mitm;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// https://github.com/moxie0/sslstrip/blob/master/sslstrip/CookieCleaner.py

public class CookieCleaner
{
  private static CookieCleaner m_instance = null;
  private Set<String> m_cleanedCookies;
  
  private CookieCleaner() {
    m_cleanedCookies = new HashSet<String>();
  }
  
  public static synchronized CookieCleaner getInstance() {
    if(m_instance == null) {
      m_instance = new CookieCleaner();
    }
    
    return m_instance;
  }
  
  public synchronized boolean add(String client, String domain) {
    return m_cleanedCookies.add(client + domain);
  }

  public String getDomainFor(String host)
  {
    String[] hostParts = host.split(".");
    String domain = host;
    
    if(hostParts.length >= 2) 
    {
      domain = "." + hostParts[hostParts.length - 2] + "." + hostParts[hostParts.length - 1];
    }
    
    return domain;
  }
  
  public List<String> getExpireCookieStringFor(String cookieKey, String host,
      String domain, String path)
  {
    String[] pathList = path.split("/");
    List<String> expireList = new ArrayList<String>();
    
    expireList.add(cookieKey + "=" + "EXPIRED;Path=/;Domain=" + domain + 
        ";Expires=Mon, 01-Jan-1990 00:00:00 GMT\r\n");
    
    expireList.add(cookieKey + "=" + "EXPIRED;Path=/;Domain=" + host + 
        ";Expires=Mon, 01-Jan-1990 00:00:00 GMT\r\n");
    
    if(pathList.length > 2) {
      expireList.add(cookieKey + "=" + "EXPIRED;Path=/" + pathList[1] + ";Domain=" +
          domain + ";Expires=Mon, 01-Jan-1990 00:00:00 GMT\r\n");
      
      expireList.add(cookieKey + "=" + "EXPIRED;Path=/" + pathList[1] + ";Domain=" +
          host + ";Expires=Mon, 01-Jan-1990 00:00:00 GMT\r\n");
    }
    
    return expireList;
  }
  
  public List<String> getExpireHeaders(String method, String client, String host, HttpMessage headers, String path) {
    String domain = getDomainFor(host);
    add(client, domain);
    
    List<String> expiredCookies = new ArrayList<String>();
    List<String> cookies = headers.get("cookie");
    
    for(String cookie : cookies) {
      for(String subCookie : cookie.split(";")) {
        String cookieKey = subCookie.split("=")[0].trim();
        List<String> expireHeadersForCookie = getExpireCookieStringFor(cookieKey, host, domain, path);
        expiredCookies.addAll(expireHeadersForCookie);
      }      
    }
    
    return expiredCookies;
  }

  public boolean hasCookies(HttpMessage headers)
  {
    return !headers.get("cookie").isEmpty();
  }
  
  public boolean isClean(String method, String client, String host, HttpMessage headers) {
    if(method == "POST" || !hasCookies(headers))
    {
      return true;
    }
    
    return m_cleanedCookies.contains(client + getDomainFor(host));
  }
  
  public String join(List<String> strings, String delimiter) {
    if(strings.isEmpty()) 
    {
      return "";
    }
    
    String message = strings.get(0);
    for(int i = 1; i < strings.size(); i++) {
      message += delimiter + strings.get(i);
    }
    
    return message;
  }
 
}
