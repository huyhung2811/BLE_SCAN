package com.example.ble_scan;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import kotlin.text.Charsets;

public class MainActivity extends AppCompatActivity {

    private BluetoothLeAdvertiser bluetoothLeAdvertiser;//Sử dụng để quảng bá dữ liệu BLE
    private AdvertiseCallback advertiseCallback;//Lắng nghe sự kiện liên quan đến việc quảng bá
    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private BluetoothAdapter bluetoothAdapter;
    private List<BluetoothDevice> deviceList;
    private ArrayAdapter<String> arrayAdapter;
    private boolean scanning = false;
    private Handler handler;
    private ListView listBLE;
    private Button btScan;
    private TextView status;
    private static final UUID SERVICE_UUID = UUID.fromString("0000110B-0000-1000-8000-00805F9B34FB");

    ActivityResultLauncher<Intent> mActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == RESULT_OK) {
                        Toast.makeText(MainActivity.this, "Bluetooth OK", Toast.LENGTH_SHORT).show();
                        startAdvertising();
                    } else {
                        Toast.makeText(MainActivity.this, "Bluetooth not OK", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btScan = findViewById(R.id.button);
        listBLE = findViewById(R.id.listBLE);
        status = findViewById(R.id.textStatus);

        deviceList = new ArrayList<>();
        arrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, android.R.id.text1);
        listBLE.setAdapter(arrayAdapter);

        handler = new Handler();

        btScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(scanning){
                    stopBleScan();
                } else {
                    startBleScan();
                }
            }
        });

        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
        } else {
            setupBluetooth();
        }
    }
    private void setupBluetooth(){
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(bluetoothAdapter==null){
            Toast.makeText(this,"Bluetooth not supported",Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if(!bluetoothAdapter.isEnabled()){
            Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            mActivityResultLauncher.launch(enableBTIntent);
        } else {
            startAdvertising();
        }
    }

    private void startBleScan() {
        deviceList.clear();
        arrayAdapter.clear();
        arrayAdapter.notifyDataSetChanged();

        // Tạo ScanFilter với UUID dịch vụ
        ScanFilter scanFilter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(SERVICE_UUID))
                .build();

        ScanSettings scanSettings = new ScanSettings.Builder().build();

        bluetoothAdapter.getBluetoothLeScanner().startScan(Arrays.asList(scanFilter), scanSettings, scanCallback);

        scanning = true;
        btScan.setText("Stop Scan");
    }


    private void stopBleScan(){
        bluetoothAdapter.getBluetoothLeScanner().stopScan(scanCallback);
        scanning=false;
        btScan.setText("Start Scan");
        stopAdvertising();
    }

    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (device != null && !deviceList.contains(device)) {
                deviceList.add(device);
                String deviceName = device.getName();
                if (deviceName != null) {
                    arrayAdapter.add(deviceName);
                    arrayAdapter.notifyDataSetChanged();
                }

            }
            ParcelUuid parcelUuid = new ParcelUuid(SERVICE_UUID);
            ScanRecord scanRecord = result.getScanRecord();
            if (scanRecord != null) {
                byte[] serviceData = scanRecord.getServiceData(parcelUuid);
                if (serviceData != null) {
                    String message = new String(serviceData);
                    // Hiển thị thông điệp từ gói quảng bá
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            }

        }
    };

    //Bắt đầu quá trình quảng bá dữ liệu BLE
    private void startAdvertising() {
        //Kiểm tra thiết bị có hỗ trợ quảng bá không
        bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (bluetoothLeAdvertiser == null) {
            Toast.makeText(this, "Thiết bị không hỗ trợ chế độ quảng bá", Toast.LENGTH_SHORT).show();
            return;
        }

        final int timeoutMillis = 0;
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .setConnectable(true)
                .setTimeout(timeoutMillis)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                .build();
//        final long timeoutEndTime = System.currentTimeMillis() + timeoutMillis;
        String deviceName = BluetoothAdapter.getDefaultAdapter().getName();
        String message = "Hi";
        byte[] serviceData =message.getBytes(Charsets.UTF_8);
        ParcelUuid serviceUuid = new ParcelUuid(SERVICE_UUID);
        //Định nghĩa dữ liệu quảng bá
        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true) //Bao gồm tên không
                .addServiceUuid(serviceUuid) // Dịch vụ
                .addServiceData(serviceUuid, serviceData)
                .build();

        //Lắng nghe sự kiện
        advertiseCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                super.onStartSuccess(settingsInEffect);
                status.setText("On");
                Toast.makeText(MainActivity.this, "Đã bắt đầu chế độ quảng bá", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onStartFailure(int errorCode) {
                super.onStartFailure(errorCode);
                Log.d("Error1", String.valueOf(errorCode));

                String errorMessage;

                switch (errorCode) {
                    case AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED:
                        errorMessage = "Failed to start advertising as the advertising is already started.";
                        break;
                    case AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE:
                        errorMessage = "Failed to start advertising as the advertise data to be broadcasted is too large.";
                        break;
                    case AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                        errorMessage = "This feature is not supported on this platform.";
                        break;
                    case AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR:
                        errorMessage = "Operation failed due to an internal error.";
                        break;
                    case AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                        errorMessage = "Failed to start advertising because no advertising instance is available.";
                        break;
                    default:
                        errorMessage = "Unknown error occurred.";
                        break;
                }

                status.setText("Fail");
                Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
            }
        };
        bluetoothLeAdvertiser.startAdvertising(settings, data, advertiseCallback);
//        handler.postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                long currentTime = System.currentTimeMillis();
//                if (currentTime >= timeoutEndTime) {
//                    stopAdvertising();
//                    status.setText("Off");
//                } else {
//                    // Thời gian chờ chưa kết thúc, tiếp tục đếm ngược
//                    long remainingTime = timeoutEndTime - currentTime;
//                    handler.postDelayed(this, remainingTime);
//                }
//            }
//        }, timeoutMillis);
    }

    private void stopAdvertising() {
        if (bluetoothLeAdvertiser != null && advertiseCallback != null) {
            bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupBluetooth();
            } else {
                Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
}
