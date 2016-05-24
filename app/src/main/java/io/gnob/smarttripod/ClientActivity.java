package io.gnob.smarttripod;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.bluetooth.*;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class ClientActivity extends AppCompatActivity {
    private TextView btStat, objStat;
    private Button upBtn, downBtn, leftBtn, rightBtn, connectBtn, takeBtn, autoBtn;
    private final int REQUEST_ENABLE_BT = 4;
    private BluetoothAdapter mBlAdapter;
    private ArrayList<BluetoothDevice> discoveredDevices = new ArrayList<>();
    private int discoveredDeviceCurIdx = -1;

    private final int MESSAGE_READ = 1;
    private Handler mHandler;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action))
            {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Toast.makeText(context, "Discover\n" + device.getAddress(), Toast.LENGTH_SHORT).show();
                discoveredDevices.add(device);

                if (discoveredDevices.size() == 1)
                    objStat.setText(device.getName() + " / " + device.getAddress());

                if (device.getName().equals("Galaxy Note3"))
                {
                    if (mConnectThread != null)
                    {
                        mConnectThread.cancel();
                        mConnectThread = null;
                    }
                    mConnectThread = new ConnectThread(device);
                    mConnectThread.start();

                    mBlAdapter.cancelDiscovery();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client);

        loadViewComponents();
        bindEvents();

        BluetoothManager blManager = (BluetoothManager) this.getSystemService(this.BLUETOOTH_SERVICE);

        mBlAdapter = blManager.getAdapter();

        if (mBlAdapter == null)
            btStat.setText(R.string.bt_not_supported);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        switch (requestCode)
        {
            case REQUEST_ENABLE_BT:
                if (resultCode == RESULT_OK)
                    connectBt();
                else
                    btStat.setText(R.string.bt_request_enable);
                break;
        }
    }


    private void checkEnabledBt()
    {
        if (mBlAdapter.isEnabled())
            connectBt();
        else
        {
            Intent enableBtIntent = new Intent(mBlAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    private void connectBt()
    {
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter);

        mBlAdapter.startDiscovery();
        btStat.setText(R.string.bt_start_discover);
    }

    private ParcelUuid[] getPairedDevice(String targetName)
    {
        Set<BluetoothDevice> pairedDevices = mBlAdapter.getBondedDevices();
        if (pairedDevices.size() > 0)
        {
            for (BluetoothDevice bd: pairedDevices)
            {
                if (targetName.equals(bd.getName()))
                    return bd.getUuids();
            }
        }
        else
            btStat.setText(R.string.bt_no_devices);

        return null;
    }

    private void showPairedDevices()
    {
        Set<BluetoothDevice> pairedDevices = mBlAdapter.getBondedDevices();
        if (pairedDevices.size() > 0)
        {
            StringBuilder sb = new StringBuilder();
            for (BluetoothDevice bd: pairedDevices)
            {
                sb.append(bd.getName());
                sb.append("\n");
            }
            Toast.makeText(this, sb.toString(), Toast.LENGTH_SHORT).show();
        }
        else
            btStat.setText(R.string.bt_no_devices);
    }


    private void loadViewComponents()
    {
        btStat = (TextView) findViewById(R.id.btStatTxt);
        objStat = (TextView) findViewById(R.id.objStatTxt);

        upBtn = (Button) findViewById(R.id.upBtn);
        downBtn = (Button) findViewById(R.id.downBtn);
        leftBtn = (Button) findViewById(R.id.leftBtn);
        rightBtn = (Button) findViewById(R.id.rightBtn);
        connectBtn = (Button) findViewById(R.id.serverBtn);
        takeBtn = (Button) findViewById(R.id.takeBtn);
        autoBtn = (Button) findViewById(R.id.autoBtn);
    }

    private void bindEvents()
    {
        connectBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                checkEnabledBt();
            }
        });

        takeBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if(mBlAdapter.isDiscovering())
                {
                    mBlAdapter.cancelDiscovery();
                    btStat.setText(R.string.bt_stop_discover);
                }
            }
        });

        upBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (discoveredDeviceCurIdx > 0)
                {
                    discoveredDeviceCurIdx--;
                    BluetoothDevice device = discoveredDevices.get(discoveredDeviceCurIdx);
                    objStat.setText(device.getName() + " / " + device.getAddress());
                }
            }
        });

        downBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (discoveredDeviceCurIdx < discoveredDevices.size() - 1)
                {
                    discoveredDeviceCurIdx++;
                    BluetoothDevice device = discoveredDevices.get(discoveredDeviceCurIdx);
                    objStat.setText(device.getName() + " / " + device.getAddress());
                }
            }
        });
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    private void toast(String msg)
    {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private class ConnectThread extends Thread
    {
        private BluetoothSocket mmSocket;

        public ConnectThread(BluetoothDevice device)
        {
            UUID btUUID = UUID.fromString(getString(R.string.bt_uuid));
            mmSocket = null;
            try
            {
                mmSocket = device.createRfcommSocketToServiceRecord(btUUID);
            } catch (IOException e)
            {
                Toast.makeText(getApplicationContext(), getString(R.string.bt_connect_thd_error), Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void run()
        {
            try
            {
                mmSocket.connect();
                manageConnection(mmSocket);
            } catch (IOException e)
            {
                Toast.makeText(getApplicationContext(), getString(R.string.bt_connect_thd_connect_error), Toast.LENGTH_SHORT).show();
                cancel();
                return;
            }
        }

        public void cancel()
        {
            if (mmSocket.isConnected())
            {
                try
                {
                    mmSocket.close();
                } catch (IOException e)
                {
                    btStat.setText(R.string.bt_connect_thd_cancel_error);
                }
            }
        }
    }

    private void manageConnection(BluetoothSocket socket)
    {
        mHandler = new Handler(getMainLooper())
        {
            @Override
            public void handleMessage(Message msg)
            {
                switch (msg.what)
                {
                    case MESSAGE_READ:
                        byte[] bytes = (byte[]) msg.obj;
                        String s = "" + bytes[0];
                        objStat.setText(s);
                        toast("Receive msg...");
                        break;
                }
            }
        };

        if (mConnectedThread != null)
        {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();
    }

    private class ConnectedThread extends Thread
    {
        private BluetoothSocket mmSocket;
        private InputStream mmInStream;
        private OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket)
        {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try
            {
                tmpIn = mmSocket.getInputStream();
                tmpOut = mmSocket.getOutputStream();
            } catch (IOException e)
            {
                Toast.makeText(getApplicationContext(), "Fail to get stream in Connected Thread.", Toast.LENGTH_SHORT).show();
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        @Override
        public void run()
        {
            byte[] buffer = new byte[128];
            int n;

            while (true)
            {
                try
                {
                    n = mmInStream.read(buffer);

                    mHandler.obtainMessage(MESSAGE_READ, n, -1, buffer)
                            .sendToTarget();
                } catch (IOException e)
                {
                    Toast.makeText(getApplicationContext(), "Fail to read while running in Connected Thread.", Toast.LENGTH_SHORT).show();
                    break;
                }
            }
        }

        public void write(byte[] bytes)
        {
            try
            {
                mmOutStream.write(bytes);
            } catch (IOException e)
            {
                Toast.makeText(getApplicationContext(), "Fail to write while writing in Connected Thread.", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        public void cancel()
        {
            try
            {
                mmSocket.close();
            } catch (IOException e)
            {
                Toast.makeText(getApplicationContext(), "Fail to cancel while canceling in Connected Thread.", Toast.LENGTH_SHORT).show();
                return;
            }
        }
    }
}
