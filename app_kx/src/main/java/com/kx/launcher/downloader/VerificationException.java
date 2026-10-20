package com.kx.launcher.downloader;

import java.io.IOException;

public class VerificationException extends IOException {
    public VerificationException(String error) {
        super(error);
    }
}
