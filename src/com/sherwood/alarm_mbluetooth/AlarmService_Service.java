package com.sherwood.alarm_mbluetooth;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import android.util.Log;
import android.widget.Toast;

public class AlarmService_Service extends BroadcastReceiver {

	//debug
	private static final String TAG = "ALARMSERVICESERVICE";
	private static final String ALARM="com.example.android.BluetoothChat.AlarmService_Service";
	@Override
	public void onReceive(Context context, Intent intent) {
		// TODO Auto-generated method stub
		Log.d(TAG,"ALARM BEGIN");
		Intent Alarm_Intent=new Intent(ALARM);
		context.sendBroadcast(Alarm_Intent);
		Toast.makeText(context, "OK",Toast.LENGTH_SHORT).show();		
	}
}

