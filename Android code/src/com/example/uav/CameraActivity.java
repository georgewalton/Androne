package com.example.uav;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

public class CameraActivity extends Activity implements OnClickListener {

	private GLSurfaceView mGLView;

	Sensors sensors;
	WakeLock mWL;

	FrameLayout frameLayout;
  	TextView textView;

	Timer dataTimer;
	TimerTask dataTimerTask;

	static String dataText = "Service not running";

	Timer endedRecordingTimer;
	TimerTask endedRecordingTask;

	Process su;
	DataOutputStream outputStream;

	final static int secondsToRecord = 5*60;
	
	boolean recording;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// full screen & full brightness
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
				WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		mWL = ((PowerManager) getSystemService(Context.POWER_SERVICE))
				.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "WakeLock");
		mWL.acquire();

		// Create a GLSurfaceView instance and set it
		// as the ContentView for this Activity

		frameLayout = new FrameLayout(this);
		frameLayout.setOnClickListener(this);
		mGLView = new CameraSurfaceView(this);
		textView = new TextView(this);
		textView.setBackgroundColor(Color.argb(128, 0, 0, 0));
		textView.setTextColor(Color.WHITE);
		textView.setLayoutParams(new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT,
				ViewGroup.LayoutParams.WRAP_CONTENT));
		frameLayout.addView(mGLView);
		frameLayout.addView(textView);
		setContentView(frameLayout);
		mGLView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE
				| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
				| View.SYSTEM_UI_FLAG_FULLSCREEN);

		dataTimerTask = new TimerTask() {
			public void run() {
				runOnUiThread(new Runnable() {
					public void run() {
						textView.setText(dataText);
					}
				});
			}
		};
		dataTimer = new Timer(true);
		dataTimer.scheduleAtFixedRate(dataTimerTask, 0, 33);

		endedRecordingTimer = new Timer(true);
		endedRecordingTask = new TimerTask() {
			public void run() {
				runOnUiThread(new Runnable() {
					public void run() {
						Toast.makeText(getApplicationContext(),
								"Recording finished", Toast.LENGTH_LONG).show();
						recording = false;
					}
				});
			}
		};
		recording = false;
	}

	@Override
	protected void onPause() {
		super.onPause();
		// The following call pauses the rendering thread.
		// If your OpenGL application is memory intensive,
		// you should consider de-allocating objects that
		// consume significant memory here.
		mWL.release();
		mGLView.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		// The following call resumes a paused rendering thread.
		// If you de-allocated graphic objects for onPause()
		// this is a good place to re-allocate them.
		mWL.acquire();
		mGLView.onResume();
	}

	@Override
	public void onClick(View arg0) {
		if (recording)
			return;
		recording = true;
		Toast.makeText(getApplicationContext(), "Started recording",
				Toast.LENGTH_LONG).show();
		endedRecordingTimer
				.schedule(endedRecordingTask, secondsToRecord * 1000);
		try {
			su = Runtime.getRuntime().exec("su");
			outputStream = new DataOutputStream(su.getOutputStream());
			outputStream.writeBytes("screenrecord --time-limit "
					+ String.valueOf(secondsToRecord)
					+ " /sdcard/Flight_Videos/flight"
					+ String.valueOf(System.currentTimeMillis()) + ".mp4\n");
			outputStream.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}