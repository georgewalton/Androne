/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.uav;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

public class CameraRenderer implements GLSurfaceView.Renderer,
		SurfaceTexture.OnFrameAvailableListener {

	// private Square mSquare;

	// mMVPMatrix is an abbreviation for "Model View Projection Matrix"
	private final float[] mMVPMatrix = new float[16];
	private final float[] mProjectionMatrix = new float[16];
	private final float[] mViewMatrix = new float[16];

	// camera looks at this point
	static public float cameraLook[] = { 0f, 0f, 1f };
	// camera up vector
	static public float cameraUp[] = { 0f, 1f, 0f };

	List<Square> mSquares = new ArrayList<Square>();

	private final String vss = "attribute vec2 vPosition;\n"
			+ "attribute vec2 vTexCoord;\n" + "varying vec2 texCoord;\n"
			+ "void main() {\n" + "  texCoord = vTexCoord;\n"
			+ "  gl_Position = vec4 ( vPosition.x, vPosition.y, 0.0, 1.0 );\n"
			+ "}";

	private final String fss = "#extension GL_OES_EGL_image_external : require\n"
			+ "precision mediump float;\n"
			+ "uniform samplerExternalOES sTexture;\n"
			+ "varying vec2 texCoord;\n"
			+ "void main() {\n"
			+ "  gl_FragColor = texture2D(sTexture,texCoord);\n" + "}";

	private int[] hTex;
	private FloatBuffer pVertex;
	private FloatBuffer pTexCoord;
	private int hProgram;

	private Camera mCamera;
	private SurfaceTexture mSTexture;

	private boolean mUpdateST = false;

	private CameraSurfaceView mView;

	public CameraRenderer(CameraSurfaceView view) {
		mView = view;
		float[] vtmp = { 1.0f, -1.0f, -1.0f, -1.0f, 1.0f, 1.0f, -1.0f, 1.0f };
		float[] ttmp = { 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f };
		pVertex = ByteBuffer.allocateDirect(8 * 4)
				.order(ByteOrder.nativeOrder()).asFloatBuffer();
		pVertex.put(vtmp);
		pVertex.position(0);
		pTexCoord = ByteBuffer.allocateDirect(8 * 4)
				.order(ByteOrder.nativeOrder()).asFloatBuffer();
		pTexCoord.put(ttmp);
		pTexCoord.position(0);
	}

	public void close() {
		mUpdateST = false;
		mSTexture.release();
		mCamera.stopPreview();
		mCamera.release();
		mCamera = null;
		deleteTex();
	}

	@Override
	public void onSurfaceCreated(GL10 unused, EGLConfig config) {

		initTex();
		mSTexture = new SurfaceTexture(hTex[0]);
		mSTexture.setOnFrameAvailableListener(this);

		mCamera = Camera.open();
		try {
			mCamera.setPreviewTexture(mSTexture);
		} catch (IOException ioe) {
		}

		// Set the background frame color
		GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

		hProgram = loadShader(vss, fss);

		float groundColor[] = { 0.63671875f, 0.76953125f, 0.22265625f, 0.0f };

		for (int j = 0; j < 360; j += 10) {
			for (int i = -80; i <= 80; i += 20) {
				float thickness = 0.0025f;
				if (i == 0)
					thickness = 0.01f;
				mSquares.add(new Square(getCoords(thickness,
						(3.14f / 180f) * i, 3.14f / 36f, j * (3.14f / 180)),
						groundColor));
			}
		}

		for (int j = 0; j < 360; j += 45) {
			for (int i = -80; i <= 80; i += 20) {
				float thickness = 0.0025f;
				if (j == 90)
					thickness = 0.01f;
				mSquares.add(new Square(getCoords(3.14f / 18f, (3.14f / 180f)
						* i, thickness, j * (3.14f / 180)), groundColor));
			}
		}
	}

	float[] getCoords(double thi, double theta, double gap, double alpha) {
		// thi = half thickness of lines, theta = offset from horizon (both rad)
		float squareCoords[] = {
				(float) (5f * Math.cos(alpha - gap) * Math.cos(theta + thi)),
				(float) (5f * Math.sin(theta + thi)),
				(float) (5f * Math.cos(theta + thi) * Math.sin(alpha - gap)),
				(float) (5f * Math.cos(alpha - gap) * Math.cos(theta - thi)),
				(float) (5f * Math.sin(theta - thi)),
				(float) (5f * Math.cos(theta - thi) * Math.sin(alpha - gap)),
				(float) (5f * Math.cos(alpha + gap) * Math.cos(theta - thi)),
				(float) (5f * Math.sin(theta - thi)),
				(float) (5f * Math.cos(theta - thi) * Math.sin(alpha + gap)),
				(float) (5f * Math.cos(alpha + gap) * Math.cos(theta + thi)),
				(float) (5f * Math.sin(theta + thi)),
				(float) (5f * Math.cos(theta + thi) * Math.sin(alpha + gap)) };
		return squareCoords;
	}

	@Override
	public void onDrawFrame(GL10 unused) {

		// Draw background color
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

		synchronized (this) {
			if (mUpdateST) {
				mSTexture.updateTexImage();
				mUpdateST = false;
			}
		}

		GLES20.glUseProgram(hProgram);

		int ph = GLES20.glGetAttribLocation(hProgram, "vPosition");
		int tch = GLES20.glGetAttribLocation(hProgram, "vTexCoord");
		int th = GLES20.glGetUniformLocation(hProgram, "sTexture");

		GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
		GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, hTex[0]);
		GLES20.glUniform1i(th, 0);

		GLES20.glVertexAttribPointer(ph, 2, GLES20.GL_FLOAT, false, 4 * 2,
				pVertex);
		GLES20.glVertexAttribPointer(tch, 2, GLES20.GL_FLOAT, false, 4 * 2,
				pTexCoord);
		GLES20.glEnableVertexAttribArray(ph);
		GLES20.glEnableVertexAttribArray(tch);

		GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
		GLES20.glFlush();

		// Set the camera position (View matrix)
		Matrix.setLookAtM(mViewMatrix, 0, 0, 0, 0, cameraLook[0],
				cameraLook[1], cameraLook[2], cameraUp[0], cameraUp[1],
				cameraUp[2]);

		// Calculate the projection and view transformation
		Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0);

		// Draw squares
		for (Square square : mSquares) {
			square.draw(mMVPMatrix);
		}
	}

	@Override
	public void onSurfaceChanged(GL10 unused, int width, int height) {
		// Adjust the viewport based on geometry changes,
		// such as screen rotation
		GLES20.glViewport(0, 0, width, height);

		Camera.Parameters param = mCamera.getParameters();
		List<Size> psize = param.getSupportedPreviewSizes();

		/*
		 * for (Size s : psize) { Log.d("Camera resolution", "size: " + s.width
		 * + ", " + s.height + " ratio: " + ((float) s.width) / ((float)
		 * s.height)); }
		 */

		int sizeNumber = 6;// 5 // 10;
		param.setPreviewSize(psize.get(sizeNumber).width,
				psize.get(sizeNumber).height);
		param.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
		// param.setExposureCompensation(param.getMinExposureCompensation());
		// param.setAutoExposureLock(true);
		// param.setAutoWhiteBalanceLock(true);
		// param.setVideoStabilization(true);
		mCamera.setParameters(param);
		mCamera.setDisplayOrientation(90);
		mCamera.startPreview();

		float ratio = (float) psize.get(sizeNumber).width
				/ psize.get(sizeNumber).height;

		float hzoom = 1;// 0.95f;//1.1f; // these might be backwards
		float vzoom = 1;// 0.95f;//1.2f;

		// this projection matrix is applied to object coordinates
		// in the onDrawFrame() method
		Matrix.frustumM(mProjectionMatrix, 0, -ratio * hzoom, ratio * hzoom, -1
				* vzoom, 1 * vzoom, 3, 7);

	}

	private void initTex() {
		hTex = new int[1];
		GLES20.glGenTextures(1, hTex, 0);
		GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, hTex[0]);
		GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
				GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
				GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
				GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
		GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
				GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
	}

	private void deleteTex() {
		GLES20.glDeleteTextures(1, hTex, 0);
	}

	public synchronized void onFrameAvailable(SurfaceTexture st) {
		mUpdateST = true;
		mView.requestRender();
	}

	public static int loadShader(int type, String shaderCode) {

		// create a vertex shader type (GLES20.GL_VERTEX_SHADER)
		// or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
		int shader = GLES20.glCreateShader(type);

		// add the source code to the shader and compile it
		GLES20.glShaderSource(shader, shaderCode);
		GLES20.glCompileShader(shader);

		return shader;
	}

	private static int loadShader(String vss, String fss) {
		int vshader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
		GLES20.glShaderSource(vshader, vss);
		GLES20.glCompileShader(vshader);
		int[] compiled = new int[1];
		GLES20.glGetShaderiv(vshader, GLES20.GL_COMPILE_STATUS, compiled, 0);
		if (compiled[0] == 0) {
			Log.e("Shader", "Could not compile vshader");
			Log.v("Shader",
					"Could not compile vshader:"
							+ GLES20.glGetShaderInfoLog(vshader));
			GLES20.glDeleteShader(vshader);
			vshader = 0;
		}

		int fshader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
		GLES20.glShaderSource(fshader, fss);
		GLES20.glCompileShader(fshader);
		GLES20.glGetShaderiv(fshader, GLES20.GL_COMPILE_STATUS, compiled, 0);
		if (compiled[0] == 0) {
			Log.e("Shader", "Could not compile fshader");
			Log.v("Shader",
					"Could not compile fshader:"
							+ GLES20.glGetShaderInfoLog(fshader));
			GLES20.glDeleteShader(fshader);
			fshader = 0;
		}

		int program = GLES20.glCreateProgram();
		GLES20.glAttachShader(program, vshader);
		GLES20.glAttachShader(program, fshader);
		GLES20.glLinkProgram(program);

		return program;
	}

}