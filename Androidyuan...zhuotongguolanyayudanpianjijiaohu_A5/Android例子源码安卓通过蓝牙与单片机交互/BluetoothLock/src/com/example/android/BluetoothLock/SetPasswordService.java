package com.example.android.BluetoothLock;

import java.io.IOException;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;


public class SetPasswordService {
	 private static final String TAG = "SetPasswordService";
	    private static final boolean D = true;

	    // Name for the SDP record when creating server socket
	    private static final String NAME = "SetPasswordActivity";

	    // Unique UUID for this application
	    static final String SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB";  
	    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	    //fa87c0d0-afac-11de-8a39-0800200c9a66
	    // Member fields
	    private final BluetoothAdapter mAdapter;
	    private final Handler mHandler;
	    private AcceptThread mAcceptThread;
	    private ConnectThread mConnectThread;
	    private ConnectedThread mConnectedThread;
	    private int mState;

	    // Constants that indicate the current connection state
	    public static final int STATE_NONE = 0;       // we're doing nothing
	    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
	    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
	    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

	    /**
	     * Constructor. Prepares a new SetPasswordActivity session.
	     *
	     * @param handler  A Handler to send messages back to the UI Activity
	     */
	    @TargetApi(19)
	    public SetPasswordService(Context context, Handler handler) {
	        mAdapter = BluetoothAdapter.getDefaultAdapter();
	        mState = STATE_NONE;
	        mHandler = handler;
	    }

	    /**
	     * Set the current state of the Lock connection
	     * @param state  An integer defining the current connection state
	     */
	    @TargetApi(19)
	    private synchronized void setState(int state) {
	        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
	        mState = state;

	        // Give the new state to the Handler so the UI Activity can update
	        mHandler.obtainMessage(SetPasswordActivity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
	    }

	    /**
	     * Return the current connection state. */
	    @TargetApi(19)
	    public synchronized int getState() {
	        return mState;
	    }

	    /**
	     * Start the Lock service. Specifically start AcceptThread to begin a
	     * session in listening (server) mode. Called by the Activity onResume() */
	    public synchronized void start() {
	        if (D) Log.d(TAG, "start");

	        // Cancel any thread attempting to make a connection
	        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

	        // Cancel any thread currently running a connection
	        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

	        // Start the thread to listen on a BluetoothServerSocket
	        if (mAcceptThread == null) {
	            mAcceptThread = new AcceptThread();
	            mAcceptThread.start();
	        }
	        setState(STATE_LISTEN);
	    }

	    /**
	     * Start the ConnectThread to initiate a connection to a remote device.
	     * @param device  The BluetoothDevice to connect
	     */
	    @TargetApi(19)
	    public synchronized void connect(BluetoothDevice device) {
	        if (D) Log.d(TAG, "connect to: " + device);

	        // Cancel any thread attempting to make a connection
	        if (mState == STATE_CONNECTING) {
	            if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
	        }

	        // Cancel any thread currently running a connection
	        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

	        // Start the thread to connect with the given device
	        mConnectThread = new ConnectThread(device);
	        //连接线程开启 自动调用run方法 进行连接
	        mConnectThread.start();
	        setState(STATE_CONNECTING);
	    }

	    /**
	     * Start the ConnectedThread to begin managing a Bluetooth connection
	     * @param socket  The BluetoothSocket on which the connection was made
	     * @param device  The BluetoothDevice that has been connected
	     */
	    @TargetApi(19)
	    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
	        if (D) Log.d(TAG, "connected");

	        // Cancel the thread that completed the connection
	        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

	        // Cancel any thread currently running a connection
	        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

	        // Cancel the accept thread because we only want to connect to one device
	        if (mAcceptThread != null) {mAcceptThread.cancel(); mAcceptThread = null;}

	        // Start the thread to manage the connection 和  执行传送
	        mConnectedThread = new ConnectedThread(socket);
	        mConnectedThread.start();
	        //SetPasswordActivity.mFlag = true;
	        // Send the name of the connected device back to the UI Activity
	        Message msg = mHandler.obtainMessage(SetPasswordActivity.MESSAGE_DEVICE_NAME);
	        Bundle bundle = new Bundle();
	        bundle.putString(SetPasswordActivity.DEVICE_NAME, device.getName());
	        msg.setData(bundle);
	        mHandler.sendMessage(msg);

	        setState(STATE_CONNECTED);
	    }

	    /**
	     * Stop all threads
	     */
	    public synchronized void stop() {
	        if (D) Log.d(TAG, "stop");
	        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
	        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}
	        if (mAcceptThread != null) {mAcceptThread.cancel(); mAcceptThread = null;}
	        setState(STATE_NONE);
	    }

	    /**
	     * Write to the ConnectedThread in an unsynchronized manner
	     * @param out The bytes to write
	     * @see ConnectedThread#write(byte[])
	     */
	    public void write(byte[] out) {
	        // Create temporary object
	        ConnectedThread r;
	        // Synchronize a copy of the ConnectedThread
	        synchronized (this) {
	            if (mState != STATE_CONNECTED) return;
	            r = mConnectedThread;
	        }
	        // send
	        r.write(out);
	    }

	    /**
	     * Indicate that the connection attempt failed and notify the UI Activity.
	     */
	    private void connectionFailed() {
	        setState(STATE_LISTEN);

	        // Send a failure message back to the Activity
	        Message msg = mHandler.obtainMessage(SetPasswordActivity.MESSAGE_TOAST);
	        Bundle bundle = new Bundle();
	        bundle.putString(SetPasswordActivity.TOAST, "Unable to connect device");
	        msg.setData(bundle);
	        mHandler.sendMessage(msg);
	    }

	    /**
	     * Indicate that the connection was lost and notify the UI Activity.
	     */
	    private void connectionLost() {
	        setState(STATE_LISTEN);

	        // Send a failure message back to the Activity
	        Message msg = mHandler.obtainMessage(SetPasswordActivity.MESSAGE_TOAST);
	        Bundle bundle = new Bundle();
	        bundle.putString(SetPasswordActivity.TOAST, "Device connection was lost");
	        msg.setData(bundle);
	        mHandler.sendMessage(msg);
	    }
	    
	    /**
	     * This thread runs while listening for incoming connections. It behaves
	     * like a server-side client. It runs until a connection is accepted
	     * (or until cancelled).
	     */ //作为服务端
	    private class AcceptThread extends Thread {
	        // The local server socket
	        private final BluetoothServerSocket mmServerSocket;

	        public AcceptThread() {
	            BluetoothServerSocket tmp = null;

	            // Create a new listening server socket
	            try {
	                tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME, MY_UUID);
	            } catch (IOException e) {
	                Log.e(TAG, "listen() failed", e);
	            }
	            mmServerSocket = tmp;
	        }
	        
	        public void run() {
	            if (D) Log.d(TAG, "BEGIN mAcceptThread" + this);
	            setName("AcceptThread");
	            BluetoothSocket socket = null;

	            // Listen to the server socket if we're not connected
	            while (mState != STATE_CONNECTED) {
	                try {
	                    // This is a blocking call and will only return on a
	                    // successful connection or an exception
	                    socket = mmServerSocket.accept();
	                } catch (IOException e) {
	                    Log.e(TAG, "accept() failed", e);
	                    break;
	                }

	                // If a connection was accepted
	                if (socket != null) {
	                    synchronized (SetPasswordService.this) {
	                        switch (mState) {
	                        case STATE_LISTEN:
	                        case STATE_CONNECTING:
	                            // Situation normal. Start the connected thread.
	                            connected(socket, socket.getRemoteDevice());
	                            break;
	                        case STATE_NONE:
	                        case STATE_CONNECTED:
	                            // Either not ready or already connected. Terminate new socket.
	                            try {
	                                socket.close();
	                            } catch (IOException e) {
	                                Log.e(TAG, "Could not close unwanted socket", e);
	                            }
	                            break;
	                        }
	                    }
	                }
	            }
	            if (D) Log.i(TAG, "END mAcceptThread");
	        }

	        public void cancel() {
	            if (D) Log.d(TAG, "cancel " + this);
	            try {
	                mmServerSocket.close();
	            } catch (IOException e) {
	                Log.e(TAG, "close() of server failed", e);
	            }
	        }
	    }


	    /**
	     * This thread runs while attempting to make an outgoing connection
	     * with a device. It runs straight through; the connection either
	     * succeeds or fails.
	     */
	    private class ConnectThread extends Thread {
	        private final BluetoothSocket mmSocket;
	        private final BluetoothDevice mmDevice;

	        public ConnectThread(BluetoothDevice device) {
	            mmDevice = device;
	            BluetoothSocket tmp = null;

	            // Get a BluetoothSocket for a connection with the
	            // given BluetoothDevice
	            try {
	            	//可以理解为向服务机发送uuid
	                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
	                //如果tmp不为空 说明已经单片机与手机已经连接成功
	       
	            } catch (IOException e) {
	                Log.e(TAG, "create() failed", e);
	            }
	            //初始化mmSocket
	            mmSocket = tmp;
	            SetPasswordActivity.temp = "" + tmp;
	        }

	        public void run() {
	            Log.i(TAG, "BEGIN mConnectThread");
	            setName("ConnectThread");

	            // 关闭发现设备 不关闭将会是连接变慢
	            mAdapter.cancelDiscovery();
	            
	            // Make a connection to the BluetoothSocket
	            try {
	                // This is a blocking call and will only return on a
	                // successful connection or an exception
	            	//开始连接
	            	SetPasswordActivity.mmSocket = "" + mmSocket;
	                mmSocket.connect();
	            } catch (IOException e) {
	                connectionFailed();
	                // Close the socket
	                try {
	                    mmSocket.close();
	                } catch (IOException e2) {
	                    Log.e(TAG, "unable to close() socket during connection failure", e2);
	                }
	                // Start the service over to restart listening mode
	                SetPasswordService.this.start();
	                return;
	            }

	            // Reset the ConnectThread because we're done
	            synchronized (SetPasswordService.this) {
	                mConnectThread = null;
	            }

	            // Start the connected thread
	            connected(mmSocket, mmDevice);
	        }

	        public void cancel() {
	            try {
	                mmSocket.close();
	            } catch (IOException e) {
	                Log.e(TAG, "close() of connect socket failed", e);
	            }
	        }
	    }

	    /**
	     * 当和远端设备连接上之后启用当前线程   进行消息发送和接收
	     * 
	     */
	    private class ConnectedThread extends Thread {
	        private final BluetoothSocket mmSocket;
	        private final InputStream mmInStream;
	        private final OutputStream mmOutStream;

	        public ConnectedThread(BluetoothSocket socket) {
	            Log.d(TAG, "create ConnectedThread");
	            mmSocket = socket;
	            InputStream tmpIn = null;
	            OutputStream tmpOut = null;

	            // Get the BluetoothSocket input and output streams
	            try {
	                tmpIn = socket.getInputStream();
	                tmpOut = socket.getOutputStream();
	            } catch (IOException e) {
	                Log.e(TAG, "temp sockets not created", e);
	            }

	            mmInStream = tmpIn;
	            mmOutStream = tmpOut;
	        }

	        public void run() {
	            Log.i(TAG, "BEGIN mConnectedThread");
	            byte[] buffer = new byte[1024];
	            int bytes;

	            // Keep listening to the InputStream while connected
	            while (true) {
	                try {
	                    // 从InputStream中读数据
	                    bytes = mmInStream.read(buffer);

	                    // 把数据发送至主窗体
	                    mHandler.obtainMessage(SetPasswordActivity.MESSAGE_READ, bytes, -1, buffer)
	                            .sendToTarget();
	                } catch (IOException e) {
	                    Log.e(TAG, "disconnected", e);
	                    connectionLost();
	                    break;
	                }
	            }
	        }

	        /**
	         * Write to the connected OutStream.
	         * @param buffer  The bytes to write
	         */
	        public void write(byte[] buffer) {
	            try {
	                mmOutStream.write(buffer);

	                // Share the sent message back to the UI Activity
	                mHandler.obtainMessage(SetPasswordActivity.MESSAGE_WRITE, -1, -1, buffer)
	                        .sendToTarget();
	            } catch (IOException e) {
	                Log.e(TAG, "Exception during write", e);
	            }
	        }

	        public void cancel() {
	            try {
	                mmSocket.close();
	            } catch (IOException e) {
	                Log.e(TAG, "close() of connect socket failed", e);
	            }
	        }
	    }
	}
