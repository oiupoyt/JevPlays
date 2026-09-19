package com.typesafe.jevplayer.core.bridge;

public interface InputBridge {
    void releaseAllInputs();

    void sendChatMessage(String message);

    boolean isRemoteServer();
}
