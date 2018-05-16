package cpm.logging;

import java.io.PrintStream;
import java.io.PrintWriter;

@SuppressWarnings("serial")
public class CanvasProjectException extends Exception
{
    private Exception wrappedException;
    
    public CanvasProjectException (String message)
    {
        super(message);
    }

    public CanvasProjectException (Exception e, String msg)
    {
        super(msg);
        wrappedException = e;
    }
    
    public CanvasProjectException (int statusCode)
    {
        super("Unexpected HTTP status code: " + statusCode);
    }
    
    @Override
    public String getMessage ()
    {
        if (wrappedException != null && wrappedException.getMessage() != null && wrappedException.getMessage().length() > 0)
        {
            return wrappedException.getMessage() + " " + super.getMessage();
        }
        else if (wrappedException != null)
        {
            return "Problem " + super.getMessage();
        }
        else
        {
            return super.getMessage();
        }
    }
    
    @Override
    public StackTraceElement[] getStackTrace ()
    {
        if (wrappedException != null)
        {
            return wrappedException.getStackTrace();
        }
        else
        {
            return super.getStackTrace();
        }
    }
    
    @Override 
    public void printStackTrace ()
    {
        if (wrappedException != null)
        {
            wrappedException.printStackTrace();
        }
        else
        {
            super.printStackTrace();
        }
    }
    
    @Override 
    public void printStackTrace (PrintStream out)
    {
        if (wrappedException != null)
        {
            wrappedException.printStackTrace(out);
        }
        else
        {
            super.printStackTrace(out);
        }
    }
    
    @Override 
    public void printStackTrace (PrintWriter out)
    {
        if (wrappedException != null)
        {
            wrappedException.printStackTrace(out);
        }
        else
        {
            super.printStackTrace(out);
        }
    }
}
