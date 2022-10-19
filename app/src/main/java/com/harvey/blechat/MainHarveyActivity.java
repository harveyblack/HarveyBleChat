package com.harvey.blechat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainHarveyActivity extends AppCompatActivity {

    public static final String TAG = "MainHarveyActivity";

    public static final int REQUEST_BLE_OPEN = 1;

    private ListView lvBle;

    private TextView btnSearch;

    private CheckBox cb_master;

    private BluetoothAdapter mBluetoothAdapter;

    private BluetoothDevice selectedDevice;

    private List<DeviceListData> list;

    private CustomDeviceAdapter bleAdapter;

    public static final int REQUEST_ACCESS_COARSE_LOCATION_PERMISSION = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_harvey);

        isSupportBle();

        bindView();

        initData();

        initView();

        registerBluetoothReceiver();

        requestPermissions();

        startDiscovery();

        bondedDevice();
    }

    @Override
    protected void onResume() {
        super.onResume();
        beDiscovered();

    }

    /**
     * 已配对的设备
     */
    private void bondedDevice(){
        Set<BluetoothDevice> devices= mBluetoothAdapter.getBondedDevices();

        if(!devices.isEmpty()){
            DeviceListData data = new DeviceListData();
            data.isClick = false;
            data.isBondedDevice = true;
            data.des = "已配对的设备";
            list.add(data);
        }

        for(BluetoothDevice device : devices){
            DeviceListData data = new DeviceListData();
            data.isClick = true;
            data.bluetoothDevice = device;
            data.isBondedDevice = true;
            list.add(data);
        }

        bleAdapter.notifyDataSetChanged();
    }

    /**
     * Android 6.0 动态申请授权定位信息权限，否则扫描蓝牙列表为空
     */
    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    Toast.makeText(this, "使用蓝牙需要授权定位信息", Toast.LENGTH_LONG).show();
                }
                //请求权限
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        REQUEST_ACCESS_COARSE_LOCATION_PERMISSION);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_ACCESS_COARSE_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //用户授权
            } else {
                finish();
            }
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void registerBluetoothReceiver() {
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(bluetoothReceiver, filter);
    }

    private void isSupportBle() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        BluetoothManager manager= (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

        mBluetoothAdapter= manager.getAdapter();

        if (mBluetoothAdapter == null
                || !getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            showNotSupportBluetoothDialog();
            Log.e(TAG, "not support bluetooth");
        } else {
            Log.e(TAG, " support bluetooth");

        }
    }


    private void startDiscovery() {
        if (mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.startDiscovery();
        } else {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQUEST_BLE_OPEN);
        }
    }


    private void showNotSupportBluetoothDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("当前设备不支持蓝牙").create();
        dialog.show();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                finish();
            }
        });

    }

    private void initData() {
        list = new ArrayList<>();

        bleAdapter = new CustomDeviceAdapter(this, list);
    }

    private void bindView() {

        lvBle = findViewById(R.id.lvBle);

        btnSearch = findViewById(R.id.btnSearch);

        cb_master = findViewById(R.id.cb_master);
    }

    private void initView() {
        lvBle.setAdapter(bleAdapter);

        lvBle.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if(!list.get(position).isClick){
                    return;
                }

                btnSearch.setText("重新搜索");
                mBluetoothAdapter.cancelDiscovery();
                try {
                    unregisterReceiver(bluetoothReceiver);
                } catch (Throwable t){

                }

                selectedDevice = list.get(position).bluetoothDevice;

                //弹框选择主从模式
                Intent intent = null;

                if(cb_master.isChecked()){
                    //当前设备为中心设备
                    intent = new Intent(MainHarveyActivity.this, TestBLEMasterActivity.class);
                    intent.putExtra("device", selectedDevice);
                    intent.putExtra("devicemode", BluetoothGattService.SERVICE_TYPE_PRIMARY);
                } else {
                    //当前设备为外围设备
                    intent =new Intent(MainHarveyActivity.this, TestBLESlaveActivity.class);
                    intent.putExtra("device", selectedDevice);
                    intent.putExtra("devicemode", BluetoothGattService.SERVICE_TYPE_SECONDARY);
                }

                startActivity(intent);

            }
        });

        btnSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mBluetoothAdapter != null) {
                    btnSearch.setText("正在搜索...");

                    registerBluetoothReceiver();
                    beDiscovered();

                    list.clear();
                    bondedDevice();

                    mBluetoothAdapter.startDiscovery();
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                mBluetoothAdapter.startDiscovery();
            }
        }
    }

    BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (TextUtils.equals(action, BluetoothDevice.ACTION_FOUND)) {

                boolean isAddSubTitle = true;
                for(DeviceListData data:list){
                    if(!data.isBondedDevice){
                        isAddSubTitle = false;
                        break;
                    }
                }

                if(isAddSubTitle){
                    DeviceListData deviceListData = new DeviceListData();
                    deviceListData.isClick = false;
                    deviceListData.des = "可用设备";
                    list.add(deviceListData);
                }

                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                boolean isAdd = true;
                for(DeviceListData deviceListData:list){
                    if(deviceListData.bluetoothDevice != null && TextUtils.equals(deviceListData.bluetoothDevice.getAddress(), device.getAddress())){
                        isAdd = false;
                        break;
                    }
                }

                if(isAdd){
                    DeviceListData deviceListData = new DeviceListData();
                    deviceListData.isClick = true;
                    deviceListData.bluetoothDevice = device;
                    list.add(deviceListData);
                    bleAdapter.notifyDataSetChanged();
                    Log.i(TAG, "discovery device, name=" + device.getName() + " mac="+device.getAddress());
                }

            } else if (TextUtils.equals(action, BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
                btnSearch.setText("重新搜索");
                mBluetoothAdapter.cancelDiscovery();
                Log.i(TAG, "停止搜索");
                unregisterReceiver(bluetoothReceiver);
            }
        }
    };


    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(bluetoothReceiver);
        } catch (Throwable t){

        }
        super.onDestroy();
    }

    /**
     * 设置蓝牙可以被其他设备搜索到
     */
    private void beDiscovered() {
        if (mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120);
            startActivity(discoverableIntent);
        }
    }

}
