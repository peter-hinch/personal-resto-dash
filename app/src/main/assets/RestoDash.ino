#include <Adafruit_GPS.h>
#include <SoftwareSerial.h>

// Connect the GPS Power pin to 5V
// Connect the GPS Ground pin to ground
// Connect the GPS TX (transmit) pin to Digital 3
// Connect the GPS RX (receive) pin to Digital 2

SoftwareSerial mySerial(3, 2);
Adafruit_GPS GPS(&mySerial);

// Set GPSECHO to 'true' to debug and listen to raw GPS sentences. 
#define GPSECHO  false

// this keeps track of whether we're using the interrupt
// off by default!
boolean usingInterrupt = true;
void useInterrupt(boolean); // Func prototype keeps Arduino 0023 happy

void setup()  
{
    
  // connect at 115200 so we can read the GPS fast enough and echo without dropping chars
  // also spit it out
  Serial.begin(9600);

  // 9600 NMEA is the default baud rate for Adafruit MTK GPS's- some use 4800
  GPS.begin(9600);
  
  // uncomment this line to turn on RMC (recommended minimum) and GGA (fix data) including altitude
  GPS.sendCommand(PMTK_SET_NMEA_OUTPUT_RMCGGA);
  // uncomment this line to turn on only the "minimum recommended" data
  //GPS.sendCommand(PMTK_SET_NMEA_OUTPUT_RMCONLY);
  // For parsing data, we don't suggest using anything but either RMC only or RMC+GGA since
  // the parser doesn't care about other sentences at this time
  
  // Set the update rate
  GPS.sendCommand(PMTK_SET_NMEA_UPDATE_1HZ);   // 1 Hz update rate
  // For the parsing code to work nicely and have time to sort thru the data, and
  // print it out we don't suggest using anything higher than 1 Hz

  // Request updates on antenna status, comment out to keep quiet
  GPS.sendCommand(PGCMD_ANTENNA);

  // the nice thing about this code is you can have a timer0 interrupt go off
  // every 1 millisecond, and read data from the GPS for you. that makes the
  // loop code a heck of a lot easier!
  useInterrupt(true);

  delay(1000);
  // Ask for firmware version
  mySerial.println(PMTK_Q_RELEASE);
  
}


// Interrupt is called once a millisecond, looks for any new GPS data, and stores it
SIGNAL(TIMER0_COMPA_vect) {
  char c = GPS.read();
  // if you want to debug, this is a good time to do it!
#ifdef UDR0
  if (GPSECHO)
    if (c) UDR0 = c;  
    // writing direct to UDR0 is much much faster than Serial.print 
    // but only one character can be written at a time. 
#endif
}

void useInterrupt(boolean v) {
  if (v) {
    // Timer0 is already used for millis() - we'll just interrupt somewhere
    // in the middle and call the "Compare A" function above
    OCR0A = 0xAF;
    TIMSK0 |= _BV(OCIE0A);
    usingInterrupt = true;
  } else {
    // do not call the interrupt function COMPA anymore
    TIMSK0 &= ~_BV(OCIE0A);
    usingInterrupt = false;
  }
}

uint32_t timer = millis();
void loop()                     // run over and over again
{
  // in case you are not using the interrupt above, you'll
  // need to 'hand query' the GPS, not suggested :(
  if (! usingInterrupt) {
    // read data from the GPS in the 'main loop'
    char c = GPS.read();
    // if you want to debug, this is a good time to do it!
    if (GPSECHO)
      if (c) Serial.print(c);
  }
  
  // if a sentence is received, we can check the checksum, parse it...
  if (GPS.newNMEAreceived()) {
    // a tricky thing here is if we print the NMEA sentence, or data
    // we end up not listening and catching other sentences! 
    // so be very wary if using OUTPUT_ALLDATA and trytng to print out data
    //Serial.println(GPS.lastNMEA());   // this also sets the newNMEAreceived() flag to false
  
    if (!GPS.parse(GPS.lastNMEA()))   // this also sets the newNMEAreceived() flag to false
      return;  // we can fail to parse a sentence in which case we should just wait for another
  }

  // if millis() or timer wraps around, we'll just reset it
  if (timer > millis())  timer = millis();

  // approximately every 2 seconds or so, print out the current stats
  if (millis() - timer > 2000) { 
    timer = millis(); // reset the timer
    
    //timestamp in YYYY-MM-DDTHH:MM:SS.SSS+00:00 format (ISO 8601)
    
    //zero padded date
    Serial.print("20"); Serial.print(GPS.year, DEC);
    Serial.print("-");
    if (GPS.month < 10) { Serial.print("0"); Serial.print(GPS.month, DEC); } else { Serial.print(GPS.month, DEC); }
    Serial.print("-");
    if (GPS.day < 10) { Serial.print("0"); Serial.print(GPS.day, DEC); } else { Serial.print(GPS.day, DEC); }

    //zero padded time
    Serial.print("T");
    if (GPS.hour < 10) { Serial.print("0"); Serial.print(GPS.hour, DEC); } else { Serial.print(GPS.hour, DEC); }
    Serial.print(":");
    if (GPS.minute < 10) { Serial.print("0"); Serial.print(GPS.minute, DEC); } else { Serial.print(GPS.minute, DEC); }
    Serial.print(":");
    if (GPS.seconds < 10) { Serial.print("0"); Serial.print(GPS.seconds, DEC); } else { Serial.print(GPS.seconds, DEC); }
    Serial.print('.'); Serial.print(GPS.milliseconds);
    
    Serial.print("+00:00"); //timezone

    //gps data when signal present
    if (GPS.fix) {
      Serial.print(",");
      Serial.print(GPS.latitudeDegrees, 4); Serial.print(","); Serial.print(GPS.longitudeDegrees, 4); Serial.print(","); //latitude and longitude
      Serial.print(GPS.altitude); Serial.print(","); //altitude
      Serial.print(GPS.angle); Serial.print(","); //direction
      Serial.print(GPS.speed*1.852); Serial.print(","); //speed output (GPS.speed is in knots, mulitply by 1.852 for kph)
      Serial.print((int)GPS.fixquality); Serial.print(","); Serial.print((int)GPS.satellites); //fix quality and number of satellites
    }
    else {
      Serial.print(",,,,,,,"); //print equivalent amount of commas where no GPS signal is found (possibly unnecessary)
    }
    Serial.println();
  }
}
