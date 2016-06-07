package io.gnob.smarttripod;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.support.v7.app.AppCompatActivity;
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

public class ServerActivity extends AppCompatActivity {
    private TextView btStat;
    private Button testBtn;

    private final int REQUEST_DISCOVERABLE_BT = 5;
    private BluetoothAdapter mBlAdapter;

    private final int MESSAGE_READ = 1;
    private AcceptThread mAcceptThread;
    private ConnectedThread mConnectedThread;
    private Handler mHandler;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server);

        loadViewComponents();
        bindEvents();

        BluetoothManager blManager = (BluetoothManager) this.getSystemService(this.BLUETOOTH_SERVICE);

        mBlAdapter = blManager.getAdapter();

        if (mBlAdapter == null)
            btStat.setText(R.string.bt_not_supported);
        else
        {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            startActivityForResult(discoverableIntent, REQUEST_DISCOVERABLE_BT);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        switch (requestCode)
        {
            case REQUEST_DISCOVERABLE_BT:
                if (resultCode == 120)
                    waitBt();
                else
                    btStat.setText("블루투스를 탐색 가능하게 활성화 할 수 없습니다.");
                break;
        }
    }

    private void waitBt()
    {
        btStat.setText(R.string.bt_start_discover);

        if (mAcceptThread != null)
        {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }

        mAcceptThread = new AcceptThread();
        mAcceptThread.start();
    }

    private void loadViewComponents()
    {
        btStat = (TextView) findViewById(R.id.btStatTxt);
        testBtn = (Button) findViewById(R.id.testBtn);
    }

    private void bindEvents()
    {
        testBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (mConnectedThread != null)
                {
                    byte data = 4;
                    mConnectedThread.write(data);
                }
            }
        });
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

    private class AcceptThread extends Thread
    {
        private BluetoothServerSocket mmServerSocket;

        public AcceptThread()
        {
            mmServerSocket = null;

            try
            {
                UUID btUUID = UUID.fromString(getString(R.string.bt_uuid));
                mmServerSocket = mBlAdapter.listenUsingRfcommWithServiceRecord("smartTripod", btUUID);
            } catch (IOException e)
            {
                Toast.makeText(getApplicationContext(), getString(R.string.bt_accept_thd_error), Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void run()
        {
            try
            {
                BluetoothSocket socket = mmServerSocket.accept();
                mmServerSocket.close();

                manageConnection(socket);
            } catch (IOException e)
            {
                Toast.makeText(getApplicationContext(), getString(R.string.bt_accept_thd_accept_error), Toast.LENGTH_SHORT).show();
            }
        }

        public void cancel()
        {
            try
            {
                mmServerSocket.close();
            } catch (IOException e)
            {
                Toast.makeText(getApplicationContext(), getString(R.string.bt_accept_thd_cancel_error), Toast.LENGTH_SHORT).show();
            }
        }
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
            byte[] buffer = new byte[1];

            while (true)
            {
                try
                {
                    buffer[0] = 4;
                    mmOutStream.write(buffer);

                    while (true);

                } catch (IOException e)
                {
                    Toast.makeText(getApplicationContext(), "Fail to write while running in Connected Thread.", Toast.LENGTH_SHORT).show();
                    break;
                }
            }
        }

        public void write(byte data)
        {
            try
            {
                mmOutStream.write(data);
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
