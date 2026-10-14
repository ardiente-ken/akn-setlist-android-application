package com.aknevs.set.domain.usecase;

import com.aknevs.set.domain.repository.RepositoryCallback;
import com.aknevs.set.domain.repository.RequestListener;
import com.aknevs.set.domain.repository.RequestRepository;

public class ManageRequestsUseCase {

    private final RequestRepository repository;

    public ManageRequestsUseCase(RequestRepository repository) {
        this.repository = repository;
    }

    public void observe(RequestListener listener) { repository.addListener(listener); }

    public void stopObserving(RequestListener listener) { repository.removeListener(listener); }

    public void setAppForeground(boolean foreground) { repository.setAppForeground(foreground); }

    public void signInAdmin(String email, String password, RepositoryCallback<Boolean> callback) {
        repository.signInAdmin(email, password, callback);
    }

    public void signOutAdmin() { repository.signOutAdmin(); }

    public boolean isAdmin() { return repository.isAdmin(); }

    public String getAdminEmail() { return repository.getAdminEmail(); }

    public void removeRequest(String requestId, RepositoryCallback<Boolean> callback) {
        repository.removeRequest(requestId, callback);
    }

    public void setRequestsOpen(boolean open, RepositoryCallback<Boolean> callback) {
        repository.setRequestsOpen(open, callback);
    }
}
