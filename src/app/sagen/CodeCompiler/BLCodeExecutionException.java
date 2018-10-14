package app.sagen.CodeCompiler;

public class BLCodeExecutionException extends Exception {

    public BLCodeExecutionException(String message) {
        super(message);
    }

    public BLCodeExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public BLCodeExecutionException(Throwable cause) {
        super(cause);
    }

    protected BLCodeExecutionException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
