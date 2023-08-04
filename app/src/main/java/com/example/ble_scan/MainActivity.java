package com.example.ble_scan;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import kotlin.text.Charsets;

public class MainActivity extends AppCompatActivity {

    private BluetoothLeAdvertiser bluetoothLeAdvertiser;
    private AdvertiseCallback advertiseCallback;
    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private BluetoothAdapter bluetoothAdapter;
    private List<BluetoothDevice> deviceList;
    private ArrayAdapter<String> arrayAdapter;
    private boolean scanning = false;
    private BluetoothGatt bluetoothGatt;

    private Handler handler;
    private ListView listBLE;
    private Button btStartServer;
    private Button btStartScan;
    private TextView status;
    private static final UUID SERVICE_UUID = UUID.fromString("00000001-0000-1000-8000-00805F9B34FB");
    private static final UUID WRITE_CHARACTERISTIC_UUID = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb");
    private static final UUID READ_CHARACTERISTIC_UUID = UUID.fromString("00001801-0000-1000-8000-00805f9b34fb");

    ActivityResultLauncher<Intent> mActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == RESULT_OK) {
                        Toast.makeText(MainActivity.this, "Bluetooth OK", Toast.LENGTH_SHORT).show();
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

        btStartServer = findViewById(R.id.btStartServer);
        btStartScan = findViewById(R.id.btStartScan);
        listBLE = findViewById(R.id.listBLE);
        status = findViewById(R.id.textStatus);

        deviceList = new ArrayList<>();
        arrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, android.R.id.text1);
        listBLE.setAdapter(arrayAdapter);

        handler = new Handler();

        btStartServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (bluetoothLeAdvertiser != null) {
                    stopAdvertising();
                    btStartServer.setText("Start server");
                } else {
                    setupBluetooth();
                }
            }
        });

        btStartScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (scanning) {
                    stopBleScan();
                } else {
                    startBleScan();
                }
            }
        });

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
        } else {
                setupBluetooth();
        }
    }

    private void setupBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            mActivityResultLauncher.launch(enableBTIntent);
        } else {
            startAdvertising();
        }
    }

    private void startAdvertising() {
        bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        BluetoothGattService gattService = new BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        if (bluetoothLeAdvertiser == null) {
            Toast.makeText(this, "Device does not support advertising", Toast.LENGTH_SHORT).show();
            return;
        }

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                .build();

        BluetoothGattCharacteristic readCharacteristic = new BluetoothGattCharacteristic(
                READ_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
        );

        // Tạo một characteristic để GATT server nhận dữ liệu từ GATT client
        BluetoothGattCharacteristic writeCharacteristic = new BluetoothGattCharacteristic(
                WRITE_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
        );

        gattService.addCharacteristic(readCharacteristic);
        gattService.addCharacteristic(writeCharacteristic);

        String deviceName = BluetoothAdapter.getDefaultAdapter().getName();
        String message = "Hello";
        byte[] serviceData = message.getBytes(Charsets.UTF_8);
        ParcelUuid serviceUuid = new ParcelUuid(SERVICE_UUID);

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(serviceUuid)
                .build();

        advertiseCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                super.onStartSuccess(settingsInEffect);
                status.setText("Server Advertising: ON");
                Toast.makeText(MainActivity.this, "Advertising started", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onStartFailure(int errorCode) {
                super.onStartFailure(errorCode);
                Log.e("Start Advertising", "Advertising onStartFailure: " + errorCode);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        status.setText("Server Advertising: OFF");
                    }
                });
            }
        };
        bluetoothLeAdvertiser.startAdvertising(settings, data, advertiseCallback);
    }

    private void stopAdvertising() {
        if (bluetoothLeAdvertiser != null && advertiseCallback != null) {
            bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
            bluetoothLeAdvertiser = null;
            status.setText("Server Advertising: OFF");
            Toast.makeText(MainActivity.this, "Advertising stopped", Toast.LENGTH_SHORT).show();
        }
    }

    private void startBleScan() {
        deviceList.clear();
        arrayAdapter.clear();
        arrayAdapter.notifyDataSetChanged();

        ScanFilter scanFilter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(SERVICE_UUID))
                .build();

        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .setReportDelay(0)
                .setMatchMode(ScanSettings.MATCH_MODE_STICKY)
                .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build();

        bluetoothAdapter.getBluetoothLeScanner().startScan(Arrays.asList(scanFilter), scanSettings, scanCallback);

        scanning = true;
        btStartScan.setText("Stop Scan");
    }

    private void stopBleScan() {
        bluetoothAdapter.getBluetoothLeScanner().stopScan(scanCallback);
        scanning = false;
        btStartScan.setText("Start Scan");
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
                connectToDevice(device);
            }
        }
    };

    private void connectToDevice(BluetoothDevice device) {
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
    }

    private void writeDataToServer() {
        if (bluetoothGatt == null) {
            return;
        }

        // Find the characteristic to write data (with the corresponding UUID)
        BluetoothGattService gattService = bluetoothGatt.getService(SERVICE_UUID);
        if (gattService == null) {
            return;
        }

        BluetoothGattCharacteristic writeCharacteristic = gattService.getCharacteristic(WRITE_CHARACTERISTIC_UUID);
        if (writeCharacteristic == null) {
            return;
        }

        byte[] dataToSend = "Hello from client".getBytes(Charsets.UTF_8);
        writeCharacteristic.setValue(dataToSend);
        bluetoothGatt.writeCharacteristic(writeCharacteristic);
    }
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // Khi đã kết nối với thiết bị BLE
                Log.d("BLE", "Connected to GATT server.");
                if (gatt.getDevice().getType() == BluetoothDevice.DEVICE_TYPE_CLASSIC) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Đã kết nối với GATT server.", Toast.LENGTH_SHORT).show();
                            showConnectedNotificationToServer();
                        }
                    });
                } else if (gatt.getDevice().getType() == BluetoothDevice.DEVICE_TYPE_LE) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Đã kết nối với GATT client.", Toast.LENGTH_SHORT).show();
                            showConnectedNotificationToClient();
                        }
                    });
                }

                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // Khi đã ngắt kết nối với thiết bị BLE
                Log.d("BLE", "Disconnected from GATT server.");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Khi đã tìm thấy các dịch vụ BLE trên thiết bị
                List<BluetoothGattService> services = gatt.getServices();
                for (BluetoothGattService service : services) {
                    Log.d("BLE", "Service discovered: " + service.getUuid());
                    // Ở đây, bạn có thể thực hiện các hoạt động liên quan đến các dịch vụ BLE tìm thấy.
                }
            } else {
                Log.e("BLE", "onServicesDiscovered error: " + status);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
//            if (status == BluetoothGatt.GATT_SUCCESS) {
//                byte[] dataReceived = characteristic.getValue();
//                if (dataReceived != null) {
//                    String messageReceived = new String(dataReceived, Charsets.UTF_8);
//
//                    Log.d("BLE", "Received message: " + messageReceived);
//                }
//            } else {
//                Log.e("BLE", "Error onCharacteristicWrite: " + status);
//            }
        }
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                byte[] dataReceived = characteristic.getValue();
                if (dataReceived != null) {
                    String messageReceived = new String(dataReceived, Charsets.UTF_8);
                    // Ở đây, bạn có thể xử lý thông điệp nhận được từ GATT client
                    Log.d("BLE", "Received message from client: " + messageReceived);
                }
            } else {
                Log.e("BLE", "Error onCharacteristicRead: " + status);
            }
        }
    };

    private void showConnectedNotificationToServer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("connected_channel_id_server", "Kênh đã kết nối", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "connected_channel_id_server")
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentTitle("Bluetooth đã kết nối")
                .setContentText("Đã kết nối với GATT client.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(1, builder.build());
    }

    private void showConnectedNotificationToClient() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("connected_channel_id_client", "Kênh đã kết nối", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "connected_channel_id_client")
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentTitle("Bluetooth đã kết nối")
                .setContentText("Đã kết nối với GATT server.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(1, builder.build());
    }


}
