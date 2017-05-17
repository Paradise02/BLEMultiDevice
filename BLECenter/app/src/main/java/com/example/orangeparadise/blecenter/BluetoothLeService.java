package com.example.orangeparadise.blecenter;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.UUID;

public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private ArrayList<LeDevice> mLeDevices;
    private final static int MAX_LOAD = 7;
    public final static String DEVICE_INDEX = "DEVICE_INDEX";

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";
    private final static UUID UUID_SERVICE =
            UUID.fromString("00001111-0000-1000-8000-00805f9b34fb");
    private final static UUID UUID_READ_CHARACTERISTIC =
            UUID.fromString("00002222-0000-1000-8000-00805f9b34fb");    //property: READ
    private final static UUID UUID_WRITE_NO_RESPONSE_CHARACTERISTIC =
            UUID.fromString("00003333-0000-1000-8000-00805f9b34fb");

    private final IBinder mBinder = new LocalBinder();
    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        return mBinder;
    }
    public class LocalBinder extends Binder{
        BluetoothLeService getService() { return BluetoothLeService.this; }
    }

    @Override
    public boolean onUnbind(Intent intent){
        close();
        return super.onUnbind(intent);
    }
    private void close(){
        return;
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            //super.onConnectionStateChange(gatt, status, newState);
            String intentAction;
            int deviceIndex = -1;
            LeDevice mLeDevice = null;
            for (LeDevice mdevice: mLeDevices){
                if (mdevice.gatt.equals(gatt)){
                    deviceIndex = mdevice.index;
                    mLeDevice = mdevice;
                    break;
                }
            }
            if (mLeDevice == null || deviceIndex == -1){
                Log.e(TAG, "UNKNOWN DEVICE, FAIL TO CALL BACK");
            }   else {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    intentAction = ACTION_GATT_CONNECTED;
                    mLeDevice.connectionState = STATE_CONNECTED;
                    broadcastUpdate(intentAction, deviceIndex);
                    Log.i(TAG, "SERVICE DISCOVERY:" + gatt.discoverServices());
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED){
                    intentAction = ACTION_GATT_DISCONNECTED;
                    mLeDevice.connectionState = STATE_DISCONNECTED;
                    broadcastUpdate(intentAction, deviceIndex);
                } else {
                    Log.i(TAG, "UNKNOWN STATUS");
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            //super.onCharacteristicRead(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS){
                Log.w(TAG, "READ SUCCESSFULLY"+characteristic.getValue());
                int deviceIndex = -1;
                LeDevice mLeDevice = null;
                for (LeDevice mdevice: mLeDevices){
                    if (mdevice.gatt.equals(gatt)){
                        deviceIndex = mdevice.index;
                        mLeDevice = mdevice;
                        break;
                    }
                }
                if (mLeDevice == null || deviceIndex == -1){
                    Log.e(TAG, "UNKNOWN DEVICE, FAIL TO CALL BACK");
                } else {
                    broadcastUpdate(ACTION_DATA_AVAILABLE, deviceIndex, characteristic);
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
        }
    };

    private void broadcastUpdate(final String action, final int deviceIndex){
        final Intent intent = new Intent(action);
        intent.putExtra(DEVICE_INDEX, String.valueOf(deviceIndex));
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action, final int deviceIndex,
                                 final BluetoothGattCharacteristic characteristic){
        final Intent intent = new Intent(action);
        intent.putExtra(DEVICE_INDEX, String.valueOf(deviceIndex));
        if (UUID_READ_CHARACTERISTIC.equals(characteristic.getUuid())||
                UUID_WRITE_NO_RESPONSE_CHARACTERISTIC.equals(characteristic.getUuid())){
            int flag = characteristic.getProperties();
            Log.i(TAG, "characteristic property: "+flag);
            int format = -1;
            if ((flag & 0x01) != 0){
                format = BluetoothGattCharacteristic.FORMAT_UINT16;
                Log.d(TAG, "DATA FEATURE FORMAT UNIT16.");
            } else {
                format = BluetoothGattCharacteristic.FORMAT_UINT8;
                Log.d(TAG, "DATA FEATURE FORMAT UNIT8.");
            }
            final int feature = characteristic.getIntValue(format, 1);
            Log.d(TAG, String.format("Received DATA: %d", feature));
            intent.putExtra(EXTRA_DATA, String.valueOf(feature));
        }
        sendBroadcast(intent);
    }

    public void readCharacteristic() {
        if (mBluetoothAdapter == null){
            Log.w(TAG, "BluetoothAdapter not initialized.");
        }
        for (LeDevice mdevice: mLeDevices){
            if (mdevice.gatt != null && mdevice.connectionState == STATE_CONNECTED){
                BluetoothGattService mservice = null;
                for (BluetoothGattService service: mdevice.gatt.getServices()){
                    if (service.getUuid().equals(UUID_SERVICE)){
                        mservice = service;
                        break;
                    }
                }
                if (mservice != null){
                    BluetoothGattCharacteristic mcharacteristic = null;
                    for (BluetoothGattCharacteristic characteristic:
                            mservice.getCharacteristics()){
                        if (characteristic.getUuid().equals(UUID_READ_CHARACTERISTIC)){
                            mcharacteristic = characteristic;
                            mdevice.gatt.readCharacteristic(mcharacteristic);
                            break;
                        }
                    }
                }
            }
        }
    }

    public void writeWithoutResponse(byte[] text){
        if (mBluetoothAdapter == null){
            Log.w(TAG, "BluetoothAdapter not initialized.");
        }
        for (LeDevice mdevice: mLeDevices){
            if (mdevice.gatt != null && mdevice.connectionState == STATE_CONNECTED){
                BluetoothGattService mservice = null;
                for (BluetoothGattService service: mdevice.gatt.getServices()){
                    if (service.getUuid().equals(UUID_SERVICE)){
                        mservice = service;
                        break;
                    }
                }
                if (mservice != null){
                    BluetoothGattCharacteristic mcharacteristic = null;
                    for (BluetoothGattCharacteristic characteristic:
                            mservice.getCharacteristics()){
                        if (characteristic.getUuid().equals(UUID_WRITE_NO_RESPONSE_CHARACTERISTIC)){
                            mcharacteristic = characteristic;
                            mdevice.gatt.setCharacteristicNotification(mcharacteristic, true);
                            mcharacteristic.setValue(text);
                            mcharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                            mdevice.gatt.writeCharacteristic(characteristic);
                            break;
                        }
                    }
                }
            }
        }
    }

    public boolean initialization(){
        if (mBluetoothManager == null){
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null){
                Log.e(TAG, "Unable to initialize BluetoothAdapter");
                return false;
            }
        }
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null){
            Log.e(TAG, "Unable to initialize BluetoothAdapter");
            return false;
        }
        mLeDevices = new ArrayList<LeDevice>();
        return true;
    }

    public int connect(final String address, final int index){
        if (mBluetoothAdapter == null || address == null){
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address");
            return -1;
        }
        for (LeDevice mLeDevice:mLeDevices){
            if (mLeDevice.address != null && address.equals(mLeDevice.address)
                    && mLeDevice.gatt != null){
                mLeDevice.index = index;
                Log.d(TAG, "Try to reuse Gatt "+String.valueOf(mLeDevice.index));
                if (mLeDevice.gatt.connect()){
                    Log.i(TAG, "Gatt "+String.valueOf(mLeDevice.index)+" connecting");
                    mLeDevice.connectionState = STATE_CONNECTING;
                    return mLeDevice.index;
                }else {
                    Log.d(TAG, "fail to re-connect");
                    return -1;
                }
            }
        }   //re connect
        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null){
            Log.w(TAG, "Device not found. Unable to connect");
            return -1;
        }   //new device
        BluetoothGatt mgatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create new connection");
        String maddress = address;
        int mconnectionState = STATE_CONNECTING;
        Log.i(TAG, "Gatt "+String.valueOf(index)+" connecting");
        LeDevice mLeDevice = new LeDevice(index, mgatt, maddress, mconnectionState);
        UpdateDeviceList();
        mLeDevices.add(mLeDevice);
        return index;
    }
    public void disconnect(final int deviceIndex){
        if (mBluetoothAdapter == null){
            Log.w(TAG, "BluetoothAdapter not initialized.");
            return;
        }
        for (LeDevice mDevice: mLeDevices){
            if (mDevice.index == deviceIndex){
                if (mDevice.gatt == null){
                    Log.w(TAG, "BluetoothAdapter not initialized.");
                    return;
                }
                mDevice.gatt.disconnect();
                return;
            } else {
                Log.w(TAG, "UNKNOWN DEVICE");
            }
        }
    }

    private void UpdateDeviceList(){
        if (mLeDevices.size() >= MAX_LOAD){
            for (int i = 0; i < mLeDevices.size(); i++) {
                LeDevice mDevice = mLeDevices.get(i);
                if (mDevice.connectionState == STATE_DISCONNECTED){
                    mLeDevices.remove(i);
                    return;
                }
            }
        }
    }

    public class LeDevice{
        public int index;
        public BluetoothGatt gatt;
        public String address;
        public int connectionState;
        public LeDevice(int mindex, BluetoothGatt mgatt, String maddress, int mstate){
            this.index = mindex;
            this.gatt = mgatt;
            this.address = maddress;
            this.connectionState = mstate;
        }
    }
}
