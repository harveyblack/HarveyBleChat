package com.harvey.blechat;

import java.util.UUID;

/**
 * 自定义服务UUID
 * 自定义特征UUID
 */
public class CustomBluetoothUUID {
    public static final UUID HARVEY_SERVICE = UUID.fromString("a0111801-0000-1000-8000-00805f9b34fb");
    public static final UUID WRITE_MESSAGE_CHARACTERISTIC = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb");
    public static final UUID NOTIFY_MESSAGE_CHARACTERISTIC = UUID.fromString("00002a01-0000-1000-8000-00805f9b34fb");
}
