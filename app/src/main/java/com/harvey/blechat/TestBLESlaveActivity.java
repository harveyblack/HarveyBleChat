package com.harvey.blechat;

import static android.bluetooth.BluetoothDevice.ACTION_BOND_STATE_CHANGED;
import static android.bluetooth.BluetoothDevice.BOND_BONDED;
import static com.harvey.blechat.CustomBluetoothUUID.NOTIFY_MESSAGE_CHARACTERISTIC;
import static com.harvey.blechat.CustomBluetoothUUID.WRITE_MESSAGE_CHARACTERISTIC;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
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
import java.util.List;


public class TestBLESlaveActivity extends Activity {
    private static final String TAG = "TestBLESlaveActivity";

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

    private String serviceUuidStr = "";
    //当前设配配对状态
    private int bondState;
    //button的状态，如果已配对为false,未配对为true，先配置对后连接
    private boolean bond = false;

    BluetoothGatt mBluetoothGatt = null;

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
                    Toast.makeText(TestBLESlaveActivity.this, "已经配对，无需继续配对", Toast.LENGTH_SHORT).show();
                }

            }
        });

        bt_write_data.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bondState == BOND_BONDED){
                    String str = "客户端问候您";

                    String editText = et_content.getEditableText().toString().trim();
                    if(!TextUtils.isEmpty(editText)){
                        str = editText;
                    }

                    str = CommonTools.getTimeStamp() + " " + str;
                    sendMessage(str);
                }
            }
        });

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

        if (bondState == BOND_BONDED && deviceMode == BluetoothGattService.SERVICE_TYPE_SECONDARY){
            mDevice.fetchUuidsWithSdp();
            connectGatt();
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
        filter.addAction(ACTION_BOND_STATE_CHANGED);
        registerReceiver(bluetoothReceiver, filter);
    }

    BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            Log.i(TAG, action);

            if (TextUtils.equals(action, ACTION_BOND_STATE_CHANGED)) {
                initDeviceBondState();
            }
        }
    };

    @Override
    protected void onDestroy() {
        if(mBluetoothGatt != null){
            mBluetoothGatt.disconnect();
        }

        unregisterReceiver(bluetoothReceiver);
        super.onDestroy();
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

    //外围设备连接中心设备
    private void connectGatt() {
        if (mDevice != null){
            mBluetoothGatt = mDevice.connectGatt(TestBLESlaveActivity.this, false, mGattCallback);
        } else {
            Toast.makeText(TestBLESlaveActivity.this, "没有找到指定的蓝牙设备，无法建立GATT", Toast.LENGTH_LONG).show();
        }
    }

    //外围设备连接中心设备过程监听
    BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
            Log.i(TAG, "onReadRemoteRssi " + rssi);
        }

        @Override
        public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
            super.onPhyUpdate(gatt, txPhy, rxPhy, status);
            Log.i(TAG, "onPhyUpdate " + status);

        }

        @Override
        public void onPhyRead(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
            super.onPhyRead(gatt, txPhy, rxPhy, status);
            Log.i(TAG, "onPhyRead " + status);

        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            Log.i(TAG, "onMtuChanged " + status);
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.i(TAG, "onConnectionStateChange newstate:" + newState + " status:" + status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "============>GATT Connect Success！！<=============");
                    mBluetoothGatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i(TAG,"============>GATT Disconnected！！<=============");
                    if (mBluetoothGatt != null) {
                        mBluetoothGatt.close();
                        mBluetoothGatt = null;
                    }
                } else {
                    Log.i(TAG, "onConnectionStateChange 新状态: " + newState);
                }
            } else if(status == BluetoothGatt.GATT_FAILURE){
                Log.i(TAG, "============>GATT 连接失败<=============");
            } else if(status == BluetoothGatt.GATT_CONNECTION_CONGESTED){
                Log.i(TAG, "============>出现拥堵<=============");
            } else {
                Log.i(TAG, "onConnectionStateChange 状态: " + status);

                if(status == 133)
                    if(mBluetoothGatt != null){
                        mBluetoothGatt.disconnect();
                        connectGatt();
                    }
            }

        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            Log.i(TAG, "onServicesDiscovered" + " "+ ((status ==  BluetoothGatt.GATT_SUCCESS) ? "成功" : "失败"));

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "连接远端服务成功");

                mBluetoothGatt = gatt;
                List<BluetoothGattService> newServices = gatt.getServices();

                int isMatchCentral = 0;

                for(BluetoothGattService service : newServices){

                    if(TextUtils.equals(service.getUuid().toString(), CustomBluetoothUUID.HARVEY_SERVICE.toString())){
                        isMatchCentral++;
                        serviceUuidStr = CustomBluetoothUUID.HARVEY_SERVICE.toString();
                    }

                    Log.i(TAG, "service:"+service.getUuid().toString());
                    List<BluetoothGattCharacteristic> characteristics =  service.getCharacteristics();
                    for(BluetoothGattCharacteristic characteristic : characteristics){

                        if(isMatchCentral != 0 ){
                            if (TextUtils.equals(characteristic.getUuid().toString(), CustomBluetoothUUID.WRITE_MESSAGE_CHARACTERISTIC.toString())) {
                                isMatchCentral++;
                            } else if(TextUtils.equals(characteristic.getUuid().toString(), CustomBluetoothUUID.NOTIFY_MESSAGE_CHARACTERISTIC.toString())){
                                isMatchCentral++;
                            }
                        }

                        Log.i(TAG, "   characteristic:" + characteristic.getUuid().toString());

                        if(TextUtils.equals(characteristic.getUuid().toString(), WRITE_MESSAGE_CHARACTERISTIC.toString())){
                            mBluetoothGatt.setCharacteristicNotification(characteristic, true);
                        } else if(TextUtils.equals(characteristic.getUuid().toString(), NOTIFY_MESSAGE_CHARACTERISTIC.toString())){
                            mBluetoothGatt.setCharacteristicNotification(characteristic, true);
                        }

                        String logInfo = "   characteristic properties = ";

                        if((BluetoothGattCharacteristic.PROPERTY_BROADCAST & characteristic.getProperties()) == BluetoothGattCharacteristic.PROPERTY_BROADCAST){
                            logInfo = logInfo + "PROPERTY_BROADCAST";
                        }
                        if((BluetoothGattCharacteristic.PROPERTY_READ & characteristic.getProperties()) == BluetoothGattCharacteristic.PROPERTY_READ){
                            logInfo = logInfo + (logInfo.contains("PROPERTY_") ? " | " : "") + "PROPERTY_READ";
                        }
                        if((BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE & characteristic.getProperties()) == BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE){
                            logInfo = logInfo + (logInfo.contains("PROPERTY_") ? " | " : "") + "PROPERTY_WRITE_NO_RESPONSE";
                        }
                        if((BluetoothGattCharacteristic.PROPERTY_WRITE & characteristic.getProperties()) == BluetoothGattCharacteristic.PROPERTY_WRITE){
                            logInfo = logInfo + (logInfo.contains("PROPERTY_") ? " | " : "") + "PROPERTY_WRITE";
                        }
                        if((BluetoothGattCharacteristic.PROPERTY_NOTIFY & characteristic.getProperties()) == BluetoothGattCharacteristic.PROPERTY_NOTIFY){
                            logInfo = logInfo + (logInfo.contains("PROPERTY_") ? " | " : "") + "PROPERTY_NOTIFY";
                        }
                        if((BluetoothGattCharacteristic.PROPERTY_INDICATE & characteristic.getProperties()) == BluetoothGattCharacteristic.PROPERTY_INDICATE){
                            logInfo = logInfo + (logInfo.contains("PROPERTY_") ? " | " : "") + "PROPERTY_INDICATE";
                        }
                        if((BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE & characteristic.getProperties()) == BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE){
                            logInfo = logInfo + (logInfo.contains("PROPERTY_") ? " | " : "") + "PROPERTY_SIGNED_WRITE";
                        }
                        if((BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS & characteristic.getProperties()) == BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS){
                            logInfo = logInfo + (logInfo.contains("PROPERTY_") ? " | " : "") + "PROPERTY_EXTENDED_PROPS";
                        }

                        Log.i(TAG, logInfo);

                        List<BluetoothGattDescriptor> descriptors =  characteristic.getDescriptors();
                        for(BluetoothGattDescriptor descriptor:descriptors){
                            Log.i(TAG, "      descriptor:"+descriptor.getUuid().toString());
                        }

                    }
                }

                final int finalIsMatchCentral = isMatchCentral;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(finalIsMatchCentral >= 2){
                            Toast.makeText(TestBLESlaveActivity.this, "可以开始通信",Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(TestBLESlaveActivity.this, "无法通信 "+"（"+finalIsMatchCentral+"）",Toast.LENGTH_SHORT).show();
                        }
                        tvUUID.setText(serviceUuidStr);
                        makeDeviceStateInfo();
                    }
                });

            }
        }

        //Callback reporting the result of a characteristic read operation.
        //报告读取特征值的结果
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            Log.i(TAG, "onCharacteristicRead:" + characteristic.getUuid().toString() + " "+ ((status ==  BluetoothGatt.GATT_SUCCESS) ? "成功" : "失败"));

            if (status == BluetoothGatt.GATT_SUCCESS) {
                final String value = new String(characteristic.getValue()).trim().replace(" ", "");
                Log.i(TAG, "=====>读取到 value =" + value + " 描述="+characteristic.getDescriptor(WRITE_MESSAGE_CHARACTERISTIC));
            } else {
                if (mBluetoothGatt != null){
                    Log.e(TAG, "读取失败， 断开连接");
                    mBluetoothGatt.disconnect();
                }
            }
        }

        //Callback indicating the result of a descriptor write operation.
        //标示一个描述写完值的结果
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                                      int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            Log.i(TAG, "onDescriptorWrite" + descriptor.getUuid().toString() + " "+ ((status ==  BluetoothGatt.GATT_SUCCESS) ? "成功" : "失败"));

            mBluetoothGatt.writeDescriptor(descriptor);
        }

        //Callback reporting the result of a descriptor read operation
        //报告读取描述值的结果
        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                                     int status) {
            super.onDescriptorRead(gatt, descriptor, status);
            Log.i(TAG, "onDescriptorWrite" + descriptor.getUuid().toString() + " "+ ((status ==  BluetoothGatt.GATT_SUCCESS) ? "成功" : "失败"));

            mBluetoothGatt.readDescriptor(descriptor);
        }

        //Callback invoked when a reliable write transaction has been completed.
        //当一次可靠的传输完成后，会被调用
        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            super.onReliableWriteCompleted(gatt, status);
            Log.i(TAG, "onReliableWriteCompleted" + " "+ ((status ==  BluetoothGatt.GATT_SUCCESS) ? "成功" : "失败"));

        }

        //Callback indicating the result of a characteristic write operation.
        //标示一个特征写完值的结果
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, final int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.i(TAG, "onCharacteristicWrite:" + characteristic.getUuid().toString() + " "+ ((status ==  BluetoothGatt.GATT_SUCCESS) ? "成功" : "失败"));

            isSendding = false;

            String sendContent = new String(characteristic.getValue());

            if (status == BluetoothGatt.GATT_SUCCESS){
                sendContent = "发送成功 : " + sendContent;
            } else {
                sendContent = "发送失败 : " + sendContent;
            }

            Log.i(TAG, sendContent);

            updateCommand(sendContent, "onCharacteristicWrite", status != BluetoothGatt.GATT_SUCCESS);

        }

        //Callback triggered as a result of a remote characteristic notification.
        //由远程特征通知触发
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic){
            super.onCharacteristicChanged(gatt, characteristic);
            Log.i(TAG, "onCharacteristicChanged:" + characteristic.getUuid().toString());

            String data = new String(characteristic.getValue());

            updateCommand(data, "onCharacteristicChanged", false);
        }

    };

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

    //外围设备发送消息给中心设备
    public boolean sendMessage(String data){
        if(isSendding){
            Toast.makeText(TestBLESlaveActivity.this, "请稍后", Toast.LENGTH_SHORT).show();
            return false;
        }

        if(mBluetoothGatt == null){
            Log.i(TAG, "不存在 mBluetoothGatt");
            return false;
        }

        BluetoothGattService service = mBluetoothGatt.getService(CustomBluetoothUUID.HARVEY_SERVICE);
        if (service == null){
            Log.i(TAG, "不存在 "+CustomBluetoothUUID.HARVEY_SERVICE);
            Toast.makeText(TestBLESlaveActivity.this, "未能获取到service:"+CustomBluetoothUUID.HARVEY_SERVICE, Toast.LENGTH_SHORT).show();
            return false;
        }

        BluetoothGattCharacteristic characteristic = service.getCharacteristic(CustomBluetoothUUID.WRITE_MESSAGE_CHARACTERISTIC);
        if(characteristic != null){
            isSendding = true;
            lastSendContent = data;

            mBluetoothGatt.setCharacteristicNotification(characteristic, true);
            characteristic.setValue(data.getBytes(StandardCharsets.UTF_8));
            mBluetoothGatt.writeCharacteristic(characteristic);

        } else {
            Log.i(TAG, "不存在 "+ WRITE_MESSAGE_CHARACTERISTIC);
            Toast.makeText(TestBLESlaveActivity.this, "未能获取到charactertic:"+WRITE_MESSAGE_CHARACTERISTIC, Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    Handler uiHandle = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            int code = msg.what;

            Bundle bundle = msg.getData();

            String function = bundle.getString("function");
            String data = bundle.getString("content");
            if(code == 1){

                if(TextUtils.equals(function, "onCharacteristicChanged")){
                    //收到远端信息
                    Log.i(TAG, "接收到新消息：" + data);
                    tvReceiveContent.setText(data);
                    Toast.makeText(TestBLESlaveActivity.this.getApplicationContext(), data, Toast.LENGTH_LONG).show();

                } else if(TextUtils.equals(function, "onCharacteristicWrite")){
                    //发送消息结果
                    if(!bundle.getBoolean("isError")){
                        et_content.setText("");
                        tvSendContent.setText(lastSendContent);
                        lastSendContent = "";
                    }
                    Toast.makeText(TestBLESlaveActivity.this.getApplicationContext(), data, Toast.LENGTH_LONG).show();
                }
            }
        }

        @Override
        public void dispatchMessage(Message msg) {
            super.dispatchMessage(msg);
        }
    };

}
