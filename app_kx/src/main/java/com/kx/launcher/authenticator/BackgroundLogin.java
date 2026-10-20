package com.kx.launcher.authenticator;

import androidx.annotation.NonNull;

import com.kx.launcher.authenticator.listener.LoginListener;
import com.kx.launcher.authenticator.accounts.Account;

public interface BackgroundLogin {
    void createAccount(@NonNull LoginListener loginListener, String code);
    void refreshAccount(@NonNull LoginListener loginListener, Account account);
    interface Creator {
        BackgroundLogin create();
    }
}
