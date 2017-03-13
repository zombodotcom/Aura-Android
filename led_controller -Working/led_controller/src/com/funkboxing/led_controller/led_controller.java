/*
* License:  This  program  is  free  software; you can redistribute it and/or
* modify it under the terms of the GNU General Public License as published by
* the  Free Software Foundation; either version 3 of the License, or (at your
* option)  any later version. This program is distributed in the hope that it
* will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
* of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
* Public License for more details.
* 
* Code home:
* 	www.funkboxing.com
* 	http://funkboxing.com/wordpress/?p=2154
* 
* This project was a very helpful reference: 
* 	http://uzzors2k.4hv.org/index.php?page=blucar
*/

package com.funkboxing.led_controller;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

import android.media.MediaRecorder;
import com.funkboxing.led_controller.R;

//import com.teldredge.btremote.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.Toast;
import android.graphics.Color;

public class led_controller extends Activity {

	//---REQUEST CODES
	private static final int REQUEST_CONNECT_DEVICE = 1;
	private static final int REQUEST_ENABLE_BT = 2;

	//---PROGRAM VARS
	private byte AttinyOut;

	private boolean connectStat = false;

	private Button mButton1;
	private Button mButton2;



	private TextView xDebugText1;


	protected static final int MOVE_TIME = 80;
	private long lastWrite = 0;
	private AlertDialog aboutAlert;
	private View aboutView;
	private View controlView;
	OnClickListener myClickListener;
	ProgressDialog myProgressDialog;
	private Toast failToast;
	private Handler mHandler;



	//---BLUETOOTH VARS AND STUFF
	private BluetoothAdapter mBluetoothAdapter = null;
	private BluetoothSocket btSocket = null;
	private OutputStream outStream = null;
	private ConnectThread mConnectThread = null;
	private String deviceAddress = null;
	// Well known SPP UUID (will *probably* map to RFCOMM channel 1 (default) if not in use);
	private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

	//---SERIAL ARDUINO COMMANDS
	private String serialCommandString = null;
	private String strOut = null;
	private byte[] serialCommandBytes;




	private Spinner mSpinner1;

	//private long speedVar;
	//private int delayVar;
	long timestampA = 00L;
	long timestampB = System.currentTimeMillis();
	long timepassed = timestampB - timestampA;

	// Toast variable in order to prevent overloading
	private Toast mCurrentToast = null;

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		//---TEST SPEED OF DEVICE, DETERMINE DELAY VAR (KIND OF HACKY)
		//long beginTime = System.currentTimeMillis();
		//for(int i = 0; i < 5000000; i = i+1) {
		//long throwaway = 22/7;
		//}
		//long endTime = System.currentTimeMillis();
		//speedVar = endTime - beginTime;
		//delayVar = (int) (10/speedVar)*10;
		//String diffString = Long.toString(diff);
		//Toast.makeText(getApplicationContext(), diffString, Toast.LENGTH_LONG).show();

		//---SETUP MAIN WINDOW VIEW
		LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		aboutView = inflater.inflate(R.layout.aboutview, null);
		controlView = inflater.inflate(R.layout.main, null);
		controlView.setKeepScreenOn(true);
		setContentView(controlView);

		//---SETUP UI WIDGETS
		mButton1 = (Button) findViewById(R.id.button1);
		mButton2 = (Button) findViewById(R.id.button2);


		xDebugText1 = (TextView) findViewById(R.id.debugText1);


		mSpinner1 = (Spinner) findViewById(R.id.spinner1);


		//---CONNECT TO BLUETOOTH MODULE
		mButton1.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (connectStat) {
					disconnect();
				} else {
					connect();
				}
			}
		});

		//---SELECT LEF FX MODE BASED ON SPINNER SELECTION
		mButton2.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				xDebugText1.setText("mode" + mSpinner1.getSelectedItemPosition());
				serialCommandString = "m" + mSpinner1.getSelectedItemPosition();
				serialCommandBytes = serialCommandString.getBytes();
				write2(serialCommandBytes);
			}
		});


		//---HELP AND INFO DIALOG CLICK HANDLER
		myClickListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				switch (which) {
					case DialogInterface.BUTTON_POSITIVE:
						dialog.dismiss();
						break;
					case DialogInterface.BUTTON_NEUTRAL:
						// Display website
						Intent browserIntent = new Intent("android.intent.action.VIEW", Uri.parse(getResources().getString(R.string.website_url)));
						startActivity(browserIntent);
						break;
					default:
						dialog.dismiss();
				}
			}
		};


		myProgressDialog = new ProgressDialog(this);
		failToast = Toast.makeText(this, R.string.failedToConnect, Toast.LENGTH_SHORT);

		mHandler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				if (myProgressDialog.isShowing()) {
					myProgressDialog.dismiss();
				}

				//---CHECK IF BLUETOOTH CONNECTED
				if (msg.what == 1) {
					//---SET BUTTON TO SHOW CONNECTION STATUS
					connectStat = true;
					mButton1.setText(R.string.connected);

					//---RESET ALL
					AttinyOut = 0;
					write(AttinyOut);
				} else {
					//---CONNECTION FAILED
					failToast.show();
				}
			}
		};

		//---ABOUT DIALOG
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setView(aboutView).setCancelable(true).setTitle(getResources().getString(R.string.app_name) + " " + getResources().getString(R.string.appVersion)).setIcon(R.drawable.ledicon_4848_mdpi).setPositiveButton(getResources().getString(R.string.okButton), myClickListener).setNeutralButton(getResources().getString(R.string.websiteButton), myClickListener);
		aboutAlert = builder.create();

		//---CHECK IF BT ADAPTER EXISTS
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBluetoothAdapter == null) {
			Toast.makeText(this, R.string.no_bt_device, Toast.LENGTH_LONG).show();
			finish();
			return;
		}

		//---IF BT NOT ON, ENABLE
		if (!mBluetoothAdapter.isEnabled()) {
			Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
		}


	}

	/**
	 * Thread used to connect to a specified Bluetooth Device
	 */
	public class ConnectThread extends Thread {
		private String address;
		private boolean connectionStatus;

		ConnectThread(String MACaddress) {
			address = MACaddress;
			connectionStatus = true;
		}

		public void run() {
			try {
				BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
				try {
					btSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
				} catch (IOException e) {
					connectionStatus = false;
				}
			} catch (IllegalArgumentException e) {
				connectionStatus = false;
			}
			mBluetoothAdapter.cancelDiscovery();
			try {
				btSocket.connect();
			} catch (IOException e1) {
				try {
					btSocket.close();
				} catch (IOException e2) {
				}
			}
			try {
				outStream = btSocket.getOutputStream();
			} catch (IOException e2) {
				connectionStatus = false;
			}
			if (connectionStatus) {
				mHandler.sendEmptyMessage(1);
			} else {
				mHandler.sendEmptyMessage(0);
			}
		}
	}

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
			case REQUEST_CONNECT_DEVICE:
				// When DeviceListActivity returns with a device to connect
				if (resultCode == Activity.RESULT_OK) {
					// Show please wait dialog
					myProgressDialog = ProgressDialog.show(this, getResources().getString(R.string.pleaseWait), getResources().getString(R.string.makingConnectionString), true);

					// Get the device MAC address
					deviceAddress = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
					// Connect to device with specified MAC address
					mConnectThread = new ConnectThread(deviceAddress);
					mConnectThread.start();

				} else {
					// Failure retrieving MAC address
					Toast.makeText(this, R.string.macFailed, Toast.LENGTH_SHORT).show();
				}
				break;
			case REQUEST_ENABLE_BT:
				// When the request to enable Bluetooth returns
				if (resultCode == Activity.RESULT_OK) {
					// Bluetooth is now enabled
				} else {
					// User did not enable Bluetooth or an error occured
					Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
					finish();
				}
		}
	}

	public void write(byte data) {
		if (outStream != null) {
			try {
				outStream.write(data);
			} catch (IOException e) {
			}
		}
	}

	public void write2(byte[] data) {
		if (outStream != null) {
			try {
				outStream.write(data);
			} catch (IOException e) {
			}
		}
	}

	public void emptyOutStream() {
		if (outStream != null) {
			try {
				outStream.flush();
			} catch (IOException e) {
			}
		}
	}

	public void connect() {
		// Launch the DeviceListActivity to see devices and do scan
		Intent serverIntent = new Intent(this, DeviceListActivity.class);
		startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
	}

	public void disconnect() {
		if (outStream != null) {
			try {
				outStream.close();
				connectStat = false;
				mButton1.setText(R.string.disconnected);
			} catch (IOException e) {
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
			case R.id.about:
				// Show info about the author (that's me!)
				aboutAlert.show();
				return true;
		}
		return false;
	}


}