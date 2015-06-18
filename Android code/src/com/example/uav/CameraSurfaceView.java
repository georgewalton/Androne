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

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.SurfaceHolder;

public class CameraSurfaceView extends GLSurfaceView {

    private final CameraRenderer mRenderer;

    public CameraSurfaceView(Context context) {
        super(context);

        // Create an OpenGL ES 2.0 context.
        setEGLContextClientVersion(2);

        // Set the Renderer for drawing on the GLSurfaceView
        mRenderer = new CameraRenderer(this);
        setRenderer(mRenderer);

        // Render the view only when there is a change in the drawing data
        //setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }
    
    public void surfaceCreated ( SurfaceHolder holder ) {
	    super.surfaceCreated ( holder );
	  }
	 
	  public void surfaceDestroyed ( SurfaceHolder holder ) {
	    mRenderer.close();
	    super.surfaceDestroyed ( holder );
	  }
	 
	  public void surfaceChanged ( SurfaceHolder holder, int format, int w, int h ) {
	    super.surfaceChanged ( holder, format, w, h );
	  }
	

}
