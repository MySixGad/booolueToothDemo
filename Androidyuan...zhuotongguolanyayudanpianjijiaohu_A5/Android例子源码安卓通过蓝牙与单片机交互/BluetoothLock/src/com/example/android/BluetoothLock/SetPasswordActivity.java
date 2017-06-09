package com.example.android.BluetoothLock;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


public class SetPasswordActivity extends Activity {
	// Debugging
    private static final String TAG = "BluetoothLock";
    private static final boolean D = true;
    
    public static final String SET_PASSWORD_SUCCESS = "T";
    public static final String SET_PASSWORD_FAIL = "E";
    public static final String TEST_PASSWORD = "C";//��ǰ������ȷ��־
    //ԭ������ȷ��־
    private Boolean mFlag = false;
    
    // Message types sent from the BluetoothLockService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    
    // Key names received from the BluetoothLockService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
    
    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
   
    //test mmSocket��ֵ
    public static String temp = null;
    public static String mmSocket = null;
    
    //���
    TextView mTitle;
    private Button mOKBtn;
    private Button mCancleBtn;
    private String mCurrentPassword;
    private String mNewPassword;
    private String mShowNewPassword;
    private String mShowCurrentPassword;
    
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // String buffer for outgoing messages
    private StringBuffer mOutStringBuffer;
    private BluetoothSocket mBtSocket = null;
    private OutputStream mOutStream = null;
    
    // Name of the connected device
    private String mConnectedDeviceName = null;
    // Member object for the Lock services
    private SetPasswordService mSetPasswordService = null;
    
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	if(D) Log.e(TAG, "+++ ON CREATE +++");

        // Set up the window layout
        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        setContentView(R.layout.set_password);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.custom_title);
        
        // Set up the custom title
        mTitle = (TextView) findViewById(R.id.title_left_text);
        mTitle.setText(R.string.setPassword_name);
        mTitle = (TextView) findViewById(R.id.title_right_text);
        
    	mCancleBtn = (Button) findViewById(R.id.btnCancel);
    	mCancleBtn.setOnClickListener(new CancelClickListener());
    	
    	// Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
        	DisplayToast("Bluetooth is not available");
            finish();
            return;
        }
       
	}
    
	public void setmCurrentPassword() {
		String str = mCurrentPassword;
		str = "*" + str + "$";
		mCurrentPassword = str;
	}

	public void setmNewPassword() {
		String str = mNewPassword;
		str = "@" + str + "$";
		mNewPassword = str;
	}

	private class CancelClickListener implements OnClickListener{
	
		@Override
		public void onClick(View v) {
			//���ȡ����ť������������
			EditText currentPasswordEditText = (EditText)findViewById(R.id.setCurrentPassword);
			currentPasswordEditText.setText("");
			
			//���ȡ����ť������������
			EditText newPasswordEditText = (EditText)findViewById(R.id.newPassword);
			newPasswordEditText.setText("");
			
		}
		 
	 }
	
	@SuppressLint("NewApi")
	@Override
    public void onStart() {
        super.onStart();
        if(D) Log.e(TAG, "++ ON START ++");

        // If BT is not on, request that it be enabled.
        // setupLock() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        // Otherwise, setup the Lock session
        } else {
            if (mSetPasswordService == null) setupLock();
        }
    }
	
	@Override
    public synchronized void onResume() {
        super.onResume();
        if(D) Log.e(TAG, "+ ON RESUME +");

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mSetPasswordService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mSetPasswordService.getState() == SetPasswordService.STATE_NONE) {
              // Start the Bluetooth Lock services
            	mSetPasswordService.start();
            }
        }
    }
	
	 @SuppressLint("NewApi")
		private void setupLock() {
	        Log.d(TAG, "setupLock()");

	        mOKBtn = (Button) findViewById(R.id.btnOK);
	        mOKBtn.setOnClickListener(new OnClickListener(){
		        @Override
				public void onClick(View v) {
					//��ȡ���û�����ĵ�ǰ����
					EditText currentPasswordEditText = (EditText)findViewById(R.id.setCurrentPassword);
					mCurrentPassword = currentPasswordEditText.getText().toString();
					mShowCurrentPassword = mCurrentPassword;
					//��ȡ�û������������
					EditText newPasswordEditText = (EditText)findViewById(R.id.newPassword);
					mNewPassword = newPasswordEditText.getText().toString();
					mShowNewPassword = mNewPassword;
					
					if(mCurrentPassword.length() != 0 && mNewPassword.length() != 0 
							&& !mCurrentPassword.equals(mNewPassword)){
						SetPasswordActivity.this.setmCurrentPassword();
						SetPasswordActivity.this.setmNewPassword();
						DisplayToast("���ڷ���ԭ����...");
						//�������� ������֤ ֻ����֤��ǰ���������Ƿ���ȷ����
						sendMessage(mCurrentPassword);
		        	}
					else if(mCurrentPassword.length() == 0 || mNewPassword.length() == 0 ){
						DisplayToast("ԭ�������������Ϊ��");
					}
					else if(mCurrentPassword.equals(mNewPassword)){
						DisplayToast("ԭ����������벻����ͬ");
						//���ȡ����ť������������
						currentPasswordEditText.setText("");
						//���ȡ����ť������������
						newPasswordEditText.setText("");
					
					}
		        }
	        });

	        // Initialize the BluetoothLockService to perform bluetooth connections
	        mSetPasswordService = new SetPasswordService(this, mHandler);

	        // Initialize the buffer for outgoing messages
	        mOutStringBuffer = new StringBuffer("");
	    }
	
	 @Override
	    public synchronized void onPause() {
	        super.onPause();
	        if(D) Log.e(TAG, "- ON PAUSE -");
	    }

	    @Override
	    public void onStop() {
	        super.onStop();
	        if(D) Log.e(TAG, "-- ON STOP --");
	    }

	    @Override
	    public void onDestroy() {
	        super.onDestroy();
	        // Stop the Bluetooth Lock services
	        if (mSetPasswordService != null) mSetPasswordService.stop();
	        if(D) Log.e(TAG, "--- ON DESTROY ---");
	    }

	    @SuppressLint("NewApi")
		private void ensureDiscoverable() {
	        if(D) Log.d(TAG, "ensure discoverable");
	        if (mBluetoothAdapter.getScanMode() !=
	            BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
	            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
	            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
	            startActivity(discoverableIntent);
	        }
	    }

	protected Boolean sendMessage(String message) {
		Boolean flag = false;
		// Check that we're actually connected before trying anything
        if (mSetPasswordService.getState() != SetPasswordService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return flag;
        }
		// Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothLockService to write
            byte[] send = message.getBytes();
            mSetPasswordService.write(send);
            
            //������������
			EditText currentPasswordEditText = (EditText)findViewById(R.id.setCurrentPassword);
			currentPasswordEditText.setText("");
			//������������
			EditText newPasswordEditText = (EditText)findViewById(R.id.newPassword);
			newPasswordEditText.setText("");
			flag = true;
        }
		return flag;
    }
	
	private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_STATE_CHANGE:
                if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                switch (msg.arg1) {
                case SetPasswordService.STATE_CONNECTED:
                    mTitle.setText(R.string.title_connected_to);
                    mTitle.append(mConnectedDeviceName);
                    break;
                case SetPasswordService.STATE_CONNECTING:
                    mTitle.setText(R.string.title_connecting);
                    break;
                case SetPasswordService.STATE_LISTEN:
                case SetPasswordService.STATE_NONE:
                    mTitle.setText(R.string.title_not_connected);
                    break;
                }
                break;
            case MESSAGE_WRITE:
            	/*
            	/*
            	 * 
            	 * ������Ϣ
            	 * 
            	 * 
            	 */
                byte[] writeBuf = (byte[]) msg.obj;
                // construct a string from the buffer
                String writeMessage = new String(writeBuf);
                break;
            case MESSAGE_READ:{
            	byte[] readBuf = (byte[]) msg.obj;
                // construct a string from the valid bytes in the buffer
                String readMessage = new String(readBuf, 0, msg.arg1);
                	/**
                	 * ��ȡ���յ�����Ϣ
                	 */
                //�Խ��յ������ݽ��м��
                if(readMessage.equals(SET_PASSWORD_SUCCESS)){
                	String str = "�������óɹ� " + "\n�����룺" + mShowNewPassword;
                	DisplayToast(str);
                }
                else if(readMessage.equals(TEST_PASSWORD)){
                	//String str = "ԭ������ȷ\n" + "ԭ���룺" + mCurrentPassword;
            		//DisplayToast(str);
            		mFlag = true;
            		sendMessage(mNewPassword);
                }
                else if(readMessage.equals(SET_PASSWORD_FAIL)){
                	String str = "ԭ�������\n" + "��������룺" + mShowCurrentPassword;
                	DisplayToast(str);
				}
				
            	}
               break;
            case MESSAGE_DEVICE_NAME:
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                Toast.makeText(getApplicationContext(), "Connected to "
                               + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                break;
            case MESSAGE_TOAST:
                Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                               Toast.LENGTH_SHORT).show();
                break;
            }
        }
        
        private Boolean sendMessage(String message) {
    		Boolean flag = false;
    		// Check that we're actually connected before trying anything
            if (mSetPasswordService.getState() != SetPasswordService.STATE_CONNECTED) {
                Toast.makeText(SetPasswordActivity.this, R.string.not_connected, Toast.LENGTH_SHORT).show();
                return flag;
            }
    		// Check that there's actually something to send
            if (message.length() > 0) {
                // Get the message bytes and tell the BluetoothLockService to write
                byte[] send = message.getBytes();
                mSetPasswordService.write(send);
                
                //������������
    			EditText currentPasswordEditText = (EditText)findViewById(R.id.setCurrentPassword);
    			currentPasswordEditText.setText("");
    			//������������
    			EditText newPasswordEditText = (EditText)findViewById(R.id.newPassword);
    			newPasswordEditText.setText("");
    			flag = true;
            }
    		return flag;
        }
    };
	
	 public void DisplayToast(String str)
	    {
	    	Toast toast=Toast.makeText(this, str, Toast.LENGTH_LONG);
	    	//toast.setGravity(Gravity.TOP, 0, 220);
	    	toast.show();
	    	
	    }
	 
	 @SuppressLint("NewApi")
		public void onActivityResult(int requestCode, int resultCode, Intent data) {
	        if(D) Log.d(TAG, "onActivityResult " + resultCode);
	        switch (requestCode) {
	        case REQUEST_CONNECT_DEVICE:
	            // When DeviceListActivity returns with a device to connect
	            if (resultCode == Activity.RESULT_OK) {
	                // Get the device MAC address
	                String address = data.getExtras()
	                                     .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
	                // Get the BLuetoothDevice object
	                BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
	        
	                /*
	                /*�����豸 ���ݵ�ַ��ȡ����device��������
	                 * 
	                 * 
	                 */
	                mSetPasswordService.connect(device);
	                DisplayToast("�Ѿ����ӵ��ӻ�ģ��");
	            }
	            break;
	        case REQUEST_ENABLE_BT:
	            // When the request to enable Bluetooth returns
	            if (resultCode == Activity.RESULT_OK) {
	                // ���ӳɹ� ���д�����Ϣ
	                setupLock();
	               
	            } else {
	                // User did not enable Bluetooth or an error occured
	                Log.d(TAG, "BT not enabled");
	                Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
	                finish();
	            }
	        }
	    }

	 @Override
	    public boolean onCreateOptionsMenu(Menu menu) {
	        MenuInflater inflater = getMenuInflater();
	        inflater.inflate(R.menu.option_menu, menu);
	        return true;
	    }

	    @Override
	    public boolean onOptionsItemSelected(MenuItem item) {
	        switch (item.getItemId()) {
	        case R.id.scan:
	            // ����DeviceListActivity���� ���в鿴��ɨ���豸
	            Intent serverIntent = new Intent(this, DeviceListActivity.class);
	            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
	            return true;
	        case R.id.discoverable:
	            // ȷ�����豸���пɼ����
	            ensureDiscoverable();
	            return true;
	        }
	        return false;
	    }
}
