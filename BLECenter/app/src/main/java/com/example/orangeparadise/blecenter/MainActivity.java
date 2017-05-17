package com.example.orangeparadise.blecenter;

import android.app.ListActivity;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringTokenizer;

public class MainActivity extends ListActivity{
    final String TAG = "MainActivity";
    private DeviceAdapter mDeviceAdapter;
    private Button scanButton;
    private Button readButton;
    private Button writeButton;
    private EditText writeText;
    private boolean mScanning = true;
    private BluetoothAdapter mBluetoothAdapter;
    private Handler mHandler;
    private static final long SCAN_PERIOD = 10000;
    private static final int CONNECTED = 2;
    private static final int CONNECTING = 1;
    private static final int DISCONNECTED = 0;
    private BluetoothLeService mBluetoothLeService;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service) .getService();
            if (!mBluetoothLeService.initialization()){
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {mBluetoothLeService=null;}
    };
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final String deviceIndex = intent.getStringExtra(BluetoothLeService.DEVICE_INDEX);
            Log.i(TAG, "Broadcast received. "+action+" from device"+deviceIndex);
            int position = Integer.parseInt(deviceIndex);
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)){
                String info = "CONNECTED";
                mDeviceAdapter.modifyInfo(position, info, CONNECTED);
                refreshView();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)){
                String info = "DISCONNECTED";
                mDeviceAdapter.modifyInfo(position, info, DISCONNECTED);
                refreshView();
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)){
                final String extraData = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                String info = extraData;
                mDeviceAdapter.modifyInfo(position, info, CONNECTED);
                refreshView();
            }
        }
    };
    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mHandler = new Handler();
        scanButton = (Button) findViewById(R.id.scan_btn);
        readButton = (Button) findViewById(R.id.read_btn);
        writeButton = (Button) findViewById(R.id.write_btn);
        writeText = (EditText) findViewById(R.id.edit_text_write);
        mDeviceAdapter = new DeviceAdapter();

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)){
            Toast.makeText(this, "BLE not Supported", Toast.LENGTH_SHORT).show();
            finish();
        }
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if (mBluetoothAdapter == null){
            Toast.makeText(this, "BLE not Supported", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        Intent mainActivityIntent = new Intent(this, BluetoothLeService.class);
        bindService(mainActivityIntent, mServiceConnection, BIND_AUTO_CREATE);

        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanLeDevice(mScanning);
            }
        });

        readButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //mDeviceAdapter.cleanDevice();
                //mDeviceAdapter.addDevice("device1","address","connected");
                //setListAdapter(mDeviceAdapter);
                //refreshView();
                mBluetoothLeService.readCharacteristic();
            }
        });

        writeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //mDeviceAdapter.modifyInfo(0,"connecting",CONNECTING);
                //refreshView();
                byte[] text = writeText.getText().toString().getBytes();
                Log.w(TAG, "receive editText content: " + writeText.getText());
                mBluetoothLeService.writeWithoutResponse(text);
            }
        });
    }

    private void refreshView(){
        setListAdapter(mDeviceAdapter);
    }

    private void scanLeDevice(final boolean enable){
        if (enable){
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = true;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    scanButton.setText("scan");
                }
            },SCAN_PERIOD);
            Log.i(TAG, "SCANNING......");
            mDeviceAdapter.cleanDevice();
            refreshView();
            scanButton.setText("stop");
            mBluetoothAdapter.startLeScan(mLeScanCallback);
            mScanning = false;
        }else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            scanButton.setText("scan");
            mScanning = true;
        }
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!mDeviceAdapter.containDevice(device)){
                        mDeviceAdapter.addDevice(device);
                        //Log.i("!!Device number: ", String.valueOf(mDeviceAdapter.getCount()));
                        refreshView();
                    }
                }
            });
        }
    };

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id){
        // TODO: 2017/5/17
    }
    
    @Override
    protected void onResume(){
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        // TODO: 2017/5/17 re-connect
    }
    @Override
    protected void onPause(){
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }
    @Override
    protected void onDestroy(){
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    private IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        return intentFilter;
    }

    public final class ViewHolder{
        public TextView Name;
        public TextView Info;
        public Button btn;
    }

    private class DeviceAdapter extends BaseAdapter{
        private LayoutInflater mInflater;
        private List<Map<String, Object>> mlist;
        private static final int MAX_LOAD = 7;
        public DeviceAdapter(){
            super();
            this.mlist = new ArrayList<Map<String, Object>>();
            mInflater = MainActivity.this.getLayoutInflater();
        }
        public void addDevice(String name, String address, String info){
            // TODO: 2017/5/17
            if (mlist.size()>= 7) return;
            Map<String, Object>map = new HashMap<String, Object>();
            map.put("Name", name);
            map.put("Info", info);
            map.put("Address", address);
            map.put("State", DISCONNECTED);
            if (!mlist.contains(map)){
                mlist.add(map);
            }
        }
        public void modifyInfo(int position, String info, int state){
            Map<String, Object>map = mlist.get(position);
            map.put("Info", info);
            map.put("State", state);
            mlist.set(position, map);
        }
        public void addDevice(BluetoothDevice device){
            if (mlist.size()>=MAX_LOAD) return;
            String name = device.getName();
            String address = device.getAddress();
            Map<String, Object>map = new HashMap<String, Object>();
            map.put("Name", name);
            map.put("Info", address);
            map.put("Address", address);
            map.put("State", DISCONNECTED);
            if (!mlist.contains(map)){
                mlist.add(map);
            }
        }
        public boolean containDevice(BluetoothDevice device){
            String address = device.getAddress();
            for (Map<String, Object>map:mlist){
                if (map.get("Address") == address) {return true;}
            }
            return false;
        }

        public void cleanDevice(){
            Log.i(TAG, "clean device list");
            if (mlist != null) {
                Log.i(TAG, "cleaning");
                mlist.clear();
            }
        }

        @Override
        public int getCount() {
            return mlist.size();
        }

        @Override
        public Object getItem(int position) {
            return mlist.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            ViewHolder holder = null;
            if (convertView == null){
                holder=new ViewHolder();
                convertView = mInflater.inflate(R.layout.vlist, null);
                holder.Name = (TextView) convertView.findViewById(R.id.name);
                holder.Info = (TextView) convertView.findViewById(R.id.info);
                holder.btn = (Button) convertView.findViewById(R.id.view_btn);
                convertView.setTag(holder);
            }else {
                holder = (ViewHolder)convertView.getTag();
            }
            final String address = String.valueOf(mlist.get(position).get("Address"));
            holder.Name.setText(String.valueOf(mlist.get(position).get("Name")));
            holder.Info.setText(String.valueOf(mlist.get(position).get("Info")));
            final int state = (int) mlist.get(position).get("State");
            switch (state){
                case CONNECTED:
                    //Log.i(TAG, String.valueOf(position)+"CONNECTED");
                    holder.btn.setText("disconnect");
                    break;
                case CONNECTING:
                    //Log.i(TAG, String.valueOf(position)+"CONNECTING");
                    holder.btn.setText("disconnect");
                    holder.Info.setText("connecting");
                    break;
                case DISCONNECTED:
                    //Log.i(TAG, String.valueOf(position)+"DISCONNECTED");
                    holder.btn.setText("connect");
                    break;
            }
            //holder.btn.setText("connect");
            holder.btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.i(TAG, String.valueOf(position)+"clicked");
                    if (state == DISCONNECTED){
                        // TODO: 2017/5/17 CONNECT
                        Log.i(TAG, "TRYING TO CONNECT");
                        mBluetoothLeService.connect(address, position);
                    } else {
                        // TODO: 2017/5/17 DISCONNECT
                        Log.i(TAG, "TRYING TO DISCONNECT");
                        mBluetoothLeService.disconnect(position);
                    }
                }
            });
            return convertView;
        }
    }
}