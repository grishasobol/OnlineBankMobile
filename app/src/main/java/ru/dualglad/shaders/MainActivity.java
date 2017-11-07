package ru.dualglad.shaders;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.Date;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final boolean DEBUG = true; // prints extra debug messages to console
    private static final int TIME_VIBRATE = 500; // time of vibration on message receiving

    private static final String ACTION_PRINT_MSG = "PRINT_MSG"; // print to console
    private static final String ACTION_USER_REQUEST_SHOW = "USER_REQUEST_SHOW"; // show UI
    private static final String ACTION_USER_REQUEST_HIDE = "USER_REQUEST_HIDE"; // hide UI
    private static final String EXTRA_PRINT_MSG = "PRINT_MSG"; // text for printing to console
    private static final String EXTRA_PRINT_MSG_COLOR = "PRINT_MSG_COLOR"; // color of text for printing to console
    private static final int COLOR_WHITE       = 0xffFFFFFF;
    private static final int COLOR_RED         = 0xffFF0000;
    private static final int COLOR_GREEN       = 0xff00FF00;
    private static final int COLOR_YELLOW      = 0xffFFFF00;
    private static final int COLOR_CYAN        = 0xff00FFFF;
    private static final int COLOR_GRAY        = 0xff7F7F7F;
    private static final int COLOR_LIGHT_RED   = 0xffFF7F7F;
    private static final int COLOR_LIGHT_GREEN = 0xff7FFF7F;

    private TextView tv_server; // server activity indicator

    private final StopPoint stopPoint = new StopPoint(); // waits for user response
    private BluetoothAdapter bluetoothAdapter; // BT
    private BluetoothServerSocket bluetoothServerSocket; // BT
    private BroadcastReceiver broadcastReceiver; // events handling
    private boolean server_alive; // is server active
    private boolean user_response; // is user response positive

    // Synchronization
    private class StopPoint {
        // Pause
        private void activate() {
            try {
                synchronized (this) {
                    this.wait();
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
        // Resume
        private void deactivate() {
            synchronized (this) {
                this.notify();
            }
        }
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Server activity indicator
        tv_server = (TextView)findViewById(R.id.tv_server);
        // Console form
        final ScrollView sv = (ScrollView)findViewById(R.id.sv);
        // Console text
        final TextView tv = (TextView)findViewById(R.id.tv);
        // Server toggle
        findViewById(R.id.b_server).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (!server_alive) {
                    startServer();
                }
                else {
                    stopServer();
                }
            }
        });
        // Clear console
        findViewById(R.id.b_clear).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                tv.setText("");
            }
        });
        // positive answer
        final Button b_accept = (Button)findViewById(R.id.b_accept);
        b_accept.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                user_response = true;
                stopPoint.deactivate();
            }
        });
        // negative answer
        final Button b_cancel = (Button)findViewById(R.id.b_cancel);
        b_cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                user_response = false;
                stopPoint.deactivate();
            }
        });

        // Check BT enable
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!bluetoothAdapter.isEnabled()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, 0);
        }

        // Events handling
        broadcastReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                switch (action) {
                    case ACTION_PRINT_MSG:
                        String msg = intent.getStringExtra(EXTRA_PRINT_MSG);
                        int color = intent.getIntExtra(EXTRA_PRINT_MSG_COLOR, COLOR_WHITE);

                        Spannable text = new SpannableString(msg);
                        text.setSpan(new ForegroundColorSpan(color), 0, text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                        tv.append(text);
                        sv.fullScroll(View.FOCUS_DOWN);
                        break;
                    case ACTION_USER_REQUEST_SHOW:
                        b_accept.setVisibility(View.VISIBLE);
                        b_cancel.setVisibility(View.VISIBLE);
                        Vibrator vibrator = (Vibrator)getSystemService(VIBRATOR_SERVICE);
                        vibrator.vibrate(TIME_VIBRATE);
                        break;
                    case ACTION_USER_REQUEST_HIDE:
                        b_accept.setVisibility(View.INVISIBLE);
                        b_cancel.setVisibility(View.INVISIBLE);
                        break;
                    default:
                        break;
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_PRINT_MSG);
        intentFilter.addAction(ACTION_USER_REQUEST_SHOW);
        intentFilter.addAction(ACTION_USER_REQUEST_HIDE);
        registerReceiver(broadcastReceiver, intentFilter);

        msg("Welcome! Turn on server to receive requests.\n", COLOR_YELLOW);
    }

    protected void onDestroy() {
        super.onDestroy();

        if (server_alive) {
            stopServer();
        }

        unregisterReceiver(broadcastReceiver);
    }

    // Print message to console
    private void msg(String string, int color) {
        Intent intent = new Intent(ACTION_PRINT_MSG);
        intent.putExtra(EXTRA_PRINT_MSG, string + "\n");
        intent.putExtra(EXTRA_PRINT_MSG_COLOR, color);
        sendBroadcast(intent);
    }

    // Print Debug message to console
    private void msgdbg(String string) {
        if (DEBUG) {
            msg("[DBG] : [" + string + "]", COLOR_GRAY);
        }
    }

    // Activate server
    private void startServer() {
        server_alive = true;
        tv_server.setTextColor(0xFF00FF00);
        msg("Starting server.", COLOR_GREEN);
        BTServer btServer = new BTServer();
        btServer.start();
    }

    // Deactivate server
    private void stopServer() {
        server_alive = false;
        tv_server.setTextColor(0xFFFF0000);
        msg("Stopping server.", COLOR_RED);
        try {
            bluetoothServerSocket.close();
        } catch (Exception e) { e.printStackTrace(); }
    }

    // User dialog
    private boolean askUser() {
        Intent intent = new Intent(ACTION_USER_REQUEST_SHOW);
        sendBroadcast(intent);
        boolean result;
        user_response = false;

        Thread thread = new Thread(new Runnable() {
            public void run() {
                stopPoint.activate();
            }
        });
        thread.start();
        try {
            thread.join();
        } catch (Exception e) { e.printStackTrace(); }

        result = user_response;
        user_response = false;
        intent = new Intent(ACTION_USER_REQUEST_HIDE);
        sendBroadcast(intent);

        return result;
    }


    // Server thread
    private class BTServer extends Thread {
        private final String SERVER_SERVICE = "SERVICE";                                          // BT server identifier
        private final UUID SERVER_UUID = UUID.fromString("446118f0-8b1e-11e2-9e96-0800200c9a66"); // BT server identifier

        private DataInputStream dataInputStream;   // input
        private DataOutputStream dataOutputStream; // output
        private String remote_device_name;         // connected device BT name
        private String remote_device_address;      // connected device BT address

        private String mobile_publickey;  // public Mobile key
        private String mobile_privatekey; // private Mobile key
        private String desktop_publickey; // public Desktop key
        private String desktop_address;   // Desktop BT address
        private String desktop_name;      // Desktop BT name

        public void run() {
            mobile_publickey = Dialog.getMobilePublickey();
            mobile_privatekey = Dialog.getMobilePrivatekey();

            while (server_alive) {
                msg("Starting session.", COLOR_WHITE);
                BluetoothSocket bluetoothSocket = null;
                InputStream inputStream = null;
                OutputStream outputStream = null;
                try {
                    bluetoothServerSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVER_SERVICE, SERVER_UUID);
                } catch (Exception e) { e.printStackTrace(); }

                try {
                    bluetoothSocket = bluetoothServerSocket.accept();
                    inputStream = bluetoothSocket.getInputStream();
                    outputStream = bluetoothSocket.getOutputStream();
                }
                catch (Exception e) {
                    if (!server_alive) {
                        return;
                    }
                    else {
                        e.printStackTrace();
                    }
                }

                remote_device_address = bluetoothSocket.getRemoteDevice().getAddress();
                remote_device_name = bluetoothSocket.getRemoteDevice().getName();
                dataInputStream = new DataInputStream(inputStream);
                dataOutputStream = new DataOutputStream(outputStream);
                msg("Connected to:\n\t\"" + remote_device_name + "\"\n\t[" + remote_device_address + "]", COLOR_YELLOW);
                connect();

                try {
                    bluetoothSocket.close();
                    bluetoothServerSocket.close();
                } catch (Exception e) { e.printStackTrace(); }
                remote_device_address = null;
                remote_device_name = null;
                msg("Ending session.\n", COLOR_WHITE);
            }
        }

        /** Desktop<->Mobile connection dialogs
         *
         * First connection - Desktop adds Mobile to its list, they exchange their public keys
         *   // Desktop->Mobile: Desktop sends its public key
         * > "pubkey" + {SEPARATOR} + [desktop_public_key]
         *    // Mobile->Desktop: Mobile user cancels connection (by user)
         * >> "bad_request"
         *    // Mobile->Desktop: Mobile sends its public key
         * >> "pubkey" + {SEPARATOR} + [mobile_public_key]
         *
         * Sign - Desktop asks Mobile for permission
         *   // Desktop->Mobile: Desktop sends message from server with separated part for signing
         * > "request" + {SEPARATOR} + [message_from_server] + {SEPARATOR} + [part_for_signature]
         *    // Mobile->Desktop: Mobile doesn't know this address
         * >> "bad_request"
         *    // Mobile->Desktop: known Desktop should sign time+random stamp
         * >> "sign" + {SEPARATOR} + [T_plus_rand]
         *   // Desktop->Mobile: Desktop signs using its private key
         * > "signed" + {SEPARATOR} + [signed_T_plus_rand]
         *    // Mobile->Desktop: wrong signature
         * >> "bad_request"
         *    // Mobile->Desktop: request accepted (by user)
         * >> "answer" + {SEPARATOR} + [signed_message_from_server]
         *
         */

        // Desktop<->Mobile connection dialog
        private void connect() {
            msgdbg("CONNECTION STARTED SUCCESSFULLY");
            String in_s = receiveS();
            if (in_s == null) {
                msg("Failure to receive.", COLOR_RED);
                return;
            }
            String[] request = in_s.split(Dialog.SEPARATOR);

            switch (request[0]) {
                // First connection - Desktop adds Mobile to its list, they exchange their public keys
                // > "pubkey" + {SEPARATOR} + [desktop_public_key]
                case Dialog.PUBKEY:
                    // >> "bad_request" // wrong number of parameters
                    if (request.length != 2) {
                        msg("Bad request.", COLOR_RED);
                        msgdbg("PUBKEY[2] : Bad request: \"" + in_s + "\"");
                        boolean out_b = sendS(Dialog.BAD_REQUEST);
                        if (!out_b) {
                            msg("Failure to send.", COLOR_RED);
                            return;
                        }
                        break;
                    }
                    request_PUBKEY(request);
                    break;
                // Sign - Desktop asks Mobile for permission
                // > "request" + {SEPARATOR} + [message_from_server] + {SEPARATOR} + [part_for_signature]
                case Dialog.REQUEST:
                    // >> "bad_request" // wrong number of parameters
                    if (request.length != 3) {
                        msg("Bad request.", COLOR_RED);
                        msgdbg("REQUEST[3] : Bad request: \"" + in_s + "\"");
                        boolean out_b = sendS(Dialog.BAD_REQUEST);
                        if (!out_b) {
                            msg("Failure to send.", COLOR_RED);
                            return;
                        }
                        break;
                    }
                    request_REQUEST(request);
                    break;
                // Invalid message
                // >> "bad_request" // wrong request type
                default:
                    msg("Bad request.", COLOR_RED);
                    msgdbg("\"UNDEF\" : Bad request: \"" + in_s + "\"");
                    boolean out_b = sendS(Dialog.BAD_REQUEST);
                    if (!out_b) {
                        msg("Failure to send.", COLOR_RED);
                        return;
                    }
                    break;
            }

            msgdbg("CONNECTION ENDED SUCCESSFULLY");
        }

        // > "pubkey" + {SEPARATOR} + [desktop_public_key]
        private void request_PUBKEY(String[] request) {
            msgdbg("\"PUBKEY\" STARTED SUCCESSFULLY");
            msg("Desktop wants to save this Mobile device to the list.\nCompare BT addresses before accepting!", COLOR_YELLOW);
            boolean result = askUser();
            if (result) {
                msg("Accept Desktop public key.\nSending Mobile public key.", COLOR_LIGHT_GREEN);
                // > "pubkey" + {SEPARATOR} + [desktop_public_key]
                desktop_publickey = request[1];
                desktop_address = remote_device_address;
                desktop_name = remote_device_name;
                String out_s = Dialog.PUBKEY + Dialog.SEPARATOR + mobile_publickey;
                // >> "pubkey" + {SEPARATOR} + [mobile_public_key]
                boolean out_b = sendS(out_s);
                if (!out_b) {
                    msg("Failure to send.", COLOR_RED);
                    return;
                }
                msg("Desktop is \"" + desktop_name + "\" (" + desktop_address + ")", COLOR_WHITE);
            }
            else {
                msg("Cancel Desktop public key.", COLOR_LIGHT_RED);
                // >> "bad_request" // Mobile user cancels connection
                boolean out_b = sendS(Dialog.BAD_REQUEST);
                if (!out_b) {
                    msg("Failure to send.", COLOR_RED);
                    return;
                }
            }
            msgdbg("\"PUBKEY\" ENDED SUCCESSFULLY");
        }

        // > "request" + {SEPARATOR} + [message_from_server] + {SEPARATOR} + [part_for_signature]
        private void request_REQUEST(String[] request) {
            msgdbg("\"REQUEST\" STARTED SUCCESSFULLY");
            if (!remote_device_address.equals(desktop_address)) {
                msg("Unknown device.", COLOR_LIGHT_RED);
                // >> "bad_request" // Mobile doesn't know this address
                boolean out_b = sendS(Dialog.BAD_REQUEST);
                if (!out_b) {
                    msg("Failure to send.", COLOR_RED);
                    return;
                }
            }
            else {
                msg("Prove device.", COLOR_WHITE);
                String checker = Dialog.getTime() + Dialog.getRand();
                String out_s = Dialog.SIGN + Dialog.SEPARATOR + checker;
                // >> "sign" + {SEPARATOR} + [T_plus_rand]
                boolean out_b = sendS(out_s);
                if (!out_b) {
                    msg("Failure to send.", COLOR_RED);
                    return;
                }

                // > "signed" + {SEPARATOR} + [signed_T_plus_rand]
                String in_s = receiveS();
                if (in_s == null) {
                    msg("Failure to receive.", COLOR_RED);
                    return;
                }
                String[] parts = in_s.split(Dialog.SEPARATOR);
                if ((parts.length != 2) || (!parts[0].equals(Dialog.SIGNED))) {
                    msg("Bad request.", COLOR_RED);
                    msgdbg("\"SIGNED\" : Bad request: \"" + in_s + "\"");
                    // >> "bad_request" // unexpected answer
                    out_b = sendS(Dialog.BAD_REQUEST);
                    if (!out_b) {
                        msg("Failure to send.", COLOR_RED);
                        return;
                    }
                    msgdbg("\"REQUEST\" ENDED SUCCESSFULLY");
                    return;
                }
                boolean signature = Dialog.checkSign(parts[1], desktop_publickey, checker);
                if (!signature) {
                    msg("Bad device.", COLOR_LIGHT_RED);
                    // >> "bad_request" // wrong signature
                    out_b = sendS(Dialog.BAD_REQUEST);
                    if (!out_b) {
                        msg("Failure to send.", COLOR_RED);
                        return;
                    }
                    msgdbg("\"REQUEST\" ENDED SUCCESSFULLY");
                    return;
                }

                // > "request" + {SEPARATOR} + [message_from_server] + {SEPARATOR} + [part_for_signature]
                msg("Incoming request:\n\"" + request[1] + "\"", COLOR_CYAN);
                boolean result = askUser();
                if (result) {
                    msg("Accepted.", COLOR_LIGHT_GREEN);
                    // > "request" + {SEPARATOR} + [message_from_server] + {SEPARATOR} + [part_for_signature]
                    String signed = Dialog.makeSign(request[2], mobile_privatekey);
                    out_s = Dialog.ANSWER + Dialog.SEPARATOR + signed;
                    // >> "answer" + {SEPARATOR} + [signed_message_from_server]
                    out_b = sendS(out_s);
                    if (!out_b) {
                        msg("Failure to send.", COLOR_RED);
                        return;
                    }
                }
                else {
                    msg("Canceled.", COLOR_LIGHT_RED);
                    // >> "bad_request" // cancelled by user
                    out_b = sendS(Dialog.BAD_REQUEST);
                    if (!out_b) {
                        msg("Failure to send.", COLOR_RED);
                        return;
                    }
                }
            }
            msgdbg("\"REQUEST\" ENDED SUCCESSFULLY");
        }

        // Safe receiving
        private String receiveS() {
            String data_in = null;
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(dataInputStream));
            try {
                data_in = bufferedReader.readLine();
            } catch (Exception e) { e.printStackTrace(); }

            String data_out = "" + "\n";
            try {
                dataOutputStream.writeBytes(data_out);
                dataOutputStream.flush();
            } catch (Exception e) { e.printStackTrace(); }

            msgdbg("RECEIVE: \"" + data_in +"\"");
            return data_in;
        }

        // Safe sending
        private boolean sendS(String string) {
            String data_out = string + "\n";
            try {
                dataOutputStream.writeBytes(data_out);
                dataOutputStream.flush();
            } catch (Exception e) { e.printStackTrace(); }

            String data_in = null;
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(dataInputStream));
            try {
                data_in = bufferedReader.readLine();
            } catch (Exception e) { e.printStackTrace(); }

            msgdbg("SEND: \"" + data_out +"\"");
            return (data_in != null) && (data_in.equals(""));
        }
    }

    // Dialog supporting functionality
    private static final class Dialog {
        private Dialog() { }

        private static final String SEPARATOR = ":";
        private static final String PUBKEY = "pubkey";
        private static final String BAD_REQUEST = "bad_request";
        private static final String REQUEST = "request";
        private static final String SIGN = "sign";
        private static final String SIGNED = "signed";
        private static final String ANSWER = "answer";

        private static String getMobilePublickey() {
            return "PoniesAreMagic";
        }

        private static String getMobilePrivatekey() {
            return "PoniesAreMagic";
        }

        private static String getTime() {
            Date date = new Date();
            Long time = date.getTime();
            return String.valueOf(time);
        }

        private static String getRand() {
            SecureRandom secureRandom = new SecureRandom();
            Long rand = Math.abs(secureRandom.nextLong());
            return String.valueOf(rand);
        }

        private static boolean checkSign(String signed, String key, String checker) {
            return signed.equals(checker + key);
        }

        private static String makeSign(String signing, String key) {
            return signing + key;
        }
    }
}
