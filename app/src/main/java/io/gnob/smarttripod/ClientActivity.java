package io.gnob.smarttripod;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.bluetooth.*;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;


// Client 엑티비티는 리모컨 역할을 한다
public class ClientActivity extends AppCompatActivity {
    // 필요한 뷰를 담기 위한 선언
    private TextView btStat, objStat;
    private Button upBtn, downBtn, leftBtn, rightBtn, connectBtn, shotBtn, autoBtn,
            leftRotateBtn, rightRotateBtn, timerBtn, panoramaBtn;
    private EditText timerTxt;

    // 블루투스 사용을 위한 선언
    private BluetoothAdapter mBlAdapter;
    private final int REQUEST_ENABLE_BT = 4;
    private ArrayList<BluetoothDevice> discoveredDevices = new ArrayList<>();
    private int discoveredDeviceCurIdx = -1;

    // Handler 선언과 사용되는 상수값
    private Handler mHandler;
    private final int MESSAGE_READ = 1;
    private final int MESSAGE_SEND= 2;
    private final int CONNECTION_SUCCESS = 11;
    private final int CONNECTION_ERROR = 12;
    private final int CONNECTION_CLOSE = 13;

    // 통신을 위한 쓰레드 선언
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;

    // 블루투스 상태를 감지하기 위한 브로드 캐스트 리시버 선언 및 정의
    private final BroadcastReceiver mReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();

            // 장비를 발견할때 마다 배열에 추가해둔다
            // Smart Tripod이나 테스트 장비를 발견하면 연결을 시도한다
            if (BluetoothDevice.ACTION_FOUND.equals(action))
            {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Toast.makeText(context, "Discover\n" + device.getAddress(), Toast.LENGTH_SHORT).show();
                discoveredDevices.add(device);

                // 발견된 장비가 있으면 뷰 갱신
                if (discoveredDevices.size() == 1)
                    objStat.setText(device.getName() + " / " + device.getAddress());

                // 연결할 장비를 찾으면 탐색을 중단하고 연결한다
                if (device.getName() != null && device.getName().equals("DevMobile"))
                {
                    mBlAdapter.cancelDiscovery();
                    initConnect(device);
                }
                else if (device.getName() != null && device.getName().equals("Smart Tripod"))
                {
                    mBlAdapter.cancelDiscovery();
                    initConnect(device);
                }
                else if (device.getName() == null && device.getAddress().equals("00:01:95:20:12:4A"))
                {
                    mBlAdapter.cancelDiscovery();
                    initConnect(device);
                }
            }
        }
    };

    // 연결을 초기화한다
    private void initConnect(BluetoothDevice device)
    {
        if (mConnectThread != null)
        {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        btStat.setText("Connecting");
        objStat.setText(device.getName() + " / " + device.getAddress());

        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client);

        // 뷰를 로드하고 이벤트 리스너를 바인딩함
        loadViewComponents();
        bindEvents();

        // 블루투스 사용을 위해 매니저로 어댑터 획득 시도
        BluetoothManager blManager = (BluetoothManager) this.getSystemService(this.BLUETOOTH_SERVICE);
        mBlAdapter = blManager.getAdapter();
        // 어댑터를 못가져오면 해당 장비는 블루투스를 지원하지 않음
        if (mBlAdapter == null)
            btStat.setText(R.string.bt_not_supported);
    }

    // 블루투스가 켜져있는지 확인하는 메소드
    private void checkEnabledBt()
    {
        // 켜져있으면 연결 시도를 한다
        if (mBlAdapter.isEnabled())
            connectBt();
        // 켜져있지 않으면 블루투스를 켜달라고 요청한다
        else
        {
            Intent enableBtIntent = new Intent(mBlAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        switch (requestCode)
        {
            // 블루투스를 켜달라는 요청에 대해 응답을 받아서 처리한다
            case REQUEST_ENABLE_BT:
                // 켜졌으면 연결 시도
                if (resultCode == RESULT_OK)
                    connectBt();
                // 아니면 에러 메세지 출력
                else
                    btStat.setText(R.string.bt_request_enable);
                break;
        }
    }

    // 연결시도를 위해 블루투스에게 탐색을 시작하라고 명령
    // 주변 블루투스 장비 정보를 받아와 처리해야한다
    // 이를 위해 브로드 캐스트 리시버를 등록한다
    private void connectBt()
    {
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter);

        mBlAdapter.startDiscovery();
        btStat.setText(R.string.bt_start_discover);
    }

    private void loadViewComponents()
    {
        btStat = (TextView) findViewById(R.id.btStatTxt);
        objStat = (TextView) findViewById(R.id.objStatTxt);

        upBtn = (Button) findViewById(R.id.upBtn);
        downBtn = (Button) findViewById(R.id.downBtn);
        leftBtn = (Button) findViewById(R.id.leftBtn);
        rightBtn = (Button) findViewById(R.id.rightBtn);
        leftRotateBtn = (Button) findViewById(R.id.leftRotateBtn);
        rightRotateBtn = (Button) findViewById(R.id.rightRotateBtn);
        connectBtn = (Button) findViewById(R.id.serverBtn);
        shotBtn = (Button) findViewById(R.id.shotBtn);
        autoBtn = (Button) findViewById(R.id.autoBtn);
        timerBtn = (Button) findViewById(R.id.timerBtn);
        panoramaBtn = (Button) findViewById(R.id.panoramaBtn);

        timerTxt = (EditText) findViewById(R.id.timerTxt);
    }

    private void bindEvents()
    {
        // 연결을 위한 버튼
        connectBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                checkEnabledBt();
            }
        });

        shotBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                // 연결 쓰레드가 존재하면 Shot 메세지 전송
                if (mConnectedThread != null)
                {
                    byte data = 'S';
                    mConnectedThread.write(data);
                }
                // 블루투스가 탐색 상태이면 중지시킨다
                else if(mBlAdapter.isDiscovering())
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
                // 연결 쓰레드가 존재하면 Up 메세지 전송
                if (mConnectedThread != null)
                {
                    byte data = 'U';
                    mConnectedThread.write(data);
                }
                // 주변 기기 정보가 배열에 담겨 있으면 목록을 순회한다
                else if (discoveredDeviceCurIdx > 0)
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
                // 연결 쓰레드가 존재하면 Down 메세지 전송
                if (mConnectedThread != null)
                {
                    byte data = 'D';
                    mConnectedThread.write(data);
                }
                // 주변 기기 정보가 배열에 담겨 있으면 목록을 순회한다
                else if (discoveredDeviceCurIdx < discoveredDevices.size() - 1)
                {
                    discoveredDeviceCurIdx++;
                    BluetoothDevice device = discoveredDevices.get(discoveredDeviceCurIdx);
                    objStat.setText(device.getName() + " / " + device.getAddress());
                }
            }
        });

        leftBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (mConnectedThread != null)
                {
                    byte data = 'L';
                    mConnectedThread.write(data);
                }
            }
        });

        rightBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (mConnectedThread != null)
                {
                    byte data = 'R';
                    mConnectedThread.write(data);
                }
            }
        });

        leftRotateBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (mConnectedThread != null)
                {
                    byte data = 'F';
                    mConnectedThread.write(data);
                }
            }
        });

        rightRotateBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (mConnectedThread != null)
                {
                    byte data = 'B';
                    mConnectedThread.write(data);
                }
            }
        });

        autoBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (mConnectedThread != null)
                {
                    byte data = 'A';
                    mConnectedThread.write(data);
                }
            }
        });

        timerBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (mConnectedThread != null)
                {
                    String timerStr = timerTxt.getText().toString();
                    if (timerStr != null && timerStr.length() <= 2)
                    {
                        byte data = 'T';
                        mConnectedThread.write(data);

                        char timerValue = (char) Integer.parseInt(timerStr);

                        data = (byte) timerValue;
                        mConnectedThread.write(data);
                        toast("Timer value send");
                    }
                    else
                    {
                        toast("Invalid timer value (1~99 seconds only)");
                    }
                }
            }
        });

        panoramaBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (mConnectedThread != null)
                {
                    byte data = 'P';
                    mConnectedThread.write(data);
                }
            }
        });
    }

    // 생명 주기상 엑티비티가 소멸되면 연결 쓰레드를 정지한다
    // 브로드 캐스트 리시버도 등록을 해제한다
    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        unregisterReceiver(mReceiver);
        if (mConnectedThread != null)
            mConnectedThread.cancel();
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
            // UUID는 SSP에 정의된 것을 따른다
            // ATMEGA128에 사용되는 블루투스의 프로파일이 SSP이기 때문
            UUID btUUID = UUID.fromString(getString(R.string.bt_other_uuid));
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
                // Toast.makeText(getApplicationContext(), getString(R.string.bt_connect_thd_connect_error), Toast.LENGTH_SHORT).show();
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

    // 연결을 관리하기 위한 메소드
    // 통신 연결에 성공해 소켓을 얻으면 통신을 담당할 쓰레드를 생성
    private void manageConnection(BluetoothSocket socket)
    {
        // 메인 쓰레드와 통신 쓰레드가 메세지를 주고받기 위한 핸들러 정의
        mHandler = new Handler(getMainLooper())
        {
            @Override
            public void handleMessage(Message msg)
            {
                byte data;
                String str;

                switch (msg.what)
                {
                    case MESSAGE_READ:
                        toast((String) msg.obj);
                        break;
                    case MESSAGE_SEND:
                        data = (byte) msg.obj;
                        str = "" + (char) data;
                        objStat.setText(str);
                        toast("Send msg...");
                        break;
                    case CONNECTION_SUCCESS:
                        btStat.setText("Connected");
                        break;
                    case CONNECTION_ERROR:
                        btStat.setText("Connection error occured");
                        break;
                    case CONNECTION_CLOSE:
                        btStat.setText("Connection is closed");
                        mConnectThread = null;
                        mConnectedThread = null;
                        break;
                }
            }
        };

        // 소켓과 핸들러를 생성자에 전달하며 통신 쓰레드를 시작한다
        if (mConnectedThread != null)
        {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        mConnectedThread = new ConnectedThread(socket, mHandler);
        mConnectedThread.start();
    }

    private class ConnectedThread extends Thread
    {
        private BluetoothSocket mmSocket;
        private InputStream mmInStream;
        private OutputStream mmOutStream;
        private Handler mmHandler;

        public ConnectedThread(BluetoothSocket socket, Handler handler)
        {
            mmSocket = socket;
            mmHandler = handler;

            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try
            {
                tmpIn = mmSocket.getInputStream();
                tmpOut = mmSocket.getOutputStream();
            } catch (IOException e)
            {
                mmHandler.obtainMessage(CONNECTION_ERROR)
                        .sendToTarget();
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        @Override
        public void run()
        {
            mmHandler.obtainMessage(CONNECTION_SUCCESS)
                    .sendToTarget();

            byte[] buffer = new byte[1];

            while (mmSocket.isConnected())
            {
                // 연결이 된 동안에 메세지를 받으면 메인 쓰레드에 전달
                try
                {
                    mmInStream.read(buffer, 0, 1);
                    String recvTxt = (char) buffer[0] + "";
                    mmHandler.obtainMessage(MESSAGE_READ, recvTxt)
                            .sendToTarget();
                } catch (IOException e)
                {
                    mmHandler.obtainMessage(CONNECTION_ERROR)
                            .sendToTarget();
                    break;
                }
            }

            cancel();


            mmHandler.obtainMessage(CONNECTION_CLOSE)
                    .sendToTarget();
        }

        public void write(byte data)
        {
            try
            {
                mmOutStream.write(data);

                mmHandler.obtainMessage(MESSAGE_SEND, data)
                        .sendToTarget();
            } catch (IOException e)
            {
                mmHandler.obtainMessage(CONNECTION_ERROR)
                        .sendToTarget();
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
                mmHandler.obtainMessage(CONNECTION_ERROR)
                        .sendToTarget();
                return;
            }
        }
    }
}
