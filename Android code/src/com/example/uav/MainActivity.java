package com.example.uav;

import com.example.uav.MainService.LocalBinder;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

public class MainActivity extends Activity {

	SurfaceView mPreview;

	MainService mService;
	boolean mBound = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Remove title bar
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);

		// Remove notification bar
		this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);

		setContentView(R.layout.activity_main);
		WindowManager.LayoutParams lp = getWindow().getAttributes();
		lp.buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF;
		lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF;
		this.getWindow().setAttributes(lp);

	}

	// Method to start the service
	public void startService(View view) {
		// Bind to LocalService
		Intent intent = new Intent(this, MainService.class);
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
		startService(new Intent(getBaseContext(), MainService.class));
	}

	// Method to stop the service
	public void stopService(View view) {
		if (mBound) {
			mService.isSendingMobileTexts = false; // i give up
			unbindService(mConnection);
			mBound = false;
		}
		stopService(new Intent(getBaseContext(), MainService.class));
	}

	public void startLaunch(View view) {
		if (mBound)
			mService.startLaunch();
		else
			Toast.makeText(this, "Need to start service first",
					Toast.LENGTH_SHORT).show();
		
		// to make things easier
		//launchCamera(view);
	}

	/** Defines callbacks for service binding, passed to bindService() */
	private ServiceConnection mConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			// We've bound to LocalService, cast the IBinder and get
			// LocalService instance
			LocalBinder binder = (LocalBinder) service;
			mService = binder.getService();
			mBound = true;
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			mBound = false;
		}
	};

	@Override
	protected void onDestroy() {
		super.onDestroy();
		// Unbind from the service
		if (mBound) {
			unbindService(mConnection);
			mBound = false;
		}
		// no fucking around
		android.os.Process.killProcess(android.os.Process.myPid());
	}

	public void launchCamera(View view) {
		final boolean fuckThisItsSoAnnoying = true;
		if (fuckThisItsSoAnnoying)
			return;
		Intent intent = getPackageManager().getLaunchIntentForPackage(
				"rubberbigpepper.lgCamera");
		// "net.sourceforge.opencamera");
		startActivity(intent);
	}

	public void launchDebugCamera(View view) {
		final boolean fuckThisItsSoAnnoying = true;
		if (fuckThisItsSoAnnoying)
			return;
		Intent intent = new Intent(this, CameraActivity.class);
		startActivity(intent);
	}
}
