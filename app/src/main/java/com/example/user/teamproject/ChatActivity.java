package com.example.user.teamproject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Adapter;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.DataSource;
import com.couchbase.lite.Database;
import com.couchbase.lite.DatabaseConfiguration;
import com.couchbase.lite.Expression;
import com.couchbase.lite.MutableDocument;
import com.couchbase.lite.Query;
import com.couchbase.lite.QueryBuilder;
import com.couchbase.lite.ResultSet;
import com.couchbase.lite.SelectResult;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.AsynchronousChannel;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class ChatActivity extends AppCompatActivity {


    // WiFi Objects
    WifiManager wifiManager;
    WifiP2pManager mManager;
    WifiP2pManager.Channel mChannel;

    BroadcastReceiver mReceiver;
    IntentFilter mIntentFilter;
    InetAddress goAddress;

    // Chat objects
    ClientClass clientClass;
    ServerClass serverClass;
    SendReceive sendReceive;
//    ArrayList<String> msgList;
    ArrayList msgList = new ArrayList<ChatModel>();
    CustomAdapter adapter;

    // UI elements
    FloatingActionButton sendButton;
    EditText entryBox;
    ListView messageList;

    //friend info
    String friendUUID;

    static final int MESSAGE_READ =  1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        //get chat db by friend UUID
        Intent intent1 = getIntent();
        friendUUID = intent1.getStringExtra("FriendUUID");
        DatabaseConfiguration config = new DatabaseConfiguration(getApplicationContext());
        Database friendChat = null;
        try {
            friendChat = new Database(friendUUID, config);
            loadMessage(friendChat);
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }

        Intent intent = getIntent();
        getSupportActionBar().setTitle(intent.getStringExtra("deviceName").substring(5));

        wireUiToVars();
        setupObjects();

        initiateConnection(intent.getStringExtra("deviceType"));

//        msgList = new ArrayList<>();
//        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, msgList);
//        messageList.setAdapter(adapter);
//        adapter = new CustomAdapter(this, msgList);
//        messageList.setAdapter(adapter);

        final Database finalFriendChat = friendChat;
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String textToSend = entryBox.getText().toString();

                //send to db
                MutableDocument message = new MutableDocument();
                int lastIndex = (int) finalFriendChat.getCount();
                message.setInt("index", lastIndex + 1);
                message.setString("sender", username);
                message.setString("context", textToSend);
                message.setString("time", String.valueOf(Calendar.getInstance().getTime()));
                try {
                    finalFriendChat.save(message);
                } catch (CouchbaseLiteException e) {
                    e.printStackTrace();
                }

                //send to chat
                ChatModel model = new ChatModel(textToSend, true);
                adapter = new CustomAdapter(getApplicationContext(), msgList);
                messageList.setAdapter(adapter);
                entryBox.setText("");
                sendReceive.write(textToSend.getBytes());
                msgList.add(model);
                adapter.notifyDataSetChanged();
            }
        });
    }

    private void wireUiToVars() {
        sendButton = (FloatingActionButton) findViewById(R.id.send_fab);
        entryBox = (EditText) findViewById(R.id.entryBox);
        messageList = (ListView) findViewById(R.id.messageList);
    }

    private void setupObjects() {
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);

        mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, this);

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
    }

    private void initiateConnection(String deviceType) {
        if (deviceType.equals("host")) {
            final String deviceName = getIntent().getStringExtra("deviceName");
            String deviceAddress = getIntent().getStringExtra("deviceAddress");
            WifiP2pConfig config = new WifiP2pConfig();
            config.deviceAddress = deviceAddress;

            mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {

                @Override
                public void onSuccess() {
                    Toast.makeText(getApplicationContext(), "Connected to " + deviceName, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onFailure(int reason) {
                    Toast.makeText(getApplicationContext(), "Failed to connect to " + deviceName, Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
        } else if (deviceType.equals("client")) {
            Toast.makeText(getApplicationContext(), "Client", Toast.LENGTH_SHORT).show();
        }
    }

    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {

            switch (msg.what) {
                case MESSAGE_READ:
                    byte[] readBuff = (byte[]) msg.obj;
                    String tempMsg = new String(readBuff, 0, msg.arg1);
                    // TODO: add chat bubble here and remove Toast
//                    Toast.makeText(getApplicationContext(), tempMsg, Toast.LENGTH_LONG).show();
                    ChatModel model = new ChatModel(tempMsg, false);
                    adapter = new CustomAdapter(getApplicationContext(), msgList);
                    messageList.setAdapter(adapter);
                    msgList.add(model);
                    adapter.notifyDataSetChanged();
                    Toast.makeText(getApplicationContext(), "Count:" + adapter.getCount(), Toast.LENGTH_LONG).show();
                    // TODO: add to database
                    break;
            }
            return true;
        }
    });

    public WifiP2pManager.ConnectionInfoListener connectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo info) {
            final InetAddress groupOwnerAddress = info.groupOwnerAddress;

            if (info.groupFormed && info.isGroupOwner) {
                Toast.makeText(getApplicationContext(), "Host", Toast.LENGTH_SHORT).show();
                serverClass = new ServerClass();
                serverClass.start();

            } else if (info.groupFormed && !info.isGroupOwner) {
                Log.d("Chat Activity", "goAddress populated");
                goAddress = groupOwnerAddress;
                clientClass = new ClientClass(goAddress);
                clientClass.start();
            }
        }
    };

    private class SendReceive extends Thread {

        private Socket socket;
        private InputStream inputStream;
        private OutputStream outputStream;

        public SendReceive(Socket skt) {
            socket = skt;
            try {
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while (socket != null) {
                try {
                    bytes = inputStream.read(buffer);

                    if (bytes > 0) {
                        handler.obtainMessage(MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void write(byte[] bytes) {
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public class ServerClass extends Thread {
        Socket socket;
        ServerSocket serverSocket;

        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(8888);
                socket = serverSocket.accept();
                sendReceive = new SendReceive(socket);
                sendReceive.start();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    public class ClientClass extends Thread {

        Socket socket;
        String hostAdd;

        public ClientClass(InetAddress hostAddress) {
            hostAdd = hostAddress.getHostAddress();
            socket = new Socket();
        }

        @Override
        public void run() {
            try {
                socket.connect(new InetSocketAddress(hostAdd, 8888), 500);
                sendReceive = new SendReceive(socket);
                sendReceive.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mReceiver, mIntentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
    }

    private void loadMessage(Database friendChat) throws CouchbaseLiteException {
        Query query = QueryBuilder.select(SelectResult.property("index"))
                .from(DataSource.database(friendChat))
                .where(Expression.property("index"));
        ResultSet rs = query.execute();
        int size = rs.allResults().size();

        //get the data and append to a list
        ArrayList<String> listData = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            query = QueryBuilder.select(SelectResult.property("sender"))
                    .from(DataSource.database(friendChat))
                    .where(Expression.property("index").equalTo(Expression.value(i)));
            rs = query.execute();
            String sender = rs.allResults().get(0).getString("sender");

            query = QueryBuilder.select(SelectResult.property("context"))
                    .from(DataSource.database(friendChat))
                    .where(Expression.property("index").equalTo(Expression.value(i)));
            rs = query.execute();
            String context = rs.allResults().get(0).getString("context");

            query = QueryBuilder.select(SelectResult.property("time"))
                    .from(DataSource.database(friendChat))
                    .where(Expression.property("index").equalTo(Expression.value(i)));
            rs = query.execute();
            String time = rs.allResults().get(0).getString("time");
        }
    }


}
