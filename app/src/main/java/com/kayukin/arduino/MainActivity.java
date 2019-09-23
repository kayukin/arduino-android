package com.kayukin.arduino;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;
import com.felhr.usbserial.UsbSerialInterface.UsbReadCallback;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashMap;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.internal.http2.Header;

public class MainActivity extends AppCompatActivity {
    private static final String ACTION_USB_PERMISSION =
            "com.kayukin.arduino.USB_PERMISSION";
    private UsbManager usbManager;
    private UsbDevice device;
    UsbDeviceConnection connection;
    private UsbSerialDevice serialPort;
    private TextView temperatureView;
    private TextView humidityView;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        usbManager = getSystemService(UsbManager.class);
        humidityView = findViewById(R.id.humidityView);
        temperatureView = findViewById(R.id.temperatureView);
        initUsbConnection();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(broadcastReceiver, filter);
    }

    final UsbReadCallback usbReadCallback = new UsbReadCallback() {
        // Определение метода обратного вызова, который вызывается при приеме данных.
        @Override
        public void onReceivedData(byte[] bytes) {
            try {
                SensorsData sensorsData = objectMapper.readValue(bytes, SensorsData.class);
                runOnUiThread(() -> {
                    humidityView.setText(sensorsData.getHumidity().toString());
                    temperatureView.setText(sensorsData.getTemperature().toString());
                });
                Request request = new Request.Builder()
                        .url("https://kayukin.systems/api/sensors")
                        .post(RequestBody.create(bytes))
                        .header("Authorization", Credentials.basic("kayukin", "4rAw28kjgy2"))
                        .header("Content-type", "application/json")
                        .build();
                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NotNull Call call, @NotNull IOException e) {
                        Log.d("HTTP", "Request failed");
                    }

                    @Override
                    public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                        if (response.isSuccessful()) {
                            Log.d("HTTP", "Request ok");
                        }
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    };

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_USB_PERMISSION.equals(intent.getAction())) {
                boolean granted =
                        intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                if (granted) {
                    connection = usbManager.openDevice(device);
                    serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection);
                    if (serialPort != null) {
                        if (serialPort.open()) { //Установить параметры последовательного соедниения.
                            serialPort.setBaudRate(9600);
                            serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                            serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                            serialPort.setParity(UsbSerialInterface.PARITY_NONE);
                            serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                            serialPort.read(usbReadCallback);
                        } else {
                            Log.d("SERIAL", "PORT NOT OPEN");
                        }
                    } else {
                        Log.d("SERIAL", "PORT IS NULL");
                    }
                } else {
                    Log.d("SERIAL", "PERM NOT GRANTED");
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
                initUsbConnection();
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction())) {
                initUsbConnection();
            }
        }
    };

    private void initUsbConnection() {
        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        if (!usbDevices.isEmpty()) {
            device = usbDevices.entrySet().iterator().next().getValue();

            PendingIntent pi = PendingIntent.getBroadcast(this, 0,
                    new Intent(ACTION_USB_PERMISSION), 0);
            usbManager.requestPermission(device, pi);
        }
    }
}
