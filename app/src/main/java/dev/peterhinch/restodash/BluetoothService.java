/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Reference: https://github.com/android/connectivity-samples/blob/main/BluetoothChat/Application/src/main/java/com/example/android/bluetoothchat/BluetoothChatService.java


package dev.peterhinch.restodash;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothService {
    public static final String TAG = "BluetoothService";

    // Name for the SDP record when creating server socket
    private static final String APP_NAME = "";

    // Assign a unique UUID to the application.
    private static final UUID APP_UUID =
            UUID.fromString("688c1706-fcb4-4856-be4b-f7d6f8465b6f");

    // Declare member fields.
    private final BluetoothAdapter adapter;
    private final Handler handler;
    private AcceptThread acceptThread;
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;
    private int state;
    private int newState;

    // Constants for current connection state.
    public static final int STATE_NONE = 0; // Nothing happening.
    public static final int STATE_LISTEN = 1; // Listening for an incoming connections.
    public static final int STATE_CONNECTING = 2; // Initiating an outgoing connection.
    public static final int STATE_CONNECTED = 3; // Connected to a remote device.

    // Constructor for preparing a new bluetooth connection.
    public BluetoothService(Context context, Handler handler) {
        adapter = BluetoothAdapter.getDefaultAdapter();
        state = STATE_NONE;
        newState = state;
        this.handler = handler;
    }

    public synchronized int getState() {
        return state;
    }

    // Start the bluetooth service.
    public synchronized void start() {
        Log.d(TAG, "start");

        // Cancel current attempts at a connection.
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        // Cancel any current connections.
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
        // Start the thread to listen on a BluetoothServerSocket.
        if (acceptThread == null) {
            acceptThread = new AcceptThread();
            acceptThread.start();
        }
    }

    public synchronized void connect(BluetoothDevice device) {
        Log.d(TAG, "connect to: " + device);

        // Cancel current attempts at a connection.
        if (state == STATE_CONNECTING && connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        // Cancel any current connections.
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
        // Start the thread to connect with the device.
        connectThread = new ConnectThread(device);
        connectThread.start();
    }

    public synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device, final String socketType) {
        Log.d(TAG, "connected, socket type: " + socketType);

        // Cancel the current thread that completed the connection.
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        // Cancel any current connections.
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
        // Cancel the accept thread so only one device is connected.
        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }
        // Start a thread that manages the connection and performs transmissions.
        connectedThread = new ConnectedThread(socket);
        connectedThread.start();
        // Send the device name to the UI Activity
        Message message = handler.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.DEVICE_NAME, device.getName());
        message.setData(bundle);
        handler.sendMessage(message);
    }

    // Stop all threads.
    public synchronized void stop() {
        Log.d(TAG, "stop");

        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }
        state = STATE_NONE;
    }

    // Write to the connectedThread in an un-synchronized manner.
    public void write(byte[] out) {
        // Create a temporary ConnectedThread object.
        ConnectedThread r;

        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (state != STATE_CONNECTED) {
                return;
            }
            r = connectedThread;
        }

        // Perform the write un-synchronized
        r.write(out);
    }

    // Notify the UI Activity when a connection has failed.
    private void connectionFailed() {
        // Send failure message back to the activity.
        Message message = handler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Unable to connect to device.");
        message.setData(bundle);
        handler.sendMessage(message);

        state = STATE_NONE;

        // Start the service once more in listening mode.
        BluetoothService.this.start();
    }

    // Notify the UI Activity when a connection is lost.
    private void connectionLost() {
        // Send failure message back to the activity.
        Message message = handler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Device connection was lost.");
        message.setData(bundle);
        handler.sendMessage(message);

        state = STATE_NONE;

        // Start the service once more in listening mode.
        BluetoothService.this.start();
    }

    // This thread runs while listening for incoming connections and runs until
    // a connection is accepted, or until cancelled.
    private class AcceptThread extends Thread {
        // Local server socket.
        private final BluetoothServerSocket serverSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;

            // Create a new listening server socket.
            try {
                tmp = adapter.listenUsingRfcommWithServiceRecord(APP_NAME, APP_UUID);
            } catch (IOException ioException) {
                Log.e(TAG, "Socket listen() failed.", ioException);
            }
            serverSocket = tmp;
            state = STATE_LISTEN;
        }

        public void run() {
            Log.d(TAG, "BEGIN acceptThread " + this);
            setName("AcceptThread");

            BluetoothSocket socket;

            // Listen to the server socket if not connected.
            while (state != STATE_CONNECTED) {
                try {
                    // This call is blocking and will only return once there is
                    // a successful connection or an exception.
                    socket = serverSocket.accept();
                } catch (IOException ioException) {
                    Log.e(TAG, "Socket accept() failed.", ioException);
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized (BluetoothService.this) {
                        switch (state) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                // Normal situation - start the connected thread.
                                connected(socket, socket.getRemoteDevice(), "Secure");
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                // Already connected or not ready - Terminate new socket.
                                try {
                                    socket.close();
                                } catch (IOException ioException) {
                                    Log.e(TAG, "Failed to close unwanted socket.",
                                            ioException);
                                }
                                break;
                        }
                    }
                }
            }
            Log.i(TAG, "END acceptThread.");
        }

        public void cancel() {
            Log.d(TAG, "Cancel " + this);
            try {
                serverSocket.close();
            } catch (IOException ioException) {
                Log.e(TAG, "close() of server failed.", ioException);
            }
        }
    }

    // This thread runs while attempting to make an outgoing connection with
    // a device. It runs straight through and either succeeds or fails.
    private class ConnectThread extends Thread {
        private final BluetoothSocket socket;
        private final BluetoothDevice device;

        public ConnectThread(BluetoothDevice device) {
            this.device = device;
            BluetoothSocket tmp = null;

            // Get a BluetoothSocket for a connection with the given BluetoothDevice.
            try {
                tmp = device.createRfcommSocketToServiceRecord(APP_UUID);
            } catch (IOException ioException) {
                Log.e(TAG, "Socket create() failed.", ioException);
            }
            socket = tmp;
            state = STATE_CONNECTING;
        }

        public void run() {
            Log.i(TAG, "BEGIN connectThread.");
            setName("connectThread");

            // Cancel discovery to prevent slowing the connection.
            adapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket.
            try {
                // This is a blocking call and will only return on a successful
                // connection or an exception.
                socket.connect();
            } catch (IOException ioException1) {
                // Close the socket
                try {
                    socket.close();
                } catch (IOException ioException2) {
                    Log.e(TAG, "Unable to close() socket during connection failure.",
                            ioException2);
                }
                connectionFailed();
                return;
            }

            // Reset the connectThread
            synchronized (BluetoothService.this) {
                connectThread = null;
            }

            // Start the connectedThread
            connected(socket, device, "Secure");
        }

        public void cancel() {
            try {
                socket.close();
            } catch (IOException ioException) {
                Log.e(TAG, "Socket close() failed.", ioException);
            }
        }
    }

    // This thread runs during a connection with a remote device and handles
    // incoming and outgoing transmissions.
    private class ConnectedThread extends Thread {
        private final BluetoothSocket socket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "Create connectedThread.");
            this.socket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams.
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException ioException) {
                Log.e(TAG, "Temporary sockets not created.", ioException);
            }

            inputStream = tmpIn;
            outputStream = tmpOut;
            state = STATE_CONNECTED;
        }

        public void run() {
            Log.i(TAG, "BEGIN connectedThread.");
            byte[] buffer = new byte[1024];
            int bytes;

            // Listen to the InputStream while connected.
            while (state == STATE_CONNECTED) {
                try {
                    // Read from the InputStream.
                    bytes = inputStream.read(buffer);

                    // Send the bytes obtained to the UI activity.
                    handler.obtainMessage(Constants.MESSAGE_READ, bytes, -1, buffer)
                            .sendToTarget();
                } catch (IOException ioException) {
                    Log.e(TAG, "Disconnected.", ioException);
                    connectionLost();
                    break;
                }
            }
        }

        // Write to the connected OutputStream.
        public void write(byte[] buffer) {
            try {
                outputStream.write(buffer);

                // Share the sent message back to the UI activity.
                handler.obtainMessage(Constants.MESSAGE_WRITE, -1, -1, buffer)
                        .sendToTarget();
            } catch (IOException ioException) {
                Log.e(TAG, "Exception during write.", ioException);
            }
        }

        public void cancel() {
            try {
                socket.close();
            } catch (IOException ioException) {
                Log.e(TAG, "connectSocket close() failed.", ioException);
            }
        }
    }
}
