package com.example.uav;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;

public class Sensors implements SensorEventListener, LocationListener {

	SensorManager mSensorManager;
	LocationManager locationManager;
	MainService service;

	Location location; // location
	
	public long timeOfLastLocation; // time that last gps fix happened

	float rollPropGain;// = 2.2f;// 1.65f;
	float rollDerivGain;// = 0.275f;
	float rollIntegralGain;// = 0;// 3.3f;
	float pitchPropGain;// = 1.4f;// 1.4f;// 2.2f;// 0.5f;
	float pitchDerivGain;// = 0.25f;// 0.1f;
	float pitchIntegralGain;// = 0;// 1.5f;

	// / in earth's coord frame;
	float[] mag = new float[3];
	float[] acc = new float[3];
	float[] gyr = new float[3];

	// corrected for true north;
	float[] frVect = new float[3]; // forwards vector
	float[] upVect = new float[3]; // upwards vector
	float[] rtVect = new float[3]; // right vector
	// uncorrected for true north
	float[] frVectM = new float[3];
	float[] upVectM = new float[3];
	float[] rtVectM = new float[3];

	// / in phone's coord frame;
	// corrected for true north;
	float[] east = new float[3];
	float[] north = new float[3];
	float[] up = new float[3];
	// uncorrected for true north;
	float[] eastM = new float[3];
	float[] northM = new float[3];
	float[] upM = new float[3];

	float angle;

	Thread fuseSensorsThread;
	boolean fuseSensorsThreadRunning;

	float damp = 0.975f;
	float invdamp = 1 - damp;
	float gyroFactor = 0.001f;
	int dt = 25; // ms

	float declination = -1.5f * 3.14159f / 180f;

	float pitchPrev, rollPrev;

	float rollIntegral, pitchIntegral;

	float pitchTrim, rollTrim;

	float bankAngle;
	
	float pitchOffset;
	
	float bankAngleLimit;

	float averagedAltitude; // is also compensated for reference ellipsoid 
						
	float targetPitch;
	
	// stuff to write to sd card
	File outputFile;
	FileOutputStream fOut;
	OutputStreamWriter myOutWriter;
	boolean recording;	
	
	// camera flashing lights
	Camera camera;
	Parameters cameraParameters;
	Thread cameraFlashThread;
	
	boolean flashCamera = false;
	
	float pressure;

	public Sensors(SensorManager mSensorManager,
			LocationManager locationManager, MainService service) {
		
		this.mSensorManager = mSensorManager;
		this.service = service;
		mSensorManager.registerListener(this,
				mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				SensorManager.SENSOR_DELAY_FASTEST);
		mSensorManager.registerListener(this,
				mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
				SensorManager.SENSOR_DELAY_FASTEST);
		mSensorManager.registerListener(this,
				mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
				SensorManager.SENSOR_DELAY_FASTEST);

		this.locationManager = locationManager;
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0,
				0, this);
		location = new Location((String) null);

		fuseSensorsThread = new Thread(new Runnable() {
			long lastTime;

			@Override
			public void run() {
				fuseSensorsThreadRunning = true;
				lastTime = System.currentTimeMillis();
				while (fuseSensorsThreadRunning) {
					long now = System.currentTimeMillis();
					while (now - lastTime > dt) {
						lastTime += dt;
						fuseSensors();
						try {
							Thread.sleep(1);
						} catch (InterruptedException e) {
						}
					}
				}
			}
		});
		fuseSensorsThread.setPriority(Thread.MAX_PRIORITY); // flying is
															// important
		fuseSensorsThread.start();

		pitchPrev = 0;
		rollPrev = 0;

		rollIntegral = 0;
		pitchIntegral = 0;

		pitchTrim = 30; // 60 for clockwise 10x4.7 prop // -5 works for landing
		rollTrim = 0; // for without power

		bankAngle = 0;

		setPDLoop();
		
		bankAngleLimit = 0;

		averagedAltitude = 0;

		//flightStage = FlightStage.Takeoff;

		//turnMethod = TurnMethod.Normal;
		
		//targetPitch = -20;//0; unused
		
		pitchOffset = 0;
		
		recording = false;
		
		if (flashCamera) {
		
		camera = Camera.open();
		cameraParameters = camera.getParameters();
		
		cameraFlashThread = new Thread() {
			public void run() {
				while (true) {
					try {
						for (int i=0; i<2; ++i) {
							cameraParameters.setFlashMode(Parameters.FLASH_MODE_TORCH);
							camera.setParameters(cameraParameters);
							Thread.sleep(50);
							cameraParameters.setFlashMode(Parameters.FLASH_MODE_OFF);
							camera.setParameters(cameraParameters);
							Thread.sleep(100);
						}
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						camera.release();
						break;
					}
						}
			}
		};
		cameraFlashThread.start();
		}
		
		timeOfLastLocation = 0;
	}
	
	// open file for recording data
	public void openFile() {
		try {
			File outputDirectory = new File(Environment
					.getExternalStorageDirectory().getPath() + "/Flight_Data/");
			outputDirectory.mkdirs();
			SimpleDateFormat dateFormat = new SimpleDateFormat(
					"yyyy-MM-dd HH-mm-ss", Locale.ENGLISH);
			String currentDateTimeString = dateFormat.format(new Date())
					.replace(" ", "_");
			outputFile = new File(outputDirectory, "Data_"
					+ currentDateTimeString + ".txt");
			//Log.d("file", "telemetryData_" + currentDateTimeString + ".txt");
			outputFile.createNewFile();
			fOut = new FileOutputStream(outputFile);
			myOutWriter = new OutputStreamWriter(fOut);
			recording = true;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void closeFile() {
		try {
			recording = false;
			myOutWriter.close();
			fOut.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	void setPDLoop() {

		
		pitchPropGain = 0.66f*1.4f;
		pitchDerivGain = 1.5f*0.25f;
		pitchIntegralGain = 0;//1.5f;//0;

		rollPropGain = 0.66f*2.2f;
		rollDerivGain = 1.5f*0.275f;
		rollIntegralGain = 0;

	}

	void setPIDLoop() {
		
		rollPropGain = 0.5f*0.22f*1.65f;
		rollDerivGain = 0.75f*1.5f*0.275f;
		rollIntegralGain = 3.3f;
		
		//pitchPropGain = /*0.66f**/1.4f;
		//pitchDerivGain = /*1.5f**/0.25f;
		pitchPropGain = 0.66f*1.4f;
		pitchDerivGain = 1.5f*0.25f;
		pitchIntegralGain = 0;

		rollIntegral = 0;
		pitchIntegral = 0;
	}

	@Override
	public void onAccuracyChanged(Sensor arg0, int arg1) {

	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		switch (event.sensor.getType()) {
		case Sensor.TYPE_MAGNETIC_FIELD:
			mag[0] = event.values[0];
			mag[1] = event.values[1];
			mag[2] = event.values[2];
			break;
		case Sensor.TYPE_ACCELEROMETER:
			acc[0] = event.values[0];
			acc[1] = event.values[1];
			acc[2] = event.values[2];
			break;
		case Sensor.TYPE_GYROSCOPE:
			gyr[0] = event.values[0];
			gyr[1] = event.values[1];
			gyr[2] = event.values[2];
			break;
		case Sensor.TYPE_PRESSURE:
			pressure = event.values[0];
			break;
		}
		//Log.d("pressure", String.valueOf(pressure));
	}

	// first order dodgy tan correction
	// this helps for fast-but-not-too-fast angle changes
	float tcr(float angle) {
		return angle;// + 0.25f * angle * angle * angle;
	}

	public void fuseSensors() {
		
		//acc[0] = plane forwards
		//acc[1] = plane left
		//acc[2] = plane up (m/s)
		
		// record data
		if (recording)
		try {
			myOutWriter.append(String.valueOf(acc[0]) + "\t"
					+ String.valueOf(acc[1]) + "\t"
					+ String.valueOf(acc[2]) + "\t"
					+ String.valueOf(location.getSpeed()) + "\t"
					+ String.valueOf(location.getBearing()) + "\n");
		} catch (IOException e) {
			e.printStackTrace();
		}

		// Acc + Mag
		float[] upR = Utils.normalize(acc);
		float[] magR = Utils.normalize(mag);

		// These are in the phone's coordinate frame, based on magnetic north
		eastM = damp(eastM, Utils.cross(magR, upR));
		northM = damp(northM, Utils.cross(upR, eastM));
		upM = damp(upM, upR);

		// Convert to earth frame
		float[][] invM = Utils.invertCoordinateFrame(eastM, northM, upM);

		rtVectM = invM[0];
		frVectM = invM[1];
		upVectM = invM[2];

		// Gyro
		float factor = dt * gyroFactor;
		float[] lfVect2 = Utils.add(rtVectM,
				Utils.mult(frVectM, tcr(gyr[2] * factor)));
		lfVect2 = Utils.add(lfVect2, Utils.mult(upVect, -tcr(gyr[1] * factor)));
		float[] frVect2 = Utils.add(frVectM,
				Utils.mult(rtVectM, -tcr(gyr[2] * factor)));
		frVect2 = Utils.add(frVect2, Utils.mult(upVect, tcr(gyr[0] * factor)));
		float[] upVect2 = Utils.add(upVectM,
				Utils.mult(frVectM, -tcr(gyr[0] * factor)));
		upVect2 = Utils.add(upVect2, Utils.mult(rtVect, tcr(gyr[1] * factor)));

		frVectM = Utils.normalize(frVect2);
		rtVectM = Utils.normalize(lfVect2);
		upVectM = Utils.normalize(upVect2);

		// Magnetic north -> true north
		frVect = Utils.rotateZ(frVectM, declination);
		rtVect = Utils.rotateZ(rtVectM, declination);
		upVect = Utils.rotateZ(upVectM, declination);

		// convert back to phone frame
		float[][] invM2 = Utils
				.invertCoordinateFrame(rtVectM, frVectM, upVectM);

		eastM = invM2[0];
		northM = invM2[1];
		upM = invM2[2];

		float[][] invM3 = Utils.invertCoordinateFrame(rtVect, frVect, upVect);

		east = invM3[0];
		north = invM3[1];
		up = invM3[2];

		// rolling average altitude
		averagedAltitude = (float) (0.95f * averagedAltitude + 0.05f * (location
				.getAltitude() - 50));
		//averagedAltitude = (float) (location.getAltitude() - 50f);

		// float bankAngleLimit = 10;
		float heading = location.bearingTo(service.targetLocation);
		float angleToHeading = getAngleToHeading(heading);
		//bankAngle = angleToHeading*0.01f*bankAngleLimit;
		
		// experimental banking strategy
		bankAngle = angleToHeading * 0.01f * bankAngleLimit;
	
		if (bankAngle > bankAngleLimit)
			bankAngle = bankAngleLimit;
		else if (bankAngle < -bankAngleLimit)
			bankAngle = -bankAngleLimit;
		
		//Log.d("bankAngle", String.valueOf(bankAngle));
		// Log.d("uav roll", String.valueOf(bankAngle));
		float targetAltitude = 50; // flies above this altitude

		float rollPropGain2 = rollPropGain;// * pitchTrim/60f; // try make less
											// when pitching down

		// roll, pitch
		//float pitch = -(float) (Math.asin(rtVect[2]) * 57.2);// + bankAngle*0.5f;
		float roll = -(float) (Math.asin(frVect[2]) * 57.2) + bankAngle;
		float pitch = (float) (Math.asin(rtVect[2]) * 57.2);// + pitchOffset;
		float pitchDeriv = (pitch - pitchPrev) / (dt * 0.001f);
		float rollDeriv = (roll - rollPrev) / (dt * 0.001f);
		pitchPrev = pitch;
		rollPrev = roll;
		
		//Log.d("uav", "pitch: "+String.valueOf(pitch));
		
		// using wind tunnel thing;
		// / ROLL
		// roll ultimate gain is 2.75
		// roll period is 1s exactly
		// therefore for a pd loop;
		// p gain is 2.2
		// d gain is 0.275
		// for a pid loop;
		// p gain is 1.65
		// d gain is 0.275
		// i gain is 3.3
		// / PITCH
		// pitch ultimate gain is 1.75 for underneath-landingish config
		// pitch period is 1.4s
		// therefore for a pd loop;
		// p gain is 1.4 (2.2 also works)
		// d gain is 0.25
		// for a pid loop;
		// p gain is 1.05
		// d gain is 0.25
		// i gain is 1.5

		rollIntegral += roll * dt * 0.001f;
		pitchIntegral += (pitch-targetPitch) * dt * 0.001f;
		
		// damping integrals to stop integral windup
		rollIntegral *= 0.99f;
		pitchIntegral *= 0.99f;
		
		//Log.d("pitchIntegral", String.valueOf(pitchIntegral));
		
		// Log.d("pitch", String.valueOf(pitch));
		service.rWing = 90 - rollDerivGain * rollDeriv - rollPropGain2 * roll
				- rollIntegralGain * rollIntegral + pitchPropGain * pitch
				+ pitchDerivGain * pitchDeriv + pitchIntegralGain
				* pitchIntegral - rollTrim + pitchTrim;
		service.lWing = 90 + rollDerivGain * rollDeriv + rollPropGain2 * roll
				+ rollIntegralGain * rollIntegral + pitchPropGain * pitch
				+ pitchDerivGain * pitchDeriv + pitchIntegralGain
				* pitchIntegral + rollTrim + pitchTrim;
		service.sendData();
		// Log.d("angle", String.valueOf(getAngleToHeading(90)));

		// / RENDERER
		// back of phone facing forwards
		CameraRenderer.cameraLook[0] = upVect[0];
		CameraRenderer.cameraLook[1] = -upVect[2];
		CameraRenderer.cameraLook[2] = -upVect[1];

		CameraRenderer.cameraUp[0] = -rtVect[0];
		CameraRenderer.cameraUp[1] = rtVect[2];
		CameraRenderer.cameraUp[2] = rtVect[1];

		String degreeSign = "" + (char) 0x00B0;

		CameraActivity.dataText = TextUtils.join("\n", Arrays.asList(
				dataStr("Pitch", pitch, 2, degreeSign),
				dataStr("Roll", roll, 2, degreeSign),
				dataStr("Heading", getHeading(), 2, degreeSign),
				dataStr("Bank Angle", bankAngle, 2, degreeSign),
				dataStr("Altitude", averagedAltitude, 2, "m"),
				dataStr("Raw altitude", location.getAltitude(), 2, "m"),
				dataStr("GPS accuracy", location.getAccuracy(), 1, "m"),
				dataStr("Ground speed", location.getSpeed() * 2.23693629f, 2,
						"mph")));
	}

	String dataStr(String name, Object value, int decimalPlaces, String units) {
		return name
				+ ": "
				+ String.format("%." + String.valueOf(decimalPlaces) + "f",
						value) + units;
	}

	float getHeading() {
		float aSin = (float) Math.toDegrees(Math.atan2(rtVect[0], rtVect[1]));
		return (aSin + 360) % 360;
	}

	float getAngleToHeading(float heading) {
		float[] headingVector = { (float) Math.sin(Math.toRadians(heading)),
				(float) Math.cos(Math.toRadians(heading)), 0 };
		float dot = headingVector[0] * rtVect[0] + headingVector[1] * rtVect[1];
		float[] flatRtVect = Utils.normalize(new float[] { rtVect[0],
				rtVect[1], 0 });
		float aSin = (float) Math.toDegrees(Math.asin(Utils.cross(
				headingVector, flatRtVect)[2]));
		if (dot > 0)
			return aSin;
		else if (aSin > 0)
			return 180 - aSin;
		else
			return -180 - aSin;
	}

	public float[] damp(float[] v1, float[] v2) {
		return Utils.normalize(Utils.add(Utils.mult(v1, damp),
				Utils.mult(v2, invdamp)));
	}

	public void close() {
		// fuseSensorsTimer.cancel();
		fuseSensorsThreadRunning = false;
		mSensorManager.unregisterListener(this);
		// locationManager.unregisterListener(this);
		if (flashCamera)
			cameraFlashThread.interrupt();
	} 

	@Override
	public void onLocationChanged(Location location) {
		this.location = location;
		timeOfLastLocation = System.currentTimeMillis();
	}

	@Override
	public void onProviderDisabled(String arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onProviderEnabled(String arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
		// TODO Auto-generated method stub

	}
}
