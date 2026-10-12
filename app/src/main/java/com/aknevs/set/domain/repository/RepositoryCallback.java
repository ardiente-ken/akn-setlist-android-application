package com.aknevs.set.domain.repository;

/** Always invoked on the main thread. */
public interface RepositoryCallback<T> {
    void onSuccess(T result);
    void onError(Exception error);
}
