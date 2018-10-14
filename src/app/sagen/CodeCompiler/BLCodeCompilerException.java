package app.sagen.CodeCompiler;

public class BLCodeCompilerException extends Exception {

    public BLCodeCompilerException(String message) {
        super(message);
    }

    public BLCodeCompilerException(String message, Throwable cause) {
        super(message, cause);
    }

    public BLCodeCompilerException(Throwable cause) {
        super(cause);
    }

    protected BLCodeCompilerException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
