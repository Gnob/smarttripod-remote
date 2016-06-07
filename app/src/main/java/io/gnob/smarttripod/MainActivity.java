package io.gnob.smarttripod;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.sql.ClientInfoStatus;

public class MainActivity extends AppCompatActivity {
    private Button serverBtn, clientBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        loadViewComponents();
        bindEvents();
    }

    private void loadViewComponents()
    {
        serverBtn = (Button) findViewById(R.id.serverBtn);
        clientBtn = (Button) findViewById(R.id.clientBtn);
    }

    private void bindEvents()
    {

        serverBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                // Start Server Activity
                startActivityOnView(ServerActivity.class);
            }
        });

        clientBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                // Start Client Activity
                startActivityOnView(ClientActivity.class);
            }
        });
    }

    private void startActivityOnView(java.lang.Class<?> clazz)
    {
        Intent activity_intent = new Intent(this, clazz);
        startActivity(activity_intent);
    }
}
