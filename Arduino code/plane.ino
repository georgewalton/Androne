#include <Servo.h>

String readString; // the bluetooth buffer

Servo rightWing;
Servo leftWing;
Servo throttle;

void setup() {
  Serial.begin(9600);
  pinMode(13,OUTPUT);
  
  rightWing.attach(9);
  rightWing.write(90);
  
  leftWing.attach(6);
  leftWing.write(90);
  
  throttle.attach(11);
  throttle.write(0);
  
  Serial.println("Hello world, I'm a plane!");
}

void loop() {
  if (Serial.available()){
    char c = Serial.read();
    if (c != ';')
      readString += c;
    else { // ';' is the terminating character
      //Serial.println(readString);
      if (readString[0] == 'r') {
        int rightAngle = readString.substring(1).toInt();
        if (rightAngle!=0) { // 0 means it was read wrong
          rightWing.write(rightAngle);
        }
      }
      else if (readString[0] == 'l') {
        int leftAngle = readString.substring(1).toInt();
        if (leftAngle!=0) { // 0 means it was read wrong
          leftWing.write(leftAngle);
        }
      }
      else if (readString[0] == 't') {
        int thrust = readString.substring(1).toInt();
        throttle.write(thrust);
      }
      readString = ""; // reset the buffer
    }
  }
}
