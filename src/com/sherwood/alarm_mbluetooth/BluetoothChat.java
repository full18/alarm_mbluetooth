package com.sherwood.alarm_mbluetooth;

import java.util.Calendar;
import java.util.TimeZone;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.TimePicker.OnTimeChangedListener;
import android.widget.Toast;


@SuppressLint("NewApi") 
public class BluetoothChat extends Activity {
    // Debugging
    private static final String TAG = "BluetoothChat";
    private static final boolean D = true;
    private static final boolean DD = false;

    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    
    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;   

    // Layout Views
    private ListView mConversationView;
    private EditText mOutEditText;
    private Button mSendButton;
    private TimePicker tp;

    // Name of the connected device
    private String mConnectedDeviceName = null;
    // Array adapter for the conversation thread
    private ArrayAdapter<String> mConversationArrayAdapter;
    // String buffer for outgoing messages
    private StringBuffer mOutStringBuffer;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the chat services
    private BluetoothChatService mChatService = null;	    
    
    //New addition,for pendingIntent
    private PendingIntent mAlarmSender;
    //Alarm flag
    private boolean mAlarm_Flag;
    //Alarm time   
    public int mHour,mMin;    
    //interval 24hours
    private static final int INTERVAL = 1000 * 60 * 60 * 24;// 24h   
   // private static int time[]=new int[3];

    //IntentFilter keys
    private static final String ALARM="com.example.android.BluetoothChat.AlarmService_Service";
    
    //sharedpreferences keys
    public static final String SEND_KEY="SEND_KEY";
    public static final String SEND_MESSAGE="SEND_MESSAGE";
    
    
   
	@SuppressLint("NewApi") @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(D) Log.e(TAG, "+++ ON CREATE +++");  
        
        // Set up the window layout
        setContentView(R.layout.main);

        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        
        // Register for broadcasts 
        IntentFilter filter = new IntentFilter(ALARM);
        this.registerReceiver(mReceiver, filter);
         mAlarm_Flag=DD;
         
        
        
        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        //initiate the timepicker
        mHour=6;
        mMin=30;
        tp = (TimePicker) findViewById(R.id.time_picker);
        tp.setIs24HourView(true);
        tp.setDescendantFocusability(TimePicker.FOCUS_BLOCK_DESCENDANTS);
        tp.setCurrentHour(mHour);
        tp.setCurrentMinute(mMin);
        tp.setOnTimeChangedListener(new TimePicker.OnTimeChangedListener() {
			
			@Override
			public void onTimeChanged(TimePicker view, int hourOfDay, int minute) {
				// TODO Auto-generated method stub
				mHour=hourOfDay;
				mMin=minute;
			}
		});
        
        
    }

	

	@Override
    public void onStart() {
        super.onStart();
        if(D) Log.e(TAG, "++ ON START ++");

        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        // Otherwise, setup the chat session
        } else {
            if (mChatService == null) setupChat();
        }
        
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        if(D) Log.e(TAG, "+ ON RESUME +");

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
              // Start the Bluetooth chat services
              mChatService.start();
            }
        }
    }

    @SuppressLint("NewApi")
    private void setupChat() {
        Log.d(TAG, "setupChat()");

        // Initialize the array adapter for the conversation thread
        mConversationArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);
        mConversationView = (ListView) findViewById(R.id.in);
        mConversationView.setAdapter(mConversationArrayAdapter);

        // Initialize the compose field with a listener for the return key
        mOutEditText = (EditText) findViewById(R.id.edit_text_out);
        mOutEditText.setOnEditorActionListener(mWriteListener);

        // Initialize the send button with a listener that for click events
        mSendButton = (Button) findViewById(R.id.button_send);
        mSendButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                TextView view = (TextView) findViewById(R.id.edit_text_out);
                String message = view.getText().toString();
                sendMessage(message);
            }
        });
        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothChatService(this, mHandler);

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
        // Stop the Bluetooth chat services
        if (mChatService != null) mChatService.stop();
        if(D) Log.e(TAG, "--- ON DESTROY ---");
     // Unregister broadcast listeners
        this.unregisterReceiver(mReceiver);
        Toast.makeText(this,"退出APP",Toast.LENGTH_SHORT).show();
    }

    
    public void alarm_send(View view){
    	// alarm exist
    	if(mAlarm_Flag){
    		String msg_send="已有";
    		Toast.makeText(this,msg_send,Toast.LENGTH_SHORT).show();
    		
    	}
    	else{ 
    		
    		//get message
    		TextView view1 = (TextView) findViewById(R.id.edit_text_out);
            String message = view1.getText().toString();
            //store message
            SharedPreferences key=getSharedPreferences(SEND_KEY,0);
    		key.edit().putString(SEND_MESSAGE, message).commit();
    		
    		//clear TextView
    		view1.setText("");
    		
    		 Calendar c=Calendar.getInstance();

    	       /*   //initiate the timepicker
    	        timePicker = (TimePicker) findViewById(R.id.time_picker);
    	        timePicker.setIs24HourView(true);
    	        
    	      //Calendar c=Calendar.getInstance();
    	        time[0]=c.get(Calendar.HOUR);
    	        time[1]=c.get(Calendar.MINUTE);
    	        time[3]=c.get(Calendar.SECOND);
    	        timePicker.setCurrentHour(time[0]);
    	        timePicker.setCurrentMinute(time[1]);
    	       
    	        timePicker.setOnTimeChangedListener(new TimePicker.OnTimeChangedListener() {
    				
    				@Override
    				public void onTimeChanged(TimePicker view, int hourOfDay, int minute) {
    					// TODO Auto-generated method stub
    					mHour=hourOfDay;
    					mMin=minute;
    					//time[2]=0;
    				}
    			}); */

    		
    		//set Alarm
    		AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            Intent intent = new Intent(this, AlarmService_Service.class);
            PendingIntent mAlarmSender = PendingIntent.getBroadcast(this, 0, intent, 0); 
          
	            //configure the alarmManager 
             
            long systemTime = System.currentTimeMillis();//java.lang.System.currentTimeMillis()，它返回从 UTC 1970 年 1 月 1 日午夜开始经过的毫秒数。  
            c.setTimeInMillis(System.currentTimeMillis());  
            c.setTimeZone(TimeZone.getTimeZone("GMT+8")); //  这里时区需要设置一下，不然会有8个小时的时间差  
            c.set(Calendar.MINUTE, mMin);  
            c.set(Calendar.HOUR_OF_DAY, mHour);
            c.set(Calendar.SECOND, 0);  
            c.set(Calendar.MILLISECOND, 0);  
            //选择的定时时间  
            long selectTime = c.getTimeInMillis();   //计算出设定的时间  
  
            //  如果当前时间大于设置的时间，那么就从第二天的设定时间开始  
            if(systemTime > selectTime) {  
                c.add(Calendar.DAY_OF_MONTH, 1);  
                selectTime = c.getTimeInMillis();  
            }  
          
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), mAlarmSender);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                am.setExact(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), mAlarmSender);
            } else {
                am.setRepeating(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), INTERVAL, mAlarmSender);
            }
           
            /*
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, c.getTimeInMillis(), mAlarmSender);
            } else {
                am.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, c.getTimeInMillis(), INTERVAL, mAlarmSender);
            } */            
            Log.e(TAG, "+++ ALARM +++");
        
        // Tell the user about what we did.
        mAlarm_Flag=D;
        String alarm_start_msg="定时时间"+mHour+"时"+mMin+"分";
        Toast.makeText(this,alarm_start_msg,Toast.LENGTH_SHORT).show();
    	}
    	}
 
    public void alarm_conceal(View view){
    	AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent intent = new Intent(this, AlarmService_Service.class);
        PendingIntent mAlarmSender = PendingIntent.getBroadcast(this, 0, intent, 0); 
    	
    	//AlarmManager am = (AlarmManager)getSystemService(ALARM_SERVICE);
         am.cancel(mAlarmSender);
         mAlarm_Flag=false;        
         // Tell the user about what we did.
         String end_msg="取消定时";
         Toast.makeText(this, end_msg,Toast.LENGTH_SHORT).show();
         Log.e(TAG, "+++ ALARM CONCEAL +++");
    }
    
    public void alarm_check(View view){
    	Log.e(TAG,"++CHECK ALARM++");
    	if(mAlarm_Flag){
    		String check_msg="闹钟时间"+mHour+":"+mMin;
	    	Toast.makeText(this, check_msg, Toast.LENGTH_SHORT).show();	
    	}
    	else{
    	String check_msg="未定时";
    	Toast.makeText(this, check_msg, Toast.LENGTH_SHORT).show();
    	}
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

    /**
     * Sends a message.
     * @param message  A string of text to send.
     */
    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mChatService.write(send);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
            mOutEditText.setText(mOutStringBuffer);
        }
    }

    // The action listener for the EditText widget, to listen for the return key
    @SuppressLint("NewApi") 
    private TextView.OnEditorActionListener mWriteListener =
        new TextView.OnEditorActionListener() {
        public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
            // If the action is a key-up event on the return key, send the message
            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                String message = view.getText().toString();
                sendMessage(message);
            }
            if(D) Log.i(TAG, "END onEditorAction");
            return true;
        }
    };

    @SuppressLint("NewApi") 
    private final void setStatus(int resId) {
        final ActionBar actionBar = getActionBar();
        actionBar.setSubtitle(resId);
    }

    @SuppressLint("NewApi") 
    private final void setStatus(CharSequence subTitle) {
        final ActionBar actionBar = getActionBar();
        actionBar.setSubtitle(subTitle);
    }
    
    

    // The Handler that gets information back from the BluetoothChatService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_STATE_CHANGE:
                if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                switch (msg.arg1) {
                case BluetoothChatService.STATE_CONNECTED:
                    setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
                    mConversationArrayAdapter.clear();
                    break;
                case BluetoothChatService.STATE_CONNECTING:
                    setStatus(R.string.title_connecting);
                    break;
                case BluetoothChatService.STATE_LISTEN:
                case BluetoothChatService.STATE_NONE:
                    setStatus(R.string.title_not_connected);
                    break;
                }
                break;
            case MESSAGE_WRITE:
                byte[] writeBuf = (byte[]) msg.obj;
                // construct a string from the buffer
                String writeMessage = new String(writeBuf);
                mConversationArrayAdapter.add("Me:  " + writeMessage);
                break;
            case MESSAGE_READ:
                byte[] readBuf = (byte[]) msg.obj;
                // construct a string from the valid bytes in the buffer
                String readMessage = new String(readBuf, 0, msg.arg1);
                mConversationArrayAdapter.add(mConnectedDeviceName+":  " + readMessage);
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

		
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
        case REQUEST_CONNECT_DEVICE_SECURE:
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                connectDevice(data, true);
            }
            break;
        case REQUEST_CONNECT_DEVICE_INSECURE:
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                connectDevice(data, false);
            }
            break;
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled, so set up a chat session
                setupChat();
            } else {
                // User did not enable Bluetooth or an error occurred
                Log.d(TAG, "BT not enabled");
                Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras()
            .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mChatService.connect(device, secure);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.bluetooth_chat, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent serverIntent = null;
        switch (item.getItemId()) {
        case R.id.secure_connect_scan:
            // Launch the DeviceListActivity to see devices and do scan
            serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
            return true;
        case R.id.insecure_connect_scan:
            // Launch the DeviceListActivity to see devices and do scan
            serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);
            return true;
        case R.id.discoverable:
            // Ensure this device is discoverable by others
            //ensureDiscoverable();
        	finishActivity(REQUEST_CONNECT_DEVICE_SECURE);
        	finishActivity(REQUEST_CONNECT_DEVICE_INSECURE);
        	
        	onPause();
        	onStop();
        	System.exit(0);
        	//onDestroy();
            return true;
        }
        return false;
    }

 // The BroadcastReceiver that listens for discovered devices and
    // changes the title when discovery is finished
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(ALARM.equals(action)){
            	Log.d(TAG, "alarm ok");
            	//get the sharedPreferences message
            	SharedPreferences key=getSharedPreferences(SEND_KEY,0);
            	//String mAlarmMessage="666";
            	String mAlarmMessage=key.getString(SEND_MESSAGE,"");
            	sendMessage(mAlarmMessage);
            	//mAlarm_Flag=false; 
            	AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) 
        		{ 
            		am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + INTERVAL, mAlarmSender); 
        		} 
            	else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) 
        		{ 
        		am.setExact(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + INTERVAL, mAlarmSender); 
        		}
            	/*
            	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
	                am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, System.currentTimeMillis(), mAlarmSender);
	            } */
            }
       
        }
        };
    
       

}

