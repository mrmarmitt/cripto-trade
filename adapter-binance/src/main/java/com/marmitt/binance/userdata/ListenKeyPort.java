package com.marmitt.binance.userdata;

import java.io.IOException;

public interface ListenKeyPort {

    String obtainListenKey() throws IOException;

    void keepAlive();

    void revoke();

    String getListenKey();
}
