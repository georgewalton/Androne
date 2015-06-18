# Androne
The code for an android-phone-controlled UAV. It uses the in-built gyro, accelerometer, magnetometer and gps to fly, and can be communicated with while flying by texting it certain commands. And it will text back its location at regular intervals, or on demand, so you won't lose it. :)

The code is a bit of a mess but it works.

Here is a montage of video recorded on the phone (while the phone also flew the plane);

[![Monage video of Chevron UAV](http://img.youtube.com/vi/y_UEsg62AnY/0.jpg)](http://www.youtube.com/watch?v=y_UEsg62AnY)

Arduino
--
The android phone controls the plane by communicating with an arduino via bluetooth. You need to connect a bluetooth module to the arduino using the hardware RX/TX pins (the software serial library interferes with the servo library). You then need to set up the arduino to control the plane's servos and motor and pair the module to the phone.

How to make the UAV
--
Attach the phone to the plane and if everything goes to plan, you start the countdown on the plane, start the video recording, close the hatch, and then lob the plane into the air once the motor starts spinning. I used a slightly modified 150% sized Mugi Evo design. I'm afraid the android code is not very user friendly, you might have to do alot of digging to get it to work. Have bluetooth and gps turned on. You'll also have to tune the PID loop to your particular aircraft, which can be a nightmare. But if you succeed, you can have a long-range drone that records 1080p for under £150 (assuming you buy a used Nexus 4). I used a HTC One S to begin with, before switching to a Nexus 4 for its better camera. I strongly recommend rooting the phone to remove all of google's bloatware. If you try this, good luck!

Here is a video showing the actual UAV and the location of the phone and the arduino inside the body;

[![Closeup of Chevron UAV ](http://img.youtube.com/vi/xc9y7vD3fL0/0.jpg)](http://www.youtube.com/watch?v=xc9y7vD3fL0)

There is a small hole in the bottom of the plane that the camera on the phone looks through, to record the video while flying.
