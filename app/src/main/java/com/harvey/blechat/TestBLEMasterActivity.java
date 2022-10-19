package com.harvey.blechat;

import static android.bluetooth.BluetoothDevice.ACTION_BOND_STATE_CHANGED;
import static android.bluetooth.BluetoothDevice.ACTION_UUID;
import static android.bluetooth.BluetoothDevice.BOND_BONDED;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class TestBLEMasterActivity extends Activity {
    private static final String TAG = "TestBLEMasterActivity";

    BluetoothDevice mDevice;

    int deviceMode = 0;

    private TextView tvBar;

    private TextView tv_deviceinfo;

    private TextView tvUUID;

    private TextView bt_status;

    private TextView bt_write_data;

    private EditText et_content;

    private TextView tvSendContent;

    private TextView tvReceiveContent;

    private String serviceUuidStr = CustomBluetoothUUID.HARVEY_SERVICE.toString();
    //当前设配配对状态
    private int bondState;
    //button的状态，如果已配对为false,未配对为true，先配置对后连接
    private boolean bond = false;

    //已选择的蓝牙类型
    private String deviceType = "";
    //蓝牙状态
    private String deviceState = "";

    //正在发送的内容
    String lastSendContent = "";
    //当前是否正在有内容发送
    boolean isSendding = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_test_ble);

        mDevice = getIntent().getParcelableExtra("device");

        deviceMode = getIntent().getIntExtra("devicemode", BluetoothGattService.SERVICE_TYPE_PRIMARY);

        bindView();

        initView();

        registerReceiver();

        sendBluetoothLeAdvertiser();
    }

    /**
     * 发送广播，可以让其它扫描的设备接收到此设备广播
     *
     */
    private void sendBluetoothLeAdvertiser(){
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setConnectable(true)
                .setTimeout(10000)
                .build();

        AdvertiseData advertiseData = new AdvertiseData.Builder()
                .addServiceUuid(new ParcelUuid(CustomBluetoothUUID.HARVEY_SERVICE))
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(true)
                .build();

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

        if(bluetoothManager == null){
            return;
        }

        BluetoothLeAdvertiser bluetoothLeAdvertiser = bluetoothManager.getAdapter().getBluetoothLeAdvertiser();
        if(bluetoothLeAdvertiser == null){
            return;
        }

        bluetoothLeAdvertiser.startAdvertising(settings, advertiseData, advertiseData, new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                super.onStartSuccess(settingsInEffect);
                Log.i(TAG ,"广播发送成功");
            }

            @Override
            public void onStartFailure(int errorCode) {
                super.onStartFailure(errorCode);

                String errorReason = "";

                if(errorCode == ADVERTISE_FAILED_DATA_TOO_LARGE){
                    errorReason = "广播数据超过了31个字节";
                } else if(errorCode == ADVERTISE_FAILED_TOO_MANY_ADVERTISERS){
                    errorReason = "没有实例化广播";
                } else if(errorCode == ADVERTISE_FAILED_ALREADY_STARTED){
                    errorReason = "广播实例已准备就绪，启动失败";
                } else if(errorCode == ADVERTISE_FAILED_INTERNAL_ERROR){
                    errorReason = "内部错误";
                } else if(errorCode == ADVERTISE_FAILED_FEATURE_UNSUPPORTED){
                    errorReason = "平台不支持广播";
                } else {
                    errorReason = "未知";
                }

                Log.i(TAG,"广播发送失败 " + errorReason);
            }
        });
    }

    private void initView() {
        String title = "";
        if(deviceMode == BluetoothGattService.SERVICE_TYPE_PRIMARY){
            title = "中心设备模式 . "+mDevice.getName();
        } else {
            title = "周围设备模式 . "+mDevice.getName();
        }
        tvBar.setText(title);

        tvUUID.setText(serviceUuidStr);

        initDeviceBondState();

        if (mDevice.getType() == BluetoothDevice.DEVICE_TYPE_LE) {
            deviceType = "低功耗蓝牙";
        } else if (mDevice.getType() == BluetoothDevice.DEVICE_TYPE_CLASSIC) {
            deviceType = "经典蓝牙";
        } else if (mDevice.getType() == BluetoothDevice.DEVICE_TYPE_DUAL) {
            deviceType = "双模蓝牙";
        } else {
            deviceType = "未知";
        }

        bt_status.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (bond) {
                    mDevice.createBond();
                } else {
                    Toast.makeText(TestBLEMasterActivity.this, "已经配对，无需继续配对", Toast.LENGTH_SHORT).show();
                }

            }
        });

        bt_write_data.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bondState == BOND_BONDED){
                    if(characteristicWrite != null && gattServer != null){

                        String str = "中心设备默认问候您";

                        String editText = et_content.getEditableText().toString().trim();
                        if(!TextUtils.isEmpty(editText)){
                            str = editText;
                        }

                        str = CommonTools.getTimeStamp() + " " + str;

                        sendMessage(mDevice, characteristicWrite, str);

                    }
                }
            }
        });

        makeDeviceStateInfo();
    }

    /**
     * 组装蓝牙状态信息
     *
     */
    private void makeDeviceStateInfo(){
        deviceSB = new StringBuilder();
        deviceSB.append("设备名：").append(mDevice.getName()).append("\n\n")
                .append("MAC：").append(mDevice.getAddress()).append("\n\n")
                .append("连接状态：").append(deviceState).append("\n\n")
                .append("类型：").append(deviceType).append("\n\n")
                .append("Server UUID：").append(serviceUuidStr).append("\n\n");
    }

    private void initDeviceBondState() {
        bondState = mDevice.getBondState();

        if (bondState == BluetoothDevice.BOND_BONDED) {
            deviceState = "已配对";
            mDevice.fetchUuidsWithSdp();
        } else if (bondState == BluetoothDevice.BOND_BONDING) {
            deviceState = "配对中";
        } else {
            deviceState = "未配对";
        }

        initBondButton();

        if (bondState == BOND_BONDED && deviceMode == BluetoothGattService.SERVICE_TYPE_PRIMARY){
            createGATTService();
        }

    }

    private void initBondButton() {
        if (bondState==BOND_BONDED){
            bt_status.setText("连接");
            bt_status.setVisibility(View.GONE);

            bond = false;
        }else {
            bt_status.setText("配对");
            bond = true;
        }

    }

    StringBuilder deviceSB = new StringBuilder();

    private void bindView() {
        tv_deviceinfo = findViewById(R.id.tv_deviceinfo);
        tv_deviceinfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(v.getContext())
                        .setTitle("连接信息" )
                        .setMessage(deviceSB.toString())
                        .setCancelable(true)
                        .show();
            }
        });

        tvBar = findViewById(R.id.tvBar);

        tvUUID = findViewById(R.id.tvDeviceUUID);

        bt_status = findViewById(R.id.bt_status);

        bt_write_data = findViewById(R.id.bt_write_data);

        et_content = findViewById(R.id.et_content);

        tvSendContent = findViewById(R.id.tvSendContent);

        tvReceiveContent = findViewById(R.id.tvReceiveContent);
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_UUID);
        filter.addAction(ACTION_BOND_STATE_CHANGED);
        registerReceiver(bluetoothReceiver, filter);
    }

    BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            Log.i(TAG, "BroadcastReceiver = " + action);

            if (TextUtils.equals(action, ACTION_BOND_STATE_CHANGED)) {

                initDeviceBondState();
                makeDeviceStateInfo();

            }
        }
    };


    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(bluetoothReceiver);
    }

    //中心设备创建GATT服务
    BluetoothGattServer gattServer = null;
    BluetoothGattCharacteristic characteristicWrite = null;
    BluetoothGattCharacteristic characteristicNotify = null;

    private void createGATTService(){
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        //服务
        BluetoothGattService service = new BluetoothGattService(CustomBluetoothUUID.HARVEY_SERVICE,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        //特征
        characteristicWrite = new BluetoothGattCharacteristic(CustomBluetoothUUID.WRITE_MESSAGE_CHARACTERISTIC,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);

        //特征
        characteristicNotify = new BluetoothGattCharacteristic(CustomBluetoothUUID.NOTIFY_MESSAGE_CHARACTERISTIC,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);

        //服务添加特征
        service.addCharacteristic(characteristicWrite);
        service.addCharacteristic(characteristicNotify);

        gattServer = bluetoothManager.openGattServer(getApplicationContext(),
                mBluetoothGattServerCallback);

        gattServer.clearServices();
        gattServer.addService(service);
    }

    //中心设备创建GATT服务过程监听
    BluetoothGattServerCallback mBluetoothGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            Log.i(TAG,"onConnectionStateChange device : " + device + " status : " + status + " new state : " + newState);
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            super.onServiceAdded(status, service);
            Log.i(TAG,"onServiceAdded service : " + service.getUuid() + " status = " + status);
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);

            Log.i(TAG,"onCharacteristicReadRequest device : " + device.getAddress() + " request = " + requestId + " offset = " + offset
                            + " characteristic = " + characteristic.getUuid());

            String data = "tip1";
            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, data.getBytes(StandardCharsets.UTF_8));
        }

        /**
         * 接收远端往本地characteristic中写数据的请求
         * 接收到请求后，必须用 BluetoothGattServer.sendResponse 给予此次请求答复
         * @param device
         * @param requestId
         * @param characteristic
         * @param preparedWrite
         * @param responseNeeded
         * @param offset
         * @param value
         */
        @Override
        public void onCharacteristicWriteRequest(final BluetoothDevice device, final int requestId,
                                                 BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded,
                                                 final int offset, final byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded,
                    offset, value);
            Log.i(TAG,"onCharacteristicWriteRequest device : " + device.getAddress() + " characteristic : " + characteristic.getUuid() + " Value = " + new String(value));

            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);

            updateCommand(new String(value), "onCharacteristicWriteRequest", false);

        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset,
                                            BluetoothGattDescriptor descriptor) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor);
            Log.i(TAG,"onDescriptorReadRequest device : " + device.getAddress() + " request = " + requestId + " offset = " + offset + " descriptor = " + descriptor.getUuid());

            String data = "tip2";
            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, data.getBytes(StandardCharsets.UTF_8));

        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                                             BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset,
                                             byte[] value) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);

            Log.i(TAG,"onDescriptorWriteRequest device : " + device.getAddress() + " \n descriptor : " + descriptor.getUuid() + new String(value));

            String data = "tip3";
            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, data.getBytes(StandardCharsets.UTF_8));

        }

        @Override
        public void onNotificationSent(BluetoothDevice device, final int status) {
            super.onNotificationSent(device, status);
            Log.i(TAG,"onNotificationSent device : " + device.getAddress());

            String sendResult = "";
            if(status ==  BluetoothGatt.GATT_SUCCESS ){
                sendResult="发送成功";
            } else {
                sendResult="发送失败"+"("+status+")";
            }

            updateCommand(sendResult, "onNotificationSent", status !=  BluetoothGatt.GATT_SUCCESS);

        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            super.onExecuteWrite(device, requestId, execute);
            Log.i(TAG,"onExecuteWrite device : " + device.getAddress() + " request = " + requestId + " execute = " + true);

            String data = "tip4";
            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, data.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            super.onMtuChanged(device, mtu);
            Log.i(TAG,"onMtuChanged device : " + device.getAddress());

        }

        @Override
        public void onPhyUpdate(BluetoothDevice device, int txPhy, int rxPhy, int status) {
            super.onPhyUpdate(device, txPhy, rxPhy, status);
            Log.i(TAG,"onPhyUpdate device : " + device.getAddress());

        }

        @Override
        public void onPhyRead(BluetoothDevice device, int txPhy, int rxPhy, int status) {
            super.onPhyRead(device, txPhy, rxPhy, status);
            Log.i(TAG,"onPhyRead device : " + device.getAddress());
        }
    };

    //中心设备发送消息给已连接的设备
    public void sendMessage(BluetoothDevice device,BluetoothGattCharacteristic characteristic ,String data){
        if(TextUtils.isEmpty(data)){
            Log.e(TAG, "发送数据不能为空");
            return;
        }
        isSendding = true;
        lastSendContent = data;

        characteristic.setValue(data.getBytes());
        gattServer.notifyCharacteristicChanged(device, characteristic, true);
        
    }

    private void updateCommand(String data, String function, boolean isError){
        Message msg = new Message();
        msg.what=1;
        Bundle bundle = new Bundle();
        bundle.putString("function", function);
        bundle.putString("content", data);
        bundle.putBoolean("isError", isError);
        msg.setData(bundle);
        uiHandle.sendMessage(msg);
    }

    Handler uiHandle = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            int code = msg.what;

            Bundle bundle = msg.getData();

            String function = bundle.getString("function");
            String data = bundle.getString("content");
            if(code == 1){

                if(TextUtils.equals(function, "onNotificationSent")){
                    //发送信息
                    isSendding = false;
                    if(!bundle.getBoolean("isError")){
                        et_content.setText("");
                        tvSendContent.setText(lastSendContent);
                        lastSendContent = "";
                    }
                    Toast.makeText(TestBLEMasterActivity.this, data, Toast.LENGTH_LONG).show();

                } else if(TextUtils.equals(function, "onCharacteristicWriteRequest")){
                    //收到远端消息
                    tvReceiveContent.setText(data);
                    Toast.makeText(TestBLEMasterActivity.this, data, Toast.LENGTH_LONG).show();

                }

            }
        }

        @Override
        public void dispatchMessage(Message msg) {
            super.dispatchMessage(msg);
        }
    };
}
