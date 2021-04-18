package org.acme;

import io.fabric8.kubernetes.client.dsl.ExecListener;
import okhttp3.Response;

class SimpleListener implements ExecListener {

    @Override
    public void onOpen(Response response) {
        System.out.println("The shell will remain open for 10 seconds.");
    }

    @Override
    public void onFailure(Throwable t, Response response) {
        System.err.println("shell barfed");
    }

    @Override
    public void onClose(int code, String reason) {
        System.out.println("The shell will now close.");
    }
}