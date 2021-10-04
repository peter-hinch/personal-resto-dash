# personal-resto-dash

## Introduction

*Resto Dash* is an app for Android created to accompany a personal project of mine, the rebuild of a vintage air-cooled Volkswagen.

## Intent

To create an Android app that receives data from additional sensors on the car's engine and display that data in real time, eliminating the need for any additional physical gauges in the car. The data will be saved to a database on the Android device for later viewing / export.

## Methodology

1. Sensor data from readily available senders is collected by an Arduino board. My car dates before the inclusion of on board diagnostics like OBD/OBDII, so the collection of this data must be done manually.
2. A Bluetooth module attached to the Arduino board broadcasts this data to an Android device running the *Resto Dash* app. Link - [Arduino Recipe](https://github.com/peter-hinch/personal-resto-dash/wiki/Arduino-Recipe)
3. *Resto Dash* app processes the data, displaying it on screen as well as saving it for later viewing / export.

## Features Wishlist
- Basic features
- Database Integration
- Custom Views

I am developing a wiki describing the project here - [Resto Dash Wiki](https://github.com/peter-hinch/personal-resto-dash/wiki).
