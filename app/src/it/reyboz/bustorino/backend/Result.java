package it.reyboz.bustorino.backend;

import androidx.annotation.Nullable;


public class Result<T> {
    @Nullable
    public final T result;

    @Nullable
    public final Exception exception;
    public boolean isSuccess() {
        return exception == null;
    }


    public static <T> Result<T> success(@Nullable T result) {
        return new Result<>(result);
    }

    /**
     * Returns a failed response
     */
    public static <T> Result<T> failure(Exception error) {
        return new Result<>(error);
    }

    private Result(@Nullable T result) {
        this.result = result;
        this.exception = null;
    }


    private Result(Exception error) {
        this.result = null;
        this.exception = error;
    }

    public interface Callback<T>{
        void onComplete(Result<T> result);
    }
}
