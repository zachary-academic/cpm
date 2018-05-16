package cpm.logging;

@SuppressWarnings("serial")
public class StatusCodeException extends Exception
{
    private int statusCode;
    
    public StatusCodeException (int statusCode)
    {
        super("Unexpected status code: " + statusCode);
        this.statusCode = statusCode;
    }
    
    public int getStatus ()
    {
        return statusCode;
    }
}
