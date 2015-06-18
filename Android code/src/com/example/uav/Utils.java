package com.example.uav;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.apache.http.conn.util.InetAddressUtils;

public class Utils {
	static final float pi = 3.14159265359f;

	// Gets Local IP for displaying on the screen
	public static String getLocalIpAddress(boolean useIPv4) {
		try {
			List<NetworkInterface> interfaces = Collections
					.list(NetworkInterface.getNetworkInterfaces());
			for (NetworkInterface intf : interfaces) {
				List<InetAddress> addrs = Collections.list(intf
						.getInetAddresses());
				for (InetAddress addr : addrs) {
					if (!addr.isLoopbackAddress()) {
						String sAddr = addr.getHostAddress().toUpperCase(
								Locale.ENGLISH);
						boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
						if (useIPv4) {
							if (isIPv4)
								return sAddr;
						} else {
							if (!isIPv4) {
								// drop ip6 port suffix
								int delim = sAddr.indexOf('%');
								return delim < 0 ? sAddr : sAddr.substring(0,
										delim);
							}
						}
					}
				}
			}
		} catch (Exception e) {
		}
		return "";
	}
	
	// HTML tags for toast messages
	static String[] Bold = {"<b>","</b>"};
	static String[] Italics = {"<i>","</i>"};
	static String[] Green = {"<font color=\"green\">","</font>"};
	static String[] Orange = {"<font color=#E67E00>","</font>"};
	static String[] Maroon = {"<font color=\"maroon\">","</font>"};
	static String[] Red = {"<font color=\"red\">","</font>"};
	static String[] Blue = {"<font color=\"dodgerblue\">","</font>"};
	static String[] Black = {"<font color=\"black\">","</font>"};

	

	public static float invSqrt(float x) {
		if (x != 0)
			return (float) (1./Math.sqrt(x));
		else
			return 9999999;
	}

	public static float[] normalize(float[] vector) {
		float[] newVector = new float[3];
		float factor = Utils.invSqrt(vector[0] * vector[0] + vector[1]
				* vector[1] + vector[2] * vector[2]);
		for (int i = 0; i < 3; ++i) {
			newVector[i] = vector[i] * factor;
		}
		return newVector;
	}

	public static float[] cross(float[] v1, float[] v2) {
		float[] v3 = { v1[1] * v2[2] - v1[2] * v2[1],
				v1[2] * v2[0] - v1[0] * v2[2], v1[0] * v2[1] - v1[1] * v2[0] };
		return v3;
	}

	public static float[] mult(float[] vector, float factor) {
		float[] newVector = new float[3];
		for (int i = 0; i < 3; ++i) {
			newVector[i] = vector[i] * factor;
		}
		return newVector;
	}

	public static float[] add(float[] v1, float[] v2) {
		float[] v3 = new float[3];
		for (int i = 0; i < 3; ++i) {
			v3[i] = v1[i] + v2[i];
		}
		return v3;
	}

	public static float[] rotateZ(float[] vector, float angle) {
		float[] newVector = new float[3];
		newVector[0] = (float) (vector[0] * Math.cos(angle) + vector[1]
				* Math.sin(angle));
		newVector[1] = (float) (vector[1] * Math.cos(angle) - vector[0]
				* Math.sin(angle));
		newVector[2] = vector[2];
		return newVector;
	}

	public static float[][] invertCoordinateFrame(float[] x, float[] y,
			float[] z) {
		float[][] invM = new float[3][3]; // make sure det = 1 by normalizing
		float[] invX = { y[1] * z[2] - z[1] * y[2], z[1] * x[2] - x[1] * z[2],
				x[1] * y[2] - y[1] * x[2] };
		float[] invY = { z[0] * y[2] - y[0] * z[2], x[0] * z[2] - x[2] * z[0],
				y[0] * x[2] - x[0] * y[2] };
		float[] invZ = { y[0] * z[1] - z[0] * y[1], z[0] * x[1] - x[0] * z[1],
				x[0] * y[1] - x[1] * y[0] };
		invM[0] = invX;
		invM[1] = invY;
		invM[2] = invZ;
		return invM;
	}
}
