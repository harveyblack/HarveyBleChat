package com.harvey.blechat;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

public class CustomDeviceAdapter extends BaseAdapter {

    private Context context;

    private List<DeviceListData> list;

    public CustomDeviceAdapter(Context context, List<DeviceListData> list) {
        this.context = context;
        this.list = list;
    }


    @Override
    public int getCount() {
        return list.size();
    }

    @Override
    public Object getItem(int position) {
        return list.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder holder = null;
        if (convertView == null) {
            holder = new Holder();
            convertView = LayoutInflater.from(context).inflate(R.layout.adapter_ble, null, false);
            holder.tvName = convertView.findViewById(R.id.tvBle);
            convertView.setTag(holder);
        } else {
            holder = (Holder) convertView.getTag();
        }

        BluetoothDevice device = list.get(position).bluetoothDevice;

        String name = "";

        if(!list.get(position).isClick){
            name = list.get(position).des;
            convertView.setBackgroundColor(Color.LTGRAY);
        } else {
            convertView.setBackgroundColor(Color.WHITE);
            if (device.getName() == null) {
                name = device.getAddress();
            } else {
                name = "(" +device.getName() + ")" + device.getAddress();
            }
        }
        holder.tvName.setText(name);

        return convertView;
    }

    class Holder {
        TextView tvName;
    }
}
