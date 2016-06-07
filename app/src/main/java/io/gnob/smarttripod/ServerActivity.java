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

// 테스트용 서버 엑티비티
// Client와 Server 모델로 블루투스 통신이 잘 되는지 확인하기 위함
public class ServerActivity extends AppCompatActivity {
    // 필요한 뷰를 담기 위한 선언
    private TextView btStat;
    private Button testBtn;

    // 블루투스 사용을 위한 선언
    private BluetoothAdapter mBlAdapter;
    private final int REQUEST_DISCOVERABLE_BT = 5;

    // Handler 선언과 사용되는 상수값
    private Handler mHandler;
    private final int MESSAGE_READ = 1;
    private final int MESSAGE_SEND= 2;
    private final int CONNECTION_SUCCESS = 11;
    private final int CONNECTION_ERROR = 12;
    private final int CONNECTION_CLOSE = 13;

    // 통신을 위한 쓰레드 선언
    private AcceptThread mAcceptThread;
    private ConnectedThread mConnectedThread;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server);

        // 뷰를 로드하고 이벤트 리스너를 바인딩함
        loadViewComponents();
        bindEvents();

        // 블루투스 사용을 위해 매니저로 어댑터 획득 시도
        BluetoothManager blManager = (BluetoothManager) this.getSystemService(this.BLUETOOTH_SERVICE);
        mBlAdapter = blManager.getAdapter();

        // 어댑터를 못가져오면 해당 장비는 블루투스를 지원하지 않음
        if (mBlAdapter == null)
            btStat.setText(R.string.bt_not_supported);
        else
        {
            // 어댑터가 있으면 블루투스의 상태를 Discoveralbe 하게 변경하도록 요청을 보냄
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            startActivityForResult(discoverableIntent, REQUEST_DISCOVERABLE_BT);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        switch (requestCode)
        {
            // 블루투스를 Discoverable 하게 하는 요청에 대해 응답을 받아서 처리한다
            case REQUEST_DISCOVERABLE_BT:
                // Discoverable 설정이 되었다면 블루투스 연결을 서버로써 대기함
                if (resultCode == 120)
                    waitBt();
                // 설정에 실패했으면 메세지 출력
                else
                    btStat.setText("블루투스를 탐색 가능하게 활성화 할 수 없습니다.");
                break;
        }
    }

    // 블루투스 연결을 기다리기 위한 메소드
    private void waitBt()
    {
        btStat.setText(R.string.bt_start_discover);

        // 연결을 기다리기위한 쓰레드를 생성, 이미 존재하면 새로 시작
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
        // 연결이 되있을때 테스트 버튼을 누르면 X라는 글자가 전송됨
        testBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (mConnectedThread != null)
                {
                    byte data = 'X';
                    mConnectedThread.write(data);
                }
            }
        });
    }

    // 생명 주기상 엑티비티가 소멸되면 연결 쓰레드를 정지한다
    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        if (mConnectedThread != null)
            mConnectedThread.cancel();
    }

    private void toast(String msg)
    {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
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
                        str = (String) msg.obj;
                        toast(str);
                        break;
                    case MESSAGE_SEND:
                        data = (byte) msg.obj;
                        str = "" + (char) data;
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
                        mAcceptThread = null;
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

    private class AcceptThread extends Thread
    {
        private BluetoothServerSocket mmServerSocket;

        public AcceptThread()
        {
            mmServerSocket = null;

            try
            {
                // 서버 UUID는 SSP에 정의된 것을 따른다
                UUID btUUID = UUID.fromString(getString(R.string.bt_other_uuid));
                mmServerSocket = mBlAdapter.listenUsingRfcommWithServiceRecord("Smart Tripod", btUUID);
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
                // 클라이언트의 Initiating을 대기함
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
        private Handler mmHandler;

        public ConnectedThread(BluetoothSocket socket, Handler handler)
        {
            mmSocket = socket;
            mmHandler = handler;

            // 통신을 위한 스트림을 소켓에서 얻어옴
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
                // 메세지를 보내는 메소드
                mmOutStream.write(data);
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
