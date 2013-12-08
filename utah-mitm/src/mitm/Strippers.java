package mitm;

public class Strippers
{
  public static void removeAcceptEncoding(HttpMessage message) throws Exception
  { 
    message.removeHeader("Accept-Encoding");
  }
  
  public static void removeContentSecurityPolicy(HttpMessage message) throws Exception
  { 
    message.removeHeader("Content-Security-Policy");
  }
  
  public static void removeCookie(HttpMessage message) throws Exception
  {   
    message.removeHeader("Cookie");
  }
  
}
