package mitm;

public class RequestFilter implements IDataFilter
{

  @Override
  public HttpMessage handle(ConnectionDetails connectionDetails, HttpMessage message) throws Exception
  {
    // TODO Auto-generated method stub
    return null;
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
