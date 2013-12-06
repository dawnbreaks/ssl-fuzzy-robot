package mitm;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Strippers
{


  public static String removeAcceptEncoding(String request) throws Exception
  { 
    Pattern p = Pattern.compile("Accept-Encoding:\\s[,a-zA-Z]+\r\n");
    Matcher m = p.matcher(request);
    
    if (m.find())
    {
      System.err.println("-- stripped \"Accept Encoding\"");
      request = request.replaceAll(p.toString(), "");
    }
    
    return request;
  }
  
  public static String removeCookie(String request) throws Exception
  {   
    Pattern p = Pattern.compile("Cookie:\\s[^\r]+\r\n");
    Matcher m = p.matcher(request);

    if (m.find())
    {
      System.err.println("-- stripped \"Cookie\"");
      request = request.replaceAll(p.toString(), "");
    }
    
    return request;
  }
  
}
