package com.example.bluetoothrssi2.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.bluetoothrssi2.R;
import com.example.bluetoothrssi2.model.DiscoveredDevice;

import java.util.List;
import java.util.Locale;

public final class DeviceListAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final List<DiscoveredDevice> devices;

    public DeviceListAdapter(@NonNull Context context, @NonNull List<DiscoveredDevice> devices) {
        inflater = LayoutInflater.from(context);
        this.devices = devices;
    }

    @Override
    public int getCount() {
        return devices.size();
    }

    @Override
    public DiscoveredDevice getItem(int position) {
        return devices.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_discovered_device, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        DiscoveredDevice device = getItem(position);
        holder.name.setText(device.getName());
        holder.address.setText(device.getAddress());
        holder.bluetoothType.setText(device.getBluetoothType());
        holder.bluetoothClass.setText(device.getBluetoothClass());
        holder.rssi.setText(String.format(Locale.US, "%d dBm", device.getRssi()));
        return convertView;
    }

    private static final class ViewHolder {
        private final TextView name;
        private final TextView address;
        private final TextView bluetoothType;
        private final TextView bluetoothClass;
        private final TextView rssi;

        private ViewHolder(View view) {
            name = view.findViewById(R.id.deviceNameTextView);
            address = view.findViewById(R.id.deviceAddressTextView);
            bluetoothType = view.findViewById(R.id.deviceTypeTextView);
            bluetoothClass = view.findViewById(R.id.deviceClassTextView);
            rssi = view.findViewById(R.id.deviceRssiTextView);
        }
    }
}
