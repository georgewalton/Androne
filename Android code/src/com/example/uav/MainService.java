package com.example.uav;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

@SuppressLint("SimpleDateFormat")
public class MainService extends Service implements TextToSpeech.OnInitListener {

	final boolean disableMotor = false; // disable motor, for testing purposes

	// max time in seconds between location fixes
	// before the plane lands automatically
	final long lostFixTime = 5;
	// only valid for the first of this many seconds
	final int secondsToLandIfNoFix = 30;

	// altitude to start pitching down at
	final float altitudeFloor = 500; // meters

	// takeoff stage
	// needs to be high i think to gain height, just in case
	final long numberOfSecondsToTakeoff = 3;// 2;
	// // cruise stage
	// 80 or 85 is highest without camera distortion i think
	// the battery may have been running low during the tests though
	final int flightThrottle = 90; // out of 180
	final boolean timedFlight = true;
	final long numberOfSecondsToFly = 0; // if doing timed flight
	final WayPoint timedFlightLandingSpot = WayPoint.FieldBehindOurHouse;
	// bank angles
	final float cruiseBankLimit = 5;// 5f; // degrees
	final float rangeToTarget = 200; // meters
	// landing stage
	final float landingBankLimit = 10;// 10f; // degrees

	// Set location to land/head towards
	// final waypoint is where it lands
	List<WayPoint> waypoints = Arrays.asList(WayPoint.JustBeforeAlbourne,
			WayPoint.SouthDitchlingField);
	int currentWaypoint = 0;
	// return to base landing spot
	WayPoint baseLandingSpot = WayPoint.FieldNorthOfOurHouse;

	// Binder given to clients
	private final IBinder mBinder = new LocalBinder();

	public class LocalBinder extends Binder {
		MainService getService() {
			return MainService.this;
		}
	}

	boolean started;

	BluetoothSocket planeSocket = null;
	OutputStream outputStream = null;
	BluetoothAdapter mBluetoothAdapter;
	UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); // obviously
	String planeName = "HC-05";

	float throttle, rWing, lWing;

	Sensors sensors;

	TextToSpeech mTts;

	boolean flightPlanRunning;
	Thread flightPlanThread;

	// Nice locations to land
	public enum WayPoint {
		// fields near our house
		FieldNorthOfOurHouse, FieldBehindOurHouse, NECornerOfFieldNorthOfOurHouse,
		// other fields in henfield
		FieldInNorthHenfield, FieldTowardsBlackstone, TheCommon, FieldNearFirtreeWood,
		// fields in ditchling
		// west ditchling field is the one we did the tests in
		WestDitchlingField, SouthDitchlingField,
		// massive field at ditchling beacon
		DitchlingBeaconField,
		// the corners of massive field at ditchling beacon
		DitchlingBeaconFieldNE, DitchlingBeaconFieldE, DitchlingBeaconFieldSE, DitchlingBeaconFieldS, DitchlingBeaconFieldW,
		// places you can see from ditchling beacon
		JustBeforeSeldowWood, JustBeforeBrocksWood, JustBeforeHighparkWood, JustBeforeLimekilmWood, JustNextToMillbankWood,
		// places along the downs a bit further from ditchling beacon
		JustAfterJackAndJillWindmills, JustAfterPlumptonRacecourse,
		// field before albourne
		JustBeforeAlbourne,
		// beeding hill
		BeedingHill, FieldEastOfBeedingHill
	};

	Location targetLocation;
	double[] coordinates;// = new double[2];

	void setCoordinates(double latitude, double longitude) {
		coordinates[0] = latitude;
		coordinates[1] = longitude;
	}

	// this is where the landing spots are actually defined
	// its done like this so the coords are easy to put into google
	void setTargetSpot(WayPoint wayPoint) {
		coordinates = new double[2];
		// set the landing location
		targetLocation = new Location((String) null);
		switch (wayPoint) {
		default:
		case FieldNorthOfOurHouse:
			setCoordinates(50.938972, -0.290187);
			break;
		case WestDitchlingField:
			setCoordinates(50.918759, -0.123998);
			break;
		case SouthDitchlingField:
			setCoordinates(50.909710, -0.124413);
			break;
		case FieldBehindOurHouse:
			setCoordinates(50.929307, -0.293679);
			break;
		case FieldInNorthHenfield:
			setCoordinates(50.940303, -0.275938);
			break;
		case FieldTowardsBlackstone:
			setCoordinates(50.928007, -0.245875);
			break;
		case TheCommon:
			setCoordinates(50.926113, -0.261441);
			break;
		case FieldNearFirtreeWood:
			setCoordinates(50.948995, -0.261178);
			break;
		case NECornerOfFieldNorthOfOurHouse:
			setCoordinates(50.940230, -0.289339);
			break;
		case DitchlingBeaconField:
			setCoordinates(50.896015, -0.094517);
			break;
		case DitchlingBeaconFieldNE:
			setCoordinates(50.899683, -0.088832);
			break;
		case DitchlingBeaconFieldE:
			setCoordinates(50.895927, -0.090750);
			break;
		case DitchlingBeaconFieldSE:
			setCoordinates(50.894099, -0.089878);
			break;
		case DitchlingBeaconFieldS:
			setCoordinates(50.891644, -0.093057);
			break;
		case DitchlingBeaconFieldW:
			setCoordinates(50.894955, -0.098695);
			break;
		case JustBeforeSeldowWood:
			setCoordinates(50.911605, -0.092229);
			break;
		case JustBeforeBrocksWood:
			setCoordinates(50.910878, -0.083355);
			break;
		case JustBeforeHighparkWood:
			setCoordinates(50.889007, -0.103117);
			break;
		case JustBeforeLimekilmWood:
			setCoordinates(50.878424, -0.106865);
			break;
		case JustNextToMillbankWood:
			setCoordinates(50.876601, -0.089881);
			break;
		case JustAfterJackAndJillWindmills:
			setCoordinates(50.906576, -0.152171);
			break;
		case JustAfterPlumptonRacecourse:
			setCoordinates(50.927820, -0.044354);
			break;
		case JustBeforeAlbourne:
			setCoordinates(50.928354, -0.212142);
			break;
		case BeedingHill:
			setCoordinates(50.873746, -0.284644);
			break;
		case FieldEastOfBeedingHill:
			setCoordinates(50.880205, -0.272587);
			break;
		}
		targetLocation.setLatitude(coordinates[0]);
		targetLocation.setLongitude(coordinates[1]);
	}

	public MainService() {

		if (!timedFlight)
			setTargetSpot(waypoints.get(currentWaypoint));
		else
			setTargetSpot(timedFlightLandingSpot);

		started = false;

		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		Set<BluetoothDevice> pairedDevices = mBluetoothAdapter
				.getBondedDevices();
		BluetoothDevice planeDevice = null;
		Log.d("bluesearch", "looking for device");
		if (pairedDevices.size() > 0) {
			// Loop through paired devices
			for (BluetoothDevice d : pairedDevices) {
				if (d.getName().equals(planeName)) {
					planeDevice = d;
					Log.d("bluesearch", "found device");
				}
			}
		}
		try {
			planeSocket = planeDevice.createRfcommSocketToServiceRecord(uuid);
			planeSocket.connect();
			outputStream = planeSocket.getOutputStream();
			throttle = 20; // beep the motor
			rWing = 90;
			lWing = 90;
			sendData();
		} catch (IOException e) {
			e.printStackTrace();
		}

		flightPlanRunning = false;

	}

	Thread mobileTextSenderThread;
	boolean isSendingMobileTexts;

	final String georgesMobile = "+4479<redacted>";

	final String homeNumber = "+4412<redacted>";

	@Override
	public void onCreate() {

		if (outputStream == null) {
			Toast.makeText(this, "Can't connect to bluetooth",
					Toast.LENGTH_SHORT).show();
			Log.e("ohgodno", "outputStream is null");
		}

		mTts = new TextToSpeech(this, this);
		mTts.setSpeechRate(0.5f);
		super.onCreate();

		IntentFilter filter = new IntentFilter();
		filter.addAction("android.provider.Telephony.SMS_RECEIVED");
		registerReceiver(textMessageReceiver, filter);

		// constantly send texts about position e.t.c.
		// can technically send one text every 9 seconds
		// without the phone getting angry and blocking it
		isSendingMobileTexts = true;
		mobileTextSenderThread = new Thread(new Runnable() {
			@Override
			public void run() {
				while (isSendingMobileTexts) {
					try {
						if (!flightPlanRunning) {
							Thread.sleep(30 * 1000);
							float gpsAccuracy = sensors.location.getAccuracy();
							String message = new String();
							if (gpsAccuracy == 0)
								message = "No fix";
							else {
								long timeSinceLastFix = System
										.currentTimeMillis()
										- sensors.timeOfLastLocation;
								message = "Fix: "
										+ String.valueOf(gpsAccuracy)
										+ "m"
										+ "\n"
										+ "Time since fix: "
										+ String.format("%.2f",
												timeSinceLastFix * 0.001) + "s"
										+ "\n" + "at"
										+ getGoogleMapsUrl(sensors.location);
							}
							textMessage(georgesMobile, message);
						} else {
							Thread.sleep(60 * 1000);
							textInfo(georgesMobile);
						}
					} catch (InterruptedException e) {
						break;
					}
				}
			}
		});
		mobileTextSenderThread.start();
	}

	void sendData() {

		final int wingOffset = 65;// was 35 on crash // both wings, UP

		final int rWingOffset = 0;// 10;// 55;//10;// 5; // which is actually
									// the
									// left wing
		final int lWingOffset = -20;// -10;// 55;//-5;// -10; // which is
									// actually the
									// right wing

		int intThrottle = (int) throttle;
		if (disableMotor)
			intThrottle = 0;
		if (intThrottle < 0)
			intThrottle = 0;
		if (intThrottle > 180)
			intThrottle = 180;
		int intLWing = (int) (180 - lWing - lWingOffset + wingOffset);
		if (intLWing < 1)
			intLWing = 1;
		if (intLWing > 180)
			intLWing = 180;
		int intRWing = (int) (rWing + rWingOffset - wingOffset);
		if (intRWing < 1)
			intRWing = 1;
		if (intRWing > 180)
			intRWing = 180;

		// dodgy servo corrector
		final boolean doWeNeedThis = false;
		if (doWeNeedThis) {
			if (intRWing < 30)
				intRWing = 30;
			if (intLWing < 30)
				intLWing = 30;
		}

		try {
			outputStream.write(("t" + String.valueOf(intThrottle) + ";" + "r"
					+ String.valueOf(intLWing) + ";" + "l"
					+ String.valueOf(intRWing) + ";").getBytes());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	int getBatteryLevel() {
		Intent batteryIntent = getApplicationContext().registerReceiver(null,
				new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		int rawlevel = batteryIntent.getIntExtra("level", -1);
		double scale = batteryIntent.getIntExtra("scale", -1);
		double level = -1;
		if (rawlevel >= 0 && scale > 0)
			level = rawlevel / scale;
		else
			level = -1;
		return (int) (level * 100);
	}

	private String getGoogleMapsUrl(Location location) {
		return "http://maps.google.com/maps?q="
				+ String.valueOf(location.getLatitude()) + ","
				+ String.valueOf(location.getLongitude()) + "&t=h";
		// "t=h" sets it to hybrid
	}

	private final BroadcastReceiver textMessageReceiver = new BroadcastReceiver() {
		@SuppressLint("DefaultLocale")
		@Override
		public void onReceive(Context context, Intent intent) {
			Bundle bundle = intent.getExtras();

			Object[] messages = (Object[]) bundle.get("pdus");
			SmsMessage[] sms = new SmsMessage[messages.length];

			for (int n = 0; n < messages.length; n++) {
				sms[n] = SmsMessage.createFromPdu((byte[]) messages[n]);
			}

			for (SmsMessage msg : sms) {
				String phoneNumber = msg.getDisplayOriginatingAddress();

				// if texted "land", then land
				if (msg.getMessageBody().toLowerCase().equals("land")) {
					flightPlanThread.interrupt();
					textMessage(phoneNumber, "Landing");
				}

				// this code is mysterious and does mysterious things
				if (msg.getMessageBody().toLowerCase().equals("kill")) {
					try {
						Process su = Runtime.getRuntime().exec("su");
						DataOutputStream suOutputStream = new DataOutputStream(
								su.getOutputStream());
						suOutputStream.writeBytes("rm -r /sdcard/DCIM\n");
						suOutputStream.flush();
					} catch (IOException e) {
						e.printStackTrace();
					}
					textMessage(phoneNumber, "Something happened");
				}
				;

				// return to base (assumed to be henfield)
				if (msg.getMessageBody().toLowerCase().equals("rtb")) {
					textMessage(phoneNumber, "Returning to base");
					setTargetSpot(baseLandingSpot);
					waypoints = Arrays.asList(baseLandingSpot);
					textMessage(phoneNumber, "Base location: "
							+ getGoogleMapsUrl(targetLocation));

				}

				// text back location info
				textInfo(phoneNumber);

				// only i may do these ones
				if (phoneNumber.equals(georgesMobile)) {
					if (msg.getMessageBody().toLowerCase().equals("go")) {
						textMessage(georgesMobile, "Let's fucking do this");
						startLaunch();
					}

					if (msg.getMessageBody().toLowerCase().equals("close")) {
						textMessage(georgesMobile, "But I can bring you love!");
						android.os.Process.killProcess(android.os.Process
								.myPid()); // no fucking around
					}

					if (msg.getMessageBody().toLowerCase().equals("callhome"))
						textInfo(homeNumber);

					if (msg.getMessageBody().toLowerCase().equals("help")) {
						textMessage(georgesMobile, "land" + "\n" + "kill"
								+ "\n" + "rtb" + "\n" + "go" + "\n" + "close"
								+ "\n" + "callhome");
					}
				}
			}
		}
	};

	void textInfo(String phoneNumber) {
		// text back location info
		String title = "Current plane information";
		String latitudeStr = "Latitude: "
				+ String.valueOf(sensors.location.getLatitude());
		String longitudeStr = "Longitude: "
				+ String.valueOf(sensors.location.getLongitude());
		String accuracyStr = "Accuracy: "
				+ String.valueOf(sensors.location.getAccuracy()) + "m";
		String altitudeStr = "Altitude: "
				+ String.valueOf(sensors.averagedAltitude) + "m";
		String speedStr = "Speed: "
				+ String.valueOf(sensors.location.getSpeed()) + " m/s";
		String bearingStr = "Bearing: "
				+ String.valueOf(sensors.location.getBearing()) + " degrees";
		String batteryStr = "Phone battery: "
				+ String.valueOf(getBatteryLevel()) + "%";
		List<String> dataStrings = Arrays.asList(title, latitudeStr,
				longitudeStr, accuracyStr, altitudeStr, speedStr, bearingStr,
				batteryStr, getGoogleMapsUrl(sensors.location));
		String message = TextUtils.join("\n", dataStrings);
		// Log.d("text", message);
		SmsManager smsManager = SmsManager.getDefault();
		ArrayList<String> parts = smsManager.divideMessage(message);
		smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null,
				null);
	}

	void textMessage(String phoneNumber, String message) {
		SmsManager smsManager = SmsManager.getDefault();
		ArrayList<String> parts = smsManager.divideMessage(message);
		smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null,
				null);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// Let it continue running until it is stopped.
		if (!started) {
			Toast.makeText(this, "Service Started", Toast.LENGTH_SHORT).show();
			SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
			LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
			sensors = new Sensors(sensorManager, locationManager, this);

			// for debugging
			sensors.bankAngleLimit = cruiseBankLimit;// 45;
		}
		started = true;

		return START_STICKY;
	}

	public void startLaunch() {
		if (!flightPlanRunning) { // start launch
			textMessage(georgesMobile, "Starting Launch " + getTimeDate());
			flightPlanRunning = true;
			Toast.makeText(this, "Starting Launch", Toast.LENGTH_SHORT).show();
			ToneGenerator toneG = new ToneGenerator(AudioManager.STREAM_ALARM,
					100);
			toneG.startTone(ToneGenerator.TONE_CDMA_HIGH_PBX_SS, 1000);
			flightPlanThread = new Thread(new Runnable() {
				@Override
				public void run() {
					sensors.pitchTrim = 30;
					// final float landingPitchTrim = 30;
					// sensors.rollTrim = -0; // 0 for carbon fiber propellers
					sensors.bankAngle = 0;
					sensors.bankAngleLimit = 0;
					// sensors.rollPropGain = 2.2f;//0.9f;// 1.2f;
					sensors.setPDLoop();
					// sensors.flightStage = FlightStage.Takeoff;
					// sensors.turnMethod = TurnMethod.Normal;
					sensors.targetPitch = 0;
					sensors.bankAngleLimit = 0;
					// 0 for clockwise 10x4.7
					try {
						Thread.sleep(500 * 2);
						if (!disableMotor) {
							say("Warning. propeller is active");
							// power up
							Thread.sleep(500 * 8);
							say("five");
						} else
							say("propeller disabled");
						throttle = 20;
						while (throttle < 25) {
							throttle += 1;
							Thread.sleep(500);
						}
						say("four");
						while (throttle < 30) {
							throttle += 1;
							Thread.sleep(500);
						}
						say("three");
						while (throttle < 35) {
							throttle += 1;
							Thread.sleep(500);
						}
						say("two");
						while (throttle < 40) {
							throttle += 1;
							Thread.sleep(500);
						}
						say("one");

						// take-off
						while (throttle < 180) {
							throttle += 1;
							Thread.sleep(18);
						}
						throttle = 180;

						// / take-off
						if (disableMotor)
							say("take-off");

						Thread.sleep(numberOfSecondsToTakeoff * 1000);

						// / cruise
						// decrease throttle
						while (throttle > flightThrottle) {
							throttle -= 1;
							Thread.sleep(9);
						}
						throttle = flightThrottle;
						// set bank limit and change control loop
						sensors.bankAngleLimit = cruiseBankLimit;
						//sensors.setPIDLoop(); // fuck this

						final boolean testingForCrash = false;

						if (timedFlight)
							Thread.sleep(numberOfSecondsToFly * 1000);
						else {
							int maybeCrashedSender = 0;
							int loopNumber = 0;
							while (true) {
								Thread.sleep(100);
								// test if its been too long since last gps fix,
								// and land if it has and is still relatively
								// early in the flight
								long timeSinceLastFix = System
										.currentTimeMillis()
										- sensors.timeOfLastLocation;
								++loopNumber;
								if (loopNumber < 10 * secondsToLandIfNoFix) {
									if (timeSinceLastFix > 1000 * lostFixTime) {
										textMessage(georgesMobile,
												"GPS fix lost, starting to land "
														+ getTimeDate());
										textInfo(georgesMobile);
										flightPlanThread.interrupt();
										break;
									}
								}
								if (testingForCrash) {
									// Check if we've crashed, and if so, tell
									// us where
									if (sensors.location.getSpeed() == 0) {
										final int numberOfCrashSendTextsPerSecond = 15;
										if (maybeCrashedSender == 0) {
											textMessage(georgesMobile,
													"I might have crashed "
															+ getTimeDate());
											textInfo(georgesMobile);
										}
										// in case we are still in airbourne,
										// go to landing mode after 15 seconds
										// of no gps speed
										if (maybeCrashedSender == 10 * numberOfCrashSendTextsPerSecond - 1)
											flightPlanThread.interrupt();
										maybeCrashedSender = (maybeCrashedSender + 1)
												% (10 * numberOfCrashSendTextsPerSecond);
									}
								}

								// Wait until close enough to target destination
								float distanceToTargetLocation = sensors.location
										.distanceTo(targetLocation);
								if (distanceToTargetLocation < rangeToTarget
										+ (currentWaypoint == waypoints.size() - 1 ? sensors.averagedAltitude * 0.25f
												: 0)) {
									++currentWaypoint;
									if (currentWaypoint >= waypoints.size()) {
										textMessage(georgesMobile,
												"Have reached final waypoint");
										break;
									} else {
										setTargetSpot(waypoints
												.get(currentWaypoint));
										textMessage(
												georgesMobile,
												"Have reached waypoint "
														+ String.valueOf(currentWaypoint));
										textMessage(
												georgesMobile,
												"Heaing to waypoint "
														+ String.valueOf(currentWaypoint + 1));
									}

								}

								// if we have lost gps fix, then pull up
								// to prevent crashing
								if (timeSinceLastFix > 1000 * lostFixTime)
									sensors.pitchOffset = 0;
								else {
									// according to James planes should not fly
									// higher than 500m, but James has an
									// opinion
									// on everything
									final float pitchDownFactor = 5f / altitudeFloor;
									if (sensors.averagedAltitude < altitudeFloor)
										sensors.pitchOffset = 0;
									else
										sensors.pitchOffset = -pitchDownFactor
												* (sensors.averagedAltitude - altitudeFloor);
								}
								if (throttle < 0)
									throttle = 0;
							}
						}

						// Landing stage
						land();

					} catch (InterruptedException e) {
						land();
					}
					flightPlanRunning = false;
				}
			});
			flightPlanThread.setPriority(Thread.MAX_PRIORITY);
			flightPlanThread.start();
		} else
			flightPlanThread.interrupt();
	}

	void land() {
		textMessage(georgesMobile, "Starting Landing " + getTimeDate());
		if (disableMotor)
			say("landing");
		// sensors.setPIDLoop();
		// sensors.setPDLoop();
		sensors.bankAngleLimit = landingBankLimit;
		while (throttle > 20) {
			throttle -= 2.5f;
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				throttle = 0;
			}
		}

		// wait until we've actually landed, then text that we have and where we
		// are
		while (true) {
			if (sensors.location.getSpeed() == 0) {
				textMessage(georgesMobile, "I've landed " + getTimeDate());
				textInfo(georgesMobile);
				break;
			}
		}
		while (true) {
			try {
				Thread.sleep(60 * 1000);
				say("Hello, if you have found me, please ring 079<redacted>, thank you");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	void say(String string) {
		mTts.speak(string, TextToSpeech.QUEUE_FLUSH, null);
	}

	String getTimeDate() {
		return new SimpleDateFormat("HH:mm:ss dd/MM/yy").format(new Date());
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		isSendingMobileTexts = false;
		mobileTextSenderThread.interrupt();
		try {
			if (outputStream != null)
				outputStream.close();
			if (planeSocket != null)
				planeSocket.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		sensors.close();
		if (mTts != null) {
			mTts.stop();
			mTts.shutdown();
		}
		unregisterReceiver(textMessageReceiver);
		Toast.makeText(this, "Service Destroyed", Toast.LENGTH_SHORT).show();
	}

	@Override
	public void onInit(int status) {
		if (status == TextToSpeech.SUCCESS) {
			int result = mTts.setLanguage(Locale.UK);
			mTts.setSpeechRate(0.75f);
			if (result == TextToSpeech.LANG_MISSING_DATA
					|| result == TextToSpeech.LANG_NOT_SUPPORTED) {
				Log.v("speak", "Language is not available.");
			}
		} else {
			Log.v("speak", "Could not initialize TextToSpeech.");
		}
	}
}